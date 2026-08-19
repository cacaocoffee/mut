package kr.mut.common.security.authz

/**
 * SPEC-08 §2 권한 매트릭스의 **각 행**.
 *
 * ## 노출 규칙 액션은 여기 없다
 *
 * 부스팅 한도 · 홈 슬롯 비율을 바꾸는 액션이 **의도적으로 부재한다** (`PRIN-P02` · `FR-ADMIN-006`).
 * `admin` 도 못 바꾼다. 바꾸려면 코드를 고치고 배포해야 한다.
 *
 * > 그게 의도다 — 영업 압박이 들어오는 순간 "어드민에서 잠깐만"이 가능하면 반드시 그렇게 된다.
 *
 * 이 enum 에 `CHANGE_BOOST_LIMIT` 같은 것을 추가하는 순간 규칙이 존재하게 된다.
 * `PermissionMatrixTest` 가 부재를 지킨다.
 */
enum class Action(val denial: DenialMode, val phase: Phase = Phase.P1A) {

    // ── Phase 1a ────────────────────────────────────────────────────────────

    /** 발행된 콘텐츠 조회. 비로그인 포함 누구나. */
    VIEW_PUBLISHED(DenialMode.HIDE),

    /** `draft` 조회. 거부는 **404** 다 — 403 이면 "그 슬러그는 존재한다"가 새어 나간다. */
    VIEW_DRAFT(DenialMode.HIDE),

    /**
     * `archived` 조회 (SPEC-07 §5).
     *
     * 이슈의 액션 목록에는 없지만 RED 10 이 요구하는 행위다. `draft` 와 같은 규칙을 쓰되
     * 별도 액션으로 둔다 — 폐기와 미발행은 다른 상태이고, 나중에 정책이 갈릴 수 있다.
     */
    VIEW_ARCHIVED(DenialMode.HIDE),

    /** 칵테일 · 재료 생성 · 수정. */
    WRITE_CONTENT(DenialMode.FORBID),

    /** 발행 · 회수. */
    PUBLISH(DenialMode.FORBID),

    /** 재료 마스터 승인 — `admin` 만 (SPEC-08 §2, DECISIONS §1). */
    APPROVE_INGREDIENT(DenialMode.FORBID),

    /** 검증 태스크 처리. */
    RESOLVE_TASK(DenialMode.FORBID),

    /** 감사 로그 조회 — `admin` 만. */
    VIEW_AUDIT_LOG(DenialMode.FORBID),

    /** 북마크 · 컬렉션. **자기 것만** (`◐`). 남의 것은 404 다. */
    OWN_BOOKMARK(DenialMode.HIDE),

    // ── Phase 1b · 2 ────────────────────────────────────────────────────────
    //
    // enum 에는 지금 정의한다. 나중에 늘리면 생성 타입이 바뀌어 클라이언트가 깨진다.
    // 매트릭스 평가는 EPICS-1B-PHASE2.md 가 이어받는다.

    EDIT_SIGNATURE_MENU(DenialMode.FORBID, Phase.P1B),
    EDIT_FULL_MENU(DenialMode.FORBID, Phase.P1B),
    EDIT_BAR_INFO(DenialMode.FORBID, Phase.P1B),
    REQUEST_BAR_EDIT(DenialMode.FORBID, Phase.P1B),
    VIEW_PARTNER_STATS(DenialMode.HIDE, Phase.P1B),

    /** 제휴 등급 변경 — `editor` 에게 **주지 않는다.** 권한 분리 자체가 중립성 장치다 (SPEC-08 §2.2). */
    CHANGE_TIER(DenialMode.FORBID, Phase.P1B),

    /** 내 술장 (`FR-STOCK-*`). */
    OWN_STOCK(DenialMode.HIDE, Phase.P2),
    ;

    val isPhase1a: Boolean get() = phase == Phase.P1A

    enum class Phase { P1A, P1B, P2 }
}

/**
 * 거부를 어떻게 알릴 것인가. **이 구분이 이 이슈의 판단이다.**
 *
 * | | 언제 | 상태 |
 * |---|---|---|
 * | [HIDE] | 리소스의 **존재 자체가 비밀** — `draft` · 타인 소유 | `404` |
 * | [FORBID] | 리소스는 공개인데 **액션 권한이 없음** — `member` 가 발행 시도 | `403` |
 *
 * 헷갈리면 이렇게 묻는다: **거부 사실을 알려 주는 것만으로 무언가가 새는가?**
 * `draft` 를 403 으로 돌려주면 "그 슬러그는 존재한다"가 새어 나간다 (SPEC-07 §1.4).
 * 반대로 어드민 엔드포인트에 403 을 주는 것은 새는 게 없다 — 그 엔드포인트는 문서에 있다.
 */
enum class DenialMode { HIDE, FORBID }
