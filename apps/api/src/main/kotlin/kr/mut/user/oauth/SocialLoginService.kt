package kr.mut.user.oauth

import jakarta.servlet.http.HttpServletRequest
import kr.mut.common.security.Role
import kr.mut.common.security.session.AbsoluteExpiryFilter
import kr.mut.common.security.session.SessionPolicy
import kr.mut.user.domain.AuthProvider
import kr.mut.user.domain.User
import kr.mut.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * 프로필 → 계정 → 세션 (ISSUE-030 · SPEC-08 §4.2).
 *
 * ## 이메일로 계정을 병합하지 않는다 (RED 19)
 *
 * **여기에 `findByEmail` 이 없는 것이 이 이슈의 산출물 하나다.**
 *
 * 편의상 넣고 싶어지는 지점이다 — 같은 사람이 카카오로 한 번, 네이버로 한 번 들어오면
 * 계정이 둘 생기니까. 그런데 SPEC-08 §4.2 가 명시적으로 금지했고, 이유가 둘이다:
 *
 * 하나, **이메일은 제공자마다 다르고 바뀐다.** 애플 비공개 릴레이는 앱마다 다른 주소를 주고,
 * 사용자는 소셜 계정의 이메일을 언제든 바꾼다. 바뀌는 값으로 동일인을 판정하면
 * 같은 사람이 어제와 오늘 다른 계정이 된다.
 *
 * 둘, **계정 탈취 경로가 된다.** 남의 이메일로 소셜 계정을 만들 수 있는 제공자가 하나라도
 * 있으면, 그것으로 그 이메일의 기존 계정에 들어갈 수 있다.
 *
 * ## 세션 고정 공격을 막는다
 *
 * 로그인 직후 세션 id 를 갈아 끼운다. 공격자가 미리 심어 둔 세션 id 로 사용자를 로그인시키면
 * 그 id 로 로그인된 세션에 올라탈 수 있다 — 인증 경계를 넘을 때 id 를 바꾸는 것이 표준 방어다.
 */
@Service
class SocialLoginService(
    private val users: UserRepository,
    private val clock: Clock = Clock.systemUTC(),
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 로그인 완료. 계정이 없으면 만든다 (RED 24).
     *
     * @return 로그인한 사용자 id
     */
    @Transactional
    fun login(request: HttpServletRequest, provider: AuthProvider, profile: SocialProfile): Long {
        val user = users.findByProviderCodeAndProviderUid(provider.code, profile.providerUid)
            ?.also { refresh(it, profile) }
            ?: create(provider, profile)

        establishSession(request, user)
        return user.id
    }

    /**
     * 최초 로그인 (RED 24~27).
     *
     * 기본 역할은 `member` 다 (RED 25 — SPEC 에 명시가 없어 정한 것).
     * 역할이 하나도 없으면 `AbsoluteExpiryFilter` 가 "역할 없음" 으로 보고 세션을 죽인다 —
     * 부여하지 않으면 가입하자마자 로그아웃되는 셈이다.
     */
    private fun create(provider: AuthProvider, profile: SocialProfile): User {
        val user = User(
            providerCode = provider.code,
            providerUid = profile.providerUid,
            displayName = displayNameOf(profile),
            email = profile.email,
        )
        user.grant(Role.MEMBER, grantedBy = null) // 스스로 가입한 것이라 부여자가 없다
        return users.save(user)
    }

    /**
     * 재로그인 시 표시 이름과 이메일을 따라간다.
     *
     * **식별자는 건드리지 않는다** (RED 20) — `(provider, provider_uid)` 는 불변이고,
     * 이메일이 바뀌어도 같은 계정이다. 그게 이메일로 판정하지 않는 이유의 뒷면이다.
     */
    private fun refresh(user: User, profile: SocialProfile) {
        profile.displayName?.takeIf { it.isNotBlank() }?.let { user.displayName = it }
        // 이메일은 `null` 로 덮지 않는다. 애플은 재로그인 때 이메일을 안 준다 —
        // 그대로 덮으면 한 번 로그인할 때마다 갖고 있던 연락처가 지워진다.
        profile.email?.takeIf { it.isNotBlank() }?.let { user.email = it }
    }

    /**
     * RED 27 — `display_name` 은 `NOT NULL` 이다 (SPEC-06 §3.5).
     *
     * 제공자가 안 줄 수 있다 — 애플은 아예 `id_token` 에 이름을 담지 않고, 카카오도
     * 닉네임 동의를 거부할 수 있다. **가입을 실패시키지 않는다**: 이름을 못 받았다는 이유로
     * 로그인이 막히면 그 사람은 서비스를 쓸 방법이 없다.
     *
     * `provider_uid` 를 그대로 쓰지 않는다 — 표시 이름은 화면에 나오고,
     * `provider_uid` 는 남에게 보여 줄 값이 아니다.
     */
    private fun displayNameOf(profile: SocialProfile): String =
        profile.displayName?.takeIf { it.isNotBlank() }
            ?: DEFAULT_NAME_PREFIX + profile.providerUid.takeLast(4)

    private fun establishSession(request: HttpServletRequest, user: User) {
        // 세션 고정 방어. 컨테이너가 id 를 갈아 끼우고 속성은 옮겨 준다.
        request.getSession(false)?.let { request.changeSessionId() }

        val session = request.getSession(true)
        val roles = user.roles

        session.setAttribute(AbsoluteExpiryFilter.USER_ID, user.id)
        session.setAttribute(SessionPolicy.ISSUED_AT, clock.instant())
        session.setAttribute(SessionPolicy.ISSUED_ROLES, roles.map(Role::code).toSet())
        session.maxInactiveInterval = SessionPolicy.lifetime(roles).seconds.toInt()

        // 어느 계정인지만 남긴다. 프로필 응답은 로그에 쓰지 않는다 (RED 38).
        log.info("로그인 (user={}, provider={})", user.id, user.providerCode)
    }

    companion object {
        /** 이름을 못 받았을 때. 뒤 네 자리를 붙여 같은 화면에 여럿 있어도 구분된다. */
        const val DEFAULT_NAME_PREFIX = "게스트-"
    }
}
