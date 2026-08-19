package kr.mut.common.security.ratelimit

import jakarta.servlet.http.HttpServletRequest
import kr.mut.common.web.ApiPaths

/**
 * 요청 → 정책. SPEC-08 §6 표의 "대상" 열을 경로로 옮긴 것이다.
 *
 * **순서가 규칙이다.** 위에서부터 처음 맞는 것을 쓴다 — `/search` 가 `공개 조회` 보다 먼저 와야
 * 60rpm 이 적용된다. 뒤에 두면 300rpm 이 먼저 걸려 검색이 조여지지 않는다.
 */
object RateLimitRoutes {

    fun policyFor(request: HttpServletRequest): RateLimitPolicy? {
        val path = request.requestURI
        val method = request.method

        return when {
            path.startsWith("${ApiPaths.BASE}/search") -> RateLimitPolicy.SEARCH
            path.startsWith("${ApiPaths.BASE}/events") -> RateLimitPolicy.EVENTS
            isAuthCallback(path) -> RateLimitPolicy.AUTH_CALLBACK

            // 어드민은 **쓰기만** 조인다. 조회까지 60rpm 이면 목록 화면 한 번에 걸린다.
            path.startsWith("${ApiPaths.ADMIN}/") && method in MUTATING -> RateLimitPolicy.ADMIN_WRITE
            path.startsWith("${ApiPaths.ADMIN}/") -> null

            ApiPaths.isPublicApi(path) -> RateLimitPolicy.PUBLIC_READ
            else -> null
        }
    }

    /** `/api/v1/auth/{provider}/callback` — provider 는 3종이지만 경로 모양으로 판정한다. */
    private fun isAuthCallback(path: String) =
        path.startsWith("${ApiPaths.BASE}/auth/") && path.endsWith("/callback")

    private val MUTATING = setOf("POST", "PUT", "PATCH", "DELETE")
}
