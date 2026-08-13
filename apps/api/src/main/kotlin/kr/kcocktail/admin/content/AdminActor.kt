package kr.kcocktail.admin.content

import jakarta.servlet.http.HttpServletRequest
import kr.kcocktail.common.security.Role
import kr.kcocktail.common.security.authz.Action
import kr.kcocktail.common.security.authz.Decision
import kr.kcocktail.common.security.authz.PermissionMatrix
import kr.kcocktail.common.security.session.AbsoluteExpiryFilter
import kr.kcocktail.common.security.session.SessionRoleLookup
import kr.kcocktail.common.web.error.ResourceNotFoundException
import kr.kcocktail.common.web.error.UnauthenticatedException
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Component

/**
 * 어드민 요청의 주체와 권한 판정 (ISSUE-025 · SPEC-08 §2).
 *
 * ## 401 과 403 을 구분한다 (RED 6)
 *
 * - **비로그인** → `401`. "로그인하면 될 수도 있다" 는 사실이다
 * - **로그인했지만 역할이 없음** → `403`
 *
 * 둘을 합쳐 403 으로 내보내면 사용자가 로그인해야 하는지 권한을 요청해야 하는지 모른다.
 *
 * ## 거부 방식은 액션이 정한다
 *
 * `PermissionMatrix` 가 `HIDE` 로 답하면 **404** 다 — `draft` 조회를 403 으로 막으면
 * "그 슬러그는 존재한다" 가 새어 나간다 (`Action.VIEW_DRAFT`).
 */
@Component
class AdminActor(private val roleLookup: SessionRoleLookup) {

    /**
     * @throws UnauthenticatedException 세션이 없다 (401)
     * @throws org.springframework.security.access.AccessDeniedException 역할이 모자라다 (403)
     * @throws ResourceNotFoundException 숨겨야 하는 거부 (404)
     */
    fun require(request: HttpServletRequest, action: Action): Actor {
        val actor = current(request) ?: throw UnauthenticatedException()

        return when (PermissionMatrix.evaluate(actor.roles, action)) {
            is Decision.Allowed -> actor
            is Decision.Denied.Hidden -> throw ResourceNotFoundException()
            // 이슈 003 의 핸들러가 이 예외를 403 으로 옮긴다. 새 예외 타입을 만들지 않는다 —
            // 상태 코드 표가 두 곳에 생기면 이슈마다 다른 코드를 쓰게 된다
            is Decision.Denied.Forbidden ->
                throw AccessDeniedException("이 작업에는 editor 또는 admin 권한이 필요합니다")
        }
    }

    private fun current(request: HttpServletRequest): Actor? {
        val session = request.getSession(false) ?: return null
        val userId = session.getAttribute(AbsoluteExpiryFilter.USER_ID) as? Long ?: return null

        // 세션에 박힌 역할이 아니라 **지금 값**을 본다. 강등이 다음 요청부터 즉시
        // 반영돼야 한다 (SPEC-08 §4.1, 이슈 005 RED 22).
        return Actor(userId, roleLookup.rolesOf(userId))
    }

    data class Actor(val userId: Long, val roles: Set<Role>)
}
