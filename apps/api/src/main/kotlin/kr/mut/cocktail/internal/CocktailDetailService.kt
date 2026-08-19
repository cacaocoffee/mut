package kr.mut.cocktail.internal

import kr.mut.cocktail.web.Actions
import kr.mut.cocktail.web.Brand
import kr.mut.cocktail.web.Classification
import kr.mut.cocktail.web.CocktailDetail
import kr.mut.cocktail.web.Hero
import kr.mut.cocktail.web.IngredientLine
import kr.mut.cocktail.web.Origin
import kr.mut.cocktail.web.PurchaseGuideItem
import kr.mut.cocktail.web.RecipeVersion
import kr.mut.cocktail.web.RecipeVersions
import kr.mut.cocktail.web.Spec
import kr.mut.cocktail.web.Step
import kr.mut.cocktail.web.Substitute
import kr.mut.cocktail.web.TastingNote
import kr.mut.cocktail.web.TaxonRef
import kr.mut.cocktail.domain.Cocktail
import kr.mut.cocktail.domain.CocktailStatus
import kr.mut.cocktail.recipe.Recipe
import kr.mut.cocktail.recipe.RecipeIngredient
import kr.mut.cocktail.recipe.RecipeRepository
import kr.mut.cocktail.repository.CocktailRepository
import kr.mut.common.taxonomy.Slugged
import kr.mut.common.web.error.ResourceNotFoundException
import kr.mut.ingredient.api.IngredientFacade
import kr.mut.ingredient.api.IngredientView
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 상세 응답 조립 (ISSUE-020 · `FR-COCKTAIL-017`·`018`).
 *
 * ## 재료는 `IngredientFacade` 로만 본다
 *
 * `PRIN-T03` — `cocktail` 이 `ingredient` 의 엔티티를 직접 들면 영속성 컨텍스트가
 * 모듈을 넘어가고, 지연 로딩이 남의 트랜잭션에서 터진다. 경계 테스트가 막는다.
 *
 * ## 한 번에 모아 온다
 *
 * 재료를 줄마다 조회하면 표준 레시피 하나에 N+1 이 뜬다. `id` 를 모아 한 번 부르고
 * 맵으로 붙인다 — 상세는 SSG 빌드가 종수만큼 부르는 경로다.
 */
