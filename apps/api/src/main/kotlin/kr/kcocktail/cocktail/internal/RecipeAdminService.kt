package kr.kcocktail.cocktail.internal

import kr.kcocktail.cocktail.abv.AbvRecalculator
import kr.kcocktail.cocktail.api.AdminRecipeIngredient
import kr.kcocktail.cocktail.api.AdminRecipeResponse
import kr.kcocktail.cocktail.api.AdminRecipeStep
import kr.kcocktail.cocktail.api.RecipeAdminFacade
import kr.kcocktail.cocktail.api.RecipeIngredientInput
import kr.kcocktail.cocktail.api.SaveRecipeRequest
import kr.kcocktail.cocktail.recipe.IngredientRole
import kr.kcocktail.cocktail.recipe.MeasureUnit
import kr.kcocktail.cocktail.recipe.Recipe
import kr.kcocktail.cocktail.recipe.RecipeAssembler
import kr.kcocktail.cocktail.recipe.RecipeRepository
import kr.kcocktail.cocktail.recipe.RecipeVersionType
import kr.kcocktail.cocktail.repository.CocktailRepository
import kr.kcocktail.common.web.error.BadRequestException
import kr.kcocktail.common.web.error.ResourceNotFoundException
import kr.kcocktail.ingredient.api.IngredientFacade
import kr.kcocktail.ingredient.api.IngredientView
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 표준 레시피 편집 (ISSUE-051 · `NFR-O-01`).
 *
 * ## 표준은 찾아서 덮고, 없으면 만든다
 *
 * 매번 새로 만들면 `uq_recipe__standard` 가 두 번째 저장에서 터진다 (`INV-COCKTAIL-07`).
 * 부분 유니크 인덱스가 있어서 **DB 가 막아 주지만**, 에디터에게 500 을 보여 줄 일이 아니다.
 *
 * ## 없는 재료는 저장 시점에 막는다
 *
 * 마스터 참조는 `GATE-COCKTAIL-04` 가 발행 때 보는 것이지만, **없는 id** 는 게이트가 아니라
 * 오타다. FK 가 터지면 500 이 되고 어느 줄이 틀렸는지 알 수 없다 — 여기서 어느 재료인지
 * 짚어 400 으로 답한다.
 *
 * 반대로 **미승인 재료는 막지 않는다** (DECISIONS §1.1). 레시피를 쓰다 새 재료가 필요하면
 * 승인을 기다리지 않고 초안을 이어 쓴다. 발행에서 걸린다.
 */
