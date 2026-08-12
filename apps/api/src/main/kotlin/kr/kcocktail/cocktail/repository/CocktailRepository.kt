package kr.kcocktail.cocktail.repository

import kr.kcocktail.cocktail.domain.Cocktail
import org.springframework.data.jpa.repository.JpaRepository

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
}
