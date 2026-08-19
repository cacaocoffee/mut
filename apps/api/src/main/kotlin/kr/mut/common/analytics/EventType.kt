package kr.mut.common.analytics

/**
 * 수집하는 이벤트 (SPEC-10 §4·§5).
 *
 * ## 1b·2 값을 지금 정의한다
 *
 * `bar_view` · `cross_nav` · `partner_action` 은 아직 심을 화면이 없다. 그래도 넣는다 —
 * 나중에 열거를 늘리면 이 목록을 읽는 쪽(수집 API 검증 · 대시보드 필터)이 그때 깨진다.
 * `AuditAction` · `TaskType` 과 같은 판단이다.
 *
 * ## payload 스키마를 타입마다 못박는다
 *
 * 임의 JSON 을 받으면 **좌표와 개인정보가 샌다** (`PRIN-D04` · SPEC-10 §2 "개인 식별 금지").
 * 클라이언트가 실수로 넣은 것을 서버가 그대로 저장하면, 그것을 지우는 일은
 * 이미 쌓인 행을 뒤지는 일이 된다.
 *
 * [allowedPayloadKeys] 밖의 키는 **버린다** — 거부가 아니라 무시다.
 * 이벤트 하나 때문에 페이지의 다른 계측까지 잃을 이유가 없다 (`NFR-R-04`).
 */
enum class EventType(
    val code: String,
    val phase: Phase,
    /** 이 이벤트가 담을 수 있는 payload 키. 여기 없는 것은 저장되지 않는다. */
    val allowedPayloadKeys: Set<String>,
) {

    // ── Phase 1a (SPEC-10 §4) ─────────────────────────────────────────────

    /** 어떤 칵테일이 실제로 읽히나. `entryPoint = external` 비율이 곧 SEO 성과다. */
    COCKTAIL_VIEW("cocktail_view", Phase.P1A, setOf("cocktailSlug", "entryPoint")),

    /** 축이 여섯인데 실제로 뭘 쓰나. **안 쓰는 축은 UI 에서 내릴 수 있다.** */
    FILTER_APPLY("filter_apply", Phase.P1A, setOf("axis", "value", "resultCount", "activeAxisCount")),

    /**
     * **Phase 1a 에서 가장 쓸모 있는 이벤트다** (SPEC-10 §4.3).
     *
     * 검색됐는데 없는 칵테일이 곧 **수요가 확인된 콘텐츠 후보**다.
     * `hadChosung` 을 따로 두는 이유: 초성 검색이 0건이면 콘텐츠가 없는 게 아니라
     * **초성 색인이 고장난 것**일 수 있다. 두 원인을 구분해야 한다.
     */
    SEARCH_MISS("search_miss", Phase.P1A, setOf("query", "matchedCount", "hadChosung")),

    /** 어느 질문에서 이탈하나. `step = 4` 도달로 완주를 판정한다 (별도 이벤트를 두지 않는다). */
    FINDER_STEP("finder_step", Phase.P1A, setOf("step", "answered", "candidateCount")),

    /** 상세에서 실제로 뭘 만지나. 아무도 안 쓰는 컨트롤은 화면을 복잡하게만 한다. */
    RECIPE_INTERACT("recipe_interact", Phase.P1A, setOf("cocktailSlug", "action", "detail")),

    BOOKMARK_ADD("bookmark_add", Phase.P1A, setOf("targetType", "targetSlug")),

    SHARE_CLICK("share_click", Phase.P1A, setOf("targetType", "targetSlug", "channel")),

    // ── Phase 1b · 2 (SPEC-10 §5) ─────────────────────────────────────────

    BAR_VIEW("bar_view", Phase.P1B, setOf("barSlug", "entryPoint")),

    /** 칵테일 ↔ 바를 오가는 흐름. `from_*` · `to_*` 컬럼이 이것을 위해 미리 있다. */
    CROSS_NAV("cross_nav", Phase.P1B, setOf("fromType", "fromId", "toType", "toId")),

    PARTNER_ACTION("partner_action", Phase.P1B, setOf("barSlug", "action")),
    ;

    val isPhase1a: Boolean get() = phase == Phase.P1A

    enum class Phase { P1A, P1B }

    companion object {
        fun find(code: String): EventType? = entries.firstOrNull { it.code == code }
    }
}

/**
 * 유입 분류 (SPEC-10 §3).
 *
 * **원본 URL 을 저장하지 않는다.** 유기 검색 비중을 세는 데는 이 다섯이면 충분하고,
 * 원본에는 개인정보가 섞일 수 있다 — 사내 위키 주소, 초대 링크의 토큰 같은 것들.
 */
enum class ReferrerType(val code: String) {
    ORGANIC("organic"),
    INTERNAL("internal"),
    SOCIAL("social"),
    DIRECT("direct"),
    UNKNOWN("unknown"),
    ;

    companion object {
        fun find(code: String?): ReferrerType? =
            code?.let { entries.firstOrNull { type -> type.code == it } }
    }
}
