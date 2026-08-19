package kr.mut.common.security.authz

/**
 * SPEC-08 §2 표의 기호를 타입으로 옮긴 것.
 *
 * | 기호 | 타입 |
 * |---|---|
 * | `○` 가능 | [Anything] |
 * | `◐` 자기 것만 | [Own] |
 * | `★` 자기 바만 | [OwnBar] — Phase 1b |
 */
sealed interface Scope {

    /** `○` — 대상을 가리지 않는다. */
    data object Anything : Scope

    /** `◐` — 소유자만. 남의 것에 손대면 **404** 다 (존재를 흘리지 않는다). */
    data class Own(val ownerId: Long) : Scope

    /**
     * `★` — 자기 바만. **Phase 1b.**
     *
     * SPEC-08 §3.2 IDOR 방어: 경로의 `barId` 를 신뢰하지 않는다.
     * `bar_owner` 에 `(userId, barId)` 행이 없으면 **403 이 아니라 404** 다.
     *
     * `bar` 실물이 없어 지금은 평가하지 않는다 — 판정기를 만들어 두면
     * 검증되지 않은 채로 다른 이슈가 그것을 믿고 쓰게 된다 (`EPICS-1B-PHASE2.md` 1B-E8).
     */
    data class OwnBar(val barId: Long) : Scope
}
