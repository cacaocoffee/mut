package kr.kcocktail.cocktail.abv

import kr.kcocktail.common.taxonomy.Technique
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * 도수 자동 계산 (SPEC-02 §2.4 · `FR-COCKTAIL-006` · `R-F1.1-4`).
 *
 * ```
 * abv_calculated = Σ(재료도수 × 용량ml) / Σ(용량ml) × (1 − 희석률)
 * ```
 *
 * ## 순수 함수다
 *
 * 재료 조합 × 기법 5종의 경우의 수를 **DB 없이 전수로** 돌려야 회귀를 잡는다.
 * `NFR-D-01`(발행분 불변식 위반 0건)의 검증 대상이기도 하다.
 *
 * ## 계산하지 않는 경우가 셋이다
 *
 * | 무엇 | 왜 |
 * |---|---|
 * | `Blend` · `Etc` | 희석률을 정할 수 없다 (SPEC-02 §2.4). **수동** |
 * | 대상 재료 0개 | `0` 이 아니라 `null` — 아래 참조 |
 * | 가니시 · 비-ml · 수량 없음 | 계산 대상에서 제외 |
 *
 * ## 0 이 아니라 null 인 이유
 *
 * `0` 으로 두면 `ck_cocktail_na`(무알콜 ⟺ `abv = 0`)가 **진 베이스 칵테일을 무알콜로 오인**한다.
 * `null` 이어야 이슈 009 가 남겨 둔 "draft 단계에서는 도수가 없다"와 맞물린다.
 */
object AbvCalculator {

    /**
     * SPEC-02 §2.4 기법별 희석률.
     *
     * `Blend` · `Etc` 가 **없는 것이 의도**다 — 블렌더는 얼음 양에 따라 편차가 크고,
     * `etc` 는 정의상 규칙이 없다. 없으면 계산하지 않고 에디터가 직접 넣는다.
     */
    private val DILUTION: Map<Technique, BigDecimal> = mapOf(
        Technique.SHAKE to BigDecimal("0.25"),
        Technique.STIR to BigDecimal("0.20"),
        Technique.BUILD to BigDecimal("0.10"),
    )

    /** 계산에 쓰는 유일한 단위. `dash`·`barspoon` 은 용량이 작아 무시한다 (DECISIONS §1). */
    private const val ML = "ml"

    private val ONE = BigDecimal.ONE
    private val HUNDRED = BigDecimal("100")

    /**
     * @return 소수점 한 자리로 반올림한 도수. 계산할 수 없으면 `null`
     */
    fun calculate(ingredients: List<Input>, method: Technique): BigDecimal? {
        val dilution = DILUTION[method] ?: return null

        val target = ingredients.filter { it.isCountedForAbv }
        if (target.isEmpty()) return null

        val totalVolume = target.fold(BigDecimal.ZERO) { acc, it -> acc + it.amountMl!! }
        if (totalVolume.signum() == 0) return null // 전부 0ml — 나눌 수 없다

        val weighted = target.fold(BigDecimal.ZERO) { acc, it ->
            acc + (it.abv ?: BigDecimal.ZERO) * it.amountMl!!
        }

        val raw = weighted
            .divide(totalVolume, MathContext.DECIMAL64)
            .multiply(ONE - dilution)

        // NUMERIC(4,1) 이라 소수점 한 자리. 음수·100 초과는 데이터가 이상한 것이므로 잘라 둔다.
        return raw.setScale(1, RoundingMode.HALF_UP).coerceIn(BigDecimal.ZERO, HUNDRED)
    }

    /** 기법이 자동 계산 대상인가. 어드민이 "수동 입력 필요"를 표시할 때 쓴다. */
    fun isAutoCalculable(method: Technique): Boolean = method in DILUTION

    fun dilutionOf(method: Technique): BigDecimal? = DILUTION[method]

    /**
     * 계산 입력 한 줄. **엔티티가 아니라 값**이다 —
     * 배치 검증(016)이 프로젝션만 읽어도 계산할 수 있어야 한다.
     */
    data class Input(
        /** 재료 마스터의 도수. 주스처럼 `0` 이거나 미상이면 `null`. */
        val abv: BigDecimal?,
        val amountMl: BigDecimal?,
        val unitSlug: String?,
        /** `R-F2.2-5` — 가니시는 `false`. 도수 계산에서도 빠진다 (`FR-COCKTAIL-006`). */
        val countsForStock: Boolean,
    ) {
        /**
         * 계산 대상인가.
         *
         * 셋 다 만족해야 한다 — 가니시가 아니고, 단위가 `ml` 이고, 수량이 있다.
         * `top_up`(채운다)과 `1조각` 은 용량을 알 수 없어 가중평균에 넣을 수 없다.
         */
        val isCountedForAbv: Boolean
            get() = countsForStock && unitSlug == ML && amountMl != null
    }
}
