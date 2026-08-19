package kr.kcocktail.search

import kr.kcocktail.cocktail.lifecycle.CocktailFixture
import kr.kcocktail.search.index.SearchReindexBatch
import kr.kcocktail.support.PostgresSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.support.TransactionTemplate

/**
 * ISSUE-053 — 검색 재색인 ([G-34](../../../../../../../docs/prd/GAPS.md)).
 *
 * ## 시드가 들어오는 길을 그대로 흉내 낸다
 *
 * [CocktailFixture] 는 **SQL 로** 칵테일을 만든다 — 시드(`R__seed_02_cocktail.sql`)와 같다.
 * 애플리케이션을 거치지 않으니 발행 이벤트가 없고, 그래서 색인이 비어 있다.
 * 실제로 41종이 발행됐는데 `search_document` 가 0행이던 상태가 이것이다.
 */
@SpringBootTest
class SearchReindexBatchTest {

    @Autowired private lateinit var batch: SearchReindexBatch
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var tx: TransactionTemplate

    private lateinit var cocktails: CocktailFixture

    @BeforeEach
    fun clear() {
        jdbc.execute("TRUNCATE cocktail, ingredient CASCADE")
        jdbc.execute("TRUNCATE search_document")
        cocktails = CocktailFixture(jdbc, tx)
    }

    /** RED 1 — SQL 로만 들어온 발행분이 재색인 뒤 검색된다. */
    @Test
    fun `시드로 들어온 발행분이 재색인 뒤 색인에 있다`() {
        val id = cocktails.cocktail(slug = "negroni", nameKo = "네그로니", nameEn = "negroni")
        publish(id)

        assertThat(indexedCount()).`as`("이벤트 없이 들어왔으니 색인이 비어 있어야 한다").isZero()

        val result = batch.run()

        assertAll(
            { assertThat(result.cocktails).isEqualTo(1) },
            { assertThat(published("cocktail", id)).`as`("공개로 안 올라갔다").isTrue() },
            {
                assertThat(
                    jdbc.queryForObject(
                        "SELECT chosung FROM search_document WHERE entity_type = 'cocktail' AND entity_id = ?",
                        String::class.java,
                        id,
                    ),
                ).`as`("초성이 안 채워졌다 — 규칙을 배치가 따로 쓰면 이렇게 된다").contains("ㄴㄱㄹㄴ")
            },
        )
    }

    /** RED 2 — 몇 번을 돌려도 같다. UPSERT 라 행이 늘지 않는다. */
    @Test
    fun `두 번 돌려도 행이 늘지 않는다`() {
        publish(cocktails.cocktail(slug = "gimlet", nameKo = "김렛", nameEn = "gimlet"))

        batch.run()
        val after = indexedCount()
        batch.run()

        assertThat(indexedCount()).isEqualTo(after)
    }

    /**
     * RED 3 — 발행이 아닌 것은 올리지 않는다.
     *
     * 색인된 적 없는 `draft` 는 행 자체가 없다. 있던 것을 내릴 때만 갱신이 일어난다
     * (DECISIONS §1.9 — 지우지 않고 내린다).
     */
    @Test
    fun `발행이 아니면 공개로 올리지 않는다`() {
        val draft = cocktails.cocktail(slug = "draft-one", nameKo = "초안", nameEn = "draft")
        val archived = cocktails.cocktail(slug = "archived-one", nameKo = "보관", nameEn = "archived")
        publish(archived)
        batch.run() // 한 번 올려 둔다
        jdbc.update("UPDATE cocktail SET status = 'archived' WHERE id = ?", archived)

        batch.run()

        assertAll(
            { assertThat(rowExists("cocktail", draft)).`as`("초안이 색인됐다").isFalse() },
            { assertThat(published("cocktail", archived)).`as`("보관인데 공개다").isFalse() },
        )
    }

    /**
     * RED 4 — 미승인 재료도 행은 만들되 내려 둔다.
     *
     * 리스너와 같은 규칙이다 — 승인 대기 중의 이름 변경을 따라가야 승인되는 순간
     * 최신 이름으로 검색된다.
     */
    @Test
    fun `미승인 재료는 행을 만들되 내려 둔다`() {
        val pending = jdbc.queryForObject(
            """INSERT INTO ingredient (slug, name_ko, name_en, category, domestic_availability, is_approved)
               VALUES ('campari', '캄파리', 'campari', 'liqueur', 'common', false) RETURNING id""",
            Long::class.java,
        )!!

        batch.run()

        assertAll(
            { assertThat(rowExists("ingredient", pending)).`as`("행이 없다").isTrue() },
            { assertThat(published("ingredient", pending)).`as`("미승인이 공개다").isFalse() },
        )
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────

    /** 시드와 같은 방식 — **이벤트 없이** 상태만 바꾼다. */
    private fun publish(id: Long) =
        jdbc.update("UPDATE cocktail SET status = 'published', published_at = now() WHERE id = ?", id)

    private fun indexedCount() =
        jdbc.queryForObject("SELECT count(*) FROM search_document", Long::class.java)!!

    private fun rowExists(type: String, id: Long) =
        jdbc.queryForObject(
            "SELECT count(*) FROM search_document WHERE entity_type = ? AND entity_id = ?",
            Long::class.java, type, id,
        )!! > 0

    private fun published(type: String, id: Long) =
        jdbc.queryForObject(
            "SELECT is_published FROM search_document WHERE entity_type = ? AND entity_id = ?",
            Boolean::class.java, type, id,
        )

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
            registry.add("kcocktail.verification.scheduled") { false }
        }
    }
}
