package kr.kcocktail.common.web

import kr.kcocktail.common.web.cache.CacheControlFilter
import kr.kcocktail.common.web.cache.PublicEtagFilter
import kr.kcocktail.common.web.idempotency.IdempotencyFilter
import kr.kcocktail.common.web.idempotency.IdempotencyStore
import kr.kcocktail.common.web.page.PageQueryArgumentResolver
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * SPEC-07 §1 규약을 애플리케이션에 건다 (ISSUE-003).
 *
 * ## 필터 순서
 *
 * ```
 * PublicEtagFilter      ← 바깥. 본문을 버퍼링한다
 *   CacheControlFilter  ← 안쪽. 아직 커밋 전이라 헤더를 붙일 수 있다
 *     IdempotencyFilter ← 더 안쪽. 재생 응답도 ETag·캐시 판단을 거친다
 *       DispatcherServlet
 * ```
 *
 * `CacheControlFilter` 가 ETag 필터보다 **바깥**에 있으면 응답이 이미 쓰인 뒤라
 * 헤더가 붙지 않는다 — 그것도 예외 없이 조용히 실패한다. 순서가 계약이다.
 */
@Configuration
class WebConventionConfig : WebMvcConfigurer {

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(PageQueryArgumentResolver())
    }

    @Bean
    fun publicEtagFilter() = FilterRegistrationBean(PublicEtagFilter()).apply {
        order = ETAG_ORDER
    }

    @Bean
    fun cacheControlFilter() = FilterRegistrationBean(CacheControlFilter()).apply {
        order = CACHE_ORDER
    }

    @Bean
    fun idempotencyFilter(store: IdempotencyStore) =
        FilterRegistrationBean(IdempotencyFilter(store)).apply {
            order = IDEMPOTENCY_ORDER
        }

    private companion object {
        // Spring Security 는 -100 근처다. 그보다 뒤에 둬서 인증을 먼저 거치게 한다.
        const val ETAG_ORDER = Ordered.LOWEST_PRECEDENCE - 30
        const val CACHE_ORDER = Ordered.LOWEST_PRECEDENCE - 20
        const val IDEMPOTENCY_ORDER = Ordered.LOWEST_PRECEDENCE - 10
    }
}
