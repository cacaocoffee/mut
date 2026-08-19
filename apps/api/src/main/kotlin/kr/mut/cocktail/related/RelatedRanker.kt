package kr.mut.cocktail.related

/**
 * 배리에이션 순위 (ISSUE-021 · `FR-COCKTAIL-024` · `R-C-3`).
 *
 * ## 순위 규칙이 이 이슈의 전부다
 *
 * ```
 * 1순위: style_primary 일치
 * 2순위: base_spirit 일치
 * ```
 *
 * `style_primary` 가 앞서는 이유는 **만드는 방식이 닮은 것**이 배리에이션이기 때문이다.
 * 같은 기주라도 하이볼과 스피릿 포워드는 다른 음료고, 반대로 진 네그로니와 럼 네그로니는
 * 기주가 달라도 같은 계열이다.
 *
 * ## 순수 함수인 이유
 *
 * 순위가 이 이슈의 **유일한 로직**이라 DB 없이 전수로 검증한다.
 * SQL 안에 넣으면 `ORDER BY` 표현식을 읽고 순위를 역추적해야 하고,
 * 그때 "둘 다 일치" 가 최상위인지 같은 질문이 다시 열린다.
 *
 * ## 명시되지 않은 것들 — 보수적 기본값
 *
 * | 질문 | 결정 | 왜 |
 * |---|---|---|
 * | 둘 다 일치가 최상위인가 | **그렇다** | 1순위 안에서 2순위로 다시 정렬한 것과 같다 |
 * | 둘 다 불일치면 | **제외** | 후순위로 채우면 "아무 상관 없는 칵테일" 이 배리에이션으로 보인다 |
 * | 자기 자신 | **제외** | |
 * | 개수 상한 | **8건** | 임의값. 카드 2행 (DECISIONS §1.2) |
 * | 동점 정렬 | **슬러그 사전순** | 결정론이 없으면 같은 요청이 페이지마다 다른 순서를 준다 |
 */
object RelatedRanker {

    /** DECISIONS §1.2 — 카드 2행. 임의값이라 근거가 생기면 바꾼다. */
    const val LIMIT = 8

    fun rank(target: CocktailRef, candidates: List<CocktailRef>): List<Related> =
        candidates
            .filterNot { it.slug == target.slug }
            .mapNotNull { it.relatedTo(target) }
            // 동점은 슬러그로 끊는다 — 결정론이 없으면 테스트가 흔들리고,
            // 사용자는 같은 화면을 새로고침할 때마다 다른 순서를 본다
            .sortedWith(compareBy({ it.matchedOn.rank }, { it.cocktail.slug }))
            .take(LIMIT)

    private fun CocktailRef.relatedTo(target: CocktailRef): Related? {
        val style = stylePrimary == target.stylePrimary
        val base = baseSpirit == target.baseSpirit

        val matched = when {
            style && base -> MatchedOn.BOTH
            style -> MatchedOn.STYLE
            base -> MatchedOn.BASE
            // 둘 다 아니면 배리에이션이 아니다. 채우면 상세 하단이
            // "관련 없는 칵테일 8개" 가 되고, 그건 추천이 아니라 잡음이다
            else -> return null
        }
        return Related(this, matched)
    }
}

/**
 * 순위 판정에 필요한 것만. **엔티티가 아니다** — 순수 함수가 영속성을 알 이유가 없고,
 * 테스트가 8종을 손으로 만들 수 있어야 전수 검증이 싸다.
 */
data class CocktailRef(
    val slug: String,
    val nameKo: String,
    val nameEn: String,
    val summary: String,
    val stylePrimary: String,
    val baseSpirit: String,
)

/**
 * 무엇이 맞아서 추천됐는가 (RED 15).
 *
 * 응답에 싣는 이유: FE 가 "같은 스타일" 배지를 그린다. 사유 없이 목록만 주면
 * 사용자는 **왜 이게 여기 있는지** 모르고, 그 순간 추천은 신뢰를 잃는다.
 *
 * `rank` 가 곧 정렬 순서다 — 상수를 따로 두면 열거 순서와 어긋날 수 있다.
 */
enum class MatchedOn(val rank: Int) {
    BOTH(0),
    STYLE(1),
    BASE(2),
}

data class Related(val cocktail: CocktailRef, val matchedOn: MatchedOn)
