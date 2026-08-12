package kr.kcocktail.ingredient.internal

import kr.kcocktail.ingredient.api.IngredientInspectionFacade
import kr.kcocktail.ingredient.api.IngredientView
import kr.kcocktail.ingredient.repository.IngredientRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 배치 검증(이슈 016)의 전수 조회.
 *
 * `IngredientService` 와 **같은 [toView] 매핑**을 쓴다. 배치가 다른 모양을 보면
 * 배치가 찾은 위반과 실제 데이터가 어긋나고, 그 어긋남은 사람이 태스크 큐에서
 * "이거 왜 위반이지" 로 만난다.
 */
@Service
class IngredientInspectionService(
    private val ingredients: IngredientRepository,
) : IngredientInspectionFacade {

    @Transactional(readOnly = true)
    override fun findAllForVerification(): List<IngredientView> =
        ingredients.findAll().map { it.toView() }
}
