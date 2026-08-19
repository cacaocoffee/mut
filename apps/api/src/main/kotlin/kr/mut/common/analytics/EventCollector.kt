package kr.mut.common.analytics

import com.fasterxml.jackson.databind.ObjectMapper
import kr.mut.common.account.ClosureHook
import kr.mut.common.logging.SensitiveParams
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.util.UUID

/**
 * 이벤트 수집 (ISSUE-034 · SPEC-10 §7 · `NFR-R-04`).
 *
 * ## 하나가 잘못됐다고 나머지를 버리지 않는다
 *
 * 이 클래스의 요체다. SPEC-10 §7:
 *
 * > **검증 실패한 이벤트는 버리고 서버 로그에만 남긴다.** 사용자 흐름을 막지 않는다.
 *
 * 배치 전체를 롤백하면 **데이터가 더 나빠진다** — 상세 화면 한 번에 이벤트가 대여섯 개
 * 나가는데, 그중 하나의 payload 가 이상하다고 나머지를 잃으면 지표에 구멍이 뚫린다.
 * 그 구멍은 소급해 메울 수 없다 (SPEC-10 §1).
 *
 * ## 저장이 실패해도 202 다
 *
 * `NFR-R-04` — "이벤트 수집 실패가 사용자 흐름을 막지 않는다" 는 **배포 차단** 조건이다.
 * DB 가 죽었을 때 계측 때문에 사용자가 에러 화면을 보는 것이 최악이다.
 * 삼키고 로그만 남긴다 ([collect] 가 예외를 밖으로 내지 않는다).
 *
 * ## JPA 엔티티를 두지 않는다
 *
 * `analytics_event` 는 PK 가 `(id, occurred_at)` 복합이다 — 월 파티셔닝 대비다 (SPEC-06 §3.8).
 * 엔티티로 만들면 `@IdClass` 가 붙고, 정작 이 테이블은 **쓰고 다시 안 읽는다.**
 * 영속성 컨텍스트에 실어 둘 이유가 없다 (`JdbcAuditRecorder` 와 같은 판단).
 */
