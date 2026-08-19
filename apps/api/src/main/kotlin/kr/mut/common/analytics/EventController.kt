package kr.mut.common.analytics

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import kr.mut.common.web.ApiPaths
import kr.mut.common.web.error.BadRequestException
import kr.mut.common.web.idempotency.IdempotencyFilter
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 이벤트 수집 (ISSUE-034 · SPEC-10 §7).
 *
 * | 항목 | |
 * |---|---|
 * | 인증 | **불필요** — 비로그인 흐름도 세야 한다 |
 * | CSRF | **면제** (`CsrfExemptions`) — 부작용이 집계뿐이다 |
 * | 레이트 리밋 | 120 req/min, **세션 기준** (`RateLimitPolicy.EVENTS`) |
 * | 배치 상한 | 요청당 50건 |
 * | 응답 | **`202 Accepted`, 본문 없음** |
 *
 * ## `202` 인 이유
 *
 * 클라이언트가 처리 결과를 기다릴 필요가 없다. **몇 건이 저장됐는지 알려 주지 않는다** —
 * 알려 주면 클라이언트가 그것을 보고 재시도하려 들고, 그 재시도가 집계를 부풀린다.
 *
 * `200` 이 아닌 것도 의도다: 이 요청은 "받았다" 이지 "처리했다" 가 아니다.
 *
 * ## CSRF 면제가 위험하지 않은 이유
 *
 * 남이 내 브라우저로 이벤트를 하나 더 쏴도 잃을 것이 없다. 방어는 레이트 리밋이 한다
 * (SPEC-08 §4.3 — `CsrfExemptions` 가 같은 말을 적어 뒀다).
 */
@RestController
@RequestMapping("${ApiPaths.BASE}/events")
class EventController(private val collector: EventCollector) {

    /**
     * `Idempotency-Key` 가 **필수**다 (RED 9·10).
     *
     * 중복 제거 자체는 [IdempotencyFilter] 가 한다 — 같은 키로 다시 오면 여기까지 안 온다.
     * 여기서는 **키가 없는 요청을 거부**한다: 필터는 헤더가 없으면 그냥 통과시키므로
     * (`shouldNotFilter`), 없어도 되는 것처럼 굳어진다.
     *
     * `PRIN-T07` 이 요구하는 것은 "재시도가 집계를 부풀리지 않는다" 이고,
     * 키 없는 재시도는 그것을 못 지킨다.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @ApiResponse(responseCode = "202", description = "받았다. 본문 없음. 처리 결과를 알려 주지 않는다")
    @Operation(
        summary = "이벤트 수집",
        description = "인증·CSRF 불필요. 배치 50건. 검증 실패한 이벤트는 버리고 나머지를 저장한다.",
    )
    fun collect(
        @RequestHeader(IdempotencyFilter.HEADER, required = false) idempotencyKey: String?,
        @RequestBody batch: EventBatch,
    ) {
        if (idempotencyKey.isNullOrBlank()) {
            throw BadRequestException("${IdempotencyFilter.HEADER} 헤더가 필요합니다")
        }

        // RED 7 — 절삭이 아니라 400 이다.
        //
        // 조용히 자르면 클라이언트는 다 보냈다고 믿고, 잘린 이벤트는 영영 안 온다.
        // 상한을 넘긴 것은 클라이언트 버그이므로 드러나야 한다 —
        // 개별 이벤트의 검증 실패(버린다)와 성격이 다르다: 저쪽은 데이터 문제고 이쪽은 계약 위반이다.
        if (batch.events.size > EventCollector.MAX_BATCH) {
            throw BadRequestException(
                "요청당 최대 ${EventCollector.MAX_BATCH}건입니다 (받은 것: ${batch.events.size})",
            )
        }

        collector.collect(batch)
    }
}
