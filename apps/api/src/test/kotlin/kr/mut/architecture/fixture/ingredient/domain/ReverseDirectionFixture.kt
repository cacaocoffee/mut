package kr.mut.architecture.fixture.ingredient.domain

import kr.mut.architecture.fixture.cocktail.api.CocktailFacadeFixture

/**
 * 일부러 어긴다 — 규칙 6 · 7.
 *
 * SPEC-05 §3 의 방향은 `COCKTAIL → INGREDIENT` 하나뿐이다. 반대는 없다.
 * 그리고 이 참조는 `CrossModuleViolationFixture` 와 맞물려 **순환**을 만든다:
 *   cocktail → ingredient → cocktail
 * §3 이 "조회는 SEARCH 가 담당해 순환을 끊는다"고 한 것이 이 모양을 막기 위해서다.
 */
@Suppress("unused")
class ReverseDirectionFixture(
    private val cocktail: CocktailFacadeFixture,
) {
    fun slug(): String = cocktail.slug()
}
