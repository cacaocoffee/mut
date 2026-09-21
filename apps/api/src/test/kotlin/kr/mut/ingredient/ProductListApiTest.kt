package kr.mut.ingredient

import kr.mut.common.web.ApiPaths
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get

/**
 * #205 — 공개 유통 제품 목록. 시드 4만 건은 두고 `manual` 출처로 시험 행을 넣고 지운다.
 */
class ProductListApiTest : IngredientApiSupport() {

    @Test
    fun `RED1,2 - 로그인 없이 200, id·링크·가격 없음, 제품명·수입사·유형으로 거르고 최근 신고순`() {
        val a = insertProduct("pubprod-test 위스키 A", "importer-a", "위스키", "2026-01-01")
        val b = insertProduct("pubprod-test 위스키 B", "importer-b", "위스키", "2026-06-01")
        val c = insertProduct("pubprod-test 리큐르", "importer-a", "리큐르", "2026-03-01")
        try {
            val all = pageOf(mvc.get("$PRODUCTS?q=pubprod-test").andReturn())
            val items = itemsOf(mvc.get("$PRODUCTS?q=pubprod-test").andReturn())
            assertThat(all["totalElements"]).isEqualTo(3)
            assertThat(items.map { it["nameKo"] })
                .containsExactly("pubprod-test 위스키 B", "pubprod-test 리큐르", "pubprod-test 위스키 A")
            assertThat(items[0].keys)
                .`as`("공개 응답엔 내부 id 가 없고(SPEC-07 §1.1), 링크·가격은 NFR-L-05 자문 뒤다")
                .doesNotContain("id", "source", "purchaseUrl", "price", "priceBand")

            assertThat(itemsOf(mvc.get("$PRODUCTS?q=importer-a").andReturn())).hasSize(2)
            // scope=name — 수입사는 안 본다. 레시피 줄의 "캄파리" 에 캄파리코리아의 와일드터키가 섞이지 않게 (#209)
            assertThat(itemsOf(mvc.get("$PRODUCTS?q=importer-a&scope=name").andReturn())).isEmpty()
            assertThat(itemsOf(mvc.get("$PRODUCTS?q=pubprod-test&scope=name").andReturn())).hasSize(3)
            assertThat(itemsOf(mvc.get("$PRODUCTS?q=pubprod-test&foodType=리큐르").andReturn()).map { it["nameKo"] })
                .containsExactly("pubprod-test 리큐르")

            val second = mvc.get("$PRODUCTS?q=pubprod-test&size=2&page=1").andReturn()
            assertThat(itemsOf(second)).hasSize(1)
            assertThat(pageOf(second)["totalPages"]).isEqualTo(2)
            @Suppress("UNCHECKED_CAST")
            val types = bodyOf(second)["foodTypes"] as List<Map<String, Any>>
            assertThat(types.map { it["foodType"] }).contains("위스키", "리큐르")
        } finally {
            exec("DELETE FROM distributed_product WHERE id IN ($a, $b, $c)")
        }
    }

    private fun insertProduct(nameKo: String, importer: String, foodType: String, reportedOn: String): Long =
        conn().use { c ->
            c.prepareStatement(
                """INSERT INTO distributed_product (source, source_key, name_ko, importer_or_maker, food_type, last_reported_on)
                   VALUES ('manual', ?, ?, ?, ?, ?::date) RETURNING id""",
            ).use { st ->
                st.setString(1, tag("public-product"))
                st.setString(2, nameKo)
                st.setString(3, importer)
                st.setString(4, foodType)
                st.setString(5, reportedOn)
                st.executeQuery().use { rs -> rs.next(); rs.getLong(1) }
            }
        }

    companion object {
        private val PRODUCTS = "${ApiPaths.BASE}/products"
    }
}
