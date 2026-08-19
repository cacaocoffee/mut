package kr.mut.common.web.error

import org.springframework.http.HttpStatus

/**
 * SPEC-02 의 불변식·발행 게이트 ID 를 그대로 쓴다 (SPEC-07 §1.4).
 *
 * **클라이언트가 문구가 아니라 코드로 분기할 수 있어야 한다.** 문구는 바뀌고 번역되지만
 * 코드는 스펙과 함께 간다. 문자열 리터럴을 코드 여기저기에 흩뿌리지 않는 이유다.
 *
 * ## Phase 1a 범위인 15종만 둔다
 *
 * SPEC-02 에는 25종이 있다. BAR · CONTENT 는 Phase 1b·2 라 아직 넣지 않는다 —
 * 쓰지 않는 코드가 enum 에 있으면 클라이언트가 대응해야 할 목록이 부풀고,
 * 어느 것이 실제로 오는지 알 수 없게 된다.
 *
 * **`INV-PARTNER-01`·`02` 는 앞으로도 넣지 않는다.** 정렬 상위 부스팅 한도와 홈 슬롯 비율은
 * 노출 규칙이고, `PRIN-P02` 가 **DB 컬럼도 API 도 어드민 입력란도 만들지 말라**고 했다.
 * 위반 코드로 표현하는 순간 그 규칙이 있다는 뜻이 된다. 이슈 027 이 부재를 검증한다.
 */
enum class ViolationCode(val code: String, val status: HttpStatus) {

    // ── 칵테일 불변식 (SPEC-02 §2.1) ────────────────────────────────────────
    /** 분류 3축은 전부 NOT NULL (`R-C-1`) */
    INV_COCKTAIL_01("INV-COCKTAIL-01"),

    /** `styles` 는 최소 1개 (`R-C-1`) */
    INV_COCKTAIL_02("INV-COCKTAIL-02"),

    /** `style_primary ∈ styles` (`R-C-3`) */
    INV_COCKTAIL_03("INV-COCKTAIL-03"),

    /** `aroma_tags` 1~3개 (`R-F1.2-1`) */
    INV_COCKTAIL_04("INV-COCKTAIL-04"),

    /** `slug` 는 발행 후 불변 (`PRIN-D02`) */
    INV_COCKTAIL_05("INV-COCKTAIL-05", HttpStatus.CONFLICT),

    /** `base_spirit = non-alcoholic` ⟺ `abv = 0` */
    INV_COCKTAIL_06("INV-COCKTAIL-06"),

    /** 표준 레시피(`version_type = standard`)가 정확히 1개 (`R-F1.1-7`) */
    INV_COCKTAIL_07("INV-COCKTAIL-07"),

    // ── 발행 게이트 (SPEC-02 §2.2) ──────────────────────────────────────────
    /** `tasting_note` 가 비면 발행 불가 (`R-F1.1-2`) */
    GATE_COCKTAIL_01("GATE-COCKTAIL-01"),

    /** 분류 3축 불변식 전부 통과 (`R-C-1`) */
    GATE_COCKTAIL_02("GATE-COCKTAIL-02"),

    /** 표준 레시피가 재료 1개 이상 · 스텝 1개 이상 */
    GATE_COCKTAIL_03("GATE-COCKTAIL-03"),

    /** 모든 `RecipeIngredient` 가 마스터 참조 (`R-F1.1-1`) */
    GATE_COCKTAIL_04("GATE-COCKTAIL-04"),

    /** 명예의 전당 · 클래식 분류면 `story` 필수 (`R-F1.1-3`) */
    GATE_COCKTAIL_05("GATE-COCKTAIL-05"),

    /** 국내 미유통 재료가 있으면 대체재 명시 (`R-F1.3-2`) */
    GATE_COCKTAIL_06("GATE-COCKTAIL-06"),

    // ── 재료 불변식 (SPEC-02 §3) ────────────────────────────────────────────
    /** `import_only` · `unavailable` 이면 대체재 또는 자가제조 안내 필수 (`R-F1.3-2`) */
    INV_INGREDIENT_01("INV-INGREDIENT-01"),

    /** 특정 브랜드 언급 시 광고성 여부를 구분해 표기 (`R-F1.3-3`) */
    INV_INGREDIENT_02("INV-INGREDIENT-02"),
    ;

    constructor(code: String) : this(code, HttpStatus.UNPROCESSABLE_ENTITY)

    companion object {
        /** SPEC-02 ID 형식. 테스트가 이 모양을 고정한다. */
        val ID_PATTERN = Regex("^(INV|GATE)-[A-Z]+-\\d{2}$")

        fun of(code: String): ViolationCode =
            entries.firstOrNull { it.code == code }
                ?: error("SPEC-02 에 없는 위반 코드: $code")
    }
}
