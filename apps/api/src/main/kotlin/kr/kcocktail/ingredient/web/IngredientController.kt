package kr.kcocktail.ingredient.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import kr.kcocktail.common.web.ApiPaths
import kr.kcocktail.common.web.page.PageQuery
import kr.kcocktail.common.web.page.PageResponse
import kr.kcocktail.common.web.page.SortableBy
import kr.kcocktail.ingredient.internal.IngredientDictionaryService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 재료 사전 (ISSUE-023 · SPEC-07 §2.2).
 *
 * ## `noindex` 를 붙이지 않는다
 *
 * 필터 결과(이슈 018)와 정반대다. **재료 사전은 콘텐츠라 색인 가치가 있다**
 * (DECISIONS §1.6) — SPEC-05 §4 렌더링 표에 `/ingredients` 경로가 없어 명시가 없었지만
 * 그 표의 누락이지 색인하지 말라는 뜻이 아니다.
 *
 * ## 캐시 헤더를 여기서 붙이지 않는다
 *
 * `ETag` 와 `Cache-Control` 은 공개 조회 전체에 걸리는 규약이라
 * `PublicEtagFilter` · `CacheControlFilter` 가 이미 붙인다 (ISSUE-003).
 */
@RestController
@RequestMapping("${ApiPaths.BASE}/ingredients")
class IngredientController(private val service: IngredientDictionaryService) {

    @GetMapping
    @Operation(
        summary = "재료 사전 목록",
        description = "**승인된 재료만** 반환한다 (FR-INGREDIENT-001).",
    )
    fun list(
        @Parameter(description = "재료 카테고리 슬러그 (7종)")
        @RequestParam(required = false) category: String?,

        @Parameter(description = "국내 유통 슬러그 (4종) — PRIN-P05")
        @RequestParam(required = false) availability: String?,

        @SortableBy("nameKo") page: PageQuery,
    ): PageResponse<IngredientItem> = service.list(category, availability, page)

    /** 미승인·부재 모두 **404** 다. 403 이면 존재가 새어 나간다. */
    @GetMapping("/{slug}")
    @Operation(
        summary = "재료 상세",
        description = "FR-INGREDIENT-002 의 6개 항목. 브랜드의 isSponsored 는 항상 실려 나간다 (NFR-L-02).",
    )
    fun detail(@PathVariable slug: String): IngredientDetail = service.detail(slug)
}
