package kr.kcocktail.common.security.authz

import kr.kcocktail.common.web.error.ResourceNotFoundException
import org.springframework.security.access.AccessDeniedException

/**
 * 권한 판정 결과. **거부의 종류를 타입으로 들고 다닌다.**
 *
 * `Boolean` 을 돌려주면 호출부마다 403 이냐 404 냐를 다시 판단하게 되고,
 * 그 판단은 이슈마다 갈린다 — 그러면 `draft` 가 어떤 엔드포인트에서는 403 으로 샌다.
 */
sealed interface Decision {

    data object Allowed : Decision

    sealed interface Denied : Decision {
        /** `403` — 리소스는 공개인데 액션 권한이 없다. */
        data object Forbidden : Denied

        /** `404` — 존재 자체가 비밀이다 (SPEC-07 §1.4 · SPEC-08 §3.2). */
        data object Hidden : Denied
    }

    val isAllowed: Boolean get() = this is Allowed

    /**
     * 거부면 던진다. `ApiExceptionHandler`(이슈 003)가 상태 코드로 옮긴다.
     *
     * 예외 타입을 여기서 고르는 이유는 호출부가 고르게 두면 반드시 어긋나기 때문이다.
     */
    fun orThrow(): Unit = when (this) {
        is Allowed -> Unit
        is Denied.Forbidden -> throw AccessDeniedException("권한이 없습니다")
        is Denied.Hidden -> throw ResourceNotFoundException()
    }
}
