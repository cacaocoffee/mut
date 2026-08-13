package kr.kcocktail.search.query

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import kr.kcocktail.common.web.ApiPaths
import kr.kcocktail.search.list.CocktailListController
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 통합 검색 (SPEC-07 §2.4 · `FR-SEARCH-006`~`008`).
 *
 * ## 레이트 리밋이 다른 조회보다 조이다
 *
 * `/search` · `/search/suggest` 는 **60 req/min** (IP) 이다 (SPEC-08 §6 · `NFR-SEC-05`).
 * 정책은 `RateLimitPolicy.SEARCH` 에 있고 필터가 건다 — 컨트롤러가 다시 세지 않는다.
 *
 * > 검색을 더 조이는 이유는 초성·별칭 매칭이 GIN 인덱스를 타긴 해도
 * > **가장 비싼 조회**이기 때문이다.
 *
 * ## `noindex` 를 붙인다
 *
 * 검색 결과는 색인 대상이 아니다 (`PRIN-P06` · DECISIONS §1.6) — 필터 결과와 같다.
 */
@RestController
@RequestMapping("${ApiPaths.BASE}/search")
class SearchController(private val reader: SearchReader) {

    @GetMapping
    @Operation(
        summary = "통합 검색",
        description = "타입별로 그룹핑한다 (R-F5-1). 초성·별칭·띄어쓰기 변형을 매칭한다.",
    )
    fun search(
        @Parameter(description = "검색어. 초성만으로 이뤄지면 초성 검색이다")
        @RequestParam(required = false) q: String?,
    ): ResponseEntity<SearchResponse> =
        noindex(reader.search(SearchQuery.of(q), GROUP_LIMIT))

    @GetMapping("/suggest")
    @Operation(
        summary = "자동완성",
        description = "프리픽스 매칭. 초성 입력도 앞에서부터 맞춘다.",
    )
    fun suggest(
        @RequestParam(required = false) q: String?,
    ): ResponseEntity<List<SearchHit>> =
        noindex(reader.suggest(SearchQuery.of(q), SUGGEST_LIMIT))

    private fun <T> noindex(body: T): ResponseEntity<T> = ResponseEntity.ok()
        .header(CocktailListController.ROBOTS_HEADER, CocktailListController.NOINDEX)
        .body(body)

    companion object {
        /**
         * 그룹당 상한 (RED 30). 페이징하지 않는 이유: 통합 검색은 **훑어보는 화면**이라
         * 타입마다 앞쪽 몇 개가 보이면 되고, 더 보려면 목록 API 로 넘어간다.
         */
        const val GROUP_LIMIT = 10

        /** 자동완성은 드롭다운이라 더 짧다 (RED 32). */
        const val SUGGEST_LIMIT = 8
    }
}
