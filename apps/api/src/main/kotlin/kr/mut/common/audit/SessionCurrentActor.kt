package kr.mut.common.audit

import kr.mut.common.security.session.AbsoluteExpiryFilter
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/**
 * 세션에서 주체를 읽는다 (SPEC-08 §3 — 세션은 서버 저장이다).
 *
 * 요청 밖(배치 · 마이그레이션 · 테스트)에서는 `null` 이다. **거짓 주체를 지어내지 않는다** —
 * `NFR-O-05` 가 "누가 무엇을 언제" 재구성을 요구하는데, 없는 사람을 적으면 재구성이 틀린다.
 * 빈 자리는 "사람이 아니었다" 는 정보다.
 */
@Component
class SessionCurrentActor : CurrentActor {

    override fun userId(): Long? {
        val attributes = RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes
            ?: return null

        return attributes.request.getSession(false)
            ?.getAttribute(AbsoluteExpiryFilter.USER_ID) as? Long
    }
}
