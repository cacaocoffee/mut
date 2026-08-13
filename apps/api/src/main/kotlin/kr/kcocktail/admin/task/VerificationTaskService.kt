package kr.kcocktail.admin.task

import com.fasterxml.jackson.databind.ObjectMapper
import kr.kcocktail.admin.verification.TaskType
import kr.kcocktail.common.web.ApiPaths
import kr.kcocktail.common.web.error.BadRequestException
import kr.kcocktail.common.web.error.ConflictException
import kr.kcocktail.common.web.error.ResourceNotFoundException
import kr.kcocktail.common.web.page.PageQuery
import kr.kcocktail.common.web.page.PageResponse
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp

/**
 * 검증 태스크 큐 (ISSUE-028 · `FR-ADMIN-004` · SPEC-06 §4.3).
 *
 * ## 이 이슈는 읽고 닫기만 한다
 *
 * 태스크를 **만드는** 것은 이슈 016 의 불변식 배치다. Phase 1a 에서 태스크를 만드는 것은
 * 그것 하나뿐이고, `FR-ADMIN-004` 가 예로 든 둘(`hours_verified_at` 만료 · 인스타 신호)은
 * 전부 BAR 도메인이라 Phase 1b 다.
 *
 * 그 하나만으로도 큐가 필요하다 — `NFR-D-01`(위반 0건) · `NFR-D-02`(게이트 우회 0건) 의
 * **처리 창구**가 여기다. 배치가 위반을 찾아도 사람이 볼 곳이 없으면 0건이 될 리가 없다.
 *
 * ## JPA 엔티티를 두지 않는다
 *
 * `verification_task` 는 배치가 SQL 로 upsert 하는 테이블이다 ([kr.kcocktail.admin.verification.VerificationTaskStore]).
 * 같은 테이블에 엔티티를 겹쳐 두면 영속성 컨텍스트가 배치의 변경을 모르는 상태가 생긴다 —
 * 읽는 쪽도 같은 층에서 읽는 편이 낫다.
 */
