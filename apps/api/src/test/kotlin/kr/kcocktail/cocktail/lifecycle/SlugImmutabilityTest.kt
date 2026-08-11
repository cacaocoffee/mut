package kr.kcocktail.cocktail.lifecycle

import kr.kcocktail.cocktail.domain.SlugLockedException
import kr.kcocktail.support.PostgresSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.support.TransactionTemplate
import java.io.File

/**
 * ISSUE-014 RED 1~7 — `slug` 불변 (`INV-COCKTAIL-05`, `PRIN-D02`, `FR-COCKTAIL-014`).
 *
 * ## 왜 이렇게까지 막나
 *
 * `PRIN-D02` — 슬러그는 노출되는 순간 URL 이다. 바꾸면 **리다이렉트 부채**가 되고,
 * 남의 북마크와 검색 색인은 우리 사정을 모른다.
 * `NFR-D-04` 는 한 발 더 나가 "변경 이력 **0건**" 을 요구한다.
 */
@SpringBootTest
@ActiveProfiles(AuditLogTest.PROFILE) // 같은 컨텍스트를 쓴다 — 프로파일이 다르면 풀이 하나 더 뜬다
class SlugImmutabilityTest {

    @Autowired private lateinit var service: CocktailLifecycleService
    @Autowired private lateinit var publish: kr.kcocktail.cocktail.publish.PublishService
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var tx: TransactionTemplate

    @BeforeEach
    fun clear() {
        jdbc.execute("TRUNCATE cocktail, ingredient, audit_log CASCADE")
    }

    // ── RED 1~4 : 잠금 시점 ────────────────────────────────────────────────

    /** 아직 노출되지 않았다. URL 이 된 적이 없으니 바꿔도 끊길 링크가 없다. */
    @Test
    fun `RED1 - draft 상태에서는 slug 를 바꿀 수 있다`() {
        val id = fixture.cocktail()

        service.changeSlug(id, "gin-and-tonic")

        assertThat(slug(id)).isEqualTo("gin-and-tonic")
    }

