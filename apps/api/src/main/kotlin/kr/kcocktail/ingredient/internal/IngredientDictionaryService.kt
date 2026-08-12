package kr.kcocktail.ingredient.internal

import kr.kcocktail.common.web.error.ResourceNotFoundException
import kr.kcocktail.common.web.page.PageQuery
import kr.kcocktail.common.web.page.PageResponse
import kr.kcocktail.ingredient.domain.Ingredient
import kr.kcocktail.ingredient.repository.IngredientRepository
import kr.kcocktail.ingredient.web.BrandItem
import kr.kcocktail.ingredient.web.IngredientDetail
import kr.kcocktail.ingredient.web.IngredientItem
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 재료 사전 조회 (ISSUE-023 · `FR-INGREDIENT-002`·`005`).
 *
 * ## `internal` 인 이유
 *
 * `web` 이 `repository` 를 직접 부르면 경계 테스트가 막는다 —
 * 트랜잭션 경계를 건너뛰고 조회 조건이 web 계층으로 샌다.
 *
 * ## "이 재료를 쓰는 칵테일" 은 여기 없다
 *
 * `search` 모듈이 담당한다. `ingredient` 가 `cocktail` 을 읽으면 SPEC-05 §3 방향표에
 * 없는 화살표가 생기고 순환이 된다 — `IngredientCocktailsController` 주석에 근거가 있다.
 */
@Service
class IngredientDictionaryService(
    private val ingredients: IngredientRepository,
) {

    /** 승인된 것만 나간다 (`FR-INGREDIENT-001` · DECISIONS §1.1). */
    @Transactional(readOnly = true)
    fun list(category: String?, availability: String?, query: PageQuery): PageResponse<IngredientItem> {
        val rows = ingredients.findDictionary(
            category,
            availability,
            PageRequest.of(query.page, query.size),
        )

        return PageResponse.of(
            items = rows.map { it.toItem() },
            query = query,
            totalElements = ingredients.countDictionary(category, availability),
        )
    }

    /** **미승인은 404 다** — 403 이면 존재가 새어 나간다 (SPEC-07 §5 와 같은 취지). */
    @Transactional(readOnly = true)
    fun detail(slug: String): IngredientDetail = approved(slug).toDetail()


    private fun approved(slug: String): Ingredient =
        ingredients.findBySlugAndIsApprovedTrue(slug) ?: throw ResourceNotFoundException()

    private fun Ingredient.toItem() = IngredientItem(
        slug = slug,
        nameKo = nameKo,
        nameEn = nameEn,
        category = category.slug,
        domesticAvailability = domesticAvailability.slug,
        abv = abv,
    )

    private fun Ingredient.toDetail() = IngredientDetail(
        slug = slug,
        nameKo = nameKo,
        nameEn = nameEn,
        aliases = aliases.toList(),
        category = category.slug,
        abv = abv,
        description = description,
        domesticAvailability = domesticAvailability.slug,
        substituteNote = substituteNote,
        priceBand = priceBand,
        // NFR-L-02 — 판정을 서버가 내려 항상 실어 보낸다. 끄는 방법을 두지 않는다
        brands = brands.map { BrandItem(it.name, it.purchaseUrl, it.isSponsored, it.requiresAdLabel) },
    )
}
