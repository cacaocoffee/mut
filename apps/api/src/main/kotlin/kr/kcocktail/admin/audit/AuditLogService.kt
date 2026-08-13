package kr.kcocktail.admin.audit

import com.fasterxml.jackson.databind.ObjectMapper
import kr.kcocktail.common.audit.AuditAction
import kr.kcocktail.common.web.error.BadRequestException
import kr.kcocktail.common.web.page.PageQuery
import kr.kcocktail.common.web.page.PageResponse
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant

/**
 * 감사 로그 조회 (ISSUE-029 · `FR-ADMIN-005` · `NFR-O-05`).
 *
 * ## 읽기만 한다
 *
 * 쓰는 것은 이슈 014 의 `JdbcAuditRecorder` 다. 여기에 수정·삭제가 없는 것은 누락이 아니라
 * **`PRIN-T08` 의 구현**이다 — 앱 역할에 `UPDATE` · `DELETE` 권한 자체가 없다 (`V014`).
 * 고칠 수 있는 이력은 이력이 아니다.
 *
 * ## 인덱스를 늘리지 않는다
 *
 * SPEC-06 §5 가 준 것은 `(entity_type, entity_id, created_at)` 하나다.
 * `actorUserId` · `action` 단독 필터는 그것을 못 탄다 — 알고도 두는 이유는
 * Phase 1a 규모(발행 500건 수준)에서 풀스캔이 견디고, **스펙에 없는 인덱스를 임의로 늘리면
 * 다음 사람이 어느 것이 근거 있는 인덱스인지 알 수 없어서**다.
 * 느려지면 `GAPS.md` 에 올리고 추가한다.
 */
@Service
class AuditLogService(
    private val jdbc: JdbcTemplate,
    private val json: ObjectMapper,
) {

    /**
     * 필터는 **AND 로 묶인다** (RED 11). OR 를 섞으면 "이 칵테일의 발행 이력"을
     * 물었는데 남의 행이 끼어든다 — 재구성(`NFR-O-05`)이 그 순간 무너진다.
     *
     * 정렬은 **최신순 고정**이다 (RED 12). 조사는 항상 "가장 최근에 무슨 일이 있었나"에서
     * 시작하고, `(entity_type, entity_id, created_at)` 인덱스도 그 방향이다.
     */
    @Transactional(readOnly = true)
    fun list(filter: AuditLogFilter, page: PageQuery): PageResponse<AuditLogItem> {
        val where = mutableListOf("1 = 1")
        val args = mutableListOf<Any>()

        filter.entityType?.let { where += "a.entity_type = ?"; args += it }
        filter.entityId?.let { where += "a.entity_id = ?"; args += it }
        filter.action?.let {
            // 모르는 슬러그는 400 이다. 빈 결과로 돌려주면 오타를 "그런 행위가 없었다"로 읽고,
            // 감사에서 그 오독은 "문제 없음" 이라는 결론이 된다.
            where += "a.action = ?"
            args += (AuditAction.entries.firstOrNull { a -> a.slug == it } ?: throw unknownAction(it)).slug
        }
        filter.actorUserId?.let { where += "a.actor_user_id = ?"; args += it }
        filter.from?.let { where += "a.created_at >= ?"; args += Timestamp.from(it) }
        filter.to?.let { where += "a.created_at <= ?"; args += Timestamp.from(it) }

        val clause = where.joinToString(" AND ")
        val total = jdbc.queryForObject(
            "SELECT count(*) FROM audit_log a WHERE $clause",
            Long::class.java,
            *args.toTypedArray(),
        )!!

        // LEFT JOIN 인 이유가 이 이슈의 요점 하나다 — 탈퇴하면 `user` 행이 사라지지만
        // `actor_user_id` 는 남는다 (SPEC-08 §5.3). INNER 로 묶으면 **그 행이 조회에서 사라진다.**
        // 이력이 남아 있는데 안 보이는 것은 없는 것보다 나쁘다.
        val rows = jdbc.queryForList(
            """
            SELECT a.id, a.entity_type, a.entity_id, a.action, a.actor_user_id,
                   a.before, a.after, a.created_at, u.display_name
              FROM audit_log a
              LEFT JOIN "user" u ON u.id = a.actor_user_id
             WHERE $clause
             ORDER BY a.created_at DESC, a.id DESC
             LIMIT ? OFFSET ?
            """.trimIndent(),
            *(args + page.size + page.offset).toTypedArray(),
        )

        return PageResponse.of(rows.map { it.toItem() }, page, total)
    }

    private fun unknownAction(slug: String) = BadRequestException(
        "알 수 없는 action 입니다: $slug (가능: ${AuditAction.entries.joinToString(", ") { it.slug }})",
    )

    private fun Map<String, Any?>.toItem(): AuditLogItem {
        val actorId = (this["actor_user_id"] as? Number)?.toLong()

        return AuditLogItem(
            id = (this["id"] as Number).toLong(),
            entityType = this["entity_type"] as String,
            entityId = (this["entity_id"] as Number).toLong(),
            action = this["action"] as String,
            // 사람이 없는 행위(배치·마이그레이션)는 `null` 이다. 거짓 주체를 지어내지 않는다.
            actor = actorId?.let { ActorRef(it, this["display_name"] as? String) },
            before = jsonOf(this["before"]),
            after = jsonOf(this["after"]),
            at = (this["created_at"] as Timestamp).toInstant(),
        )
    }

    // 드라이버가 `runtimeOnly` 라 `PGobject` 를 컴파일 시점에 못 본다. 값은 `toString()` 이 준다.
    private fun jsonOf(value: Any?) =
        value?.toString()?.takeIf { it.isNotBlank() }?.let { json.readTree(it) }
}

/**
 * 조회 조건 (RED 6~11).
 *
 * 파라미터를 한 덩이로 묶는다 — 여섯 개를 컨트롤러부터 SQL 까지 낱개로 나르면
 * 하나 추가할 때마다 세 곳을 고치게 된다.
 */
data class AuditLogFilter(
    val entityType: String? = null,
    val entityId: Long? = null,
    val action: String? = null,
    val actorUserId: Long? = null,
    val from: Instant? = null,
    val to: Instant? = null,
)
