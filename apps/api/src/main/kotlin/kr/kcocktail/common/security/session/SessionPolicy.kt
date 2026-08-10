package kr.kcocktail.common.security.session

import kr.kcocktail.common.security.Role
import java.time.Duration
import java.time.Instant

/**
 * SPEC-08 §4.1 세션 수명.
 *
 * | 역할 | 수명 | 성격 |
 * |---|---|---|
 * | 일반 | 30일 | **rolling** — 활동하면 갱신 |
 * | `editor` · `admin` | 8시간 | **절대** — 활동해도 연장되지 않음 |
 *
 * ## "절대"가 요점이다
 *
 * Spring Session 의 `maxInactiveInterval` 은 **마지막 접근 이후** 시간이라 rolling 이다.
 * 8시간마다 한 번씩만 눌러도 세션이 영원히 산다 — 발행 권한을 가진 계정에는 그게 위험하다.
 * 그래서 발급 시각을 세션 속성에 넣고 [isExpired] 가 그걸 본다.
 *
 * 어드민 세션을 짧게 두는 이유는 **발행 권한이 곧 콘텐츠 신뢰**이기 때문이다.
 * 공용 PC 에 남은 세션으로 아무나 발행할 수 있으면 안 된다.
 */
object SessionPolicy {

    /** 세션 발급 시각. 절대 만료를 재려면 마지막 접근이 아니라 이것이 필요하다. */
    const val ISSUED_AT = "kcocktail.session.issuedAt"

    /** 발급 시점의 역할. 변경 감지에 쓴다 — 값 자체를 권한 판정에 쓰지 않는다. */
    const val ISSUED_ROLES = "kcocktail.session.issuedRoles"

    fun lifetime(roles: Set<Role>): Duration = Role.sessionLifetime(roles)

    fun isAbsolute(roles: Set<Role>): Boolean = Role.isAbsoluteExpiry(roles)

    /**
     * 절대 만료를 넘겼는가.
     *
     * rolling 세션에는 해당하지 않는다 — 그쪽은 Spring Session 의 비활동 만료로 충분하다.
     */
    fun isExpired(roles: Set<Role>, issuedAt: Instant, now: Instant): Boolean {
        if (!isAbsolute(roles)) return false
        return !now.isBefore(issuedAt.plus(Role.ABSOLUTE_8H))
    }
}
