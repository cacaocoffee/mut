package kr.kcocktail.cocktail.internal

import kr.kcocktail.cocktail.api.CocktailFacade
import kr.kcocktail.cocktail.api.CocktailSummary
import kr.kcocktail.cocktail.domain.Cocktail
import kr.kcocktail.cocktail.domain.CocktailStatus
import kr.kcocktail.cocktail.repository.CocktailRepository
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
