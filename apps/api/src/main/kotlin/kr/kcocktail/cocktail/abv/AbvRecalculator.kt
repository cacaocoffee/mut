package kr.kcocktail.cocktail.abv

import kr.kcocktail.cocktail.recipe.Recipe
import kr.kcocktail.ingredient.api.IngredientFacade
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * 레시피가 바뀌면 `abv_calculated` 를 다시 채운다 (ISSUE-011).
 *
 * ## 이벤트로 미루지 않는다
 *
 * 저장 트랜잭션 안에서 계산한다 — **저장 직후 어드민이 값을 봐야** 하기 때문이다.
 * 비동기로 하면 에디터가 저장하고 화면을 봤을 때 도수가 비어 있다.
 *
 * ## 재료 마스터 도수가 바뀌면
 *
 * 전체 재계산을 즉시 하지 않는다. **검증 태스크로 올린다** (DECISIONS §1, 이슈 028) —
 * 500종 규모에서 부담이고, 에디터가 확인해야 할 변경이다.
 */
@Component
class AbvRecalculator(private val ingredients: IngredientFacade) {

    /**
     * 표준 레시피 기준으로 계산한다.
     *
     * `is_optional` 재료도 **포함**한다 (DECISIONS §1) — 표준 레시피가 기준이고,
     * 빼면 실제보다 낮게 나와 사용자가 도수를 과소평가한다. 보수적인 쪽을 택했다.
     */
    fun calculate(recipe: Recipe): BigDecimal? {
        val abvByIngredient = ingredients
            .findAll(recipe.ingredients.map { it.ingredientId }.distinct())
            .associate { it.id to it.abv }

        val inputs = recipe.ingredients.map { row ->
            AbvCalculator.Input(
                abv = abvByIngredient[row.ingredientId],
                amountMl = row.amount,
                unitSlug = row.unit?.slug,
                countsForStock = row.countsForStock,
            )
        }

        return AbvCalculator.calculate(inputs, recipe.cocktail.method)
    }

    /** 저장 직후 칵테일에 반영한다. 표준 레시피만 표시값의 근거다. */
    fun apply(recipe: Recipe) {
        if (!recipe.isStandard) return
        recipe.cocktail.abvCalculated = calculate(recipe)
    }
}
