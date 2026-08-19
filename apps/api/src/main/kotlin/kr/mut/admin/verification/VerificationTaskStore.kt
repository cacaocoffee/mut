package kr.mut.admin.verification

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * `verification_task` 를 쓴다. **멱등이 이 클래스의 전부다** (`PRIN-T07`, RED 22~24).
 *
 * ## 왜 upsert 인가
 *
 * 배치는 매일 같은 위반을 다시 본다. 매번 INSERT 하면 태스크 큐가 같은 문제의
 * 사본으로 가득 차고, 사람이 그 안에서 새 위반을 찾지 못한다.
 *
 * 유니크 제약(`uq_verification_task__occurrence`)에 부딪히면 **새 줄을 만들지 않고
 * 다시 연다.** `resolved` 였는데 또 걸렸다면 고쳐진 줄 알았던 것이 안 고쳐진 것이고,
 * 그건 새 사건이 아니라 **같은 사건의 재발**이다 — 이력을 나누면 그 사실이 흐려진다.
 */
@Component
class VerificationTaskStore(
    private val jdbc: JdbcTemplate,
    private val json: ObjectMapper,
) {

    /** @return 새로 연 건수와 다시 연 건수 */
    @Transactional
    fun openAll(tasks: List<VerificationTask>): OpenResult {
        var opened = 0
        var reopened = 0

        tasks.forEach { task ->
            when (upsert(task)) {
                Outcome.INSERTED -> opened++
                Outcome.REOPENED -> reopened++
                Outcome.UNCHANGED -> Unit
            }
        }
        return OpenResult(opened, reopened)
    }

    private fun upsert(task: VerificationTask): Outcome {
        // `xmax = 0` 이면 이번에 INSERT 된 행이다 — 갱신된 행과 구분하는 표준 수법.
        val inserted = jdbc.queryForObject(
            """
            INSERT INTO verification_task (task_type, entity_type, entity_id, code, detail, status)
            VALUES (?, ?, ?, ?, ?::jsonb, 'open')
            ON CONFLICT (task_type, entity_type, entity_id, code) DO UPDATE
                SET detail = EXCLUDED.detail,
                    -- 이미 열려 있으면 그대로 둔다. detected_at 을 갱신하면
                    -- "언제부터 이랬는지" 가 매일 지워진다
                    status = 'open',
                    detected_at = CASE
                        WHEN verification_task.status = 'open' THEN verification_task.detected_at
                        ELSE now()
                    END,
                    resolved_at = NULL,
                    resolved_by = NULL,
                    -- 다시 걸렸으면 지난번 "넘긴 사유"는 더 이상 유효하지 않다 (이슈 028).
                    -- 남겨 두면 열린 태스크에 해소 사유가 붙어 있는 상태가 된다.
                    resolution = NULL
            RETURNING xmax = 0
            """.trimIndent(),
            Boolean::class.java,
            task.taskType.slug,
            task.entityType,
            task.entityId,
            task.code,
            json.writeValueAsString(task.detail),
        )!!

        if (inserted) return Outcome.INSERTED

        // 갱신된 행이 방금 다시 열린 것인지, 원래 열려 있던 것인지.
        val reopened = jdbc.queryForObject(
            """
            SELECT resolved_at IS NULL AND detected_at >= now() - interval '1 second'
            FROM verification_task
            WHERE task_type = ? AND entity_type = ? AND entity_id = ? AND code = ?
            """.trimIndent(),
            Boolean::class.java,
            task.taskType.slug,
            task.entityType,
            task.entityId,
            task.code,
        ) ?: false

        return if (reopened) Outcome.REOPENED else Outcome.UNCHANGED
    }

    /**
     * RED 24 — **해소된 위반의 태스크는 자동으로 닫는다.**
     *
     * 사람이 고쳤는데 태스크가 열린 채면 큐가 거짓말을 하고, 곧 아무도 큐를 안 본다.
     * 이번 스캔에서 안 나온 열린 태스크가 곧 해소된 것이다.
     *
     * @param seen 이번 스캔이 본 위반들
     * @return 닫은 건수
     */
    @Transactional
    fun resolveMissing(seen: List<VerificationTask>, types: Set<TaskType>): Int {
        val keys = seen.map { "${it.taskType.slug}|${it.entityType}|${it.entityId}|${it.code}" }.toSet()

        val open = jdbc.queryForList(
            """
            SELECT id, task_type, entity_type, entity_id, code FROM verification_task
            WHERE status = 'open' AND task_type IN (${types.joinToString(",") { "?" }})
            """.trimIndent(),
            *types.map { it.slug }.toTypedArray(),
        )

        val stale = open.filterNot { row ->
            "${row["task_type"]}|${row["entity_type"]}|${row["entity_id"]}|${row["code"]}" in keys
        }
        if (stale.isEmpty()) return 0

        return jdbc.update(
            """
            UPDATE verification_task
               SET status = 'resolved', resolved_at = now()
             WHERE id IN (${stale.joinToString(",") { "?" }})
            """.trimIndent(),
            *stale.map { it["id"] }.toTypedArray(),
        )
    }

    data class OpenResult(val opened: Int, val reopened: Int)

    private enum class Outcome { INSERTED, REOPENED, UNCHANGED }
}
