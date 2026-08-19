package kr.mut.cocktail.api

/**
 * 발행 이후의 생애 사건 (SPEC-05 §3 — 부수효과는 도메인 이벤트로).
 *
 * ## `cocktail` 이 `search` 를 부르지 않는다
 *
 * 색인을 갱신해야 하는 쪽은 이슈 017 이다. `cocktail` 이 `search` 를 직접 부르면
 * `search → cocktail` 과 맞물려 순환이 생기고, 경계 테스트(이슈 001)가 막는다.
 * 사건만 알리고 누가 듣는지는 모른다.
 *
 * [CocktailPublished] 는 발행 게이트와 함께 `PublishGate.kt` 에 있다 (이슈 013).
 */

/** 회수 — **색인에서 내려야 한다.** `draft` 는 공개 API 에서 404 다 (SPEC-07 §5). */
data class CocktailUnpublished(
    val entityId: Long,
    val slug: String,
)

/** 보관 — 회수와 마찬가지로 색인에서 내린다. 구분해 두는 이유는 사후 조사에서 뜻이 달라서다. */
data class CocktailArchived(
    val entityId: Long,
    val slug: String,
)

/**
 * 이름·별칭이 바뀌었다.
 *
 * **발행 상태가 그대로여도 색인은 바뀌어야 한다** — 검색어가 이름과 별칭이라
 * (`R-F2.1-3`), 갱신하지 않으면 바뀐 이름으로 찾을 수 없다.
 *
 * `slug` 는 여기 없다. 바뀌지 않기 때문이다 (`INV-COCKTAIL-05`) — 식별자로만 싣는다.
 */
data class CocktailRenamed(
    val entityId: Long,
    val slug: String,
    val nameKo: String,
    val nameEn: String,
    val aliases: List<String>,
)
