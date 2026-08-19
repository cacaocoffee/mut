package kr.mut.ingredient.domain

/**
 * `PRIN-P05` — 국내 유통 현황.
 *
 * > 이 서비스가 해외 DB 의 번역판이 아닌 이유는 이 축 하나다.
 *
 * 레시피에 있는 재료를 국내에서 못 구하면 그 레시피는 읽을거리일 뿐이다.
 * 그래서 [needsSubstitute] 인 재료는 대체재 안내가 **필수**다 (`INV-INGREDIENT-01`).
 */
enum class DomesticAvailability(val slug: String, val labelKo: String) {
    /** 마트·편의점. */
    COMMON("common", "쉽게 구할 수 있음"),

    /** 주류 전문점 · 온라인. */
    SPECIALTY("specialty", "전문점"),

    /** 직구 · 해외 구매. */
    IMPORT_ONLY("import_only", "해외 구매만"),

    /** 국내 유통 없음. */
    UNAVAILABLE("unavailable", "국내 유통 없음"),
    ;

    /**
     * `INV-INGREDIENT-01` (`R-F1.3-2`) — 대체재 또는 자가제조 안내가 필수인가.
     *
     * DB CHECK 와 **같은 조건**이다 (`ck_ingredient__substitute`). 한쪽만 고치면 어긋난다.
     */
    val needsSubstitute: Boolean get() = this == IMPORT_ONLY || this == UNAVAILABLE

    companion object {
        fun ofSlug(slug: String): DomesticAvailability =
            entries.firstOrNull { it.slug == slug } ?: error("알 수 없는 유통 현황: $slug")
    }
}
