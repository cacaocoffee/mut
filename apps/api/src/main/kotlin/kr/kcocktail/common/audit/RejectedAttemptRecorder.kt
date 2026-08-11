package kr.kcocktail.common.audit

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * **거부된 시도**를 남긴다 (`NFR-D-04`).
 *
 * 보통의 감사는 본 트랜잭션에 붙어 있어야 한다 — 전이가 롤백되면 기록도 없어야 하니까
 * ([AuditRecorder] 가 `MANDATORY` 인 이유). 거부된 시도는 정확히 반대다.
 * 요청이 예외로 끝나 롤백되는 것이 **정상 동작**이고, 그때 같은 트랜잭션에 적은 기록은
 * 함께 사라진다. 그러면 `NFR-D-04` 의 "발견 시 즉시 조사"에서 발견할 것이 영영 없다.
 *
 * 그래서 새 트랜잭션에서 쓰고 즉시 커밋한다. 바깥이 롤백돼도 남는다.
 */
@Component
class RejectedAttemptRecorder(private val recorder: AuditRecorder) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun record(
        entityType: String,
        entityId: Long,
        action: AuditAction,
        before: Any? = null,
        after: Any? = null,
    ) = recorder.record(entityType, entityId, action, before, after)
}
