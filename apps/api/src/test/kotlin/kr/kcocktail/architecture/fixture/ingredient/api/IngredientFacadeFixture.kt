package kr.kcocktail.architecture.fixture.ingredient.api

/** 타 모듈이 참조해도 되는 유일한 지점 (SPEC-05 §3). */
class IngredientFacadeFixture {
    fun displayName(): String = "진"
}
