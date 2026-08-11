package kr.kcocktail.ingredient.domain

/**
 * `FR-INGREDIENT-006` — 재료 카테고리 7종.
 *
 * ## `garnish` 만 `counts_for_stock` 이 `false` 다
 *
 * `R-F2.2-5` · `PRIN-D01` 의 요체다. 역검색(내 술장)에서 가니시·얼음·물을 빼지 않으면
 * **민트 잎 하나 없다고 모히토가 안 나온다.**
 *
 * 기본값일 뿐이라 레시피가 뒤집을 수 있다 — 가니시가 그 칵테일의 정체성인 경우가 있다.
 * 뒤집는 것은 이슈 010 의 `recipe_ingredient` 가 한다.
 */
enum class IngredientCategory(
    val slug: String,
    /** 역검색에서 이 재료를 "있어야 하는 것"으로 셀지의 기본값. */
    val defaultCountsForStock: Boolean,
) {
    SPIRIT("spirit", true),
    LIQUEUR("liqueur", true),
    BITTERS("bitters", true),
    SYRUP("syrup", true),
    JUICE("juice", true),

    /** 민트 잎 · 라임 슬라이스 · 체리. 없다고 못 만드는 것이 아니다. */
    GARNISH("garnish", false),

    MIXER("mixer", true),
    ;

    companion object {
        fun ofSlug(slug: String): IngredientCategory =
            entries.firstOrNull { it.slug == slug } ?: error("알 수 없는 재료 카테고리: $slug")

        /** 이슈 010 의 `recipe_ingredient` 가 이 함수를 호출한다 (RED 4). */
        fun defaultCountsForStock(category: IngredientCategory): Boolean =
            category.defaultCountsForStock
    }
}
