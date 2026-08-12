package kr.kcocktail.cocktail.category

/**
 * 카테고리 경로를 만든다 (ISSUE-022, `FR-COCKTAIL-029` · `FR-COCKTAIL-030` · `R-C-2`).
 *
 * ## 축을 이어 붙일 자리가 없다
 *
 * [categoryPath] 는 축을 **하나만** 받는다. 오버로드도, 가변인자도, 컬렉션도 두지 않는다 —
 * 축 2개를 받는 시그니처가 하나라도 생기는 순간 `/cocktails/base/gin/style/sour` 를 만들 길이
 * 열리고, 그것이 `NFR-S-03`("축 조합 경로가 0개" — 배포 차단)이 막으려는 상황이다.
 *
 * **조합은 쿼리스트링 필터로만 표현한다** (`FR-COCKTAIL-030`).
 * `/cocktails?base=gin&style=sour` 는 이슈 018 이고 그쪽은 `noindex` 다.
 *
 * [kr.kcocktail.common.revalidate.RevalidatePaths] 가 같은 규율을 재생성 쪽에서 지킨다.
 */
object CategoryPaths {

    /** 카테고리 경로의 뿌리. `/cocktails/<축>/<슬러그>` 세 마디로 끝난다. */
    const val PREFIX = "/cocktails"

    /**
     * 단일 축 경로 하나. **축 2개를 받는 오버로드를 만들지 않는다** (`R-C-2`).
     *
     * 결과는 항상 `/cocktails/<축>/<슬러그>` 다 — 마디가 셋을 넘으면 조합 경로다.
     */
    fun categoryPath(axis: CategoryAxis, slug: String): String = "$PREFIX/${axis.slug}/$slug"
}
