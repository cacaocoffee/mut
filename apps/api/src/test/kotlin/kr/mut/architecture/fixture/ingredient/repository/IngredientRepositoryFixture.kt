package kr.mut.architecture.fixture.ingredient.repository

/** 모듈 밖에서 참조하면 안 된다. */
class IngredientRepositoryFixture {
    fun findAll(): List<String> = emptyList()
}
