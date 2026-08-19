package kr.mut.admin.audit

import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant

/**
 * 감사 로그 한 줄 (ISSUE-029 · `FR-ADMIN-005` · `PRIN-T08`).
 *
 * `NFR-O-05` 가 요구하는 것은 **"누가 · 무엇을 · 언제" 재구성**이다.
 * 그래서 [actor] · [before] · [after] · [at] 이 한 줄에 다 있어야 한다 —
 * 어느 하나가 빠지면 다른 곳을 뒤져야 하고, 다툼이 생겼을 때 그 뒤짐이 근거를 약하게 만든다.
 */
data class AuditLogItem(
    val id: Long,
    val entityType: String,
    val entityId: Long,
    val action: String,

    /** `null` 이면 사람이 아니다 — 배치·마이그레이션 (`AuditRecorder` 의 `CurrentActor`). */
    val actor: ActorRef?,

    val before: JsonNode?,
    val after: JsonNode?,

    /**
     * 행위 시각.
     *
     * SPEC-06 §3.8 은 이 자리를 `at` 이라 불렀고 컬럼은 `created_at` 이다 (GAPS G-26).
     * **감사 행은 한 번 쓰이고 다시 바뀌지 않으므로 생성 시각이 곧 행위 시각**이다.
     * API 는 스펙의 이름을 쓴다 — 컬럼 사정이 계약에 새어 나갈 이유가 없다.
     */
    val at: Instant,
)

/**
 * 행위자 (RED 18~20).
 *
 * `audit_log.actor_user_id` 에 **FK 가 없다** (이슈 014 · SPEC-08 §5.3) —
 * "탈퇴해도 `actor_user_id` 는 유지" 와 "user 행 즉시 삭제" 를 둘 다 만족하려면 그래야 한다.
 *
 * 그 결과가 여기 드러난다: **id 는 남는데 조인이 안 되는 행이 생긴다.**
 * `displayName` 이 `null` 인 것이 그 상태이고, [withdrawn] 이 그것을 이름 붙인다 —
 * 클라이언트가 `displayName == null` 을 각자 해석하면 "탈퇴" 와 "이름 없음" 이 섞인다.
 */
data class ActorRef(
    val userId: Long,
    val displayName: String?,
) {
    /** `true` 면 "탈퇴한 사용자" 로 표시한다 (RED 20). 이력은 남고 사람만 사라진 상태다. */
    val withdrawn: Boolean get() = displayName == null
}
