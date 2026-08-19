package kr.mut.user.oauth

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import jakarta.servlet.http.HttpServletRequest
import kr.mut.common.security.session.AbsoluteExpiryFilter
import kr.mut.common.web.ApiPaths
import kr.mut.common.web.error.BadRequestException
import kr.mut.common.web.error.ResourceNotFoundException
import kr.mut.common.web.error.UnauthenticatedException
import kr.mut.user.domain.AuthProvider
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.net.URI

/**
 * 소셜 로그인 (ISSUE-030 · SPEC-07 §2.5 · SPEC-08 §4.2).
 *
 * ```
 * GET  /auth/{provider}/authorize   state 발급 · 제공자로 302
 * GET  /auth/{provider}/callback    state 검증 · 세션 발급 · 프론트로 302
 * POST /auth/logout                 세션 무효화
 * ```
 *
 * ## 성인 인증 엔드포인트가 없다
 *
 * ADR-0004 — **판매를 하지 않으므로 전면 인증을 요구하지 않는다.** 부재가 결정이라
 * `ExposureRuleAbsenceTest` 와 같은 방식으로 테스트가 부재를 지킨다 (RED 33).
 *
 * ## 오픈 리다이렉트를 두 겹으로 막는다 (RED 36)
 *
 * 하나, `redirect_uri` 는 **설정에서만** 온다 — 요청이 정하지 못한다. 제공자 콘솔에
 * 등록된 값과 완전 일치해야 하므로 제공자도 함께 막아 준다.
 *
 * 둘, 로그인 후 돌아갈 `returnTo` 는 화이트리스트 밖이면 **조용히 기본값으로** 간다.
 * 거부하지 않는 이유: 로그인은 이미 성공했는데 그것 때문에 에러 화면을 보여 줄 이유가 없다.
 */
@RestController
@RequestMapping("${ApiPaths.BASE}/auth")
class AuthController(
    providers: List<SocialAuthProvider>,
    private val states: OAuthStateStore,
    private val login: SocialLoginService,
    private val properties: OAuthProperties,
) {

    /** 제공자별 어댑터를 이름으로 찾는다. 컨트롤러는 벤더를 모른다 (`PRIN-T06` · RED 16). */
    private val byProvider: Map<AuthProvider, SocialAuthProvider> =
        providers.associateBy { it.provider }

    /**
     * 인가 시작. `state` 와 PKCE 검증자를 세션에 심고 제공자로 보낸다.
     *
     * `302` 로 답하는 이유: 브라우저가 그대로 따라가야 한다. URL 을 JSON 으로 주면
     * 프론트가 한 번 더 처리해야 하고, 그 사이에 값이 노출될 자리가 생긴다.
     */
    @GetMapping("/{provider}/authorize")
    @Operation(summary = "소셜 로그인 시작", description = "state 발급 후 제공자로 302. PKCE S256 고정.")
    fun authorize(
        @PathVariable provider: String,
        @Parameter(description = "로그인 후 돌아갈 프론트 주소. 화이트리스트 밖이면 무시된다")
        @RequestParam(required = false) returnTo: String?,
        request: HttpServletRequest,
    ): ResponseEntity<Void> {
        val adapter = adapterOf(provider)
        val state = Pkce.newState()
        val verifier = Pkce.newVerifier()

        states.issue(request, adapter.provider, state, verifier)
        request.getSession(true).setAttribute(RETURN_TO, properties.sanitizeReturn(returnTo))

        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(adapter.authorizeUrl(state, Pkce.challengeOf(verifier))))
            .build()
    }

    /**
     * 콜백. **state 를 먼저 검증한다** — 교환은 그다음이다.
     *
     * 순서가 중요하다. 코드를 먼저 교환하면 위조된 콜백 하나로 제공자 API 를 부르게 되고,
     * 레이트 리밋(10rpm, SPEC-08 §6)이 있어도 우리 몫의 호출이 남에게 소비된다.
     */
    @GetMapping("/{provider}/callback")
    @Operation(summary = "소셜 로그인 콜백", description = "state 검증 후 세션 쿠키 발급. state 는 1회용 10분.")
    fun callback(
        @PathVariable provider: String,
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) state: String?,
        @Parameter(description = "제공자가 보낸 거부 사유. 사용자가 동의를 취소한 경우 등")
        @RequestParam(required = false) error: String?,
        request: HttpServletRequest,
    ): ResponseEntity<Void> {
        val adapter = adapterOf(provider)

        if (error != null) throw BadRequestException("로그인이 취소되었습니다")
        if (code.isNullOrBlank() || state.isNullOrBlank()) {
            throw BadRequestException("code 와 state 가 필요합니다")
        }

        val verifier = states.consume(request, adapter.provider, state)
        val profile = adapter.exchange(code, verifier)

        // 되돌아갈 곳은 세션 id 를 갈아 끼우기 전에 읽는다.
        val returnTo = request.getSession(false)?.getAttribute(RETURN_TO) as? String
            ?: properties.defaultReturn

        login.login(request, adapter.provider, profile)

        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(returnTo)).build()
    }

    /**
     * 로그아웃 (RED 30).
     *
     * `POST` 다. `GET` 으로 두면 `<img src="/auth/logout">` 하나로 남을 로그아웃시킬 수 있다 —
     * 피해가 작아 보여도 CSRF 는 CSRF 다.
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "로그아웃", description = "세션을 무효화한다.")
    fun logout(request: HttpServletRequest) {
        val session = request.getSession(false) ?: throw UnauthenticatedException()
        session.getAttribute(AbsoluteExpiryFilter.USER_ID) ?: throw UnauthenticatedException()

        session.invalidate()
    }

    /**
     * 알 수 없는 제공자는 **404** 다 (RED 15).
     *
     * 400 이 아닌 이유: `/auth/google/authorize` 는 잘못된 파라미터가 아니라 **없는 경로**다.
     * 400 으로 답하면 "그 제공자는 형식이 틀렸다" 로 읽혀, 있는데 뭔가 잘못한 것처럼 보인다.
     */
    private fun adapterOf(code: String): SocialAuthProvider {
        val provider = AuthProvider.entries.firstOrNull { it.code == code }
            ?: throw ResourceNotFoundException()
        return byProvider[provider] ?: throw ResourceNotFoundException()
    }

    companion object {
        private const val RETURN_TO = "mut.oauth.returnTo"
    }
}
