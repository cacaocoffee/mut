package kr.mut.architecture.fixture.cocktail.web

import kr.mut.architecture.fixture.cocktail.repository.CocktailRepositoryFixture

/**
 * 일부러 어긴다 — 규칙 5.
 *
 * 같은 모듈 안이라도 컨트롤러가 리포지토리를 직행하면 안 된다.
 * 조회 조건이 web 계층에 새고, internal 의 트랜잭션 경계를 건너뛴다.
 */
@Suppress("unused")
class WebToRepositoryFixture(
    private val repository: CocktailRepositoryFixture,
) {
    fun count(): Long = repository.countPublished()
}
