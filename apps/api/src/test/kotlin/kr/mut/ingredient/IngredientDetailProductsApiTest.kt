package kr.mut.ingredient

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import java.sql.Connection

/**
 * #196 — 재료 상세가 승인된 유통 제품과 마지막 신고일을 낸다 (G-41 · `FR-INGREDIENT-002`).
 *
 * 제품 행은 `manual` 출처로 넣고 끝나면 지운다 — 시드 4만 건은 공유 컨테이너의 것이다.
 */
class IngredientDetailProductsApiTest : IngredientApiSupport() {

    @Test
    fun `RED2,4 - 승인된 매핑만 products 로 나오고, 제품명·수입사만 있고 링크·가격은 없다`() {
        val slug = tag("with-products")
        val id = insertIngredient(slug)
        val newer = insertProduct("테스트 캄파리 1L", "2026-07-30")
        val older = insertProduct("테스트 캄파리 700ml", "2025-01-15")
        val suggestedOnly = insertProduct("테스트 캄파리 미승인", "2026-09-01")
        insertMatch(id, newer, "approved")
        insertMatch(id, older, "approved")
        insertMatch(id, suggestedOnly, "suggested")
        try {
            val body = bodyOf(mvc.get("$BASE/$slug").andReturn())

            assertThat(body["lastReportedOn"]).isEqualTo("2026-07-30")
            @Suppress("UNCHECKED_CAST")
            val products = body["products"] as List<Map<String, Any?>>
            assertThat(products.map { it["nameKo"] }).containsExactly("테스트 캄파리 1L", "테스트 캄파리 700ml")
            assertThat(products[0]["importerOrMaker"]).isEqualTo("테스트수입")
            assertThat(products[0].keys)
                .`as`("구매 링크·가격은 NFR-L-05 자문 뒤다 — 필드 자체가 없어야 한다")
                .doesNotContain("purchaseUrl", "price", "priceBand", "id")
        } finally {
            exec("DELETE FROM distributed_product WHERE id IN ($newer, $older, $suggestedOnly)")
        }
    }

    @Test
    fun `매핑이 없으면 products 는 비고 lastReportedOn 은 null 이다`() {
        val slug = tag("no-products")
        insertIngredient(slug)

        val body = bodyOf(mvc.get("$BASE/$slug").andReturn())

        assertThat(body["products"] as List<*>).isEmpty()
        assertThat(body["lastReportedOn"]).isNull()
    }

    private fun insertProduct(nameKo: String, reportedOn: String): Long = conn().use { c: Connection ->
        c.prepareStatement(
            """INSERT INTO distributed_product (source, source_key, name_ko, importer_or_maker, food_type, last_reported_on)
               VALUES ('manual', ?, ?, '테스트수입', '리큐르', ?::date) RETURNING id""",
        ).use { st ->
            st.setString(1, tag("product"))
            st.setString(2, nameKo)
            st.setString(3, reportedOn)
            st.executeQuery().use { rs -> rs.next(); rs.getLong(1) }
        }
    }

    private fun insertMatch(ingredientId: Long, productId: Long, status: String) = exec(
        """INSERT INTO ingredient_product_match (ingredient_id, product_id, status, matched_keyword, confidence)
           VALUES ($ingredientId, $productId, '$status', '테스트', 80)""",
    )
}
