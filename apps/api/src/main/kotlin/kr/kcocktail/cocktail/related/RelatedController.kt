package kr.kcocktail.cocktail.related

import io.swagger.v3.oas.annotations.Operation
import kr.kcocktail.common.web.ApiPaths
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * `GET /cocktails/{slug}/related` — 배리에이션 (SPEC-07 §2.1 · `R-C-3`).
 *
 * ## `noindex` 를 붙이지 않는다
 *
 * 상세 페이지의 일부다 (RED 21). 필터·검색 결과와 다르다 — 상세는 색인 대상이고
 * (`NFR-S-01`) 그 안의 배리에이션 목록도 같은 페이지에서 렌더링된다.
 *
 * ## 캐시 헤더도 붙이지 않는다
 *
 * `ETag` 와 `Cache-Control` 은 공개 조회 전체 규약이라 필터가 이미 붙인다 (ISSUE-003).
 * 컨트롤러가 또 붙이면 검증자가 두 곳에서 나오고, 어느 쪽이 이기는지가 필터 순서에 달린다.
 */
@RestController
@RequestMapping("${ApiPaths.BASE}/cocktails")
class RelatedController(private val reader: RelatedReader) {

    @GetMapping("/{slug}/related")
    @Operation(
        summary = "배리에이션",
        description = "style_primary 일치가 1순위, base_spirit 일치가 2순위다 (R-C-3). " +
            "둘 다 아니면 제외한다 — 채우면 추천이 아니라 잡음이 된다.",
    )
    fun related(@PathVariable slug: String): RelatedResponse =
        RelatedResponse(reader.related(slug).map { it.toItem() })
}

/** 없으면 **빈 배열**이다 (RED 12) — 404 가 아니다. 칵테일은 있고 닮은 것이 없을 뿐이다. */
data class RelatedResponse(val items: List<RelatedItem>)

/**
 * 카드 한 장 (RED 13). **내부 `id` 를 담지 않는다** (RED 14 · SPEC-07 §5).
 */
data class RelatedItem(
    val slug: String,
    val nameKo: String,
    val nameEn: String,
    val summary: String,

    /**
     * 무엇이 맞아서 추천됐는가 (RED 15).
     *
     * FE 가 "같은 스타일" 배지를 그린다. 사유 없이 목록만 주면 사용자는
     * **왜 이게 여기 있는지** 모르고, 그 순간 추천은 신뢰를 잃는다.
     */
    val matchedOn: String,
)

private fun Related.toItem() = RelatedItem(
    slug = cocktail.slug,
    nameKo = cocktail.nameKo,
    nameEn = cocktail.nameEn,
    summary = cocktail.summary,
    matchedOn = matchedOn.name.lowercase(),
)
