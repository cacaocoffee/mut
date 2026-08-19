package kr.mut.cocktail.related

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kr.mut.search.facet.FacetCorpus
import kr.mut.support.PostgresSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.support.TransactionTemplate

/**
 * ISSUE-021 RED 8~10 · 16~21 — DB 가 필요한 것들.
 *
 * 순위 규칙 자체는 [RelatedRankerTest] 가 DB 없이 전수로 돈다.
 * 여기서는 **쿼리가 그 규칙에 맞는 후보를 주는지**와 규약을 본다.
 *
 * 코퍼스는 이슈 018·019 와 같은 8종이다 (`FacetCorpus`) — 세 API 가 같은 데이터를 봐야
 * 어긋났을 때 어느 쪽이 틀렸는지 알 수 있다.
 *
 * | 기준 | 같은 스타일 | 같은 기주 |
 * |---|---|---|
 * | `negroni` (spirit-forward · gin) | `espresso-martini` | `gin-tonic` |
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RelatedApiTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var json: ObjectMapper
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var tx: TransactionTemplate

    @BeforeAll
    fun seed() {
        jdbc.execute("TRUNCATE cocktail, ingredient CASCADE")
        tx.execute {
            FacetCorpus.rows().forEach { FacetCorpus.insert(jdbc, it) }
            // RED 9·10 — 발행되지 않은 것. 네그로니와 스타일·기주가 모두 같다
            FacetCorpus.insert(
                jdbc,
                FacetCorpus.rows().first().copy(slug = "draft-twin", nameKo = "초안 쌍둥이"),
                status = "draft",
            )
        }
    }

    // ── RED 8~10 : 발행분만 ───────────────────────────────────────────────

    /**
     * RED 9·10 — `draft-twin` 은 스타일·기주가 **둘 다 같아** 순위상 최상위여야 하지만
     * 발행되지 않아 아예 없다. 조건이 약하면 이 항목이 1등으로 올라온다.
     */
    @Test
    fun `RED8-10 - 발행분만 추천된다`() {
        val slugs = items("negroni").map { it["slug"].asText() }

        assertThat(slugs).isNotEmpty()
        assertThat(slugs).doesNotContain("draft-twin")
    }

    // ── RED 16~17 : 순위가 실제 데이터에서도 성립한다 ─────────────────────

    @Test
    fun `RED17 - 1순위 그룹의 style_primary 가 원본과 같다`() {
        val first = items("negroni").first()

        assertAll(
            { assertThat(first["matchedOn"].asText()).isIn("both", "style") },
            {
                assertThat(
                    jdbc.queryForObject(
                        "SELECT style_primary FROM cocktail WHERE slug = ?",
                        String::class.java,
                        first["slug"].asText(),
                    ),
                ).isEqualTo("spirit-forward")
            },
        )
    }

    /** 기주만 같은 것은 스타일이 같은 것보다 뒤다. 실제 코퍼스에서 확인한다. */
    @Test
    fun `스타일 일치가 기주 일치보다 앞선다`() {
        val byMatch = items("negroni").associate { it["slug"].asText() to it["matchedOn"].asText() }

        assertAll(
            { assertThat(byMatch).containsEntry("espresso-martini", "style") },
            { assertThat(byMatch).containsEntry("gin-tonic", "base") },
            {
                val order = items("negroni").map { it["slug"].asText() }
                assertThat(order.indexOf("espresso-martini")).isLessThan(order.indexOf("gin-tonic"))
            },
        )
    }

    /** 둘 다 아닌 것은 없다 (RED 5 의 통합 확인). */
    @Test
    fun `상관없는 칵테일은 추천되지 않는다`() {
        val slugs = items("negroni").map { it["slug"].asText() }

        assertThat(slugs).doesNotContain("pina-colada", "makgeolli-punch")
    }

    // ── RED 12~14 : 형태 ─────────────────────────────────────────────────

    /** RED 12 — 닮은 것이 없어도 **404 가 아니다.** 칵테일은 있다. */
    @Test
    fun `RED12 - 추천이 없으면 빈 배열이다`() {
        // 스타일도 기주도 혼자인 것
        val response = mvc.get(uri("pina-colada")).andReturn().response

        assertThat(response.status).isEqualTo(200)
        assertThat(items("pina-colada")).isEmpty()
    }

    @Test
    fun `RED13-14 - 카드 필드가 있고 내부 id 는 없다`() {
        val first = items("negroni").first()

        assertAll(
            { assertThat(first.fieldNames().asSequence().toList()).contains("slug", "nameKo", "nameEn", "summary") },
            { assertThat(first.has("id")).`as`("공개 식별자는 slug 다").isFalse() },
        )
    }

    @Test
    fun `없는 칵테일은 404 다`() {
        assertThat(mvc.get(uri("아예-없다")).andReturn().response.status).isEqualTo(404)
    }

    /** 대상이 `draft` 면 404 다 — 상세가 404 인데 배리에이션만 200 이면 존재가 샌다. */
    @Test
    fun `draft 대상의 배리에이션도 404 다`() {
        assertThat(mvc.get(uri("draft-twin")).andReturn().response.status).isEqualTo(404)
    }

    // ── RED 18~19 : 성능 ─────────────────────────────────────────────────

    /**
     * RED 18 — `(status, style_primary)` 인덱스 경로 (SPEC-06 §5).
     *
     * 8행짜리 코퍼스에서는 플래너가 seq scan 을 고르는 것이 정상이라 **계획을 단언하지 않는다.**
     * 인덱스의 존재를 본다 — 이슈 023 RED 28 에서 계획 단언이 데이터 크기에 흔들리는 것을 겪었다.
     */
    @Test
    fun `RED18 - 추천 조건의 인덱스가 있다`() {
        val indexes = jdbc.queryForList(
            "SELECT indexdef FROM pg_indexes WHERE tablename = 'cocktail'",
            String::class.java,
        ).map { it.replace(" ", "") }

        assertAll(
            { assertThat(indexes).anySatisfy { assertThat(it).contains("(status,style_primary") } },
            { assertThat(indexes).anySatisfy { assertThat(it).contains("(status,base_spirit") } },
        )
    }

    // ── RED 20~21 : 캐싱 · 색인 ──────────────────────────────────────────

    @Test
    fun `RED20 - 캐시 헤더가 붙는다`() {
        val response = mvc.get(uri("negroni")).andReturn().response

        assertAll(
            { assertThat(response.getHeader(HttpHeaders.ETAG)).isNotBlank() },
            { assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).contains("max-age") },
        )
    }

    /** RED 21 — 상세 페이지의 일부라 색인 대상이다. 필터·검색과 정반대다. */
    @Test
    fun `RED21 - noindex 가 붙지 않는다`() {
        assertThat(mvc.get(uri("negroni")).andReturn().response.getHeader("X-Robots-Tag").orEmpty())
            .doesNotContainIgnoringCase("noindex")
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────

    private fun items(slug: String): List<JsonNode> =
        json.readTree(
            mvc.get(uri(slug)).andReturn().response.getContentAsString(Charsets.UTF_8),
        )["items"].toList()

    private fun uri(slug: String) = "/api/v1/cocktails/$slug/related"

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { PostgresSupport.container.jdbcUrl }
            registry.add("spring.datasource.username") { PostgresSupport.container.username }
            registry.add("spring.datasource.password") { PostgresSupport.container.password }
            registry.add("spring.flyway.enabled") { true }
            registry.add("spring.flyway.user") { PostgresSupport.container.username }
            registry.add("spring.flyway.password") { PostgresSupport.container.password }
            registry.add("mut.verification.scheduled") { false }
        }
    }
}
