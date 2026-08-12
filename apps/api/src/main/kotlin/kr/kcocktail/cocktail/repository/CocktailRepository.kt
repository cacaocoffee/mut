package kr.kcocktail.cocktail.repository

import kr.kcocktail.cocktail.domain.Cocktail
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * `PRIN-T03` — 모듈 밖에서 참조하지 않는다. 타 모듈은 `cocktail.api` 의 Facade 를 쓴다.
 */
interface CocktailRepository : JpaRepository<Cocktail, Long> {

    fun findBySlug(slug: String): Cocktail?

    fun existsBySlug(slug: String): Boolean

    /** 공개 조회는 `published` 만 본다. `draft`·`archived` 는 404 다 (SPEC-07 §5). */
    fun findBySlugAndStatusSlug(slug: String, statusSlug: String): Cocktail?

    /**
     * 배치 검증(이슈 016)의 전수 스캔. `NFR-D-01` 이 대상을 **발행분**으로 못박았다.
     *
     * 500종 규모라 한 번에 읽는다. 더 커지면 페이징으로 바꾼다 — 그때는
     * 배치가 메모리에 다 올리지 않아야 한다.
     */
    fun findByStatusSlug(statusSlug: String): List<Cocktail>

    /**
     * 이 재료를 쓰는 발행 칵테일 (`R-F1.3-1`, 이슈 023).
     *
     * `recipe_ingredient(ingredient_id)` 인덱스를 탄다 (SPEC-06 §5) —
     * 역검색과 재료 사전이 같은 인덱스를 쓴다.
     *
     * 표준 레시피만 본다. `bar_signature`(Phase 1b)에만 쓰인 재료를 세면
     * "이 재료를 쓰는 칵테일" 이 **일반 레시피에 없는 것까지** 포함하게 된다.
     */
    /**
     * `[0]` 은 칵테일, `[1]` 은 **한 줄이라도 필수면 0** 인 최솟값이다.
     *
     * 같은 재료가 한 레시피에 두 줄로 들어갈 수 있어 `GROUP BY` 로 접는다 —
     * `DISTINCT` 만 쓰면 선택 여부가 줄마다 달라 같은 칵테일이 두 번 나온다.
     */
    @Query(
        """
        SELECT c, MIN(CASE WHEN ri.isOptional = true THEN 1 ELSE 0 END)
          FROM Cocktail c
          JOIN Recipe r ON r.cocktail = c AND r.versionTypeSlug = 'standard'
          JOIN RecipeIngredient ri ON ri.recipe = r
         WHERE ri.ingredientId = :ingredientId AND c.statusSlug = 'published'
         GROUP BY c
         ORDER BY c.nameKo
        """,
    )
    fun findPublishedByIngredient(
        @Param("ingredientId") ingredientId: Long,
        pageable: Pageable,
    ): List<Array<Any>>

    @Query(
        """
        SELECT count(DISTINCT c) FROM Cocktail c
          JOIN Recipe r ON r.cocktail = c AND r.versionTypeSlug = 'standard'
          JOIN RecipeIngredient ri ON ri.recipe = r
         WHERE ri.ingredientId = :ingredientId AND c.statusSlug = 'published'
        """,
    )
    fun countPublishedByIngredient(@Param("ingredientId") ingredientId: Long): Long
}
