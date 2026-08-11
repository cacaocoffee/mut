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
}
