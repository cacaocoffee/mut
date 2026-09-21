package kr.mut.ingredient.internal

import kr.mut.common.web.error.ResourceNotFoundException
import kr.mut.common.web.page.PageQuery
import kr.mut.common.web.page.PageResponse
import kr.mut.ingredient.domain.Ingredient
import kr.mut.ingredient.domain.IngredientProductMatch
import kr.mut.ingredient.domain.MatchStatus
import kr.mut.ingredient.repository.DistributedProductRepository
import kr.mut.ingredient.repository.IngredientProductMatchRepository
import kr.mut.ingredient.repository.IngredientRepository
import kr.mut.ingredient.web.BrandItem
import kr.mut.ingredient.web.DistributedProductItem
import kr.mut.ingredient.web.IngredientDetail
import kr.mut.ingredient.web.IngredientItem
import kr.mut.ingredient.web.ProductFoodType
import kr.mut.ingredient.web.ProductListResponse
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
    private val matches: IngredientProductMatchRepository,
    private val products: DistributedProductRepository,
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
    fun detail(slug: String): IngredientDetail {
        val ingredient = approved(slug)
        // 승인된 매핑만 낸다 — 제안·거절은 어드민 화면의 것이다 (#195)
        val approvedProducts = matches.findAllForIngredient(ingredient.id)
            .filter { it.status == MatchStatus.APPROVED }
        return ingredient.toDetail(approvedProducts)
    }


    /** 공개 유통 제품 목록 (#205). 제품명·수입사 부분일치 + 식품유형, 최근 신고순. */
    @Transactional(readOnly = true)
    fun products(q: String?, foodType: String?, query: PageQuery): ProductListResponse {
        val keyword = q?.trim()?.takeIf { it.isNotEmpty() }
        val type = foodType?.trim()?.takeIf { it.isNotEmpty() }
        val rows = products.browse(keyword, type, PageRequest.of(query.page, query.size))
        val paged = PageResponse.of(
            items = rows.map {
                DistributedProductItem(
                    nameKo = it.nameKo,
                    nameEn = it.nameEn,
                    importerOrMaker = it.importerOrMaker,
                    originCountry = it.originCountry,
                    foodType = it.foodType,
                    lastReportedOn = it.lastReportedOn,
                )
            },
            query = query,
            totalElements = products.countBrowse(keyword, type),
        )
        return ProductListResponse(
            items = paged.items,
            page = paged.page,
            foodTypes = products.countByFoodType().map { ProductFoodType(it[0] as String, it[1] as Long) },
        )
    }

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

    private fun Ingredient.toDetail(approved: List<IngredientProductMatch>) = IngredientDetail(
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
        lastReportedOn = approved.mapNotNull { it.product.lastReportedOn }.maxOrNull(),
        // 최근 신고순. 이미 정렬돼 오지만(상태·신뢰도 우선) 화면은 날짜순이 읽기 쉽다
        products = approved
            .sortedWith(compareByDescending<IngredientProductMatch> { it.product.lastReportedOn }.thenBy { it.product.nameKo })
            .map {
                DistributedProductItem(
                    nameKo = it.product.nameKo,
                    nameEn = it.product.nameEn,
                    importerOrMaker = it.product.importerOrMaker,
                    originCountry = it.product.originCountry,
                    foodType = it.product.foodType,
                    lastReportedOn = it.product.lastReportedOn,
                )
            },
    )
}
