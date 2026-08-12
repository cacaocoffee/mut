package kr.kcocktail.admin.verification

import kr.kcocktail.common.audit.AuditAction
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * `NFR-D-04` — **슬러그 변경 이력 0건.** 있으면 즉시 조사한다.
 *
 * ## 왜 감사 로그를 보나
 *
 * 앱은 이미 막는다 (`INV-COCKTAIL-05`, 이슈 014). 그런데도 감시하는 이유는
 * **막힌 것을 확인하려는 게 아니라 뚫린 것을 찾으려는** 것이다 —
 * 마이그레이션이나 직접 UPDATE 로 바뀌면 앱은 아무것도 모른다.
 *
 * 거부된 시도(`slug_change_attempt`)도 올린다. `NFR-D-04` 가 "발견 시 즉시 조사"를
 * 요구하는데, 시도가 있었다는 것 자체가 조사할 일이다 — 누군가 그 경로를 찾고 있었다.
 */
@Component
class SlugChangeWatch(private val jdbc: JdbcTemplate) {

    @Transactional(readOnly = true)
    fun detect(): List<VerificationTask> =
        jdbc.queryForList(
            """
            SELECT entity_id, actor_user_id, before->>'slug' AS before_slug, after->>'slug' AS after_slug
              FROM audit_log
             WHERE entity_type = 'cocktail' AND action = ?
             ORDER BY id
            """.trimIndent(),
            AuditAction.SLUG_CHANGE_ATTEMPT.slug,
        ).map { row ->
            VerificationTask(
                taskType = TaskType.SLUG_CHANGED,
                entityType = InvariantVerificationBatch.COCKTAIL,
                entityId = row["entity_id"] as Long,
                code = SLUG_CODE,
                detail = mapOf(
                    "before" to row["before_slug"],
                    "after" to row["after_slug"],
                    "actorUserId" to row["actor_user_id"],
                    "why" to "슬러그 변경 시도가 감사 로그에 있다 (NFR-D-04 는 0건을 요구한다)",
                ),
            )
        }

    companion object {
        /** SPEC-02 의 불변식 ID 를 그대로 쓴다 — 큐에서 다른 위반과 같은 방식으로 읽힌다. */
        const val SLUG_CODE = "INV-COCKTAIL-05"
    }
}
