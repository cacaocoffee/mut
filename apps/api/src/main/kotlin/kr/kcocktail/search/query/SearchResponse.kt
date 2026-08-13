package kr.kcocktail.search.query

/**
 * 통합 검색 응답 (SPEC-07 §2.4 · `FR-SEARCH-008` · `R-F5-1`).
 *
 * ## `hadChosung` 이 왜 응답에 있나
 *
 * SPEC-10 §4.3 의 `search_miss` — **Phase 1a 에서 가장 쓸모 있는 이벤트**다.
 *
 * > 에디터 1명이 하루 3~5종을 쓰는 상황에서 "다음에 뭘 등재할까" 에 데이터로 답한다.
 * > 검색됐는데 없는 칵테일이 곧 수요가 확인된 콘텐츠 후보다.
 *
 * `hadChosung` 을 따로 두는 이유는 **0건의 원인이 둘**이기 때문이다 —
 * 콘텐츠가 없거나, **초성 색인이 고장났거나.** 구분하지 못하면 색인 장애를
 * "수요 없음" 으로 읽고 영영 못 고친다.
 *
 * 이벤트를 서버가 쏘지 않는 이유: 수집은 이슈 034·035 다. 여기서는 **FE 가 쏠 수 있도록
 * 재료를 응답에 담는 것까지** 한다.
 */
data class SearchResponse(
    val query: String,
    val hadChosung: Boolean,
    val matchedCount: Int,

    /**
     * 타입별 그룹 (`R-F5-1`).
     *
     * **4종 자리를 항상 채운다** (RED 15·18). `bar` 는 Phase 1b, `article` 은 Phase 2 라
     * 지금은 늘 비어 있지만, 나중에 키가 생기면 클라이언트의 그룹 렌더링이 깨진다.
     * 빈 그룹을 생략하지 않는 이유도 같다 — 클라이언트가 **자리를 알아야** 한다.
     */
    val groups: Map<String, SearchGroup>,
)

data class SearchGroup(val items: List<SearchHit>) {
    /** 그룹마다 건수를 낸다 (RED 16). 클라이언트가 목록을 세지 않아도 되게. */
    val count: Int get() = items.size
}

/**
 * 검색 결과 한 줄. **내부 `id` 를 담지 않는다** (RED 34 · SPEC-07 §5) —
 * 공개 식별자는 `slug` 다.
 */
data class SearchHit(
    val entityType: String,
    val slug: String,
    val nameKo: String,
    val nameEn: String?,

    /**
     * 정렬 가중치 (RED 17). 산정식은 **미정**이라 `entity_type` 별 고정값이다
     * (G-13 · SPEC-06 §7 · DECISIONS §1.9).
     *
     * 응답에 싣는 이유는 클라이언트가 재정렬할 수 있어야 해서가 아니라,
     * **정렬 근거가 보여야 산정식을 고칠 때 무엇이 달라졌는지 알 수 있어서**다.
     */
    val weight: Int,
)
