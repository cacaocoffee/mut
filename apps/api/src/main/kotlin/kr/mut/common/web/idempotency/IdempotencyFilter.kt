package kr.mut.common.web.idempotency

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingResponseWrapper
import java.security.MessageDigest

/**
 * SPEC-07 §1.7 멱등성 (`PRIN-T07`).
 *
 * 재시도가 전제인 요청에 `Idempotency-Key` 헤더를 붙이면, 같은 키로 두 번 와도
 * **부수효과는 한 번만** 일어나고 두 번째는 첫 응답을 그대로 받는다.
 * 대상은 이벤트 수집(이슈 034) · 쿠폰 사용(P3) · 알림 발송이다.
 *
 * ## 선점은 DB 가 한다
 *
 * `idempotency_key.key` 의 `UNIQUE` 제약이 직렬화를 담당한다. 애플리케이션 잠금으로 하면
 * 인스턴스가 둘 이상일 때 무너진다 — 그리고 그 순간이 하필 트래픽이 몰릴 때다.
 *
 * ## 키 재사용은 거부한다
 *
 * 같은 키에 **다른 본문**이 오면 처리하지 않는다. 처리하면 공격자가 남의 키를 가로채
 * 응답을 대신 받거나, 클라이언트 버그로 서로 다른 요청이 한 결과를 공유한다.
 *
 * 거부는 `409` 다. `422` 는 우리 규약상 `violations` 에 `INV-`/`GATE-` 코드가 붙는 자리인데
 * 이것은 도메인 규칙 위반이 아니라 **프로토콜 수준의 상태 충돌**이다.
 */
class IdempotencyFilter(private val store: IdempotencyStore) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun shouldNotFilter(request: HttpServletRequest) =
        request.getHeader(HEADER).isNullOrBlank() || request.method !in MUTATING_METHODS

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val key = request.getHeader(HEADER).trim()
        if (key.length > MAX_KEY_LENGTH) {
            problem(response, HttpStatus.BAD_REQUEST, "$HEADER 는 $MAX_KEY_LENGTH 자 이하여야 합니다")
            return
        }

        // 본문을 지문에 넣어야 하므로 다시 읽을 수 있게 감싼다.
        val cachedRequest = CachedBodyRequest(request)
        val fingerprint = fingerprintOf(request.method, request.requestURI, cachedRequest.body)

        when (val existing = store.claim(key, fingerprint)) {
            null -> process(cachedRequest, response, chain, key)

            else -> when {
                existing.fingerprint != fingerprint ->
                    problem(response, HttpStatus.CONFLICT, "이미 다른 요청에 사용된 $HEADER 입니다")

                // 첫 요청이 아직 처리 중이다. 지금 통과시키면 부수효과가 두 번 난다.
                !existing.completed ->
                    problem(response, HttpStatus.CONFLICT, "같은 요청이 처리 중입니다. 잠시 후 재시도해 주세요")

                else -> replay(response, existing)
            }
        }
    }

    private fun process(
        request: CachedBodyRequest,
        response: HttpServletResponse,
        chain: FilterChain,
        key: String,
    ) {
        val cachedResponse = ContentCachingResponseWrapper(response)
        var succeeded = false
        try {
            chain.doFilter(request, cachedResponse)
            succeeded = true
        } finally {
            val bytes = cachedResponse.contentAsByteArray
            cachedResponse.copyBodyToResponse()

            if (succeeded && cachedResponse.status in 200..299) {
                store.complete(key, cachedResponse.status, bytes.toStringOrNull())
            } else {
                // 실패를 저장하면 그 키로는 영원히 같은 에러만 돌려받는다.
                // 재시도 안전을 위한 장치가 재시도를 막는 꼴이 된다.
                store.release(key)
                if (!succeeded) log.warn("멱등 요청 처리 실패, 키 해제: {}", key)
            }
        }
    }

    private fun replay(response: HttpServletResponse, record: IdempotencyRecord) {
        response.status = record.responseStatus ?: HttpStatus.OK.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        response.setHeader(REPLAY_HEADER, "true")
        record.responseBody?.let { response.writer.write(it) }
    }

    private fun problem(response: HttpServletResponse, status: HttpStatus, detail: String) {
        response.status = status.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        response.writer.write(
            """{"type":"/problems/idempotency-conflict","title":"${status.reasonPhrase}",""" +
                """"status":${status.value()},"detail":"$detail"}""",
        )
    }

    private fun fingerprintOf(method: String, path: String, body: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(("$method $path\n").toByteArray() + body)
            .joinToString("") { "%02x".format(it) }

    private fun ByteArray.toStringOrNull(): String? =
        if (isEmpty()) null else toString(Charsets.UTF_8)

    companion object {
        const val HEADER = "Idempotency-Key"

        /** 재요청이 재생된 것임을 클라이언트가 알 수 있게 한다. 디버깅에서 이게 없으면 헤맨다. */
        const val REPLAY_HEADER = "Idempotency-Replayed"

        const val MAX_KEY_LENGTH = 120 // idempotency_key.key VARCHAR(120)

        private val MUTATING_METHODS = setOf("POST", "PUT", "PATCH", "DELETE")
    }
}
