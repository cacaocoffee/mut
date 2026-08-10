package kr.kcocktail.common.entity

import jakarta.persistence.Column
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import java.time.Instant

/**
 * SPEC-06 §1.2 공통 컬럼 — 모든 실체 테이블이 갖는다.
 *
 * | 컬럼 | 타입 |
 * |---|---|
 * | `id` | `BIGINT GENERATED ALWAYS AS IDENTITY` |
 * | `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
 * | `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` — **트리거로 갱신** |
 *
 * ## `id` 를 URL 에 쓰지 않는다
 *
 * 공개 식별자는 `slug` 다 (SPEC-07 §1.1). 슬러그가 SEO 자산이고 `PRIN-D02` 가 불변을 요구한다.
 * `id` 는 조인용 내부 키일 뿐이라 공개 응답에 실어 나가지 않는다.
 *
 * ## `updatedAt` 은 여기서 갱신하지 않는다
 *
 * `@PreUpdate` 를 달지 않은 것은 실수가 아니다. SPEC-06 §1.2 가 "트리거로 갱신"이라고 했고,
 * JPA 콜백은 **벌크 `UPDATE` 와 마이그레이션을 놓친다.** 정본은 `set_updated_at()` 트리거다
 * (`V001__baseline.sql`). 이 필드는 DB 가 쓴 값을 읽어 오는 자리다.
 *
 * 그래서 저장 직후 메모리의 `updatedAt` 은 DB 값과 다를 수 있다. 정확한 값이 필요하면
 * `refresh` 하거나 다시 조회한다.
 */
@MappedSuperclass
abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    var id: Long = 0
        protected set

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    var createdAt: Instant = Instant.EPOCH
        protected set

    /** 트리거가 정본이다. 애플리케이션이 쓰지 않는다. */
    @Column(name = "updated_at", nullable = false, updatable = false, insertable = false)
    var updatedAt: Instant = Instant.EPOCH
        protected set

    /** 영속 전 엔티티는 서로 같지 않다 — `id` 가 전부 0 이기 때문이다. */
    val isNew: Boolean get() = id == 0L

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BaseEntity) return false
        if (isNew || other.isNew) return false
        return id == other.id && javaClass == other.javaClass
    }

    override fun hashCode(): Int = javaClass.hashCode()

    override fun toString(): String = "${javaClass.simpleName}(id=$id)"
}
