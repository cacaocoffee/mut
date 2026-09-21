package kr.mut.ingredient.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import kr.mut.common.web.ApiPaths
import kr.mut.common.web.page.PageQuery
import kr.mut.common.web.page.SortableBy
import kr.mut.ingredient.internal.IngredientDictionaryService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 공개 유통 제품 목록 (#205 · G-41).
 *
 * "이 술 국내에 들어오나" 에 답한다. 식약처 수입신고 기준이라 "신고가 있었다" 까지만 말한다.
 * 제품명·수입사는 사실 정보라 공개한다. 구매 링크·가격은 계약에 없다 (`NFR-L-05`).
 * 캐시·ETag 는 공개 조회 전체의 필터가 붙인다 (ISSUE-003).
 */
@RestController
@RequestMapping("${ApiPaths.BASE}/products")
class ProductController(private val service: IngredientDictionaryService) {

    @GetMapping
    @Operation(
        summary = "유통 제품 목록 (공개)",
        description = "식약처 수입신고 제품. 제품명(한/영)·수입사 부분일치와 식품유형으로 거른다. 최근 신고순 고정.",
    )
    fun list(
        @Parameter(description = "제품명 · 수입사의 일부") @RequestParam(required = false) q: String?,
        @Parameter(description = "식품유형 (위스키 · 리큐르 · 과실주 …)") @RequestParam(required = false) foodType: String?,
        @Parameter(description = "name 이면 제품명만 본다 (수입사 제외). 기본은 제품명·수입사 둘 다")
        @RequestParam(required = false) scope: String?,
        @SortableBy page: PageQuery,
    ): ProductListResponse = service.products(q, foodType, nameOnly = scope == "name", page)
}
