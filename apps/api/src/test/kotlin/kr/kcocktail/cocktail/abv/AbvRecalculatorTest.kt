package kr.kcocktail.cocktail.abv

import kr.kcocktail.cocktail.domain.Cocktail
import kr.kcocktail.cocktail.recipe.MeasureUnit
import kr.kcocktail.cocktail.recipe.Recipe
import kr.kcocktail.cocktail.recipe.RecipeVersionType
import kr.kcocktail.common.taxonomy.BaseSpirit
import kr.kcocktail.common.taxonomy.StyleKey
import kr.kcocktail.common.taxonomy.SweetLevel
import kr.kcocktail.common.taxonomy.Technique
import kr.kcocktail.ingredient.api.IngredientFacade
import kr.kcocktail.ingredient.api.IngredientView
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * ISSUE-011 RED 22~25 — 재계산. DB 없이 돈다.
 *
 * ## 이벤트로 미루지 않는다
 *
 * 저장 트랜잭션 안에서 계산한다 — **저장 직후 어드민이 값을 봐야** 하기 때문이다.
 * 비동기면 에디터가 저장하고 화면을 봤을 때 도수가 비어 있다.
 */
class AbvRecalculatorTest {

    private val gin = 1L
    private val vermouth = 2L
    private val mint = 3L

    private val facade = FakeFacade(
        mapOf(
            gin to BigDecimal("40"),
            vermouth to BigDecimal("18"),
            mint to null, // 도수를 안 채운 재료
        ),
    )
    private val recalculator = AbvRecalculator(facade)

    @Test
    fun `RED22 - 재료를 추가하면 계산값이 바뀐다`() {
        val recipe = recipe(Technique.STIR)
        recipe.setIngredients(listOf(draft(gin, 60)))
        val before = recalculator.calculate(recipe)

        recipe.setIngredients(listOf(draft(gin, 60), draft(vermouth, 20)))
        val after = recalculator.calculate(recipe)

        assertThat(before).isEqualByComparingTo("32.0")  // 40 × 0.8
        assertThat(after).isEqualByComparingTo("27.6")   // (2400+360)/80 × 0.8
    }

    @Test
    fun `RED23 - 용량을 바꾸면 계산값이 바뀐다`() {
        val recipe = recipe(Technique.STIR)

        recipe.setIngredients(listOf(draft(gin, 60), draft(vermouth, 20)))
        val before = recalculator.calculate(recipe)

        recipe.setIngredients(listOf(draft(gin, 30), draft(vermouth, 50)))
        val after = recalculator.calculate(recipe)

        assertThat(after).isLessThan(before)
    }

    @Test
    fun `RED24 - 기법을 바꾸면 계산값이 바뀐다`() {
        val stir = recipe(Technique.STIR).apply { setIngredients(listOf(draft(gin, 100))) }
        val shake = recipe(Technique.SHAKE).apply { setIngredients(listOf(draft(gin, 100))) }

        assertThat(recalculator.calculate(stir)).isEqualByComparingTo("32.0")
        assertThat(recalculator.calculate(shake))
            .`as`("셰이크가 더 희석된다")
            .isEqualByComparingTo("30.0")
    }

    /**
     * RED 25 — 재료 마스터 도수가 바뀌면 **전체 재계산을 즉시 하지 않는다** (DECISIONS §1).
     *
     * 500종 규모에서 부담이고, 에디터가 확인해야 할 변경이다 —
     * 검증 태스크로 올린다 (이슈 028). 여기서는 재계산이 **호출될 때** 최신 값을 쓰는 것까지.
     */
    @Test
    fun `RED25 - 재계산은 호출 시점의 마스터 도수를 쓴다`() {
        val recipe = recipe(Technique.BUILD)
        recipe.setIngredients(listOf(draft(gin, 100)))
        assertThat(recalculator.calculate(recipe)).isEqualByComparingTo("36.0")

        facade.abvById[gin] = BigDecimal("20") // 마스터 도수 변경

        assertThat(recalculator.calculate(recipe))
            .`as`("다시 부르면 새 값을 쓴다 — 자동 전파는 검증 태스크의 몫이다")
            .isEqualByComparingTo("18.0")
    }

