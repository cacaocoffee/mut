package kr.kcocktail.common.web.error

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.NoHandlerFoundException
import java.net.URI

/**
 * 예외 → RFC 9457 Problem Details (SPEC-07 §1.4).
 *
 * **매핑을 한 곳에 모은다.** 이슈마다 컨트롤러에서 상태 코드를 고르면 같은 상황에 400 과 422 가
 * 섞여 나오고, 클라이언트가 분기할 수 없다.
 *
 * ## 400 과 422 의 구분
 *
 * | | 뜻 | 예 |
 * |---|---|---|
 * | `400` | 요청을 **해석할 수 없다** | JSON 이 깨졌다, 파라미터 타입이 다르다 |
 * | `422` | 해석은 됐고 **값이 도메인 규칙을 어겼다** | `tasting_note` 가 비었는데 발행 |
 *
 * `422` 에만 [Violation] 배열이 붙는다.
 */
@RestControllerAdvice
class ApiExceptionHandler(
    /**
     * `type` URI 의 베이스. **호스팅이 미정이라(G-07) 기본값을 상대 경로로 둔다** —
     * RFC 9457 이 상대 URI 를 허용한다. 도메인이 정해지면 절대 URI 로 바꾼다.
     */
    @Value("\${kcocktail.api.problem-base-uri:/problems}")
    private val problemBaseUri: String,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    // ── 422 — 도메인 규칙 위반 ─────────────────────────────────────────────

    /**
     * `FR-ADMIN-003` — 실패한 항목을 **전부** 담는다. 하나씩 고치게 하지 않는다.
     */
    @ExceptionHandler(DomainViolationException::class)
    fun onDomainViolation(e: DomainViolationException, req: HttpServletRequest) =
        problem(
            status = HttpStatus.UNPROCESSABLE_ENTITY,
            typeSlug = "domain-rule-violated",
            title = "요청을 처리할 수 없습니다",
            detail = e.message,
            req = req,
            violations = e.violations,
        )

    /** 빈 검증 실패도 `violations` 형태로 통일한다 — 클라이언트가 한 가지만 파싱하면 된다. */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun onBeanValidation(e: MethodArgumentNotValidException, req: HttpServletRequest) =
        problem(
            status = HttpStatus.UNPROCESSABLE_ENTITY,
            typeSlug = "validation-failed",
            title = "요청을 처리할 수 없습니다",
            detail = "입력값이 올바르지 않습니다",
            req = req,
            violations = e.bindingResult.fieldErrors.map {
                Violation("VALIDATION", it.field, it.defaultMessage ?: "올바르지 않은 값입니다")
            },
        )

    // ── 400 ────────────────────────────────────────────────────────────────

    @ExceptionHandler(
        BadRequestException::class,
        HttpMessageNotReadableException::class,
        MethodArgumentTypeMismatchException::class,
        MissingServletRequestParameterException::class,
    )
    fun onBadRequest(e: Exception, req: HttpServletRequest) = problem(
        status = HttpStatus.BAD_REQUEST,
        typeSlug = "malformed-request",
        title = "요청을 해석할 수 없습니다",
        // 파서 예외 메시지에는 클래스명·경로가 섞여 나온다. 우리가 쓴 것만 그대로 보낸다.
        detail = if (e is BadRequestException) e.message else "요청 형식이 올바르지 않습니다",
        req = req,
    )

    // ── 401 · 403 · 404 · 409 ──────────────────────────────────────────────

    @ExceptionHandler(UnauthenticatedException::class)
    fun onUnauthenticated(e: UnauthenticatedException, req: HttpServletRequest) = problem(
        status = HttpStatus.UNAUTHORIZED,
        typeSlug = "unauthenticated",
        title = "인증이 필요합니다",
        detail = e.message,
        req = req,
    )

    @ExceptionHandler(AccessDeniedException::class)
    fun onAccessDenied(req: HttpServletRequest) = problem(
        status = HttpStatus.FORBIDDEN,
        typeSlug = "access-denied",
        title = "권한이 없습니다",
        detail = "이 작업을 수행할 권한이 없습니다",
        req = req,
    )

    /**
     * **비공개 리소스도 여기로 온다.** `draft` 를 403 으로 돌려주면
     * "그 슬러그는 존재한다"는 사실이 새어 나간다 (SPEC-07 §1.4 · §5).
     */
    @ExceptionHandler(ResourceNotFoundException::class, NoHandlerFoundException::class)
    fun onNotFound(req: HttpServletRequest) = problem(
        status = HttpStatus.NOT_FOUND,
        typeSlug = "not-found",
        title = "찾을 수 없습니다",
        detail = "요청한 리소스가 없습니다",
        req = req,
    )

    @ExceptionHandler(ConflictException::class)
    fun onConflict(e: ConflictException, req: HttpServletRequest) = problem(
        status = HttpStatus.CONFLICT,
        typeSlug = "conflict",
        title = "현재 상태에서는 처리할 수 없습니다",
        detail = e.message,
        req = req,
    )

    // ── 429 ────────────────────────────────────────────────────────────────

    @ExceptionHandler(RateLimitExceededException::class)
    fun onRateLimit(e: RateLimitExceededException, req: HttpServletRequest) =
        problem(
            status = HttpStatus.TOO_MANY_REQUESTS,
            typeSlug = "rate-limit-exceeded",
            title = "요청이 너무 많습니다",
            detail = "잠시 후 다시 시도해 주세요",
            req = req,
            // ResponseEntity 의 헤더는 읽기 전용이다. 만든 뒤에 넣으려 하면 예외가 나고,
            // 그러면 Spring 은 **원래 예외를 다시 던져서** 매핑이 없는 것처럼 보인다.
            headers = mapOf(HttpHeaders.RETRY_AFTER to e.retryAfterSeconds.toString()),
        )

    // ── 500 ────────────────────────────────────────────────────────────────

    /**
     * 처리되지 않은 예외. **내부 정보를 응답에 담지 않는다** — 스택트레이스 · SQL · 클래스명.
     * 원인은 로그로만 남긴다.
     */
    @ExceptionHandler(Exception::class)
    fun onUnhandled(e: Exception, req: HttpServletRequest): ResponseEntity<ProblemDetail> {
        log.error("처리되지 않은 예외: {} {}", req.method, req.requestURI, e)
        return problem(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            typeSlug = "internal-error",
            title = "요청을 처리하지 못했습니다",
            detail = "잠시 후 다시 시도해 주세요",
            req = req,
        )
    }

    // ── 조립 ───────────────────────────────────────────────────────────────

    private fun problem(
        status: HttpStatus,
        typeSlug: String,
        title: String,
        detail: String?,
        req: HttpServletRequest,
        violations: List<Violation>? = null,
        headers: Map<String, String> = emptyMap(),
    ): ResponseEntity<ProblemDetail> {
        val body = ProblemDetail.forStatus(status).apply {
            this.type = URI.create("$problemBaseUri/$typeSlug")
            this.title = title
            this.detail = detail
            this.instance = URI.create(req.requestURI)
            // 성공 응답은 물론이고 422 가 아닌 에러에도 violations 를 붙이지 않는다.
            violations?.let { setProperty("violations", it) }
        }
        return ResponseEntity.status(status)
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .body(body)
    }
}
