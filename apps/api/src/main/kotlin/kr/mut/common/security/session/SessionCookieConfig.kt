package kr.mut.common.security.session

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.servlet.server.CookieSameSiteSupplier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.session.web.http.CookieSerializer
import org.springframework.session.web.http.DefaultCookieSerializer

/**
 * SPEC-08 §4.1 · SPEC-07 §1.2 — 세션 쿠키.
 *
 * | 속성 | 값 | 왜 |
 * |---|---|---|
 * | `httpOnly` | 항상 | JS 가 못 읽는다 → XSS 로 탈취되지 않는다 (`NFR-SEC-01`) |
 * | `Secure` | 환경별 | 로컬 `http://localhost` 에서는 꺼야 로그인이 된다 |
 * | `SameSite` | `Lax` | 크로스 사이트 POST 를 막되 일반 링크 이동은 살린다 |
 *
 * ## ⚠️ 호스팅 제약 (G-07)
 *
 * 쿠키를 공유하려면 **프론트와 API 가 같은 상위 도메인**이어야 한다
 * (`www.example.kr` / `api.example.kr`). SPEC-07 §1.2 가 인증 방식으로 호스팅에 제약을 걸었다 —
 * 호스팅을 정할 때 이 조건을 먼저 본다. 도메인을 환경변수로 뺀 이유다.
 */
@Configuration
class SessionCookieConfig(
    @Value("\${mut.session.cookie-name:KCSESSION}")
    private val cookieName: String,

    /** 운영은 반드시 `true`. 로컬만 `false` 다. */
    @Value("\${mut.session.secure:true}")
    private val secure: Boolean,

    /** 비우면 발급한 호스트에만 붙는다. 서브도메인 공유가 필요하면 `.example.kr` 형태로. */
    @Value("\${mut.session.cookie-domain:}")
    private val cookieDomain: String,
) {

    @Bean
    fun cookieSerializer(): CookieSerializer = DefaultCookieSerializer().apply {
        setCookieName(cookieName)
        setUseHttpOnlyCookie(true)      // 협상 대상이 아니다
        setUseSecureCookie(secure)
        setSameSite("Lax")
        setCookiePath("/")
        cookieDomain.takeIf { it.isNotBlank() }?.let { setDomainName(it) }
    }

    /** Spring Session 이 아닌 경로로 나가는 쿠키도 같은 정책을 따르게 한다. */
    @Bean
    fun sameSiteSupplier(): CookieSameSiteSupplier = CookieSameSiteSupplier.ofLax()
}
