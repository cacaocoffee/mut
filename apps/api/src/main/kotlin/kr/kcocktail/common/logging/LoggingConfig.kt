package kr.kcocktail.common.logging

import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered

@Configuration
class LoggingConfig {

    /**
     * 가장 바깥이다. 레이트 리밋에 막힌 요청도 접근 로그에는 남아야 조사가 된다 —
     * 안쪽에 두면 429 로 끊긴 요청이 로그에서 사라진다.
     */
    @Bean
    fun requestLoggingFilter() =
        FilterRegistrationBean(RequestLoggingFilter()).apply {
            order = Ordered.HIGHEST_PRECEDENCE
        }
}
