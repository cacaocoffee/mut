package kr.mut.common.security.session

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kr.mut.common.security.Role
import org.slf4j.LoggerFactory
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Clock
import java.time.Instant

/**
 * `editor` · `admin` 세션의 **절대 만료**를 강제한다 (SPEC-08 §4.1).
 *
 * ## Spring Session 만으로는 안 된다
 *
 * `maxInactiveInterval` 은 **마지막 접근 이후** 시간이다 — rolling 이다.
 * 8시간에 한 번씩만 눌러도 세션이 영원히 산다. 발행 권한을 가진 계정에는 그게 위험하다.
 *
 * 그래서 발급 시각을 세션 속성에 넣고 여기서 검사한다. 넘었으면 **세션을 지운다** —
 * 만료를 응답으로만 알리면 다음 요청에 그 세션이 다시 살아 있다.
 *
 * ## 역할은 매 요청에 다시 읽는다
 *
 * SPEC-08 §3.3 — "세션에 캐시하지 않는다." 강등이 다음 요청부터 즉시 반영돼야 한다.
 * 세션에 넣어 둔 `issuedRoles` 는 **변경 감지용**이지 권한 판정용이 아니다.
 */
class AbsoluteExpiryFilter(
    private val roleLookup: SessionRoleLookup,
    private val clock: Clock = Clock.systemUTC(),
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val session = request.getSession(false)
        val userId = session?.getAttribute(USER_ID) as Long?

        if (session != null && userId != null) {
            val roles = roleLookup.rolesOf(userId)

            when {
                // 역할이 사라졌다 = 탈퇴했거나 전부 회수됐다. 세션도 같이 죽는다.
                roles.isEmpty() -> invalidate(session, userId, "역할 없음")

                // SPEC-08 §4.1 — 역할 변경 시 즉시 무효화.
                rolesChanged(session, roles) -> invalidate(session, userId, "역할 변경")

                expired(session, roles) -> invalidate(session, userId, "절대 만료")

                else -> session.maxInactiveInterval = SessionPolicy.lifetime(roles).seconds.toInt()
            }
        }

        chain.doFilter(request, response)
    }

    private fun expired(session: jakarta.servlet.http.HttpSession, roles: Set<Role>): Boolean {
        val issuedAt = session.getAttribute(SessionPolicy.ISSUED_AT) as Instant?
            // 발급 시각이 없는 승격 세션은 언제 만들어졌는지 알 수 없다. 신뢰하지 않는다.
            ?: return SessionPolicy.isAbsolute(roles)

        return SessionPolicy.isExpired(roles, issuedAt, clock.instant())
    }

    @Suppress("UNCHECKED_CAST")
    private fun rolesChanged(session: jakarta.servlet.http.HttpSession, current: Set<Role>): Boolean {
        val issued = session.getAttribute(SessionPolicy.ISSUED_ROLES) as Set<String>?
            ?: return false
        return issued != current.map(Role::code).toSet()
    }

    private fun invalidate(session: jakarta.servlet.http.HttpSession, userId: Long, why: String) {
        log.info("세션 무효화 (user={}, 사유={})", userId, why)
        session.invalidate()
    }

    companion object {
        const val USER_ID = "mut.session.userId"
    }
}

/**
 * 세션 필터가 역할을 읽는 경로.
 *
 * `user` 모듈의 리포지토리를 직접 잡지 않는다 (`PRIN-T03`) — `common` 이 도메인을 되참조하면
 * 의존이 양방향이 되고, 모듈을 떼어낼 때 공용 커널이 따라 쪼개진다. 경계 테스트가 막는다.
 */
fun interface SessionRoleLookup {
    fun rolesOf(userId: Long): Set<Role>
}
