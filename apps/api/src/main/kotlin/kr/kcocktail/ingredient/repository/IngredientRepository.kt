package kr.kcocktail.ingredient.repository

import kr.kcocktail.ingredient.domain.Ingredient
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * `PRIN-T03` — 모듈 밖에서 참조하지 않는다. 타 모듈은 `ingredient.api` 의 Facade 를 쓴다.
 * 경계 테스트(이슈 001)가 막는다.
 */
interface IngredientRepository : JpaRepository<Ingredient, Long> {

    fun findBySlug(slug: String): Ingredient?

    fun existsBySlug(slug: String): Boolean

    fun findByIdInAndIsApprovedTrue(ids: Collection<Long>): List<Ingredient>

    /** DECISIONS §1.2 — 300개 초과 시 **경고**다. 차단이 아니다. */
    fun countByIsApprovedTrue(): Long

    /**
     * 재료 사전 목록 (이슈 023).
     *
     * **승인된 것만** 나간다 (`FR-INGREDIENT-001` · DECISIONS §1.1) —
     * 승인 전 재료가 사전에 보이면 승인제가 아무것도 막지 못한다.
     *
     * 필터는 `null` 이면 무시한다. 두 축을 각각 옵션으로 두는 것이
     * 조합마다 메서드를 만드는 것보다 낫다 — 축이 늘면 메서드가 곱으로 는다.
     */
    @Query(
        """
        SELECT i FROM Ingredient i
         WHERE i.isApproved = true
           AND (:category IS NULL OR i.categorySlug = :category)
           AND (:availability IS NULL OR i.availabilitySlug = :availability)
         ORDER BY i.nameKo
        """,
    )
    fun findDictionary(
        @Param("category") category: String?,
        @Param("availability") availability: String?,
        pageable: Pageable,
    ): List<Ingredient>

    @Query(
        """
        SELECT count(i) FROM Ingredient i
         WHERE i.isApproved = true
           AND (:category IS NULL OR i.categorySlug = :category)
           AND (:availability IS NULL OR i.availabilitySlug = :availability)
        """,
    )
    fun countDictionary(
        @Param("category") category: String?,
        @Param("availability") availability: String?,
    ): Long

    /** 상세. **미승인은 404 다** — 403 이면 존재가 새어 나간다 (이슈 023 RED 17). */
    fun findBySlugAndIsApprovedTrue(slug: String): Ingredient?

}

// 참조 중인 재료의 삭제는 **FK 가 막는다** (RED 24).
//
// 애플리케이션이 미리 세어 보지 않는 이유가 둘이다.
//   · recipe_ingredient 는 이슈 010 이 만든다. 지금 그 이름을 쿼리에 적으면
//     Postgres 가 실행 전 파싱에서 터진다 — EXISTS 로 감싸도 마찬가지다.
//   · 세어 보고 지우는 사이에 새 참조가 들어올 수 있다. FK 는 그 틈이 없다 (PRIN-T05).
//
// IngredientService 가 DataIntegrityViolationException 을 409 로 옮긴다.