    @Test
    fun `RED2 - 최초 발행 후 slug 변경이 거부된다`() {
        val id = fixture.cocktail()
        publish.publish(id)

        val thrown = assertThatThrownBy { service.changeSlug(id, "changed") }
            .isInstanceOf(SlugLockedException::class.java)

        thrown.extracting { (it as SlugLockedException).violations.map { v -> v.code } }
            .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(String::class.java))
            .containsExactly("INV-COCKTAIL-05")
    }

    /**
     * RED 3 — **"최초 발행 이후"** 다. 회수해서 `draft` 로 돌아가도 잠긴 채다.
     *
     * `status` 를 기준으로 삼았다면 "회수 → 변경 → 재발행" 으로 규칙을 빠져나간다.
     * 그래서 잠금 기준이 `published_at` 이다.
     */
    @Test
    fun `RED3 - 회수해서 draft 로 돌아와도 slug 를 못 바꾼다`() {
        val id = fixture.cocktail()
        publish.publish(id)
        publish.unpublish(id)

        assertThat(status(id)).`as`("확실히 draft 로 돌아왔다").isEqualTo("draft")
        assertThatThrownBy { service.changeSlug(id, "changed") }
            .isInstanceOf(SlugLockedException::class.java)
    }

    @Test
    fun `RED4 - archived 상태에서도 slug 를 못 바꾼다`() {
        val id = fixture.cocktail()
        publish.publish(id)
        publish.archive(id)

        assertThatThrownBy { service.changeSlug(id, "changed") }
            .isInstanceOf(SlugLockedException::class.java)
    }

    // ── RED 5 : 거부된 시도도 감사에 남는다 (NFR-D-04) ─────────────────────

    /**
     * RED 5 — **거부돼도 시도는 남긴다.**
     *
     * `NFR-D-04` 가 "발견 시 즉시 조사"를 요구한다. 요청이 롤백될 때 기록도 함께 사라지면
     * 발견할 것이 영영 없다. 그래서 별도 트랜잭션에서 쓰고 즉시 커밋한다.
     */
    @Test
    fun `RED5 - slug 변경 시도가 거부돼도 감사에 남는다`() {
        val id = fixture.cocktail()
        publish.publish(id)

        runCatching { service.changeSlug(id, "sneaky-slug") }

        val row = jdbc.queryForMap(
            "SELECT action, before->>'slug' AS b, after->>'slug' AS a FROM audit_log " +
                "WHERE entity_id = $id AND action = 'slug_change_attempt'",
        )
        assertThat(row["a"]).`as`("무엇으로 바꾸려 했는지가 조사의 시작이다").isEqualTo("sneaky-slug")
        assertThat(row["b"]).isEqualTo(slug(id))
    }

    /** 시도가 남았어도 **바뀌지는 않았다.** 기록과 효력은 다른 문제다. */
    @Test
    fun `RED5 - 시도가 기록돼도 slug 는 그대로다`() {
        val id = fixture.cocktail()
        publish.publish(id)
        val before = slug(id)

        runCatching { service.changeSlug(id, "sneaky-slug") }

        assertThat(slug(id)).isEqualTo(before)
    }

    // ── RED 6 : 재사용 금지 ────────────────────────────────────────────────

    /**
     * RED 6 — `PRIN-D02` "기존 슬러그를 **재사용하지 않는다**".
     *
     * 물리 삭제가 없으니 UNIQUE 가 보장한다 (`REVOKE DELETE ON cocktail`).
     * 여기서 명시적으로 확인하는 것은 **archived 도 자리를 계속 점유한다**는 점이다 —
     * 내렸다고 슬러그가 풀리면 옛 URL 이 다른 칵테일을 가리키게 된다.
     */
    @Test
    fun `RED6 - archived 가 된 slug 도 재사용할 수 없다`() {
        val id = fixture.cocktail()
        val taken = slug(id)
        publish.publish(id)
        publish.archive(id)

        assertThatThrownBy { fixture.cocktail(slug = taken) }
            .`as`("UNIQUE 가 막는다")
            .hasMessageContaining("uq_cocktail__slug")
    }

    // ── RED 7 : 마이그레이션에 slug 변경이 없다 (SPEC-06 §6) ───────────────

    /**
     * RED 7 — 앱을 다 막아도 **마이그레이션으로 바꾸면 그만**이다.
     * `NFR-D-04` 의 "0건" 은 DDL 이력까지 포함해야 뜻이 있다.
     */
    @Test
    fun `RED7 - slug 를 바꾸는 마이그레이션이 없다`() {
        val offending = File("src/main/resources/db/migration")
            .listFiles { f -> f.extension == "sql" }
            .orEmpty()
            .filter { file ->
                file.readText()
                    .lineSequence()
                    .filterNot { it.trimStart().startsWith("--") }
                    .any { line -> SLUG_UPDATE.containsMatchIn(line) }
            }
            .map { it.name }

        assertThat(offending)
            .`as`("slug 를 UPDATE 하는 마이그레이션은 PRIN-D02 위반이다")
            .isEmpty()
    }

    // ── 헬퍼 ───────────────────────────────────────────────────────────────

    private val fixture by lazy { CocktailFixture(jdbc, tx) }

    private fun slug(id: Long) = one("SELECT slug FROM cocktail WHERE id = $id")

    private fun status(id: Long) = one("SELECT status FROM cocktail WHERE id = $id")

    private fun one(sql: String): String? =
        jdbc.query(sql) { rs, _ -> rs.getString(1) }.firstOrNull()

    companion object {
        /** `UPDATE cocktail … SET … slug …` 를 한 줄 안에서 찾는다. */
        private val SLUG_UPDATE =
            Regex("""UPDATE\s+cocktail\b[^;]*\bset\b[^;]*\bslug\b""", RegexOption.IGNORE_CASE)

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { PostgresSupport.container.jdbcUrl }
            registry.add("spring.datasource.username") { PostgresSupport.container.username }
            registry.add("spring.datasource.password") { PostgresSupport.container.password }
            registry.add("spring.flyway.enabled") { true }
            registry.add("spring.flyway.user") { PostgresSupport.container.username }
            registry.add("spring.flyway.password") { PostgresSupport.container.password }
        }
    }
}
