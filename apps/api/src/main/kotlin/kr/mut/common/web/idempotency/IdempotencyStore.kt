package kr.mut.common.web.idempotency

import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * SPEC-07 §1.7 멱등 키 저장소 (`PRIN-T07`).
 *
 * JPA 엔티티를 두지 않는다. 이 표는 **HTTP 계층의 물건**이지 도메인 모델이 아니고,
 * 필터 안에서 짧은 트랜잭션으로만 쓰인다. 영속성 컨텍스트에 얹을 이유가 없다.
 */
@Component
class IdempotencyStore(private val jdbc: JdbcTemplate) {

    /**
     * 키를 선점한다. `UNIQUE` 제약이 직렬화를 담당한다 —
     * 애플리케이션 잠금으로 하면 인스턴스가 둘 이상일 때 무너진다.
     *
     * @return 선점 성공이면 `null`, 이미 있으면 그 기록
     */
    fun claim(key: String, fingerprint: String): IdempotencyRecord? = try {
        jdbc.update(
            "INSERT INTO idempotency_key (key, request_fingerprint) VALUES (?, ?)",
            key, fingerprint,
        )
        null
    } catch (_: DuplicateKeyException) {
        find(key) ?: error("선점에 실패했는데 기록이 없다: $key")
    }

    fun find(key: String): IdempotencyRecord? = jdbc.query(
        """
        SELECT key, request_fingerprint, response_status, response_body, completed_at IS NOT NULL
        FROM idempotency_key WHERE key = ?
        """.trimIndent(),
        { rs, _ ->
            IdempotencyRecord(
                key = rs.getString(1),
                fingerprint = rs.getString(2),
                responseStatus = rs.getObject(3) as Int?,
                responseBody = rs.getString(4),
                completed = rs.getBoolean(5),
            )
        },
        key,
    ).firstOrNull()

    fun complete(key: String, status: Int, body: String?) {
        jdbc.update(
            """
            UPDATE idempotency_key
            SET response_status = ?, response_body = ?, completed_at = now()
            WHERE key = ?
            """.trimIndent(),
            status, body, key,
        )
    }

    /**
     * 선점만 하고 처리에 실패했으면 기록을 지운다.
     *
     * 남겨 두면 그 키는 영원히 "진행 중"이라 클라이언트가 재시도할 수 없다 —
     * 재시도 안전을 위해 만든 장치가 재시도를 막는 꼴이 된다.
     */
    fun release(key: String) {
        jdbc.update("DELETE FROM idempotency_key WHERE key = ? AND completed_at IS NULL", key)
    }
}

data class IdempotencyRecord(
    val key: String,
    val fingerprint: String,
    val responseStatus: Int?,
    val responseBody: String?,
    val completed: Boolean,
)
