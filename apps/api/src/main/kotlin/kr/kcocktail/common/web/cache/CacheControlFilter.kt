package kr.kcocktail.common.web.cache

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kr.kcocktail.common.web.ApiPaths
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.web.filter.OncePerRequestFilter

/**
 * SPEC-07 §1.6 — 공개 조회에 `Cache-Control: public, max-age=60, stale-while-revalidate=600`.
 *
 * `stale-while-revalidate` 가 핵심이다. 60초가 지나도 캐시가 즉시 응답하고 뒤에서 갱신하므로
 * 발행 직후 몰리는 요청이 오리진에 그대로 꽂히지 않는다.
 *
 * ## 순서가 중요하다
 *
 * **[PublicEtagFilter] 보다 안쪽에 있어야 한다.** 헤더는 응답이 커밋되기 전에만 붙는다.
 * ETag 필터가 본문을 버퍼링하고 있는 동안이 그 시점이고, 바깥에 두면 이미 다 쓰인 뒤라
 * 아무 일도 일어나지 않는다 — 그것도 **조용히** 일어난다.
 *
 * 어드민에는 붙이지 않는다. 발행 전 데이터가 중간 캐시에 남으면 안 된다.
 *
 * ## `/me` · `/auth` 도 아니다 (이슈 031 에서 드러났다)
 *
 * `isPublicApi` 로 판정하다가 `/me/bookmarks` 에 `public, max-age=60` 이 붙어 나갔다 —
 * **중간 캐시가 한 사람의 북마크를 다른 사람에게 줄 수 있다는 뜻이다.**
 * "공개 API 인가" 와 "공유 캐시에 올려도 되는가" 는 다른 질문이라
 * [ApiPaths.isPubliclyCacheable] 로 나눴다.
 */
class CacheControlFilter : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest) =
        request.method != HttpMethod.GET.name() || !ApiPaths.isPubliclyCacheable(request.requestURI)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        chain.doFilter(request, response)

        // 에러 응답을 캐시시키지 않는다. 404 가 60초 붙어 있으면 발행 직후가 그대로 빈다.
        if (response.status !in 200..299) return
        if (response.containsHeader(HttpHeaders.CACHE_CONTROL)) return

        response.setHeader(HttpHeaders.CACHE_CONTROL, PUBLIC_CACHE)
    }

    companion object {
        const val MAX_AGE_SECONDS = 60L
        const val STALE_WHILE_REVALIDATE_SECONDS = 600L
        const val PUBLIC_CACHE =
            "public, max-age=$MAX_AGE_SECONDS, stale-while-revalidate=$STALE_WHILE_REVALIDATE_SECONDS"
    }
}
