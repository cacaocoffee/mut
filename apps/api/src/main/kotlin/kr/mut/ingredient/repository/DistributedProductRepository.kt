package kr.mut.ingredient.repository

import kr.mut.ingredient.domain.DistributedProduct
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface DistributedProductRepository : JpaRepository<DistributedProduct, Long> {

    /**
     * 제품명(한/영) 부분일치. 매칭 배치가 검색어마다 한 번 부른다 (#195).
     *
     * `%` 와 `_` 는 호출부가 이스케이프한다 — 검색어에 들어 있으면 와일드카드로 읽힌다.
     */
    @Query(
        """
        SELECT p FROM DistributedProduct p
         WHERE lower(p.nameKo) LIKE lower(concat('%', :keyword, '%')) ESCAPE '\'
            OR lower(p.nameEn) LIKE lower(concat('%', :keyword, '%')) ESCAPE '\'
         ORDER BY p.lastReportedOn DESC NULLS LAST, p.id
        """,
    )
    fun searchByName(@Param("keyword") keyword: String, pageable: Pageable): List<DistributedProduct>
}
