package kr.mut.search.list

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import kr.mut.common.web.ApiPaths
import kr.mut.common.web.page.PageQuery
import kr.mut.common.web.page.PageResponse
import kr.mut.common.web.page.SortableBy
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * `GET /cocktails` — 목록 · 필터 (SPEC-07 §3.1 · `FR-SEARCH-001`·`003`·`005`).
 *
 * ## 발행분만 보인다
 *
 * `draft`·`archived` 는 목록에 없다 (SPEC-07 §2.1·§5). 상태로 걸러내는 것이 아니라
 * **애초에 조건에 넣지 않는다** — 필터로 다시 열 수 있는 문을 만들지 않는다.
 *
 * ## 색인하지 않는다 (`R-F2.1-1` · `NFR-S-02`)
 *
 * 필터 결과는 쿼리스트링이고 색인 대상이 아니다 (`PRIN-P06`). 응답마다
 * `X-Robots-Tag: noindex` 를 붙인다. **전역 필터로 붙이지 않는 것이 중요하다** —
 * 카테고리 경로(이슈 022)와 상세(이슈 020)는 색인해야 하고(`NFR-S-01`·`S-02`),
 * 전역으로 붙이면 그쪽까지 색인에서 사라진다. 배포 차단 조건이다.
 *
 * ## 서버가 계약을 갖는 이유
 *
 * SPEC-05 §4 는 Phase 1 프론트가 **전체를 받아 클라이언트에서 거른다**고 했다.
 * 모순이 아니다 — 서버가 같은 URL 계약을 제공해야(SSG 빌드·확장 대비) 나중에
 * 갈아끼울 수 있다. "데이터가 커지면 서버 필터로 옮기되 URL 계약은 유지한다."
 */
@RestController
@RequestMapping("${ApiPaths.BASE}/cocktails")
class CocktailListController(private val reader: CocktailListReader) {

    @GetMapping
    @Operation(
        summary = "칵테일 목록 · 필터",
        description = "발행분만 반환한다. 필터 결과는 색인하지 않는다 (X-Robots-Tag: noindex).",
    )
    fun list(
        @Parameter(description = "기주 슬러그. 콤마로 여러 개 — **OR**")
        @RequestParam(required = false) base: String?,

        @Parameter(description = "스타일 슬러그. 콤마로 여러 개 — **OR**. style_primary 가 아니라 보유 스타일 전체와 맞춘다")
        @RequestParam(required = false) style: String?,

        @Parameter(description = "메이킹 방법 슬러그. 콤마로 여러 개 — **OR**")
        @RequestParam(required = false) method: String?,

        @Parameter(description = "당도 슬러그. **단일값** — 여러 개를 주면 400")
        @RequestParam(required = false) sweet: String?,

        @Parameter(description = "도수 구간 na·low·mid·high. 콤마로 여러 개 — **OR** (ADR-0003)")
        @RequestParam(required = false) abv: String?,

        @Parameter(description = "향·맛 슬러그. 콤마로 여러 개 — **AND**. 전부 가진 것만 (SPEC-07 §3.1)")
        @RequestParam(required = false) flavor: String?,

        @Parameter(description = "이름 검색. 초성·별칭까지 보는 정밀 검색은 /search 다")
        @RequestParam(required = false) q: String?,

        @SortableBy("name", "abv") page: PageQuery,
    ): ResponseEntity<PageResponse<CocktailListItem>> {
        val filter = CocktailFilterParser.parse(base, style, method, sweet, abv, flavor, q)

        return ResponseEntity.ok()
            .header(ROBOTS_HEADER, NOINDEX)
            .body(reader.find(filter, page))
    }

    companion object {
        const val ROBOTS_HEADER = "X-Robots-Tag"
        const val NOINDEX = "noindex"
    }
}
