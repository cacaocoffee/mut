package kr.kcocktail.common.web.error

import org.springframework.http.HttpStatus

/**
 * 도메인 규칙 위반 → `422` + `violations` (SPEC-07 §1.4).
 *
 * **`violations` 는 실패한 항목을 전부 담는다.** `FR-ADMIN-003` 이 "하나씩 고치게 하지 않는다"고
 * 요구했다 — 첫 실패에서 멈추면 에디터가 저장·실패를 여섯 번 반복한다.
 * 실제 게이트 검사는 이슈 013 이 채우고, 여기서는 **전부 담을 수 있는 구조**를 만든다.
 */
open class DomainViolationException(
    val violations: List<Violation>,
    override val message: String = "도메인 규칙을 위반했습니다",
) : RuntimeException(message) {

    init {
        require(violations.isNotEmpty()) { "violations 가 비어 있으면 422 가 아니다" }
    }

    constructor(vararg violations: Violation) : this(violations.toList())
}

/** `404`. **비공개 리소스도 이것을 쓴다** — 존재 여부를 흘리지 않는다 (SPEC-07 §1.4). */
class ResourceNotFoundException(message: String = "찾을 수 없습니다") : RuntimeException(message)

/** `409` — 상태 충돌. 이미 발행됨, 멱등 키 재사용 등. */
class ConflictException(message: String) : RuntimeException(message)

/** `400` — 문법적으로 잘못된 요청. 값이 도메인 규칙을 어긴 것은 `422` 다. */
class BadRequestException(message: String) : RuntimeException(message)

/** `401` — 미인증. 실제 인증은 이슈 005 가 채운다. */
class UnauthenticatedException(message: String = "인증이 필요합니다") : RuntimeException(message)

/** `429` — 레이트 리밋. `Retry-After` 를 함께 내보낸다. */
class RateLimitExceededException(val retryAfterSeconds: Long) :
    RuntimeException("요청이 너무 많습니다")

/** 예외를 상태 코드로 옮기는 표. 흩어지면 이슈마다 다르게 쓴다. */
internal val STATUS_BY_EXCEPTION: List<Pair<Class<out Throwable>, HttpStatus>> = listOf(
    BadRequestException::class.java to HttpStatus.BAD_REQUEST,
    UnauthenticatedException::class.java to HttpStatus.UNAUTHORIZED,
    ResourceNotFoundException::class.java to HttpStatus.NOT_FOUND,
    ConflictException::class.java to HttpStatus.CONFLICT,
)
