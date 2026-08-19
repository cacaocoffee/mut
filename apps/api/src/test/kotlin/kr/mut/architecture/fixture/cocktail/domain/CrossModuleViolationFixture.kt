package kr.mut.architecture.fixture.cocktail.domain

import kr.mut.architecture.fixture.ingredient.domain.IngredientEntityFixture
import kr.mut.architecture.fixture.ingredient.internal.IngredientServiceFixture
import kr.mut.architecture.fixture.ingredient.repository.IngredientRepositoryFixture

/**
 * 일부러 어긴다 — 규칙 1 · 2 · 3.
 *
 * COCKTAIL → INGREDIENT 는 SPEC-05 §3 이 허용하는 **방향**이지만,
 * 허용되는 것은 `ingredient.api` 뿐이다. 나머지 세 계층을 잡으면 위반이다.
 */
@Suppress("unused")
class CrossModuleViolationFixture(
    private val repository: IngredientRepositoryFixture, // 규칙 1 — repository 직접 참조
    private val entity: IngredientEntityFixture,         // 규칙 2 — entity 직접 참조
    private val service: IngredientServiceFixture,       // 규칙 3 — internal 참조
) {
    fun touch(): String {
        repository.findAll()
        service.reload()
        return entity.name
    }
}
