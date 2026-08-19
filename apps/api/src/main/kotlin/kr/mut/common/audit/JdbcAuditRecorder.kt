package kr.mut.common.audit

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * `audit_log` 에 한 줄 넣는다. **INSERT 밖에 하지 않는다** —
 * 앱 역할에 `UPDATE` · `DELETE` 권한 자체가 없다 (`V014__audit_log.sql`).
 *
 * JPA 엔티티를 두지 않은 이유: `audit_log` 는 `BaseEntity` 의 공통 컬럼 규약
 * (`created_at` · `updated_at`) 을 따르지 않는다. 시각이 `at` 하나뿐이고 갱신이 없다.
 * 영속성 컨텍스트에 실어 둘 이유도 없다 — 쓰고 잊는 기록이다.
 */
@Component
class JdbcAuditRecorder(
    private val jdbc: JdbcTemplate,
    private val actor: CurrentActor,
    private val json: ObjectMapper,
) : AuditRecorder {

    /**
     * `MANDATORY` 다. 부르는 쪽에 트랜잭션이 없으면 **터진다.**
     *
     * 이것이 `PRIN-T08` 을 지키는 방식이다 — 감사를 트랜잭션 밖에서 부르는 코드는
     * 전이가 롤백돼도 기록이 남고, 감사가 실패해도 전이가 성공한다. 둘 다 이력을 거짓말로 만든다.
     * 실수를 런타임에 드러내는 편이 조용히 어긋나는 것보다 낫다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    override fun record(
        entityType: String,
        entityId: Long,
        action: AuditAction,
        before: Any?,
        after: Any?,
    ) {
        jdbc.update(
            """
            INSERT INTO audit_log (entity_type, entity_id, action, actor_user_id, before, after)
            VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb)
            """.trimIndent(),
            entityType,
            entityId,
            action.slug,
            actor.userId(),
            before?.let { json.writeValueAsString(it) },
            after?.let { json.writeValueAsString(it) },
        )
    }
}
