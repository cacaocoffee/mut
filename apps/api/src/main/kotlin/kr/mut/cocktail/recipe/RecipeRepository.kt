package kr.mut.cocktail.recipe

import org.springframework.data.jpa.repository.JpaRepository

/** `PRIN-T03` — 모듈 밖에서 참조하지 않는다. */
interface RecipeRepository : JpaRepository<Recipe, Long> {

    fun findByCocktailIdAndVersionTypeSlug(cocktailId: Long, versionTypeSlug: String): Recipe?

    fun findAllByCocktailId(cocktailId: Long): List<Recipe>

    /**
     * `GATE-COCKTAIL-03` 이 쓴다 — 표준 레시피가 없으면 발행할 수 없다.
     * 게이트 자체는 이슈 013 이고 여기서는 조회만 제공한다.
     */
    fun existsByCocktailIdAndVersionTypeSlug(cocktailId: Long, versionTypeSlug: String): Boolean
}
