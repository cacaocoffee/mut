package kr.kcocktail.ingredient

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get

/**
 * ISSUE-023 — `GET /ingredients/{slug}/cocktails` (`R-F1.3-1`).
 *
 * `PRIN-P01` 의 그래프가 재료 쪽에서 흐르는 지점이다. 사전이 사전으로만 끝나면
 * "이 재료로 뭘 만들 수 있나"라는 질문에 답하지 못한다.
 *
 * ## 세 가지를 이슈가 확정했다
 *
 * | 질문 | 결정 | RED |
 * |---|---|---|
 * | `bar_signature` 에만 쓰인 재료도 세나 | **표준 레시피만** | 24 |
 * | `is_optional` 재료도 포함하나 | **포함하되 표시** | 25 |
 * | 대체재로만 등장하는 칵테일도 포함하나 | **제외** | 26 |
 */
class IngredientCocktailsApiTest : IngredientApiSupport() {

    /** RED 22 — SPEC-07 §5. 공개 조회는 `published` 만 본다. */
    @Test
    fun `RED22 - 발행된 칵테일만 반환된다`() {
        val ingredientSlug = tag("only-published")
        val id = insertIngredient(ingredientSlug)

        val published = tag("is-published")
        insertRecipeIngredient(insertRecipe(insertCocktail(published)), id)

        val result = mvc.get("$BASE/$ingredientSlug/cocktails").andReturn()

        assertThat(slugsOf(result)).containsExactly(published)
    }

    /** RED 23 — 발행 전 칵테일이 재료 사전 경유로 새어 나가면 회수의 의미가 없다. */
    @Test
    fun `RED23 - draft 칵테일이 없다`() {
        val ingredientSlug = tag("no-draft")
        val id = insertIngredient(ingredientSlug)

        val draft = tag("still-draft")
        val archived = tag("archived-one")
        insertRecipeIngredient(insertRecipe(insertCocktail(draft, status = "draft")), id)
        insertRecipeIngredient(insertRecipe(insertCocktail(archived, status = "archived")), id)

        assertThat(slugsOf(mvc.get("$BASE/$ingredientSlug/cocktails").andReturn()))
            .isEmpty()
    }

    /**
     * RED 24 **결정** — 표준 레시피만 센다.
     *
     * `bar_signature`(Phase 1b) 는 특정 바의 변주라 "이 재료를 쓰는 칵테일"의 답이 아니다.
     * 넣기 시작하면 바 하나가 재료를 넣었다는 이유로 사전 목록이 늘어난다.
     */
    @Test
    fun `RED24 - 표준 레시피 기준이다`() {
        val ingredientSlug = tag("standard-only")
        val id = insertIngredient(ingredientSlug)

        val barOnly = tag("bar-signature-only")
        insertRecipeIngredient(
            insertRecipe(insertCocktail(barOnly), versionType = "bar_signature", barId = 1),
            id,
        )

        val standard = tag("has-standard")
        insertRecipeIngredient(insertRecipe(insertCocktail(standard)), id)

        val returned = slugsOf(mvc.get("$BASE/$ingredientSlug/cocktails").andReturn())

        assertThat(returned).contains(standard)
        assertThat(returned).`as`("바 시그니처에만 쓰인 것은 세지 않는다").doesNotContain(barOnly)
    }

    /**
     * RED 25 **결정** — 포함하되 표시한다.
     *
     * 빼면 "이 재료를 쓰는 칵테일"이 사실과 달라지고, 표시 없이 넣으면
     * 사용자가 필수 재료로 오해한다. 판단 재료를 주고 화면이 정하게 한다.
     */
    @Test
    fun `RED25 - 선택 재료도 포함되는가`() {
        val ingredientSlug = tag("optional-use")
        val id = insertIngredient(ingredientSlug)

        val optional = tag("uses-optionally")
        insertRecipeIngredient(insertRecipe(insertCocktail(optional)), id, isOptional = true)

        val required = tag("uses-required")
        insertRecipeIngredient(insertRecipe(insertCocktail(required)), id, isOptional = false)

        val items = itemsOf(mvc.get("$BASE/$ingredientSlug/cocktails").andReturn())
            .associateBy { it["slug"] }

        assertThat(items.keys).contains(optional, required)
        assertThat(items[optional]!!["isOptional"]).`as`("표시가 없으면 필수로 오해한다").isEqualTo(true)
        assertThat(items[required]!!["isOptional"]).isEqualTo(false)
    }

    /**
     * RED 26 **결정** — 제외한다.
     *
     * `substitute_ingredient_id` 는 "그 재료가 없을 때의 대안"이지 그 칵테일이 쓰는 재료가 아니다.
     * 넣으면 진을 쓰지 않는 칵테일이 진의 사전에 올라온다.
     */
    @Test
    fun `RED26 - 대체재로만 등장하는 칵테일도 포함되는가`() {
        val substituteSlug = tag("as-substitute")
        val substituteId = insertIngredient(substituteSlug)
        val mainId = insertIngredient(tag("as-main"))

        val cocktail = tag("substitute-only")
        insertRecipeIngredient(
            insertRecipe(insertCocktail(cocktail)),
            mainId,
            substituteIngredientId = substituteId,
        )

        assertThat(slugsOf(mvc.get("$BASE/$substituteSlug/cocktails").andReturn()))
            .`as`("대체재로만 등장하면 그 칵테일이 이 재료를 쓰는 것은 아니다")
            .isEmpty()
    }