@Service
class CocktailDetailService(
    private val cocktails: CocktailRepository,
    private val recipes: RecipeRepository,
    private val ingredients: IngredientFacade,
) {

    /** `draft` · `archived` 는 **404** 다 (SPEC-07 §5). 403 이면 존재가 새어 나간다. */
    @Transactional(readOnly = true)
    fun detail(slug: String): CocktailDetail {
        val cocktail = published(slug)
        val standard = standardRecipe(cocktail)
        val views = viewsOf(standard)

        return CocktailDetail(
            slug = cocktail.slug,
            isClassic = cocktail.isClassic,
            hero = Hero(
                nameKo = cocktail.nameKo,
                nameEn = cocktail.nameEn,
                summary = cocktail.summary,
                // Phase 1a 에는 media_asset 이 없다. 필드만 두는 이유는 DTO 주석에 있다
                imageUrl = null,
            ),
            classification = Classification(
                base = cocktail.baseSpirit.toRef(),
                stylePrimary = cocktail.stylePrimary.toRef(),
                styles = cocktail.styles.map { it.toRef() }.sortedBy { it.slug },
                method = cocktail.method.toRef(),
            ),
            spec = Spec(
                abv = cocktail.abv,
                sweetness = cocktail.sweetness.toRef(),
                glassType = cocktail.glassType,
                prepTimeMin = cocktail.prepTimeMin,
            ),
            ingredients = standard?.ingredients.orEmpty().map { it.toLine(views) },
            steps = standard?.steps.orEmpty().map { Step(it.stepNo, it.text, it.techniqueRef) },
            tastingNote = TastingNote(
                // GATE-COCKTAIL-01 이 발행을 막으므로 발행분에는 반드시 있다
                note = cocktail.tastingNote.orEmpty(),
                aromaTags = cocktail.aromaTags.map { it.toRef() }.sortedBy { it.slug },
            ),
            purchaseGuide = purchaseGuide(standard, views),
            actions = Actions(
                bookmarkTargetType = BOOKMARK_TARGET_TYPE,
                bookmarkTargetSlug = cocktail.slug,
                sharePath = "/cocktails/${cocktail.slug}",
            ),
            story = cocktail.story,
            origin = cocktail.originOrNull(),
        )
    }

    /**
     * `FR-COCKTAIL-003` — 표준 1개 + 바 시그니처 n개.
     *
     * 표준이 먼저 나온다. **기본 노출이 표준**이라(SPEC-02 §2.6) 순서가 곧 기본값이다.
     */
    @Transactional(readOnly = true)
    fun recipes(slug: String): RecipeVersions {
        val cocktail = published(slug)
        val all = recipes.findAllByCocktailId(cocktail.id).sortedByDescending { it.isStandard }

        return RecipeVersions(
            items = all.map { recipe ->
                val views = viewsOf(recipe)
                RecipeVersion(
                    versionType = recipe.versionType.slug,
                    isDefault = recipe.isStandard,
                    servingCount = recipe.servingCount,
                    note = recipe.note,
                    ingredients = recipe.ingredients.map { it.toLine(views) },
                    steps = recipe.steps.map { Step(it.stepNo, it.text, it.techniqueRef) },
                )
            },
        )
    }

    // ── 조립 ───────────────────────────────────────────────────────────────

    private fun published(slug: String): Cocktail =
        cocktails.findBySlugAndStatusSlug(slug, CocktailStatus.PUBLISHED.slug)
            ?: throw ResourceNotFoundException()

    private fun standardRecipe(cocktail: Cocktail): Recipe? =
        recipes.findByCocktailIdAndVersionTypeSlug(cocktail.id, STANDARD)

    /** 재료를 한 번에 모아 온다 (N+1 방지). 대체 재료도 같은 호출에 싣는다. */
    private fun viewsOf(recipe: Recipe?): Map<Long, IngredientView> {
        val rows = recipe?.ingredients.orEmpty()
        if (rows.isEmpty()) return emptyMap()

        val ids = (rows.map { it.ingredientId } + rows.mapNotNull { it.substituteIngredientId })
            .distinct()
        return ingredients.findAll(ids).associateBy { it.id }
    }

    private fun RecipeIngredient.toLine(views: Map<Long, IngredientView>): IngredientLine {
        val view = views[ingredientId]
        return IngredientLine(
            // 마스터에 없는 참조는 발행 게이트가 막는다 (GATE-COCKTAIL-04).
            // 그래도 여기서 터뜨리지 않는 이유는 상세가 읽기 경로라서다 —
            // 데이터가 어긋났으면 배치 검증(이슈 016)이 태스크로 올린다
            slug = view?.slug ?: "id:$ingredientId",
            nameKo = view?.nameKo.orEmpty(),
            nameEn = view?.nameEn.orEmpty(),
            amount = amount,
            unit = unit?.slug,
            amountLabel = amountLabel,
            role = role?.slug,
            isOptional = isOptional,
            isScalable = isScalable,
            substitute = substituteOrNull(views),
        )
    }

    /** `FR-COCKTAIL-021` — 지정 없이 안내만 있을 수 있다 (`INV-INGREDIENT-01`). */
    private fun RecipeIngredient.substituteOrNull(views: Map<Long, IngredientView>): Substitute? {
        if (substituteIngredientId == null && substituteNote.isNullOrBlank()) return null

        val view = substituteIngredientId?.let { views[it] }
        return Substitute(slug = view?.slug, nameKo = view?.nameKo, note = substituteNote)
    }

    /**
     * 국내 구매 가이드 (`PRIN-P05`). 표준 레시피에 **실제로 쓰인 재료만** 싣는다 —
     * 대체 재료까지 넣으면 "이걸 다 사야 하나" 로 읽힌다.
     */
    private fun purchaseGuide(
        standard: Recipe?,
        views: Map<Long, IngredientView>,
    ): List<PurchaseGuideItem> =
        standard?.ingredients.orEmpty()
            .mapNotNull { views[it.ingredientId] }
            .distinctBy { it.id }
            .map { view ->
                PurchaseGuideItem(
                    slug = view.slug,
                    nameKo = view.nameKo,
                    availability = TaxonRef(view.availabilitySlug, view.availabilitySlug),
                    substituteNote = view.substituteNote,
                    priceBand = view.priceBand,
                    brands = view.brands.map { Brand(it.name, it.purchaseUrl, it.isSponsored) },
                )
            }

    private fun Cocktail.originOrNull(): Origin? =
        if (originYear == null && originPlace == null && originCreator == null) null
        else Origin(originYear, originPlace, originCreator)

    private fun Slugged.toRef() = TaxonRef(slug, labelKo)

    companion object {
        /** SPEC-07 §2.5 의 `targetType`. 프론트가 문자열을 조립하지 않게 서버가 준다. */
        const val BOOKMARK_TARGET_TYPE = "cocktail"
        private const val STANDARD = "standard"
    }
}
