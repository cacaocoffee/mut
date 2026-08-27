package kr.mut.user.profile

import kr.mut.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 로그인한 본인 정보 (SPEC-07 §2.5 — 권한 `◐`).
 *
 * 화면이 "로그인했는가 · 누구인가" 를 알기 위한 최소 조회다. 역할은 세션에 캐시하지 않고
 * 매 요청에 읽는다(SPEC-08 §3.3) — 여기서도 엔티티에서 바로 뽑는다.
 */
@Service
class ProfileService(private val users: UserRepository) {

    /** 세션의 user_id 로 본인을 읽는다. 세션은 있는데 사용자가 없으면(삭제 등) null. */
    @Transactional(readOnly = true)
    fun profileOf(userId: Long): MyProfile? =
        users.findById(userId)
            .map { MyProfile(displayName = it.displayName, roles = it.roles.map { r -> r.code }.sorted()) }
            .orElse(null)
}

/**
 * 내 프로필 한 줄. 이메일·제공자·내부 식별자를 담지 않는다 — 화면이 쓰는 것은
 * 표시명과 역할(어드민 진입 노출 여부)뿐이다. 넣어 두고 빼는 것보다 필요할 때 넣는 게 쉽다.
 */
data class MyProfile(
    val displayName: String,
    val roles: List<String>,
)