    @Test
    fun `RED27 - 페이징된다`() {
        val ingredientSlug = tag("paged-usage")
        val id = insertIngredient(ingredientSlug)
        repeat(3) { insertRecipeIngredient(insertRecipe(insertCocktail(tag("paged-usage-c"))), id) }

        val first = mvc.get("$BASE/$ingredientSlug/cocktails") {
            param("page", "0"); param("size", "2")
        }.andReturn()
        val page = pageOf(first)

        assertThat(page.keys).containsExactlyInAnyOrder("number", "size", "totalElements", "totalPages")
        assertThat(page["number"]).isEqualTo(0)
        assertThat(page["size"]).isEqualTo(2)
        assertThat(page["totalElements"]).isEqualTo(3)
        assertThat(page["totalPages"]).isEqualTo(2)
        assertThat(itemsOf(first)).hasSize(2)

        val second = mvc.get("$BASE/$ingredientSlug/cocktails") {
            param("page", "1"); param("size", "2")
        }.andReturn()

        assertThat(itemsOf(second)).hasSize(1)
        assertThat(slugsOf(second)).doesNotContainAnyElementsOf(slugsOf(first))
    }

    /** 같은 칵테일이 한 레시피에서 두 줄에 걸쳐도 목록에는 한 번만 나온다. */
    @Test
    fun `한 칵테일이 같은 재료를 두 줄에 써도 중복되지 않는다`() {
        val ingredientSlug = tag("twice-in-one")
        val id = insertIngredient(ingredientSlug)

        val cocktail = tag("uses-twice")
        val recipe = insertRecipe(insertCocktail(cocktail))
        insertRecipeIngredient(recipe, id, position = 1, isOptional = true)
        insertRecipeIngredient(recipe, id, position = 2, isOptional = false)

        val items = itemsOf(mvc.get("$BASE/$ingredientSlug/cocktails").andReturn())

        assertThat(items).hasSize(1)
        assertThat(items.single()["isOptional"])
            .`as`("한 줄이라도 필수면 필수다")
            .isEqualTo(false)
    }

    @Test
    fun `미승인 재료의 칵테일 목록은 404다`() {
        val slug = tag("pending-usage")
        insertIngredient(slug, approved = false)

        mvc.get("$BASE/$slug/cocktails").andExpect { status { isNotFound() } }
        mvc.get("$BASE/없는-재료-슬러그/cocktails").andExpect { status { isNotFound() } }
    }

    @Test
    fun `칵테일 항목에 내부 id가 없다`() {
        val ingredientSlug = tag("usage-no-id")
        val id = insertIngredient(ingredientSlug)
        insertRecipeIngredient(insertRecipe(insertCocktail(tag("usage-no-id-c"))), id)

        assertThat(itemsOf(mvc.get("$BASE/$ingredientSlug/cocktails").andReturn()))
            .isNotEmpty()
            .allSatisfy {
                assertThat(it).containsKey("slug")
                assertThat(it).doesNotContainKey("id")
            }
    }

    /**
     * RED 28 — SPEC-06 §5 가 `recipe_ingredient(ingredient_id)` 인덱스를 둔 목적이 이 쿼리다.
     *
     * ## 왜 `enable_seqscan = off` 인가
     *
     * 테스트 DB 는 행이 적어 플래너가 항상 순차 스캔을 고른다 — 인덱스가 있든 없든 같은 계획이 나온다.
     * 순차 스캔을 막고 계획을 뽑으면 **이 쿼리 모양이 인덱스를 탈 수 있는가**를 본다.
     * 조건을 함수로 감싸거나 타입이 어긋나면 그때는 인덱스가 후보에서 빠져 계획에 나타나지 않는다.
     */
    @Test
    fun `RED28 - 인덱스를 탄다`() {
        val id = insertIngredient(tag("explain-me"))
        val recipes = List(20) { insertRecipe(insertCocktail(tag("explain-c"))) }
        recipes.forEach { insertRecipeIngredient(it, id) }

        // 다른 재료로 행을 채운다. **이게 없으면 인덱스가 이길 수 없다** —
        // 모든 행의 ingredient_id 가 같으면 그 조건이 아무것도 걸러 내지 못해,
        // 플래너가 PK 로 훑고 필터하는 편을 고른다. 실제 데이터(재료 300종)에서는
        // 이 조건이 선택적이므로, 픽스처가 그 성질을 흉내 내야 계획이 같아진다.
        val others = List(10) { insertIngredient(tag("explain-other")) }
        recipes.forEach { recipe ->
            others.forEachIndexed { i, other -> insertRecipeIngredient(recipe, other, position = i + 2) }
        }

        // 통계를 세 테이블 다 갱신한다. 하나만 하면 나머지 추정이 낡아 계획이 흔들린다.
        exec("ANALYZE recipe_ingredient")
        exec("ANALYZE recipe")
        exec("ANALYZE cocktail")

        val plan = conn().use { c ->
            c.createStatement().use { st ->
                st.execute("SET enable_seqscan = off")
                st.executeQuery(
                    """
                    EXPLAIN
                    SELECT DISTINCT c.slug
                    FROM recipe_ingredient ri
                    JOIN recipe r   ON r.id = ri.recipe_id AND r.version_type = 'standard'
                    JOIN cocktail c ON c.id = r.cocktail_id AND c.status = 'published'
                    WHERE ri.ingredient_id = $id
                    """.trimIndent(),
                ).use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
            }
        }.joinToString("\n")

        assertThat(plan)
            .`as`("계획:\n%s", plan)
            .contains("ix_recipe_ingredient__ingredient")
    }
}
