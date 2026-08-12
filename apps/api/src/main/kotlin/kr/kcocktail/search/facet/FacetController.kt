package kr.kcocktail.search.facet

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import kr.kcocktail.common.web.ApiPaths
import kr.kcocktail.search.list.CocktailFilterParser
import kr.kcocktail.search.list.CocktailListController
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * `GET /cocktails/facets` — 필터 값별 결과 개수 (SPEC-07 §3.2 · `FR-SEARCH-002`).
 *
 * `R-F2.1-2` 는 **모든 필터 값 옆에 개수**를 요구하고 0건은 비활성 처리하라고 한다.
 * PRD 가 "초기부터 넣지 않으면 나중에 UI 를 다시 짜야 한다" 고 못박은 항목이다.
 *
 * ## 파라미터가 목록과 같다
 *
 * 같은 쿼리스트링을 그대로 받는다 (SPEC-05 §5 — "UI 계약은 두 단계에서 동일하다.
 * **계산 위치만 바뀐다**"). 파서를 공유하므로 모르는 값은 양쪽에서 똑같이 400 이다.
 *
 * ## `noindex` 를 붙인다
 *
 * 필터 계열이라 색인 대상이 아니다 (`PRIN-P06` · DECISIONS §1.6) —
 * 상세·카테고리와 정반대다.
 */
@RestController
@RequestMapping("${ApiPaths.BASE}/cocktails")
class FacetController(private val reader: FacetReader) {

    @GetMapping("/facets")
    @Operation(
        summary = "필터 값별 결과 개수",
        description = "기주·스타일·메이킹·당도·도수는 같은 축 선택을 무시하고, " +
            "향·맛만 현재 선택에 더했을 때의 수다 (SPEC-07 §3.2).",
    )
    fun facets(
        @Parameter(description = "기주 슬러그. 콤마로 여러 개")
        @RequestParam(required = false) base: String?,
        @RequestParam(required = false) style: String?,
        @RequestParam(required = false) method: String?,
        @RequestParam(required = false) sweet: String?,
        @RequestParam(required = false) abv: String?,
        @RequestParam(required = false) flavor: String?,
        @RequestParam(required = false) q: String?,
    ): ResponseEntity<FacetCounts> {
        val filter = CocktailFilterParser.parse(base, style, method, sweet, abv, flavor, q)

        return ResponseEntity.ok()
            .header(CocktailListController.ROBOTS_HEADER, CocktailListController.NOINDEX)
            .body(reader.counts(filter))
    }
}
