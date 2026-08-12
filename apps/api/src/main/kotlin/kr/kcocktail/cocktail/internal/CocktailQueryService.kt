package kr.kcocktail.cocktail.internal

import kr.kcocktail.cocktail.api.CocktailFacade
import kr.kcocktail.cocktail.api.CocktailIngredientUsage
import kr.kcocktail.cocktail.api.CocktailSummary
import kr.kcocktail.cocktail.domain.Cocktail
import kr.kcocktail.cocktail.domain.CocktailStatus
import kr.kcocktail.cocktail.repository.CocktailRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [CocktailFacade] 구현 (ISSUE-009).
 *
 * 조회 API 는 이슈 018·020 이다. 여기서는 **타 모듈이 쓸 계약**까지만 채운다 —
 * 이슈 023·031 이 `cocktail` 을 직접 조인하지 못하게 하려면 이것이 먼저 있어야 한다.
 */
@Service
class CocktailQueryService(private val cocktails: CocktailRepository) : CocktailFacade {

    @Transactional(readOnly = true)
    override fun findPublished(slug: String): CocktailSummary? =
        cocktails.findBySlugAndStatusSlug(slug, CocktailStatus.PUBLISHED.slug)?.toSummary()

    @Transactional(readOnly = true)
    override fun findPublishedByIds(ids: Collection<Long>): List<CocktailSummary> =
        if (ids.isEmpty()) emptyList()
        else cocktails.findAllById(ids).filter { it.status.isPublic }.map { it.toSummary() }

    @Transactional(readOnly = true)
    override fun findAny(slug: String): CocktailSummary? = cocktails.findBySlug(slug)?.toSummary()

    /**
     * 이슈 023 의 "이 재료를 쓰는 칵테일" (`R-F1.3-1`).
     *
     * 조인을 여기서 하는 이유는 `ingredient` 가 `cocktail` 테이블을 볼 수 없어서다
     * (`PRIN-T03`). `recipe_ingredient(ingredient_id)` 인덱스를 탄다 (SPEC-06 §5).
     *
     * `substitute_ingredient_id` 는 보지 않는다 — **대체재로만 등장하는 것은
     * "이 재료를 쓰는" 이 아니다.** 그것까지 세면 사전에서 재료를 눌렀을 때
     * 실제로 안 들어가는 칵테일이 나온다.
     */
    @Transactional(readOnly = true)
    override fun findPublishedByIngredient(
        ingredientId: Long,
        limit: Int,
        offset: Int,
    ): List<CocktailIngredientUsage> {
        val size = limit.coerceAtLeast(1)
        return cocktails
            .findPublishedByIngredient(ingredientId, PageRequest.of(offset / size, size))
            .map { row ->
                CocktailIngredientUsage(
                    cocktail = (row[0] as Cocktail).toSummary(),
                    // 최솟값이 1 이면 모든 줄이 선택이다 — 한 줄이라도 필수면 0 이 섞인다
                    isOptional = (row[1] as Number).toInt() == 1,
                )
            }
    }

    @Transactional(readOnly = true)
    override fun countPublishedByIngredient(ingredientId: Long): Long =
        cocktails.countPublishedByIngredient(ingredientId)
}

private fun Cocktail.toSummary() = CocktailSummary(
    id = id,
    slug = slug,
    nameKo = nameKo,
    nameEn = nameEn,
    summary = summary,
    baseSpiritSlug = baseSpirit.slug,
    stylePrimarySlug = stylePrimary.slug,
    styleSlugs = styles.map { it.slug }.toSet(),
    methodSlug = method.slug,
    sweetnessSlug = sweetness.slug,
    aromaTagSlugs = aromaTags.map { it.slug }.toSet(),
    // 표시값 하나만. abv_calculated / abv_override 구분은 내부 사정이다 (SPEC-07 §5).
    abv = abv,
    statusSlug = status.slug,
)
