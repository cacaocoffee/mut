package kr.kcocktail.common.security.csrf

import kr.kcocktail.common.security.ratelimit.RateLimitFilter
import kr.kcocktail.common.security.ratelimit.RateLimitProperties
import kr.kcocktail.common.security.ratelimit.RateLimiter
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository
import org.springframework.security.web.util.matcher.AntPathRequestMatcher

/**
 * SPEC-08 §4.3 CSRF · §6 레이트 리밋 (ISSUE-007).
 *
 * ## 쿠키가 아니라 세션에 바인딩한다
 *
 * Spring Security 기본은 `CookieCsrfTokenRepository` 인데, 스펙이 "세션에 바인딩"이라고 했다.
 * 쿠키 방식은 토큰이 브라우저에 있어 XSS 로 읽히면 그대로 뚫린다 —
 * 세션 방식은 서버가 쥐고 있어서 토큰을 아는 것만으로는 부족하다.
 *
 * ## 인증 자체는 아직 없다
 *
 * OAuth 는 이슈 030(#32)이다. 여기서는 CSRF · 레이트 리밋만 걸고
 * 인가 규칙은 각 도메인 이슈가 `PermissionMatrix`(ISSUE-006)로 판정한다.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties::class)
class SecurityConfig {

    @Bean
    fun apiSecurityChain(http: HttpSecurity): SecurityFilterChain = http
        .csrf { csrf ->
            // 세션 바인딩 (SPEC-08 §4.3). 헤더 이름도 스펙이 정했다.
            csrf.csrfTokenRepository(
                HttpSessionCsrfTokenRepository().apply { setHeaderName(CSRF_HEADER) },
            )
            // 유일한 면제. 목록은 코드 상수다 — CsrfExemptions 참조.
            csrf.ignoringRequestMatchers(
                *CsrfExemptions.PATHS.map { AntPathRequestMatcher("$it/**") }.toTypedArray(),
                *CsrfExemptions.PATHS.map { AntPathRequestMatcher(it) }.toTypedArray(),
            )
        }
        // 인가는 각 엔드포인트가 PermissionMatrix 로 한다 (SPEC-07 §1.3 표기 🔒).
        // 여기서 경로별 규칙을 또 쓰면 판정이 두 곳이 되고 반드시 어긋난다.
        .authorizeHttpRequests { it.anyRequest().permitAll() }
        .build()

    @Bean
    fun rateLimitFilter(limiter: RateLimiter) =
        FilterRegistrationBean(RateLimitFilter(limiter)).apply {
            // 시큐리티보다 앞이다. 무차별 시도를 인증 처리 전에 끊는 것이 요점이다.
            order = Ordered.HIGHEST_PRECEDENCE + 10
        }

    companion object {
        /** SPEC-07 §1.2 · SPEC-08 §4.3. */
        const val CSRF_HEADER = "X-CSRF-Token"
    }
}
