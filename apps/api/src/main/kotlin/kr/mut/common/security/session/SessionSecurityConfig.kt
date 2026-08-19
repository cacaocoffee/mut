package kr.mut.common.security.session

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import java.time.Clock

/**
 * 세션 규약을 애플리케이션에 건다 (ISSUE-005, SPEC-08 §4.1).
 *
 * [AbsoluteExpiryFilter] 는 **Spring Security 보다 뒤**에 둔다. 앞에 두면 인증이 끝나기 전에
 * 세션을 검사하게 되고, 그때는 아직 세션에 사용자가 없다.
 */
@Configuration
class SessionSecurityConfig {

    /** 테스트가 시계를 갈아 끼운다 — 8시간을 실제로 기다리지 않기 위해서다. */
    @Bean
    @ConditionalOnMissingBean(Clock::class)
    fun systemClock(): Clock = Clock.systemUTC()

    @Bean
    fun absoluteExpiryFilter(lookup: SessionRoleLookup, clock: Clock) =
        FilterRegistrationBean(AbsoluteExpiryFilter(lookup, clock)).apply {
            order = Ordered.LOWEST_PRECEDENCE - 50 // 시큐리티(-100 근처) 뒤, 나머지 앞
        }
}
