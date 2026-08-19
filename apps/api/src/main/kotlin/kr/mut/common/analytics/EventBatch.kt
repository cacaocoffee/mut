package kr.mut.common.analytics

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

/**
 * 배치 수집 요청 (SPEC-10 §7).
 *
 * 페이지당 여러 이벤트를 모아 한 번에 보낸다. 하나씩 보내면 상세 화면 한 번에
 * 요청이 대여섯 개 나가고, 그것이 사용자 흐름과 경쟁한다 (`NFR-R-04`).
 */
data class EventBatch(
    @Schema(description = "요청당 최대 50건 (SPEC-10 §7). 넘으면 400 이다")
    val events: List<EventRequest> = emptyList(),
)

/**
 * 이벤트 하나.
 *
 * **모든 필드가 nullable 이다.** 검증을 Bean Validation 이 아니라 코드로 하기 때문인데,
 * 이유가 `NFR-R-04` 다 — `@Valid` 로 막으면 **한 건이 잘못됐을 때 배치 전체가 400** 이 된다.
 * 페이지의 다른 계측까지 잃는다.
 *
 * 잘못된 것은 [EventCollector] 가 버리고 나머지를 저장한다.
 */
data class EventRequest(
    val eventType: String? = null,

    @Schema(description = "클라이언트 생성 UUID. 30분 무활동 시 갱신된다")
    val sessionId: String? = null,

    @Schema(description = "비로그인은 null")
    val userId: Long? = null,

    val occurredAt: Instant? = null,

    @Schema(description = "쿼리스트링은 서버가 잘라 낸다 (SPEC-10 §3)")
    val path: String? = null,

    @Schema(description = "organic · internal · social · direct · unknown")
    val referrerType: String? = null,

    /**
     * 이벤트별 payload.
     *
     * **알려진 키만 저장된다** ([EventType.allowedPayloadKeys]). 임의 필드를 그대로 받으면
     * 좌표·개인정보가 샌다 (`PRIN-D04` · SPEC-10 §2).
     */
    val payload: Map<String, Any?>? = null,
)
