package kr.kcocktail.contract

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import kr.kcocktail.support.PostgresSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledIfSystemProperty
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.io.File

/**
 * ISSUE-004 — OpenAPI 스펙 생성과 **드리프트 게이트** (`PRIN-T02`).
 *
 * > 계약이 깨지면 빌드가 깨져야 한다. 런타임에 발견하지 않는다.
 *
 * ## 왜 Gradle 플러그인이 아니라 테스트인가
 *
 * `org.springdoc.openapi-gradle-plugin` 은 앱을 실제로 기동해 `/v3/api-docs` 를 긁는다.
 * 그러려면 빌드가 DB 를 붙잡아야 하고, CI 에서 포트·기동 시간·헬스체크가 전부 실패 지점이 된다.
 *
 * 여기서는 이미 있는 Testcontainers 위에서 MockMvc 로 같은 문서를 뽑는다.
 * **드리프트 판정이 `check` 안으로 들어오는 것**이 더 중요하다 —
 * `git diff` 를 CI 스텝에만 두면 로컬에서는 아무도 모른 채 낡은 생성물을 커밋한다.
 *
 * 갱신은 `./gradlew generateOpenApiDocs`.
 */
@Tag("contract")
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiSpecTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var json: ObjectMapper

    /** RED 1 — 스펙이 생성된다. */
    @Test
    fun `RED1 - OpenAPI 문서가 생성된다`() {
        val spec = fetchSpec()

        assertThat(spec).containsKeys("openapi", "info", "components")
        assertThat(spec["openapi"].toString()).startsWith("3.")
    }

    /**
     * RED 2·7·8 — **이 이슈의 핵심.**
     *
     * 커밋된 `openapi.json` 이 현재 코드와 다르면 실패한다. 손으로 고친 것도 여기서 걸린다.
     * 이게 없으면 `PRIN-T02` 는 문서상 규칙으로만 남고, 드리프트는 조용히 쌓이다가 런타임에 터진다.
     */
    @Test
    @DisabledIfSystemProperty(named = WRITE_FLAG, matches = "true", disabledReason = "생성 모드")
    fun `RED7 - 커밋된 스펙이 최신이 아니면 실패한다`() {
        val committed = SPEC_FILE
        assertThat(committed)
            .`as`("커밋된 스펙이 없다. ./gradlew generateOpenApiDocs 로 만든다")
            .exists()

        assertThat(committed.readText())
            .`as`(
                """
                |커밋된 OpenAPI 스펙이 현재 코드와 다르다.
                |
                |    cd apps/api && ./gradlew generateOpenApiDocs
                |
                |로 갱신하고 함께 커밋한다. 손으로 고치지 않는다 (PRIN-T02).
                """.trimMargin(),
            )
            .isEqualTo(renderSpec())
    }

    /**
     * `./gradlew generateOpenApiDocs` 의 실체.
     *
     * 평소에는 건너뛴다 — 테스트가 소스 트리를 고치면 `check` 가 멱등하지 않게 되고,
     * 드리프트 게이트가 스스로를 통과시켜 버린다.
     */
    @Test
    @EnabledIfSystemProperty(named = WRITE_FLAG, matches = "true")
    fun `스펙을 파일로 쓴다`() {
        SPEC_FILE.writeText(renderSpec())
        println("생성: ${SPEC_FILE.absolutePath}")
    }

    // ── RED 10~14 : 분류 축 enum (PRIN-T02) ────────────────────────────────

    @Test
    fun `RED10-11 - 분류축 5종이 스키마에 있다`() {
        assertThat(schemas().keys)
            .contains("BaseSpirit", "StyleKey", "Technique", "FlavorKey", "SweetLevel")
    }

    /** RED 12 — enum 이름이 아니라 슬러그가 값이다. DB 와 URL 이 쓰는 것이 슬러그다. */
    @Test
    fun `RED12 - enum 값이 슬러그다`() {
        assertThat(enumValues("BaseSpirit"))
            .contains("non-alcoholic", "korean")
            .`as`("enum 이름(NON_ALCOHOLIC)이 새어 나오면 안 된다")
            .noneMatch { it.any(Char::isUpperCase) || it.contains('_') }

        assertThat(enumValues("StyleKey")).contains("spirit-forward")
    }

    /** RED 14 — ADR-0002 가 PRD 5.1 에서 고친 두 곳이 그대로 반영됐는가. */
    @Test
    fun `RED14 - ADR-0002 확정 슬러그와 일치한다`() {
        assertThat(enumValues("BaseSpirit")).containsExactly(
            "gin", "vodka", "whisky", "rum", "agave",
            "brandy", "liqueur", "wine", "korean", "non-alcoholic",
        )
        assertThat(enumValues("BaseSpirit"))
            .`as`("ADR-0002 §4 — 막걸리·문배주를 소주로 부르는 건 부정확하다")
            .doesNotContain("soju")
    }

    @Test
    fun `한국어 레이블은 타입이 아니라 확장으로 나간다`() {
        @Suppress("UNCHECKED_CAST")
        val labels = schema("BaseSpirit")["x-labels"] as Map<String, String>

        assertThat(labels["korean"]).isEqualTo("전통주")
        assertThat(labels["non-alcoholic"]).isEqualTo("무알콜")
        assertThat(enumValues("BaseSpirit"))
            .`as`("값에는 한국어가 없다")
            .allMatch { it.matches(Regex("^[a-z][a-z-]*$")) }
    }

    // ── 헬퍼 ───────────────────────────────────────────────────────────────

    private fun fetchSpec(): Map<String, Any> = json.readValue(rawSpec(), Map::class.java)
        .let {
            @Suppress("UNCHECKED_CAST")
            it as Map<String, Any>
        }

    /**
     * **UTF-8 을 명시한다.** MockMvc 의 `contentAsString` 은 응답에 charset 이 없으면
     * ISO-8859-1 로 읽는다 — 한국어 레이블이 소리 없이 깨져서 파일로 그대로 저장된다.
     */
    private fun rawSpec(): String =
        mvc.get("/v3/api-docs").andReturn().response.getContentAsString(Charsets.UTF_8)

    /**
     * 커밋 파일과 비교하려면 직렬화가 **결정적**이어야 한다.
     *
     * 키 순서가 실행마다 흔들리면 드리프트 게이트가 매번 빨개지고, 그러면 아무도 안 믿는다.
     * `ORDER_MAP_ENTRIES_BY_KEYS` 가 중첩 맵까지 정렬한다.
     */
    private fun renderSpec(): String = DETERMINISTIC
        .writerWithDefaultPrettyPrinter()
        .writeValueAsString(json.readValue(rawSpec(), Map::class.java)) + "\n"

    @Suppress("UNCHECKED_CAST")
    private fun schemas(): Map<String, Map<String, Any>> =
        (fetchSpec()["components"] as Map<String, Any>)["schemas"] as Map<String, Map<String, Any>>

    private fun schema(name: String): Map<String, Any> =
        schemas()[name] ?: error("스키마에 $name 이 없다")

    @Suppress("UNCHECKED_CAST")
    private fun enumValues(name: String): List<String> =
        schema(name)["enum"] as? List<String> ?: error("$name 이 enum 이 아니다")

    /** 시큐리티는 이슈 005~007. 여기서는 문서 엔드포인트만 열면 된다. */
    @TestConfiguration
    class OpenSecurity {
        @Bean
        fun openChain(http: HttpSecurity): SecurityFilterChain = http
            .csrf { it.disable() }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .build()
    }

    companion object {
        const val WRITE_FLAG = "openapi.write"

        /** Gradle 은 `apps/api` 에서 돈다. */
        val SPEC_FILE = File("openapi.json")

        val DETERMINISTIC: ObjectMapper = ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)

        @JvmStatic
        @DynamicPropertySource
        fun datasource(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { PostgresSupport.container.jdbcUrl }
            registry.add("spring.datasource.username") { PostgresSupport.container.username }
            registry.add("spring.datasource.password") { PostgresSupport.container.password }
            registry.add("spring.flyway.enabled") { true }
            registry.add("spring.flyway.user") { PostgresSupport.container.username }
            registry.add("spring.flyway.password") { PostgresSupport.container.password }
        }
    }
}
