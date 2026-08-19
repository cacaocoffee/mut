package kr.mut.cocktail.abv

import kr.mut.common.taxonomy.Technique
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.math.BigDecimal

/**
 * ISSUE-011 RED 1~16 — 도수 계산 (SPEC-02 §2.4 · `FR-COCKTAIL-006`).
 *
 * ## DB 없이 전수로 돈다
 *
 * 재료 조합 × 기법 5종의 경우의 수를 여기서 다 본다. 통합 테스트로 하면
 * 조합마다 컨테이너를 왕복해야 하고, 그러면 전수를 포기하게 된다.
 * `NFR-D-01`(발행분 불변식 위반 0건)의 검증 대상이기도 하다.
 */
class AbvCalculatorTest {

    // ── RED 1~6 : 기법별 희석률 (SPEC-02 §2.4) ────────────────────────────

    /** 진 60ml 40% 를 `build`(10%) 로 → 40 × 0.9 = 36.0 */
    @Test
    fun `RED1 - 단일 재료의 가중평균이 그 재료의 도수다`() {
        val result = AbvCalculator.calculate(listOf(ml(abv = 40, amount = 60)), Technique.BUILD)

        assertThat(result).isEqualByComparingTo("36.0")
    }

    /**
     * SPEC-02 §2.4 표 그대로. **문서에서 읽어 옮긴 값**이지 구현에서 가져온 것이 아니다 —
     * 구현을 그대로 쓰면 희석률을 잘못 적어도 초록이다.
     */
    @Test
    fun `RED2-4 - 희석률 3종이 표와 일치한다`() {
        val gin = listOf(ml(abv = 40, amount = 100))

        assertAll(
            listOf(
                Technique.SHAKE to "30.0",  // 40 × 0.75
                Technique.STIR to "32.0",   // 40 × 0.80
                Technique.BUILD to "36.0",  // 40 × 0.90
            ).map<Pair<Technique, String>, () -> Unit> { (method, expected) ->
                {
                    assertThat(AbvCalculator.calculate(gin, method))
                        .`as`("%s", method.slug)
                        .isEqualByComparingTo(expected)
                }
            },
        )
    }

    /**
     * `Blend` · `Etc` 가 표에 **없는 것이 의도**다. 블렌더는 얼음 양에 따라 편차가 크고
     * `etc` 는 정의상 규칙이 없다. 없으면 계산하지 않고 에디터가 직접 넣는다.
     */
    @Test
    fun `RED5-6 - Blend 와 Etc 는 자동 계산하지 않는다`() {
        val gin = listOf(ml(abv = 40, amount = 100))

        assertThat(AbvCalculator.calculate(gin, Technique.BLEND)).isNull()
        assertThat(AbvCalculator.calculate(gin, Technique.ETC)).isNull()

        assertThat(AbvCalculator.isAutoCalculable(Technique.BLEND)).isFalse()
        assertThat(AbvCalculator.isAutoCalculable(Technique.SHAKE)).isTrue()
    }

    @Test
    fun `기법 5종 전수 - 자동 계산 대상은 셋뿐이다`() {
        assertThat(Technique.entries.filter(AbvCalculator::isAutoCalculable).map { it.slug })
            .containsExactlyInAnyOrder("shake", "stir", "build")
    }

    // ── RED 7~11 : 계산식 ─────────────────────────────────────────────────

    /** 진 60ml 40% + 베르무트 20ml 18% → (2400 + 360) / 80 = 34.5, × 0.8 = 27.6 */
    @Test
    fun `RED7 - 복수 재료는 가중평균이다`() {
        val result = AbvCalculator.calculate(
            listOf(ml(abv = 40, amount = 60), ml(abv = 18, amount = 20)),
            Technique.STIR,
        )

        assertThat(result).isEqualByComparingTo("27.6")
    }

    /** 주스가 섞이면 도수가 내려간다 — 무알콜 재료도 **부피에는 들어간다.** */
    @Test
    fun `RED8 - 무알콜 재료가 섞이면 희석된다`() {
        val withJuice = AbvCalculator.calculate(
            listOf(ml(abv = 40, amount = 50), ml(abv = 0, amount = 50)),
            Technique.SHAKE,
        )
        val ginOnly = AbvCalculator.calculate(listOf(ml(abv = 40, amount = 50)), Technique.SHAKE)

        assertThat(withJuice).isEqualByComparingTo("15.0") // 20 × 0.75
        assertThat(withJuice).isLessThan(ginOnly)
    }

    /** 도수를 아직 안 채운 재료는 `null` 이다. 0 으로 다루되 부피에는 들어간다. */
    @Test
    fun `도수가 null 인 재료는 0 으로 다룬다`() {
        val result = AbvCalculator.calculate(
            listOf(ml(abv = 40, amount = 50), Input(abv = null, amount = 50)),
            Technique.SHAKE,
        )

        assertThat(result).isEqualByComparingTo("15.0")
    }

    /** `NUMERIC(4,1)` 이라 소수점 한 자리다. */
    @Test
    fun `RED9 - 소수점 한 자리로 반올림한다`() {
        // 37 × 0.75 = 27.75 → 27.8 (HALF_UP)
        val result = AbvCalculator.calculate(listOf(ml(abv = 37, amount = 100)), Technique.SHAKE)

        assertThat(result).isEqualByComparingTo("27.8")
        assertThat(result!!.scale()).isEqualTo(1)
    }

