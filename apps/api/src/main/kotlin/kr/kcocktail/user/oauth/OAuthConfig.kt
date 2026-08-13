package kr.kcocktail.user.oauth

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
@EnableConfigurationProperties(OAuthProperties::class)
class OAuthConfig {

    /**
     * 어댑터가 공유하는 HTTP 클라이언트.
     *
     * **타임아웃을 반드시 건다.** 제공자가 응답하지 않으면 요청 스레드가 그대로 붙잡히고,
     * 로그인 몰릴 때 그것 하나로 서버 전체가 멈춘다 — 기본값은 무한이다.
     *
     * 사용자가 기다리는 경로라 짧게 잡는다. 5초 뒤에도 안 오면 다시 누르는 편이 낫다.
     */
    @Bean
    fun oauthRestClient(): RestClient = RestClient.builder()
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(Duration.ofSeconds(3))
                setReadTimeout(Duration.ofSeconds(5))
            },
        )
        .build()
}