@Service
class VerificationTaskService(
    private val jdbc: JdbcTemplate,
    private val json: ObjectMapper,
) {

    /**
     * 큐 조회.
     *
     * **기본이 `open` 이다** (RED 5). 큐를 여는 이유가 "지금 처리할 것"을 보기 위해서인데
     * 닫힌 것까지 섞여 나오면 첫 화면부터 걸러 내야 한다.
     *
     * 정렬은 **최근 탐지순** 고정이다 (RED 10). `(status, detected_at DESC)` 인덱스가
     * 정확히 이 순서라 (V016), 다른 컬럼을 열어 주면 인덱스 없는 정렬이 열린다.
     */
    @Transactional(readOnly = true)
    fun list(
        status: String?,
        taskType: String?,
        entityType: String?,
        page: PageQuery,
    ): PageResponse<VerificationTaskItem> {
        val where = mutableListOf<String>()
        val args = mutableListOf<Any>()

        where += "status = ?"
        args += validStatus(status ?: OPEN)

        taskType?.let {
            // 모르는 슬러그는 400 이다. 빈 결과로 돌려주면 오타를 "그런 태스크가 없다"로 읽는다.
            // 1b 값(hours_expired · instagram_signal)은 **에러가 아니라 빈 결과**다 (RED 25).
            where += "task_type = ?"
            args += (TaskType.findBySlug(it) ?: throw BadRequestException(unknownTaskType(it))).slug
        }
        entityType?.let { where += "entity_type = ?"; args += it }

        val clause = where.joinToString(" AND ")
        val total = jdbc.queryForObject(
            "SELECT count(*) FROM verification_task WHERE $clause",
            Long::class.java,
            *args.toTypedArray(),
        )!!

        val rows = jdbc.queryForList(
            """
            SELECT id, task_type, entity_type, entity_id, code, detail, status,
                   detected_at, resolved_at, resolved_by, resolution
              FROM verification_task
             WHERE $clause
             ORDER BY detected_at DESC, id DESC
             LIMIT ? OFFSET ?
            """.trimIndent(),
            *(args + page.size + page.offset).toTypedArray(),
        )

        return PageResponse.of(rows.map { it.toItem() }, page, total)
    }

    @Transactional(readOnly = true)
    fun find(id: Long): VerificationTaskItem = row(id).toItem()

    /**
     * 해소 (SPEC-07 §2.7).
     *
     * **감사에 남기지 않는다** (RED 20 · DECISIONS §1.3). `PRIN-T08` 의 4종에 없고,
     * 태스크 테이블 자체가 이력이다 — `resolved_at` · `resolved_by` · `resolution` 이
     * 감사 로그가 담을 것과 같은 내용이다. 두 벌로 남기면 둘이 어긋날 때 어느 쪽이
     * 맞는지 알 수 없다.
     *
     * @throws ConflictException 이미 닫힌 태스크 (RED 19) — 멱등하게 넘기면
     *   "누가 언제 닫았나"가 나중 사람의 것으로 덮인다
     */
    @Transactional
    fun resolve(id: Long, request: ResolveTaskRequest, actorId: Long): VerificationTaskItem {
        val current = row(id)

        if (current["status"] != OPEN) {
            throw ConflictException("이미 처리된 태스크입니다 (status=${current["status"]})")
        }

        // RED 18 — 무시에는 사유가 필수다. 사유 없는 무시는 무시가 아니라 삭제다.
        val reason = request.reason?.takeUnless { it.isBlank() }
        if (request.dismiss && reason == null) {
            throw BadRequestException("무시하려면 사유가 필요합니다 (reason)")
        }

        jdbc.update(
            """
            UPDATE verification_task
               SET status = ?, resolved_at = now(), resolved_by = ?, resolution = ?
             WHERE id = ?
            """.trimIndent(),
            if (request.dismiss) DISMISSED else RESOLVED,
            actorId,
            reason,
            id,
        )

        return find(id)
    }

    private fun row(id: Long): Map<String, Any?> = jdbc.queryForList(
        """
        SELECT id, task_type, entity_type, entity_id, code, detail, status,
               detected_at, resolved_at, resolved_by, resolution
          FROM verification_task WHERE id = ?
        """.trimIndent(),
        id,
    ).firstOrNull() ?: throw ResourceNotFoundException()

    private fun validStatus(value: String): String =
        value.takeIf { it in STATUSES }
            ?: throw BadRequestException("알 수 없는 status 입니다: $value (가능: ${STATUSES.joinToString(", ")})")

    private fun unknownTaskType(slug: String) =
        "알 수 없는 taskType 입니다: $slug (가능: ${TaskType.entries.joinToString(", ") { it.slug }})"

    private fun Map<String, Any?>.toItem(): VerificationTaskItem {
        val entityType = this["entity_type"] as String
        val entityId = (this["entity_id"] as Number).toLong()

        return VerificationTaskItem(
            id = (this["id"] as Number).toLong(),
            taskType = this["task_type"] as String,
            entityType = entityType,
            entityId = entityId,
            code = this["code"] as String,
            // JDBC 는 `jsonb` 를 `PGobject` 로 준다. 타입으로 캐스팅하지 않는 이유는
            // 드라이버가 `runtimeOnly` 라 컴파일 시점에 그 클래스가 없어서다 —
            // 컴파일 의존을 늘리느니 `toString()` 이 낫다 (PGobject 는 값을 그대로 돌려준다).
            detail = this["detail"]?.toString()?.takeIf { it.isNotBlank() }?.let { json.readTree(it) },
            adminPath = adminPath(entityType, entityId),
            status = this["status"] as String,
            detectedAt = (this["detected_at"] as Timestamp).toInstant(),
            resolvedAt = (this["resolved_at"] as? Timestamp)?.toInstant(),
            resolvedBy = (this["resolved_by"] as? Number)?.toLong(),
            resolution = this["resolution"] as? String,
        )
    }

    companion object {
        private const val OPEN = "open"
        private const val RESOLVED = "resolved"
        private const val DISMISSED = "dismissed"
        private val STATUSES = listOf(OPEN, RESOLVED, DISMISSED)

        /**
         * `entityType` → 어드민 경로 (RED 15).
         *
         * **서버가 만든다.** 프론트가 이 표를 따로 들면 엔티티 종류가 늘 때
         * 서버는 태스크를 만들고 프론트는 링크를 못 만드는 상태가 생긴다 —
         * 큐에는 보이는데 갈 수가 없다.
         *
         * 모르는 종류는 `null` 이다. 짐작해서 죽은 링크를 주는 것보다,
         * 링크가 없다는 사실이 드러나는 편이 낫다 (Phase 1b 의 `bar` 가 그 자리다).
         */
        private val ADMIN_PATHS = mapOf(
            "cocktail" to "${ApiPaths.ADMIN}/cocktails",
            "ingredient" to "${ApiPaths.ADMIN}/ingredients",
        )

        fun adminPath(entityType: String, entityId: Long): String? =
            ADMIN_PATHS[entityType]?.let { "$it/$entityId" }
    }
}
