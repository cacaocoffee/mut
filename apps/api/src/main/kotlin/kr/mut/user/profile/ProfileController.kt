package kr.mut.user.profile

import io.swagger.v3.oas.annotations.Operation
import jakarta.servlet.http.HttpServletRequest
import kr.mut.common.security.session.AbsoluteExpiryFilter
import kr.mut.common.web.ApiPaths
import kr.mut.common.web.error.UnauthenticatedException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 내 프로필 (SPEC-07 §2.5).
 *
 * 화면 내비가 "로그인했는가" 를 이 하나로 판단한다 — 200 이면 로그인, 401 이면 아니다.
 * 북마크 목록(401 여부)으로도 알 수 있지만, 표시명을 함께 주려면 전용 경로가 낫다.
 *
 * `/me` 아래라 공유 캐시에 올라가지 않는다 (ApiPaths.isPubliclyCacheable 이 /me 를 뺀다) —
 * 한 사람의 로그인 상태가 다른 사람에게 캐시되면 안 된다.
 */
@RestController
@RequestMapping("${ApiPaths.BASE}/me")
class ProfileController(private val profiles: ProfileService) {

    /** 비로그인은 401 이다 — 북마크와 같은 규약 (SPEC-08 §2: 비로그인 `—`). */
    @GetMapping("/profile")
    @Operation(summary = "내 프로필", description = "로그인 상태·표시명·역할. 비로그인은 401.")
    fun me(http: HttpServletRequest): MyProfile {
        val userId = http.getSession(false)?.getAttribute(AbsoluteExpiryFilter.USER_ID) as? Long
            ?: throw UnauthenticatedException()
        return profiles.profileOf(userId) ?: throw UnauthenticatedException()
    }
}
