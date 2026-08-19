package kr.mut.cocktail.lifecycle

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate

/**
 * **발행 게이트 6종을 전부 통과하는** 칵테일을 만든다 (이슈 013 `PublishGate`).
 *
 * 이슈 014 의 테스트는 전부 "발행된 것" 을 출발점으로 삼는다. 게이트를 통과하지 못하면
 * `publish()` 가 `422` 로 끝나 정작 보려던 것(잠금 · 감사 · 이벤트)에 닿지 못한다.
 *
 * 한 트랜잭션으로 묶는 이유: `fk_cocktail__style_primary` 가 `DEFERRABLE INITIALLY DEFERRED`
 * 다 (`V009__cocktail.sql`). 칵테일 행과 스타일 행이 서로를 가리키므로 **커밋 시점에 함께**
 * 봐야 한다 — 문장마다 커밋하면 첫 INSERT 에서 걸린다.
 */
class CocktailFixture(
    private val jdbc: JdbcTemplate,
    private val tx: TransactionTemplate,
) {

    private var seq = 0

    fun cocktail(
        slug: String? = null,
        nameKo: String = "진토닉",
        nameEn: String = "gin tonic",
    ): Long = tx.execute { insert(slug, nameKo, nameEn) }!!

    private fun insert(slug: String?, nameKo: String, nameEn: String): Long {
        val n = seq++
        val ingredientId = jdbc.queryForObject(
            """INSERT INTO ingredient (slug, name_ko, name_en, category, domestic_availability, is_approved)
               VALUES ('gin-$n', '진', 'gin', 'spirit', 'common', true) RETURNING id""",
            Long::class.java,
        )!!

        val cocktailId = jdbc.queryForObject(
            """
            INSERT INTO cocktail (slug, name_ko, name_en, summary,
                base_spirit, style_primary, method, sweetness, glass_type,
                abv_calculated, tasting_note)
            VALUES (?, ?, ?, '가장 흔한 하이볼',
                'gin', 'highball', 'build', 'dry', '하이볼 글라스',
                12, '쌉싸름한 진 향에 토닉의 단맛이 얹힌다')
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            slug ?: "gin-tonic-$n",
            nameKo,
            nameEn,
        )!!

        jdbc.execute("INSERT INTO cocktail_style VALUES ($cocktailId, 'highball')")
        jdbc.execute("INSERT INTO cocktail_aroma_tag VALUES ($cocktailId, 'citrus')")

        val recipeId = jdbc.queryForObject(
            "INSERT INTO recipe (cocktail_id, version_type) VALUES ($cocktailId, 'standard') RETURNING id",
            Long::class.java,
        )!!
        jdbc.execute(
            "INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit) " +
                "VALUES ($recipeId, $ingredientId, 1, 45, 'ml')",
        )
        jdbc.execute("INSERT INTO recipe_step (recipe_id, step_no, text) VALUES ($recipeId, 1, '얼음을 채운다')")

        return cocktailId
    }
}
