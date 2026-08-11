package kr.kcocktail.ingredient.repository

import kr.kcocktail.ingredient.domain.Ingredient
import org.springframework.data.jpa.repository.JpaRepository

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

}

// 참조 중인 재료의 삭제는 **FK 가 막는다** (RED 24).
//
// 애플리케이션이 미리 세어 보지 않는 이유가 둘이다.
//   · recipe_ingredient 는 이슈 010 이 만든다. 지금 그 이름을 쿼리에 적으면
//     Postgres 가 실행 전 파싱에서 터진다 — EXISTS 로 감싸도 마찬가지다.
//   · 세어 보고 지우는 사이에 새 참조가 들어올 수 있다. FK 는 그 틈이 없다 (PRIN-T05).
//
// IngredientService 가 DataIntegrityViolationException 을 409 로 옮긴다.
