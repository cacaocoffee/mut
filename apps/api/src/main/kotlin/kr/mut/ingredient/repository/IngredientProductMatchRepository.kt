package kr.mut.ingredient.repository

import kr.mut.ingredient.domain.IngredientProductMatch
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface IngredientProductMatchRepository : JpaRepository<IngredientProductMatch, Long> {

    /**
     * 한 재료의 매핑 전부. 제품을 함께 가져온다 — 화면이 제품명·수입사·신고일을 그린다.
     * 승인 → 제안 → 거절 순, 그 안에서 신뢰도 높은 것부터.
     */
    @Query(
        """
        SELECT m FROM IngredientProductMatch m
          JOIN FETCH m.product p
         WHERE m.ingredient.id = :ingredientId
         ORDER BY CASE m.statusSlug WHEN 'approved' THEN 0 WHEN 'suggested' THEN 1 ELSE 2 END,
                  m.confidence DESC, p.lastReportedOn DESC NULLS LAST, m.id
        """,
    )
    fun findAllForIngredient(@Param("ingredientId") ingredientId: Long): List<IngredientProductMatch>

    @Query("SELECT m.product.id FROM IngredientProductMatch m WHERE m.ingredient.id = :ingredientId")
    fun productIdsOf(@Param("ingredientId") ingredientId: Long): Set<Long>

    fun findByIdAndIngredientId(id: Long, ingredientId: Long): IngredientProductMatch?
}
