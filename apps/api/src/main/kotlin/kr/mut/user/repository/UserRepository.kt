package kr.mut.user.repository

import kr.mut.user.domain.AuthProvider
import kr.mut.user.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * `PRIN-T03` — 이 인터페이스는 **모듈 밖에서 참조하지 않는다.**
 * 타 모듈은 `user/api` 의 Facade 를 쓴다 (이슈 006 이 만든다). 경계 테스트가 막는다.
 */
interface UserRepository : JpaRepository<User, Long> {

    fun findByProviderCodeAndProviderUid(providerCode: String, providerUid: String): User?

    fun findByProviderCodeAndProviderUid(provider: AuthProvider, providerUid: String): User? =
        findByProviderCodeAndProviderUid(provider.code, providerUid)

    /**
     * SPEC-08 §3.3 — 역할을 세션에 캐시하지 않는다. 매 요청에 이걸로 읽는다.
     * 복합 PK 가 곧 `(user_id, role)` 인덱스라 이 조회가 싸다 (SPEC-06 §5).
     */
    @Query("SELECT r.roleCode FROM UserRole r WHERE r.user.id = :userId")
    fun findRoleCodes(@Param("userId") userId: Long): List<String>
}