@Service
class RecipeAdminService(
    private val cocktails: CocktailRepository,
    private val recipes: RecipeRepository,
    private val assembler: RecipeAssembler,
    private val abv: AbvRecalculator,
    private val ingredientMaster: IngredientFacade,
) : RecipeAdminFacade {

    @Transactional(readOnly = true)
    override fun find(cocktailId: Long): AdminRecipeResponse {
        val cocktail = cocktails.findById(cocktailId).orElseThrow { ResourceNotFoundException() }
        val recipe = standardOf(cocktailId)

        return recipe?.toResponse() ?: AdminRecipeResponse(
            cocktailId = cocktailId,
            exists = false,
            servingCount = 1,
            note = null,
            ingredients = emptyList(),
            steps = emptyList(),
            abvCalculated = cocktail.abvCalculated,
        )
    }

    @Transactional
    override fun save(cocktailId: Long, request: SaveRecipeRequest): AdminRecipeResponse {
        val cocktail = cocktails.findById(cocktailId).orElseThrow { ResourceNotFoundException() }

        val inputs = request.ingredients.map { it.toAssemblerInput() }
        requireIngredientsExist(request.ingredients)

        val recipe = standardOf(cocktailId)
            ?: Recipe(cocktail = cocktail, versionTypeSlug = RecipeVersionType.STANDARD.slug)

        recipe.servingCount = request.servingCount
        recipe.note = request.note
        recipe.setIngredients(assembler.toDrafts(inputs))
        recipe.setSteps(request.steps.map { Recipe.StepDraft(it.text.trim(), it.techniqueRef) })

        val saved = recipes.save(recipe)
        // 저장 트랜잭션 안에서 계산한다 — 저장 직후 에디터가 도수를 봐야 한다 (이슈 011)
        abv.apply(saved)

        return saved.toResponse()
    }

    private fun standardOf(cocktailId: Long): Recipe? =
        recipes.findByCocktailIdAndVersionTypeSlug(cocktailId, RecipeVersionType.STANDARD.slug)

    /** 없는 재료를 짚어 준다. **미승인은 통과시킨다** — 그것은 발행 게이트의 몫이다. */
    private fun requireIngredientsExist(inputs: List<RecipeIngredientInput>) {
        val referenced = inputs.flatMap { listOfNotNull(it.ingredientId, it.substituteIngredientId) }
        if (referenced.isEmpty()) return

        val found = ingredientMaster.findAll(referenced.distinct()).map { it.id }.toSet()
        val missing = referenced.distinct().filterNot { it in found }

        if (missing.isNotEmpty()) {
            throw BadRequestException("없는 재료입니다: ${missing.joinToString(", ")}")
        }
    }

    private fun RecipeIngredientInput.toAssemblerInput(): RecipeAssembler.Input {
        val measure = unit?.let { slug ->
            MeasureUnit.entries.firstOrNull { it.slug == slug }
                ?: throw BadRequestException(
                    "알 수 없는 unit 입니다: $slug " +
                        "(가능: ${MeasureUnit.entries.joinToString(", ") { it.slug }})",
                )
        }

        // `top_up` 은 수량이 없다. 넣어 두면 잔 수 환산이 그 값을 곱해 잔 크기를 넘긴다
        if (measure != null && measure.amountRequired && amount == null && amountLabel.isNullOrBlank()) {
            throw BadRequestException("${measure.slug} 단위에는 수량이나 표기가 필요합니다")
        }

        return RecipeAssembler.Input(
            ingredientId = ingredientId,
            countsForStock = countsForStock,
            amount = amount,
            unit = measure,
            amountLabel = amountLabel,
            role = role?.let { slug ->
                IngredientRole.entries.firstOrNull { it.slug == slug }
                    ?: throw BadRequestException(
                        "알 수 없는 role 입니다: $slug " +
                            "(가능: ${IngredientRole.entries.joinToString(", ") { it.slug }})",
                    )
            },
            isOptional = isOptional,
            substituteIngredientId = substituteIngredientId,
            substituteNote = substituteNote,
        )
    }

    private fun Recipe.toResponse(): AdminRecipeResponse {
        // 이름을 함께 싣는다 — 화면이 줄마다 다시 묻지 않게 (미승인도 보여 준다)
        val views: Map<Long, IngredientView> = ingredientMaster
            .findAll(ingredients.map { it.ingredientId }.distinct())
            .associateBy { it.id }

        return AdminRecipeResponse(
            cocktailId = cocktail.id,
            exists = true,
            servingCount = servingCount,
            note = note,
            ingredients = this.ingredients.map { row ->
                val view = views[row.ingredientId]
                AdminRecipeIngredient(
                    position = row.position,
                    ingredientId = row.ingredientId,
                    ingredientSlug = view?.slug,
                    nameKo = view?.nameKo,
                    isApproved = view?.isApproved ?: false,
                    amount = row.amount,
                    unit = row.unit?.slug,
                    amountLabel = row.amountLabel,
                    role = row.role?.slug,
                    isOptional = row.isOptional,
                    substituteIngredientId = row.substituteIngredientId,
                    substituteNote = row.substituteNote,
                    countsForStock = row.countsForStock,
                )
            },
            steps = steps.map { AdminRecipeStep(it.stepNo, it.text, it.techniqueRef) },
            abvCalculated = cocktail.abvCalculated,
        )
    }
}
