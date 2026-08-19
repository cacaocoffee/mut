package kr.mut.common.audit

/**
 * 감사 기록의 유일한 창구 (`PRIN-T08`).
 *
 * `common` 이 소유하되 **도메인 모듈을 참조하지 않는다** — `entityType` 이 문자열인 이유다.
 * `cocktail` · `bar` · `partner` 가 전부 이것을 부르는데, `common` 이 그중 하나라도
 * 타입으로 알면 의존이 거꾸로 선다 (경계 테스트, 이슈 001).
 *
 * ## 같은 트랜잭션에서 쓴다
 *
 * 별도 트랜잭션이나 비동기로 빼지 않는다. 전이가 롤백되면 감사도 없어야 하고,
 * **감사에 실패하면 전이도 실패해야 한다** — 감사 없는 발행은 `PRIN-T08` 위반이다.
 */
interface AuditRecorder {

    /**
     * @param entityType `cocktail` · `bar` 처럼 테이블 이름을 쓴다
     * @param before·after 스냅샷. `null` 이면 해당 방향이 없다는 뜻이다 (생성 · 삭제)
     */
    fun record(
        entityType: String,
        entityId: Long,
        action: AuditAction,
        before: Any? = null,
        after: Any? = null,
    )
}

/**
 * 감사 기록의 주체.
 *
 * 배치·마이그레이션처럼 사람이 없는 경로는 `null` 이다 — 거짓 주체를 지어내지 않는다.
 * 실제 해석은 [SessionCurrentActor] 가 한다.
 */
interface CurrentActor {
    fun userId(): Long?
}