@Service
class EventCollector(
    private val jdbc: JdbcTemplate,
    private val json: ObjectMapper,
) : ClosureHook {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * @return 저장된 건수. 응답에 쓰지 않는다 — 202 는 본문이 없다.
     *   테스트와 로그가 본다.
     */
    fun collect(batch: EventBatch): Int {
        val (valid, invalid) = batch.events
            .map { it to validate(it) }
            .partition { (_, result) -> result is Validated.Ok }

        invalid.forEach { (event, result) ->
            // RED 16 — 서버 로그에만 남긴다. 무엇이 왜 버려졌는지 알아야 클라이언트를 고친다.
            // 이벤트 원문을 통째로 찍지 않는다: payload 에 검색어가 들어 있다 (SPEC-10 §4.3).
            log.warn(
                "이벤트를 버렸다 (type={}, 사유={})",
                event.eventType,
                (result as Validated.Dropped).reason,
            )
        }

        val rows = valid.map { (_, result) -> (result as Validated.Ok).row }
        if (rows.isEmpty()) return 0

        return try {
            save(rows)
            rows.size
        } catch (e: Exception) {
            // RED 17 — 저장 실패가 202 를 막지 않는다 (`NFR-R-04`).
            // 여기서 던지면 계측 때문에 사용자가 에러를 본다.
            log.error("이벤트 저장 실패 — {}건을 잃었다. 사용자 흐름은 막지 않는다", rows.size, e)
            0
        }
    }

    @Transactional
    fun save(rows: List<EventRow>) {
        jdbc.batchUpdate(
            """
            INSERT INTO analytics_event
                   (event_type, session_id, user_id, path, referrer_type, payload, occurred_at)
            VALUES (?, ?::uuid, ?, ?, ?, ?::jsonb, ?)
            """.trimIndent(),
            rows.map { row ->
                arrayOf(
                    row.eventType.code,
                    row.sessionId.toString(),
                    row.userId,
                    row.path,
                    row.referrerType.code,
                    json.writeValueAsString(row.payload),
                    Timestamp.from(row.occurredAt),
                )
            },
        )
    }

    /**
     * 탈퇴 시 **`user_id` 만 지운다. 행은 남긴다** (SPEC-08 §5.3 · SPEC-10 §8).
     *
     * 행까지 지우면 **집계 지표가 소급해 바뀐다** — 지난달 조회수가 오늘 줄어드는 셈이고,
     * 그러면 그 숫자로 아무것도 결정할 수 없다. 개인을 식별할 수 없게 만드는 것과
     * 일어난 일을 없던 것으로 만드는 것은 다르다.
     */
    @Transactional
    override fun onAccountClosing(userId: Long) {
        val anonymized = jdbc.update(
            "UPDATE analytics_event SET user_id = NULL WHERE user_id = ?",
            userId,
        )
        if (anonymized > 0) log.info("이벤트 익명화 {}건 (user={})", anonymized, userId)
    }

    // ── 검증 ─────────────────────────────────────────────────────────────

    private fun validate(event: EventRequest): Validated {
        val type = EventType.find(event.eventType.orEmpty())
            ?: return Validated.Dropped("알 수 없는 eventType") // RED 18

        val sessionId = event.sessionId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: return Validated.Dropped("sessionId 가 UUID 가 아니다") // RED 20

        val occurredAt = event.occurredAt
            ?: return Validated.Dropped("occurredAt 이 없다") // RED 22

        // RED 28 — 좌표가 섞여 오면 **이벤트를 통째로 버린다.**
        //
        // 키만 떨어뜨려도 저장은 막을 수 있지만, 그러면 클라이언트 버그가 조용히 산다.
        // 좌표를 보내는 클라이언트는 다른 곳에서도 보내고 있을 가능성이 높다 (`PRIN-D04`).
        val offending = event.payload?.keys?.filter { SensitiveParams.isMasked(it) }.orEmpty()
        if (offending.isNotEmpty()) {
            return Validated.Dropped("payload 에 좌표가 있다: ${offending.joinToString()}")
        }

        return Validated.Ok(
            EventRow(
                eventType = type,
                sessionId = sessionId,
                // RED 21 — 비로그인은 null 이다.
                userId = event.userId,
                // RED 23 — 쿼리스트링을 **서버가 잘라 낸다.** 거부하지 않는 이유:
                // 클라이언트가 실수로 붙였다고 이벤트를 잃을 이유가 없고,
                // 자르는 것으로 요구(SPEC-10 §3)가 충족된다.
                path = event.path?.substringBefore('?')?.take(MAX_PATH),
                // RED 24 — 5종 밖은 `unknown` 이다. 분류 실패도 정보다.
                referrerType = ReferrerType.find(event.referrerType) ?: ReferrerType.UNKNOWN,
                // RED 29 — **알려진 키만 남긴다.** 임의 필드를 받으면 개인정보가 샌다.
                payload = event.payload.orEmpty().filterKeys { it in type.allowedPayloadKeys },
                occurredAt = occurredAt,
            ),
        )
    }

    private sealed interface Validated {
        data class Ok(val row: EventRow) : Validated
        data class Dropped(val reason: String) : Validated
    }

    companion object {
        /** SPEC-10 §7 — 요청당 50건. 넘으면 400 이다 (RED 6·7). */
        const val MAX_BATCH = 50

        /** `analytics_event.path VARCHAR(255)`. */
        private const val MAX_PATH = 255
    }
}

/** 저장할 모양. 검증을 통과한 것만 이 타입이 된다 — **잘못된 값이 여기까지 못 온다.** */
data class EventRow(
    val eventType: EventType,
    val sessionId: UUID,
    val userId: Long?,
    val path: String?,
    val referrerType: ReferrerType,
    val payload: Map<String, Any?>,
    val occurredAt: java.time.Instant,
)
