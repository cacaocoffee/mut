package kr.mut.user.oauth

import jakarta.servlet.http.HttpServletRequest
import kr.mut.common.web.error.BadRequestException
import kr.mut.user.domain.AuthProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * `state` 와 PKCE 검증자를 잠깐 들고 있는 곳 (SPEC-08 §4.2).
 *
 * ## 세션에 담고 테이블을 만들지 않는다
 *
 * 수명이 10분이라 세션 수명 안에 들어간다. 테이블을 만들면 만료된 행을 지우는 배치가
 * 따라오고, 그 배치가 죽어 있으면 아무도 모른다 — 세션은 스프링이 이미 정리한다.
 *
 * ## 1회용이 요점이다 (RED 7)
 *
 * 검증하는 순간 지운다. 남겨 두면 같은 코드로 두 번 교환할 수 있고, 그게 되면
 * `state` 는 CSRF 방어가 아니라 **재생 가능한 토큰**이 된다.
 *
 * 재사용 시도는 **거부하고 로그한다** (RED 8) — 스펙이 명시적으로 요구했다.
 * 정상 사용자에게는 일어나지 않는 일이라, 로그에 찍히면 그 자체가 신호다.
 */
@Component
class OAuthStateStore(private val clock: Clock = Clock.systemUTC()) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 인가 요청을 시작하며 심는다. 세션이 없으면 만든다 — 콜백이 같은 세션으로 돌아와야 한다. */
    fun issue(request: HttpServletRequest, provider: AuthProvider, state: String, verifier: String) {
        request.getSession(true).setAttribute(
            key(state),
            Pending(provider.code, verifier, clock.instant()),
        )
    }

    /**
     * 검증하고 **즉시 제거**한다.
     *
     * @return PKCE 검증자
     * @throws OAuthStateException 없음 · 만료 · 제공자 불일치 — 이유를 밖으로 구분해 주지 않는다
     */
    fun consume(request: HttpServletRequest, provider: AuthProvider, state: String): String {
        val session = request.getSession(false)
            ?: throw reject(provider, state, "세션 없음")

        val pending = session.getAttribute(key(state)) as? Pending
            // 없다 = 처음 보는 state 이거나 **이미 쓴 state** 다. 둘을 구분해 알려 주지 않는다 —
            // 공격자에게 "그 state 는 존재했다" 를 알려 줄 이유가 없다.
            ?: throw reject(provider, state, "미발급이거나 이미 사용된 state")

        session.removeAttribute(key(state)) // 성공하든 실패하든 1회용이다

        if (pending.providerCode != provider.code) {
            // 카카오로 시작해 네이버 콜백으로 들어오는 경우. 정상 흐름에는 없다.
            throw reject(provider, state, "제공자 불일치 (발급=${pending.providerCode})")
        }
        if (Duration.between(pending.issuedAt, clock.instant()) > TTL) {
            throw reject(provider, state, "만료 (${TTL.toMinutes()}분)")
        }

        return pending.verifier
    }

    private fun reject(provider: AuthProvider, state: String, why: String): OAuthStateException {
        // SPEC-08 §4.2 — "거부하고 로그한다".
        // state 값 자체를 통째로 남기지 않는다. 앞 8자면 로그끼리 대조하기에 충분하고,
        // 전체를 남기면 로그를 읽을 수 있는 사람이 유효한 state 를 손에 넣는다.
        log.warn("OAuth state 거부 (provider={}, state={}…, 사유={})", provider.code, state.take(8), why)
        return OAuthStateException(why)
    }

    private fun key(state: String) = "$PREFIX$state"

    private data class Pending(
        val providerCode: String,
        val verifier: String,
        val issuedAt: Instant,
    ) : java.io.Serializable

    companion object {
        /** SPEC-08 §4.2 — 10분. */
        val TTL: Duration = Duration.ofMinutes(10)

        private const val PREFIX = "mut.oauth.state."
    }
}

/**
 * 400 으로 나간다 — [BadRequestException] 을 물려받아 상태 코드가 자동으로 붙는다.
 *
 * 사유를 메시지에 담되 **어느 검사에서 걸렸는지는 흐리게** 둔다.
 * "이미 사용된 state" 와 "처음 보는 state" 를 구분해 주면, 공격자가 그 차이로
 * 유효했던 state 를 가려낼 수 있다. 정확한 사유는 로그에만 남는다.
 */
class OAuthStateException(reason: String) :
    BadRequestException("로그인 요청이 유효하지 않습니다 ($reason)")
