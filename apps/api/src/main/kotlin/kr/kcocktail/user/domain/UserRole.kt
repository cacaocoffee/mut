package kr.kcocktail.user.domain

import jakarta.persistence.Column
import kr.kcocktail.common.security.Role
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant

/**
 * SPEC-06 §3.5 — 역할 부여 기록. PK 가 `(user_id, role)` 복합이다.
 *
 * [kr.kcocktail.common.entity.BaseEntity] 를 상속하지 않는다 — 대리키가 없는 표라
 * `id` 컬럼을 두면 복합 PK 가 의미를 잃고 같은 역할이 두 번 들어간다.
 */
@Entity
@Table(name = "user_role")
@IdClass(UserRoleId::class)
class UserRole(
    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Id
    @Column(name = "role", nullable = false, length = 16)
    val roleCode: String,

    /** 부여자가 탈퇴해도 이력은 남는다 (FK 는 `ON DELETE SET NULL`). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by")
    val grantedBy: User? = null,

    @Column(name = "granted_at", nullable = false, insertable = false, updatable = false)
    val grantedAt: Instant = Instant.EPOCH,
) {
    val role: Role get() = Role.ofCode(roleCode)
}

/** JPA 복합 키. 필드 이름이 [UserRole] 의 `@Id` 와 같아야 한다. */
data class UserRoleId(
    val user: Long = 0,
    val roleCode: String = "",
) : Serializable
