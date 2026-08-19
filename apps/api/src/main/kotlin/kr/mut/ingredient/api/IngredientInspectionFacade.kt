package kr.mut.ingredient.api

/**
 * 배치 검증(이슈 016)이 재료를 보는 창구 (`PRIN-T03`).
 *
 * [IngredientFacade] 는 **id 로 집어 오는** 용도라 전수 스캔을 못 한다.
 * 배치는 반대로 "전부 훑어서 어긋난 것을 찾는" 일이라 창구를 따로 둔다 —
 * 기존 인터페이스에 `findAll()` 을 더하면 조회 경로에서도 전수 조회가 가능해지고,
 * 그것은 500종 규모에서 사고가 된다.
 */
interface IngredientInspectionFacade {

    /**
     * 승인 여부와 무관하게 전부.
     *
     * `INV-INGREDIENT-01`·`02` 는 발행분에만 걸리는 규칙이 아니다 —
     * 마스터가 오염되면 그것을 쓰는 모든 레시피가 같이 틀린다 (`PRIN-D01`).
     */
    fun findAllForVerification(): List<IngredientView>
}