    /** 가니시는 도수 계산에서도 빠진다 (`FR-COCKTAIL-006`). */
    @Test
    fun `가니시는 계산에서 빠진다`() {
        val recipe = recipe(Technique.BUILD)
        recipe.setIngredients(
            listOf(draft(gin, 60), draft(mint, 100, counts = false)),
        )

        assertThat(recalculator.calculate(recipe)).isEqualByComparingTo("36.0")
    }

    /** 표준 레시피만 표시값의 근거다. 바 시그니처가 칵테일의 도수를 바꾸면 안 된다. */
    @Test
    fun `표준이 아닌 레시피는 칵테일에 반영하지 않는다`() {
        val cocktail = cocktail(Technique.BUILD)
        val signature = Recipe(
            cocktail = cocktail,
            versionTypeSlug = RecipeVersionType.BAR_SIGNATURE.slug,
            authorBarId = 1,
        ).apply { setIngredients(listOf(draft(gin, 100))) }

        recalculator.apply(signature)

        assertThat(cocktail.abvCalculated).isNull()
    }

    @Test
    fun `표준 레시피는 칵테일에 반영된다`() {
        val cocktail = cocktail(Technique.BUILD)
        val standard = Recipe(cocktail = cocktail, versionTypeSlug = RecipeVersionType.STANDARD.slug)
            .apply { setIngredients(listOf(draft(gin, 100))) }

        recalculator.apply(standard)

        assertThat(cocktail.abvCalculated).isEqualByComparingTo("36.0")
    }

    // ── 헬퍼 ───────────────────────────────────────────────────────────────

    private fun cocktail(method: Technique) = Cocktail(
        slug = "probe",
        nameKo = "테스트",
        nameEn = "test",
        summary = "요약",
        baseSpiritSlug = BaseSpirit.GIN.slug,
        stylePrimarySlug = StyleKey.HIGHBALL.slug,
        methodSlug = method.slug,
        sweetnessSlug = SweetLevel.DRY.slug,
        glassType = "하이볼 글라스",
    )

    private fun recipe(method: Technique) =
        Recipe(cocktail = cocktail(method), versionTypeSlug = RecipeVersionType.STANDARD.slug)

    private fun draft(ingredientId: Long, amountMl: Int, counts: Boolean = true) =
        Recipe.IngredientDraft(
            ingredientId = ingredientId,
            countsForStock = counts,
            amount = BigDecimal(amountMl),
            unit = MeasureUnit.ML,
        )

    /** 마스터 도수를 바꿔 가며 재계산을 확인한다. */
    private class FakeFacade(initial: Map<Long, BigDecimal?>) : IngredientFacade {
        /** 마스터 도수를 테스트 중에 바꿀 수 있어야 RED 25 를 볼 수 있다. */
        val abvById: MutableMap<Long, BigDecimal?> = initial.toMutableMap()

        override fun findAll(ids: Collection<Long>): List<IngredientView> =
            ids.map { id ->
                IngredientView(
                    id = id,
                    slug = "ing-$id",
                    nameKo = "재료",
                    nameEn = "ingredient",
                    categorySlug = "spirit",
                    availabilitySlug = "common",
                    abv = abvById[id],
                    isApproved = true,
                    countsForStockByDefault = true,
                    requiresSubstitute = false,
                    substituteNote = null,
                    hasSponsoredBrand = false,
                )
            }

        override fun findApproved(ids: Collection<Long>): List<IngredientView> = findAll(ids)
        override fun defaultCountsForStock(ingredientId: Long): Boolean = true
        override fun requiresSubstitute(ingredientId: Long): Boolean = false
    }
}