    @Test
    fun `RED10-11 - 결과가 0 미만이거나 100 초과가 되지 않는다`() {
        assertThat(AbvCalculator.calculate(listOf(ml(abv = 0, amount = 100)), Technique.BUILD))
            .isEqualByComparingTo("0.0")

        // 데이터가 이상해도(순수 알코올 100%) 범위를 넘지 않는다.
        assertThat(AbvCalculator.calculate(listOf(ml(abv = 100, amount = 100)), Technique.BUILD))
            .isBetween(BigDecimal.ZERO, BigDecimal("100"))
    }

    // ── RED 12~16 : 계산 제외 ─────────────────────────────────────────────

    /** `FR-COCKTAIL-006` — **가니시는 계산에서 제외한다.** 민트 잎이 도수를 낮추면 안 된다. */
    @Test
    fun `RED12 - counts_for_stock 이 false 면 제외된다`() {
        val withGarnish = AbvCalculator.calculate(
            listOf(
                ml(abv = 40, amount = 60),
                ml(abv = 0, amount = 100, counts = false), // 가니시로 표시된 큰 부피
            ),
            Technique.BUILD,
        )

        assertThat(withGarnish)
            .`as`("가니시가 계산에 들어갔으면 훨씬 낮았을 것이다")
            .isEqualByComparingTo("36.0")
    }

    /** `top_up`(채운다)과 `1조각` 은 용량을 알 수 없어 가중평균에 넣을 수 없다. */
    @Test
    fun `RED13 - 수량이 없는 재료는 제외된다`() {
        val result = AbvCalculator.calculate(
            listOf(ml(abv = 40, amount = 60), Input(abv = 0, amount = null, unit = "top_up")),
            Technique.BUILD,
        )

        assertThat(result).isEqualByComparingTo("36.0")
    }

    /** DECISIONS §1 — `dash`·`barspoon` 은 용량이 작아 무시한다. */
    @Test
    fun `RED14 - ml 이 아닌 단위는 제외된다`() {
        val result = AbvCalculator.calculate(
            listOf(
                ml(abv = 40, amount = 60),
                Input(abv = 45, amount = 2, unit = "dash"),
                Input(abv = 20, amount = 1, unit = "barspoon"),
            ),
            Technique.BUILD,
        )

        assertThat(result).isEqualByComparingTo("36.0")
    }

    /**
     * DECISIONS §1 — 선택 재료도 **포함**한다. 표준 레시피가 기준이고,
     * 빼면 실제보다 낮게 나와 사용자가 도수를 과소평가한다. 보수적인 쪽이다.
     *
     * 계산기는 `is_optional` 을 보지 않는다 — 그 판단은 [AbvRecalculator] 가 이미 내렸다.
     */
    @Test
    fun `RED15 - 계산기는 선택 여부를 구분하지 않는다`() {
        assertThat(AbvCalculator.Input::class.java.declaredFields.map { it.name })
            .`as`("선택 재료를 뺄지는 호출자의 판단이다")
            .doesNotContain("isOptional")
    }

    /**
     * **0 이 아니라 `null`** 이다. `0` 으로 두면 `ck_cocktail_na`(무알콜 ⟺ `abv = 0`)가
     * 진 베이스 칵테일을 무알콜로 오인한다.
     */
    @Test
    fun `RED16 - 계산 대상이 없으면 null 이다`() {
        assertAll(
            listOf<() -> Unit>(
                { assertThat(AbvCalculator.calculate(emptyList(), Technique.SHAKE)).isNull() },
                {
                    assertThat(
                        AbvCalculator.calculate(
                            listOf(ml(abv = 40, amount = 60, counts = false)),
                            Technique.SHAKE,
                        ),
                    ).`as`("전부 가니시").isNull()
                },
                {
                    assertThat(
                        AbvCalculator.calculate(
                            listOf(Input(abv = 40, amount = null, unit = "top_up")),
                            Technique.SHAKE,
                        ),
                    ).`as`("전부 수량 없음").isNull()
                },
                {
                    assertThat(AbvCalculator.calculate(listOf(ml(abv = 40, amount = 0)), Technique.SHAKE))
                        .`as`("총 부피 0 — 나눌 수 없다")
                        .isNull()
                },
                {
                    // 이슈 051 에서 드러났다. 0 을 돌려주면 진 베이스 칵테일이 무알콜로
                    // 저장되려다 `ck_cocktail__non_alcoholic` 에 막혀 500 이 된다 —
                    // 재료 마스터에 도수를 아직 안 채운 초안이 그 상태다.
                    assertThat(
                        AbvCalculator.calculate(
                            listOf(Input(abv = null, amount = 45), Input(abv = null, amount = 15)),
                            Technique.STIR,
                        ),
                    ).`as`("도수를 아는 재료가 하나도 없다 — 모르는 것이지 0도가 아니다").isNull()
                },
            ),
        )
    }

    // ── 헬퍼 ───────────────────────────────────────────────────────────────

    private fun ml(abv: Int, amount: Int, counts: Boolean = true) =
        Input(abv = abv, amount = amount, unit = "ml", counts = counts)

    @Suppress("FunctionName")
    private fun Input(
        abv: Int?,
        amount: Int?,
        unit: String? = "ml",
        counts: Boolean = true,
    ) = AbvCalculator.Input(
        abv = abv?.let { BigDecimal(it) },
        amountMl = amount?.let { BigDecimal(it) },
        unitSlug = unit,
        countsForStock = counts,
    )
}
