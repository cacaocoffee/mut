package kr.mut.common.security.csrf

import jakarta.servlet.http.HttpServletRequest
import kr.mut.common.web.ApiPaths
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * `GET /api/v1/auth/csrf` — 토큰 발급 (SPEC-07 §1.2 · SPEC-08 §4.3).
 *
 * 클라이언트는 이 값을 `X-CSRF-Token` 헤더로 되돌려 보낸다.
 * 토큰은 **세션에 바인딩**돼 있어 다른 세션의 토큰으로는 통과하지 못한다.
 *
 * ## 왜 엔드포인트가 필요한가
 *
 * 쿠키 방식이면 브라우저가 알아서 들고 있지만, 세션 바인딩 방식은 서버가 쥐고 있어
 * 클라이언트가 **명시적으로 받아 가야** 한다. 첫 상태 변경 전에 한 번 부른다.
 *
 * `GET` 이라 CSRF 검사 대상이 아니고, 공개 조회 레이트 리밋(300rpm)이 적용된다.
 */
@RestController
@RequestMapping("${ApiPaths.BASE}/auth")
class CsrfTokenController {

    /**
     * 토큰을 **요청 속성에서** 읽는다.
     *
     * `CsrfToken` 을 메서드 파라미터로 받아도 동작하지만, 그러면 springdoc 이 그것을
     * 요청 스키마로 오해해 **Spring Security 내부 타입이 우리 계약에 실려 나간다** (`PRIN-T02`).
     * 계약에는 우리가 약속한 것만 있어야 한다.
     *
     * 속성을 읽는 순간 지연 생성된 토큰이 확정되고 세션에 심긴다.
     */
    @GetMapping("/csrf")
    fun issue(request: HttpServletRequest): CsrfTokenResponse {
        val token = request.getAttribute(CsrfToken::class.java.name) as CsrfToken
        return CsrfTokenResponse(headerName = token.headerName, token = token.token)
    }
}

/**
 * 파라미터 이름(`_csrf`)은 내보내지 않는다 — 폼 전송을 쓰지 않고,
 * 헤더 이름만 있으면 클라이언트가 할 일을 다 할 수 있다.
 */
data class CsrfTokenResponse(
    val headerName: String,
    val token: String,
)
