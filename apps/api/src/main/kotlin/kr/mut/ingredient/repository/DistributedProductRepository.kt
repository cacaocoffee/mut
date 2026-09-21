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

    /**
     * 어드민 목록 (#203). 제품명(한/영)·수입사 부분일치 + 식품유형. 최근 신고순.
     * `cast(:q as string)` — null 일 때 Postgres 가 bytea 로 추론하는 것을 막는다 (IngredientRepository 와 같은 이유).
     */
    @Query(
        """
        SELECT p FROM DistributedProduct p
         WHERE (:q IS NULL
                OR lower(p.nameKo) LIKE lower(concat('%', cast(:q as string), '%'))
                OR lower(p.nameEn) LIKE lower(concat('%', cast(:q as string), '%'))
                OR lower(p.importerOrMaker) LIKE lower(concat('%', cast(:q as string), '%')))
           AND (:foodType IS NULL OR p.foodType = :foodType)
         ORDER BY p.lastReportedOn DESC NULLS LAST, p.id DESC
        """,
    )
    fun browse(@Param("q") q: String?, @Param("foodType") foodType: String?, pageable: Pageable): List<DistributedProduct>

    @Query(
        """
        SELECT count(p) FROM DistributedProduct p
         WHERE (:q IS NULL
                OR lower(p.nameKo) LIKE lower(concat('%', cast(:q as string), '%'))
                OR lower(p.nameEn) LIKE lower(concat('%', cast(:q as string), '%'))
                OR lower(p.importerOrMaker) LIKE lower(concat('%', cast(:q as string), '%')))
           AND (:foodType IS NULL OR p.foodType = :foodType)
        """,
    )
    fun countBrowse(@Param("q") q: String?, @Param("foodType") foodType: String?): Long

    /**
     * 제품명(한/영)만 본다 — 수입사는 안 본다 (#209 후속). 레시피 줄에서 "캄파리" 를 찾을 때
     * 수입사 "캄파리코리아" 가 들여온 와일드터키가 섞여 나왔다.
     */
    @Query(
        """
        SELECT p FROM DistributedProduct p
         WHERE (:q IS NULL
                OR lower(p.nameKo) LIKE lower(concat('%', cast(:q as string), '%'))
                OR lower(p.nameEn) LIKE lower(concat('%', cast(:q as string), '%')))
           AND (:foodType IS NULL OR p.foodType = :foodType)
         ORDER BY p.lastReportedOn DESC NULLS LAST, p.id DESC
        """,
    )
    fun browseByName(@Param("q") q: String?, @Param("foodType") foodType: String?, pageable: Pageable): List<DistributedProduct>

    @Query(
        """
        SELECT count(p) FROM DistributedProduct p
         WHERE (:q IS NULL
                OR lower(p.nameKo) LIKE lower(concat('%', cast(:q as string), '%'))
                OR lower(p.nameEn) LIKE lower(concat('%', cast(:q as string), '%')))
           AND (:foodType IS NULL OR p.foodType = :foodType)
        """,
    )
    fun countBrowseByName(@Param("q") q: String?, @Param("foodType") foodType: String?): Long

    /** 유형 select 의 선택지. 시드에 실제로 있는 유형과 건수. */
    @Query("SELECT p.foodType, count(p) FROM DistributedProduct p GROUP BY p.foodType ORDER BY count(p) DESC")
    fun countByFoodType(): List<Array<Any>>
}
