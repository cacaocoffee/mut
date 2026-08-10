package kr.kcocktail.architecture.fixture.common.entity

import kr.kcocktail.architecture.fixture.ingredient.repository.IngredientRepositoryFixture

/**
 * 일부러 어긴다 — 규칙 4.
 *
 * `entity` 는 SPEC-05 §2 의 5개 계층에 없는 커널 패키지다. `ModuleBoundaryTest` 의
 * LAYERS 에 등록하지 않으면 이 클래스가 **그래프에 아예 안 보여** 무엇을 참조하든 통과한다.
 * 이 픽스처가 그 구멍을 지킨다 (ISSUE-002 가 BaseEntity 를 넣으며 드러났다).
 */
@Suppress("unused")
class EntityLayerViolationFixture(
    private val repository: IngredientRepositoryFixture,
) {
    fun touch() = repository.findAll()
}
