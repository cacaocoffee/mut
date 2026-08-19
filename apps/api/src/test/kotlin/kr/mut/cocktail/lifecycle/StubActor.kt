package kr.mut.cocktail.lifecycle

import kr.mut.common.audit.CurrentActor
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * 주체를 테스트가 정한다.
 *
 * 운영에서는 `SessionCurrentActor` 가 세션에서 읽지만, 서비스 계층 테스트에는 요청이 없다.
 * 요청을 흉내 내는 대신 주체만 바꿔 끼운다 — 보려는 것은 "세션을 어떻게 읽나"가 아니라
 * "**기록에 누가 남나**"다 (`NFR-O-05`).
 */
@Profile(AuditLogTest.PROFILE)
@Primary
@Component
class StubActor : CurrentActor {

    override fun userId(): Long? = Companion.userId

    companion object {
        @JvmStatic
        var userId: Long? = null
    }
}
