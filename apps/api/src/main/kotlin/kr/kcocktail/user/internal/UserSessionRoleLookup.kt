package kr.kcocktail.user.internal

import kr.kcocktail.common.security.Role
import kr.kcocktail.common.security.session.SessionRoleLookup
import kr.kcocktail.user.repository.UserRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 세션 필터가 역할을 읽는 실제 경로 (SPEC-08 §3.3).
 *
 * ## 매 요청에 DB 를 친다
 *
 * "세션에 캐시하지 않는다"가 요구사항이다. 캐시하면 회수한 권한이 세션 수명만큼 살아남고,
 * 그 수명이 일반 사용자는 30일이다.
 *
 * 복합 PK `(user_id, role)` 가 곧 인덱스라 이 조회는 인덱스 한 번이다 (SPEC-06 §5).
 * 그래도 비싸지면 짧은 TTL 캐시를 검토하되, **강등 반영 지연이 곧 그 TTL 이라는 것**을
 * 받아들일 수 있을 때만 한다.
 *
 * 이 구현이 `common` 이 아니라 `user` 에 있는 이유는 의존 방향이다 —
 * 공용 커널이 도메인 모듈을 되참조하면 안 된다 (`PRIN-T03`).
 */
@Component
class UserSessionRoleLookup(private val users: UserRepository) : SessionRoleLookup {

    @Transactional(readOnly = true)
    override fun rolesOf(userId: Long): Set<Role> =
        users.findRoleCodes(userId).map(Role::ofCode).toSet()
}
