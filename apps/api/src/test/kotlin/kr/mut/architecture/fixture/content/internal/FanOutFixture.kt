package kr.mut.architecture.fixture.content.internal

import kr.mut.architecture.fixture.cocktail.api.CocktailFacadeFixture
import kr.mut.architecture.fixture.ingredient.api.IngredientFacadeFixture
import kr.mut.architecture.fixture.user.api.UserFacadeFixture

/**
 * 일부러 어긴다 — 규칙 8.
 *
 * 전부 `api` 경유라 규칙 1~3 은 통과한다. 그런데도 잡혀야 한다 —
 * 여러 도메인을 한데 모아 읽는 자리는 SPEC-05 §3 상 SEARCH 하나뿐이다.
 * 다른 모듈이 그 일을 하기 시작하면 조회 경로가 흩어지고 순환이 뒤따른다.
 */
@Suppress("unused")
class FanOutFixture(
    private val cocktail: CocktailFacadeFixture,
    private val ingredient: IngredientFacadeFixture,
    private val user: UserFacadeFixture,
) {
    fun summary(): String = cocktail.slug() + ingredient.displayName() + user.currentRole()
}
