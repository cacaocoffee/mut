package kr.mut.cocktail.recipe

import kr.mut.ingredient.api.IngredientFacade
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * 레시피 재료를 조립한다 (ISSUE-010).
 *
 * ## 여기가 모듈 경계를 지키는 자리다
 *
 * `counts_for_stock` 의 기본값은 재료 카테고리에서 온다 (`R-F2.2-5`). 그 값을 알려면
 * `ingredient` 를 봐야 하는데, **엔티티가 직접 보면 `cocktail` → `ingredient` 의존이
 * 도메인 계층에 박힌다** (`PRIN-T03`). 조립을 이 클래스로 빼서 `IngredientFacade` 만 쓴다.
 *
 * `ingredient` 테이블을 직접 조회하지 않는다 — 경계 테스트(이슈 001)가 막는다.
 */
@Component
class RecipeAssembler(private val ingredients: IngredientFacade) {

    /**
     * 입력을 [Recipe.IngredientDraft] 로 바꾼다.
     *
     * `countsForStock` 이 주어지지 않으면 재료 카테고리의 기본값을 쓴다.
     * **주어지면 그대로 존중한다** — 가니시가 그 칵테일의 정체성인 경우가 있다.
     */
    fun toDrafts(inputs: List<Input>): List<Recipe.IngredientDraft> = inputs.map { input ->
        Recipe.IngredientDraft(
            ingredientId = input.ingredientId,
            countsForStock = input.countsForStock
                ?: ingredients.defaultCountsForStock(input.ingredientId),
            amount = input.amount,
            unit = input.unit,
            amountLabel = input.amountLabel,
            role = input.role,
            isOptional = input.isOptional,
            substituteIngredientId = input.substituteIngredientId,
            substituteNote = input.substituteNote,
        )
    }

    /**
     * 어드민·시드가 넘기는 값. `countsForStock` 이 **nullable 인 것이 요점**이다 —
     * `false` 를 명시한 것과 "안 정했다"를 구분해야 기본값을 언제 쓸지 알 수 있다.
     */
    data class Input(
        val ingredientId: Long,
        val countsForStock: Boolean? = null,
        val amount: BigDecimal? = null,
        val unit: MeasureUnit? = null,
        val amountLabel: String? = null,
        val role: IngredientRole? = null,
        val isOptional: Boolean = false,
        val substituteIngredientId: Long? = null,
        val substituteNote: String? = null,
    )
}
