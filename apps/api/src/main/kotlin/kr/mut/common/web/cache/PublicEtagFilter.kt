package kr.mut.common.web.cache

import jakarta.servlet.http.HttpServletRequest
import kr.mut.common.web.ApiPaths
import org.springframework.web.filter.ShallowEtagHeaderFilter

/**
 * 공개 조회에만 `ETag` 를 붙인다 (SPEC-07 §1.6).
 *
 * **SSG 빌드가 같은 엔드포인트를 반복 호출하므로 실효가 크다** — 칵테일 500종이면
 * 빌드마다 500번이고, 내용이 안 바뀌었으면 304 로 끝난다.
 *
 * 어드민은 제외한다. 발행 전 데이터에 검증자(ETag)를 붙이면 중간 캐시가 그것을 근거로
 * 재사용할 여지가 생긴다.
 *
 * `/me` · `/auth` 도 같다 — 개인 응답에 검증자를 달면 그것을 근거로 재사용된다
 * (이슈 031 에서 드러났다). [ApiPaths.isPubliclyCacheable] 참조.
 */
class PublicEtagFilter : ShallowEtagHeaderFilter() {
    override fun shouldNotFilter(request: HttpServletRequest) =
        !ApiPaths.isPubliclyCacheable(request.requestURI)
}
