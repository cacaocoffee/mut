package kr.mut.ingredient.api

/**
 * 재료가 저장됐다 (SPEC-05 §3 — 부수효과는 도메인 이벤트로).
 *
 * ## 왜 이벤트인가
 *
 * 색인 갱신은 `search` 모듈의 일이다. `ingredient` 가 `search` 를 직접 부르면
 * **순환이 생긴다**:
 *
 * ```
 * 008 → 017 → 014 → 013 → 010 → 008
 * ```
 *
 * SPEC-05 §3 이 "조회는 SEARCH 가 담당해 순환을 끊는다"고 한 것이 이 얘기다.
 * 발행하는 쪽은 누가 듣는지 모르고, 듣는 쪽(이슈 017)이 구독한다.
 *
 * ## 색인에 필요한 것만 담는다
 *
 * 엔티티를 담으면 리스너가 다른 트랜잭션에서 지연 로딩을 만난다.
 */
data class IngredientSaved(
    val entityId: Long,
    val slug: String,
    val nameKo: String,
    val nameEn: String,
    val aliases: List<String>,
    /** 미승인 재료는 색인하지 않는다 — 공개 검색에 나오면 안 된다. */
    val isApproved: Boolean,
)
