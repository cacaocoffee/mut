package kr.kcocktail.common.revalidate

/**
 * 칵테일 하나가 바뀌었을 때 다시 만들어야 하는 정적 경로 (SPEC-05 §4).
 *
 * ## 축 조합 경로를 만들 수 없는 구조다
 *
 * `R-C-2` 가 축 조합 경로(`/cocktails/base/gin/style/sour`)를 금지한다.
 * 여기서 축을 이어 붙이는 자리를 아예 두지 않는 이유가 그것이다 —
 * 리스트에 고정된 다섯 줄뿐이고, 각 줄은 축을 **하나만** 쓴다.
 * 반복문으로 조합을 만들면 그 순간 `R-C-2` 를 어길 길이 열린다.
 *
 * ## `styles` 가 복수여도 경로는 `style_primary` 하나다
 *
 * 스타일은 여러 개를 가질 수 있지만 카테고리 경로는 대표 하나만 만든다 (`R-C-2`).
 * 전부 만들면 같은 칵테일이 여러 카테고리의 정본처럼 보이고 색인이 갈린다.
 */
object RevalidatePaths {

    /** 사이트맵도 대상이다 — 발행분 전체가 들어가야 한다 (`NFR-S-04`). */
    const val SITEMAP = "/sitemap.xml"

    fun forCocktail(target: RevalidateTarget): List<String> = listOf(
        "/cocktails/${target.slug}",
        "/cocktails/base/${target.baseSpiritSlug}",
        "/cocktails/style/${target.stylePrimarySlug}",
        "/cocktails/method/${target.methodSlug}",
        SITEMAP,
    ).distinct()
}

/**
 * 경로를 만드는 데 필요한 것만. **엔티티가 아니다** —
 * `common` 이 `cocktail` 을 타입으로 알면 의존이 거꾸로 선다 (경계 테스트, 이슈 001).
 */
data class RevalidateTarget(
    val slug: String,
    val baseSpiritSlug: String,
    val stylePrimarySlug: String,
    val methodSlug: String,
)
