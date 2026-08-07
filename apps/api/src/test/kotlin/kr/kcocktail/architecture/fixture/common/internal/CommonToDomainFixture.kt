package kr.kcocktail.architecture.fixture.common.internal

import kr.kcocktail.architecture.fixture.cocktail.api.CocktailFacadeFixture

/**
 * 일부러 어긴다 — 규칙 4.
 *
 * common 은 공용 커널이라 **모두가 참조한다.** 그것이 도메인을 되참조하면
 * 의존이 양방향이 되고, 모듈을 서비스로 떼어낼 때 common 이 따라 쪼개져야 한다.
 */
@Suppress("unused")
class CommonToDomainFixture(
    private val cocktail: CocktailFacadeFixture,
) {
    fun slug(): String = cocktail.slug()
}
