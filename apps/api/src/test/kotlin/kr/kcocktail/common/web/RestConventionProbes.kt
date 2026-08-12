package kr.kcocktail.common.web

import kr.kcocktail.common.web.error.BadRequestException
import kr.kcocktail.common.web.error.ConflictException
import kr.kcocktail.common.web.error.DomainViolationException
import kr.kcocktail.common.web.error.RateLimitExceededException
import kr.kcocktail.common.web.error.ResourceNotFoundException
import kr.kcocktail.common.web.error.UnauthenticatedException
import kr.kcocktail.common.web.error.Violation
import kr.kcocktail.common.web.error.ViolationCode
import kr.kcocktail.common.web.page.PageQuery
import kr.kcocktail.common.web.page.PageResponse
import kr.kcocktail.common.web.page.SortableBy
import org.springframework.context.annotation.Profile
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.atomic.AtomicInteger

/**
 * ISSUE-003 규약을 HTTP 로 실제로 태워 보기 위한 프로브다. 도메인 엔드포인트가 아니다.
 *
 * 규약이 필터 · `@RestControllerAdvice` · 아규먼트 리졸버에 흩어져 있어서 단위 테스트로는
 * 조립 결과를 못 본다 — **필터 순서가 틀리면 헤더가 조용히 안 붙는** 종류의 버그다.
 *
 * ## 프로파일로 가둔다
 *
 * 테스트 소스도 `kr.kcocktail` 아래라 컴포넌트 스캔에 걸린다. 그냥 두면
 * **모든 웹 통합 테스트에 이 매핑이 딸려 들어간다.** `@Profile` 로 이 테스트에서만 켜지게 한다.
 *
 * ## 경로가 `/probe/…` 인 이유 (이슈 018 에서 옮겼다)
 *
 * 원래는 `/cocktails` · `/cocktails/{slug}` 를 썼다. 진짜 엔드포인트가 없던 시절
 * 실감 나는 경로를 빌린 것인데, 이슈 018 이 `GET /api/v1/cocktails` 를 만드는 순간
 * **같은 프로파일 안에서 매핑이 충돌해 컨텍스트가 아예 안 떴다.** `@Profile` 은 다른 테스트를
 * 지켜 줄 뿐, 이 테스트 자신은 못 지킨다.
 *
 * 그래서 도메인 경로를 빌리지 않는다. 여기서 검증하는 것은 **페이징 · 캐시 · 에러 규약**이지
 * 칵테일이 아니다 — 경로가 무엇이든 성립해야 맞다. 이슈 020 의 `/cocktails/{slug}` 도
 * 같은 벽을 만났을 것이라 함께 비웠다.
 */
object RestProbes {
    const val PROFILE = "rest-probe"
}

@Profile(RestProbes.PROFILE)
@RestController
@RequestMapping(ApiPaths.BASE)
class PublicProbe {

    /** RED 30·31 — 공개 응답에 내부 `id` 가 없고 필드는 `camelCase` 다. */
    @GetMapping("/probe/resource/{slug}")
    fun detail(): Map<String, Any> = mapOf(
        "slug" to "gin-tonic",
        "displayName" to "진토닉",
        "abv" to 12,
    )

    @GetMapping("/probe/paged")
    fun list(@SortableBy("name", "abv") page: PageQuery): PageResponse<Map<String, Any>> =
        PageResponse.of(
            items = listOf(mapOf("slug" to "gin-tonic")),
            query = page,
            totalElements = 137,
        )

    @GetMapping("/errors/not-found")
    fun notFound(): Nothing = throw ResourceNotFoundException()

    /** SPEC-07 §5 — `draft` 는 403 이 아니라 404 다. 존재를 흘리지 않는다. */
    @GetMapping("/errors/draft")
    fun draft(): Nothing = throw ResourceNotFoundException()

    @GetMapping("/errors/bad-request")
    fun badRequest(): Nothing = throw BadRequestException("본문이 올바르지 않습니다")

    @GetMapping("/errors/unauthenticated")
    fun unauthenticated(): Nothing = throw UnauthenticatedException()

    @GetMapping("/errors/forbidden")
    fun forbidden(): Nothing = throw AccessDeniedException("no")

    @GetMapping("/errors/conflict")
    fun conflict(): Nothing = throw ConflictException("이미 발행되었습니다")

    @GetMapping("/errors/rate-limit")
    fun rateLimit(): Nothing = throw RateLimitExceededException(retryAfterSeconds = 30)

    /** RED 16 — 내부 정보가 새지 않아야 한다. 메시지에 일부러 단서를 심는다. */
    @GetMapping("/errors/boom")
    fun boom(): Nothing =
        throw IllegalStateException("org.postgresql: SELECT * FROM cocktail WHERE secret=1")

    /** RED 3·4·5 — 게이트 두 개가 동시에 실패하면 `violations` 가 2건이다. */
    @GetMapping("/errors/domain")
    fun domain(): Nothing = throw DomainViolationException(
        Violation.of(ViolationCode.GATE_COCKTAIL_01, "향과 맛 서술은 발행 필수입니다.", "tastingNote"),
        Violation.of(ViolationCode.GATE_COCKTAIL_05, "클래식으로 분류된 항목은 관련 이야기가 필요합니다.", "story"),
    )
}

@Profile(RestProbes.PROFILE)
@RestController
@RequestMapping(ApiPaths.ADMIN)
class AdminProbe {
    /** RED 25 — 어드민에는 캐시 헤더가 붙지 않는다. */
    @GetMapping("/cocktails")
    fun list(): Map<String, Any> = mapOf("items" to emptyList<Any>())
}

/** 멱등 프로브. 부수효과를 세어 "정말 한 번만 일어났는지"를 본다. */
@Profile(RestProbes.PROFILE)
@RestController
@RequestMapping("${ApiPaths.BASE}/events")
class EventProbe {
    val sideEffects = AtomicInteger(0)

    @PostMapping
    fun collect(@RequestBody body: Map<String, Any>): Map<String, Any> = mapOf(
        "accepted" to true,
        "sideEffectCount" to sideEffects.incrementAndGet(),
        "echo" to body,
    )
}
