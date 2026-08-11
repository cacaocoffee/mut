package kr.kcocktail.cocktail.recipe

/**
 * SPEC-02 §2.6 — 레시피 버전 3종.
 *
 * `PRIN-D03` 이 Cocktail 과 Recipe 를 분리한 이유가 이것이다 —
 * 칵테일 하나에 **에디터 표준 1개 + 제휴 바 버전 n개**가 공존해야 한다.
 *
 * > `bar_signature` 가 파트너 상품의 가장 강력한 셀링 포인트다.
 * > 홈텐더가 레시피 검색으로 들어와 바를 알게 되는 **역방향 유입**이 여기서 생긴다.
 */
enum class RecipeVersionType(val slug: String) {
    /** 에디터가 쓴다. 칵테일당 **정확히 1개** (`INV-COCKTAIL-07`). 기본 노출. */
    STANDARD("standard"),

    /** 제휴 바. Phase 1b. 여럿 가능하다. */
    BAR_SIGNATURE("bar_signature"),

    /** 유저. v2. */
    USER("user"),
    ;

    companion object {
        fun ofSlug(slug: String): RecipeVersionType =
            entries.firstOrNull { it.slug == slug } ?: error("알 수 없는 레시피 버전: $slug")
    }
}

/**
 * SPEC-02 §2.7 — 계량 단위 5종.
 *
 * `top_up` 은 "채운다"라서 [amountRequired] 가 `false` 다 — 토닉워터를 몇 ml 붓는지
 * 정하는 순간 잔 크기에 종속되고, 그러면 잔 수 환산이 틀어진다.
 */
enum class MeasureUnit(val slug: String, val amountRequired: Boolean) {
    ML("ml", true),
    DASH("dash", true),
    BARSPOON("barspoon", true),
    PIECE("piece", true),

    /** 잔을 채운다. 수량이 없다. */
    TOP_UP("top_up", false),
    ;

    companion object {
        fun ofSlug(slug: String): MeasureUnit =
            entries.firstOrNull { it.slug == slug } ?: error("알 수 없는 단위: $slug")
    }
}

/** SPEC-02 §2.7 — 레시피 안에서의 역할 5종. */
enum class IngredientRole(val slug: String) {
    BASE("base"),
    MODIFIER("modifier"),
    SWEETENER("sweetener"),
    CITRUS("citrus"),
    GARNISH("garnish"),
    ;

    companion object {
        fun ofSlug(slug: String): IngredientRole =
            entries.firstOrNull { it.slug == slug } ?: error("알 수 없는 역할: $slug")
    }
}
