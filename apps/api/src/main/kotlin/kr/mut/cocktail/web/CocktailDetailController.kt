package kr.mut.cocktail.web

import kr.mut.cocktail.internal.CocktailDetailService
import kr.mut.common.web.ApiPaths
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * ISSUE-020 — 칵테일 상세 (SPEC-07 §2.1).
 *
 * ## SSG 빌드와 브라우저가 같은 엔드포인트를 쓴다
 *
 * 내부 전용 조회 API 를 따로 두지 않는다 (SPEC-07 §5) — **두 벌이 되면 반드시 어긋난다.**
 * 그래서 여기서 나가는 것이 곧 공개 노출 범위이고, 그 판정을 DTO 가 들고 있다
 * ([CocktailDetail]).
 *
 * ## 캐시 헤더를 여기서 붙이지 않는다
 *
 * `ETag` 와 `Cache-Control` 은 공개 조회 전체에 걸리는 규약이라
 * `PublicEtagFilter` · `CacheControlFilter` 가 이미 붙인다 (ISSUE-003 · SPEC-07 §1.6).
 * 컨트롤러가 또 붙이면 검증자가 두 곳에서 나오고, 그때 어느 쪽이 이기는지는
 * 필터 순서에 달리게 된다.
 *
 * ## 프로파일 조건을 걸지 않는다
 *
 * 한때 `@Profile("!rest-probe")` 가 붙어 있었다. ISSUE-003 의 `RestConventionProbes` 가
 * 규약을 HTTP 로 태워 보려고 **같은 경로**에 스텁을 두고 있어서, 그 프로파일이 켜진
 * 컨텍스트에서 매핑이 둘이 되어 앱이 아예 뜨지 않았기 때문이다.
 *
 * **프로덕션 컨트롤러가 테스트 프로파일을 알아야 하는 것이 거꾸로였다.** 이슈 018 이
 * 프로브를 `/probe/…` 로 옮기면서 충돌 자체가 사라졌다 — 프로브가 검증하는 것은
 * 페이징·캐시·에러 규약이지 칵테일이 아니라, 도메인 경로를 빌릴 이유가 없었다.
 */
@RestController
@RequestMapping("${ApiPaths.BASE}/cocktails")
class CocktailDetailController(private val service: CocktailDetailService) {

    /**
     * `draft` · `archived` 는 **404** 다 (SPEC-07 §5). 403 이면 존재가 새어 나간다.
     */
    @GetMapping("/{slug}")
    fun detail(@PathVariable slug: String): CocktailDetail = service.detail(slug)

    /** `FR-COCKTAIL-003` — 표준 1개 + 바 시그니처 n개. 기본 노출은 표준이다. */
    @GetMapping("/{slug}/recipes")
    fun recipes(@PathVariable slug: String): RecipeVersions = service.recipes(slug)
}
