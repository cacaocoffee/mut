package kr.kcocktail.user.domain

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import kr.kcocktail.common.entity.BaseEntity
import kr.kcocktail.common.security.Role

/**
 * SPEC-06 §3.5 — 사용자.
 *
 * ## 여기 없는 것
 *
 * | 없는 것 | 근거 |
 * |---|---|
 * | 성인 인증 (`birthDate` · `ageVerified`) | ADR-0004 — 판매를 하지 않으므로 전면 인증을 요구하지 않는다 |
 * | 위치 좌표 (`lat` · `lng`) | `PRIN-D04` — 좌표는 요청 스코프에만 산다. 세션에도 저장하지 않는다 |
 * | 제공자 갱신 토큰 | DECISIONS §1 — 세션이 우리 것이다 |
 *
 * **부재는 주석만으로 지켜지지 않는다.** `SchemaAbsenceTest` 가 컬럼이 생기는 것을 막는다.
 */
@Entity
@Table(name = "\"user\"") // Postgres 예약어. SPEC-06 §3.5 가 이 이름으로 명시했다
class User(
    @Column(name = "provider", nullable = false, length = 12)
    @Suppress("JpaAttributeTypeInspection")
    var providerCode: String,

    @Column(name = "provider_uid", nullable = false, length = 120, updatable = false)
    var providerUid: String,

    @Column(name = "display_name", nullable = false, length = 60)
    var displayName: String,

    /** 애플 비공개 릴레이는 이메일을 주지 않는다 (SPEC-08 §4.2). */
    @Column(name = "email", length = 255)
    var email: String? = null,
) : BaseEntity() {

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    private val roleRows: MutableSet<UserRole> = mutableSetOf()

    var provider: AuthProvider
        get() = AuthProvider.ofCode(providerCode)
        set(value) { providerCode = value.code }

    /**
     * SPEC-08 §3.3 — **세션에 캐시하지 않는다.** 매 요청에 여기서 읽어야
     * 강등이 다음 요청부터 즉시 반영된다.
     */
    val roles: Set<Role> get() = roleRows.map { it.role }.toSet()

    /** 같은 역할 중복 부여는 복합 PK 가 막지만, 왕복하지 않고 여기서 먼저 거른다. */
    fun grant(role: Role, grantedBy: User?) {
        if (roleRows.any { it.role == role }) return
        roleRows += UserRole(user = this, roleCode = role.code, grantedBy = grantedBy)
    }

    fun revoke(role: Role) {
        roleRows.removeIf { it.role == role }
    }

    fun has(role: Role): Boolean = roleRows.any { it.role == role }
}
