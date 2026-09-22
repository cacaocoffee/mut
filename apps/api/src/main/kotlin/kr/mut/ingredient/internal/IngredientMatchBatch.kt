package kr.mut.ingredient.internal

import kr.mut.ingredient.api.MatchRunResult
import kr.mut.ingredient.repository.IngredientRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 승인된 재료 전부에 매칭을 돌린다 (#213 · G-41).
 *
 * 재료마다 [IngredientProductMatcher.suggest] → [IngredientProductMatcher.apply].
 * 한 트랜잭션이다 — 중간에 죽으면 통째로 되돌아가고 다음 주기에 다시 돈다.
 * 검증 배치(`InvariantVerificationBatch`)와 같은 꼴이고, Spring Batch 를 안 쓰는 이유도 같다 (DECISIONS §1.9).
 */
@Component
class IngredientMatchBatch(
    private val ingredients: IngredientRepository,
    private val matcher: IngredientProductMatcher,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun run(): MatchRunResult {
        val startedAt = System.nanoTime()
        var created = 0
        var approved = 0
        var changed = 0
        val targets = ingredients.findAllByIsApprovedTrueOrderBySlugAsc()
        for (ingredient in targets) {
            val s = matcher.suggest(ingredient)
            created += s.created
            approved += s.autoApproved
            if (matcher.apply(ingredient)) changed += 1
        }
        val result = MatchRunResult(
            ingredients = targets.size,
            newMatches = created,
            autoApproved = approved,
            availabilityChanged = changed,
            elapsedMs = (System.nanoTime() - startedAt) / 1_000_000,
        )
        log.info(
            "매칭 배치 — 재료 {} · 새 매핑 {} (자동 승인 {}) · 유통 여부 변경 {} · {}ms",
            result.ingredients, result.newMatches, result.autoApproved, result.availabilityChanged, result.elapsedMs,
        )
        return result
    }
}
