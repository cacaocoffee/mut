package kr.kcocktail.cocktail.category

import kr.kcocktail.common.taxonomy.BaseSpirit
import kr.kcocktail.common.taxonomy.Slugged
import kr.kcocktail.common.taxonomy.SluggedLookup
import kr.kcocktail.common.taxonomy.StyleKey
import kr.kcocktail.common.taxonomy.Technique

/**
 * 카테고리 축 — **셋뿐이다** (ISSUE-022, `FR-COCKTAIL-029` · `PRIN-P06`).
 *
 * ## 축이 셋인 것이 타입에 박혀 있다
 *
 * 당도 · 도수 · 향맛은 여기 없다. 카테고리가 아니라 **필터**이기 때문이다 —
 * 카테고리로 올리는 순간 `/cocktails/sweet/high-abv/gin/` 같은 조합 폭발이 생기고
 * 중복 콘텐츠로 SEO 페널티를 받는다 (`PRIN-P06`).
 *
 * 넷째 값을 추가하려면 이 enum 을 고쳐야 하고, 그때 이 주석을 읽게 된다.
 *
 * ## 축값의 정본은 여기가 아니다
 *
 * [BaseSpirit] · [StyleKey] · [Technique] 가 정본이다 (`PRIN-T02`, ISSUE-004).
 * 여기서 다시 열거하면 ADR-0002 확정 슬러그가 두 곳에 존재하게 된다.
 */
enum class CategoryAxis(
    override val slug: String,
    override val labelKo: String,
    /** 이 축이 가질 수 있는 값 전부. 정본은 [kr.kcocktail.common.taxonomy] 다. */
    val taxonomy: List<Slugged>,
) : Slugged {
    BASE("base", "기주", BaseSpirit.entries),
    STYLE("style", "스타일", StyleKey.entries),
    METHOD("method", "메이킹", Technique.entries),
    ;

    /**
     * 건수를 세는 컬럼 (SPEC-06 §3.1).
     *
     * **스타일은 `style_primary` 다** (DECISIONS §1.11). `styles` 전체로 세면 같은 칵테일이
     * 여러 카테고리의 정본처럼 보이고 색인이 갈린다 — `R-C-3` 이 primary 를 대표로 규정했다.
     * `styles` 전체를 보는 것은 **필터**(이슈 018)이지 카테고리가 아니다.
     */
    val countColumn: String
        get() = when (this) {
            BASE -> "base_spirit"
            STYLE -> "style_primary"
            METHOD -> "method"
        }

    companion object : SluggedLookup<CategoryAxis>(entries)
}
