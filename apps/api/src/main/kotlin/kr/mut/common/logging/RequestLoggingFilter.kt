package kr.mut.common.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 접근 로그 (ISSUE-033 · SPEC-08 §5.2 · `NFR-SEC-04`).
 *
 * ## `DEBUG` 로 남긴다
 *
 * 운영 기본이 `INFO` 라 평소에는 아무것도 안 나간다. 조사할 일이 생겨 `DEBUG` 를 켤 때가
 * 문제인데, **그때 켜는 사람은 좌표를 가려야 한다는 것을 기억하지 못한다.**
 * 그래서 마스킹을 로거가 아니라 여기에 박아 둔다 — 켜는 순간 이미 가려져 있다.
 *
 * ## IP 를 남기지 않는다
 *
 * SPEC-10 §2·§10 — "IP · User-Agent 원문 저장" 금지. 레이트 리밋(SPEC-08 §6)이 IP 기준이라
 * **런타임에는 안다**. 그것과 남기는 것은 다르다: 메모리 버킷 키로만 쓰고 여기 찍지 않는다
 * (`RateLimitPolicy.KeyBy` 의 주석이 같은 말을 한다).
 */
class RequestLoggingFilter : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun shouldNotFilter(request: HttpServletRequest) = !log.isDebugEnabled

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val startedAt = System.nanoTime()
        try {
            chain.doFilter(request, response)
        } finally {
            log.debug(
                "{} {} → {} ({}ms)",
                request.method,
                // 쿼리스트링을 그대로 넣지 않는다. 이 한 줄이 이 필터의 존재 이유다.
                SensitiveParams.maskUri(request.requestURI, request.queryString),
                response.status,
                (System.nanoTime() - startedAt) / 1_000_000,
            )
        }
    }
}
