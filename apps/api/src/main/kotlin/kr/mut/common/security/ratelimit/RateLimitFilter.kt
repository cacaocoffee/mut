package kr.mut.common.security.ratelimit

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kr.mut.common.security.session.AbsoluteExpiryFilter
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.filter.OncePerRequestFilter

/**
 * SPEC-08 §6 — 초과 시 `429` + `Retry-After`.
 *
 * ## 키를 만들 뿐 남기지 않는다
 *
 * IP 는 **메모리 맵의 키로만** 쓴다. 저장하지도 로그에 남기지도 않는다
 * (DECISIONS §1 · `PRIN-D04`). 429 로그에도 정책 이름만 적는다.
 */
class RateLimitFilter(private val limiter: RateLimiter) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val policy = RateLimitRoutes.policyFor(request)
        val key = policy?.let { keyOf(request, it) }

        if (policy != null && key != null) {
            val result = limiter.check(policy, key)
            if (result is RateLimitResult.Exceeded) {
                tooManyRequests(response, request, result)
                return
            }
        }

        chain.doFilter(request, response)
    }

    /**
     * 기준별 키.
     *
     * 세션·사용자 기준인데 그것이 없으면 **세지 않는다** (`null`) — IP 로 대체하지 않는다.
     * 대체하면 `/events` 가 공유 IP 뒤에서 서로를 막고, 그게 바로 스펙이 세션 기준을 고른 이유다.
     */
    private fun keyOf(request: HttpServletRequest, policy: RateLimitPolicy): String? =
        when (policy.keyBy) {
            KeyBy.IP -> clientIp(request)
            KeyBy.SESSION -> request.getSession(false)?.id
            KeyBy.USER -> request.getSession(false)
                ?.getAttribute(AbsoluteExpiryFilter.USER_ID)?.toString()
        }

    /**
     * 프록시 뒤를 고려한다. `X-Forwarded-For` 의 **첫 값**이 원 클라이언트다.
     *
     * 클라이언트가 위조할 수 있는 헤더라 신뢰 경계 밖에서는 의미가 없다 —
     * 리버스 프록시가 이 헤더를 덮어쓰도록 설정하는 것이 전제다 (호스팅 확정 후, G-07).
     */
    private fun clientIp(request: HttpServletRequest): String =
        request.getHeader("X-Forwarded-For")
            ?.substringBefore(',')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: request.remoteAddr

    private fun tooManyRequests(
        response: HttpServletResponse,
        request: HttpServletRequest,
        result: RateLimitResult.Exceeded,
    ) {
        response.status = HttpStatus.TOO_MANY_REQUESTS.value()
        response.setHeader(HttpHeaders.RETRY_AFTER, result.retryAfterSeconds.toString())
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        response.writer.write(
            """{"type":"/problems/rate-limit-exceeded","title":"요청이 너무 많습니다",""" +
                """"status":429,"detail":"잠시 후 다시 시도해 주세요","instance":"${request.requestURI}"}""",
        )
    }
}
