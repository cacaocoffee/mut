package kr.mut.ingredient.internal

import kr.mut.ingredient.api.AvailabilityProposal
import kr.mut.ingredient.domain.DistributedProduct
import kr.mut.ingredient.domain.DomesticAvailability
import kr.mut.ingredient.domain.Ingredient
import kr.mut.ingredient.domain.IngredientProductMatch
import kr.mut.ingredient.domain.MatchStatus
import kr.mut.ingredient.repository.DistributedProductRepository
import kr.mut.ingredient.repository.IngredientProductMatchRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate

/**
 * 재료 ↔ 유통 제품 매칭 (#195).
 *
 * ## 제안까지만 한다
 *
 * 제품명 표기가 제각각이라("캄파리"·"깜빠리"·"CAMPARI BITTER 25%") 자동 판정은 틀린다.
 * 여기서 만드는 매핑은 전부 `suggested` 다. 승인은 어드민이 하고, 유통 여부도 [propose] 가
 * **제안**할 뿐 `ingredient` 를 바꾸지 않는다.
 *
 * ## 무엇으로 찾나
 *
 * `brandKeywords`(신뢰도 80) 와 `aliases`(50). 재료 이름 자체는 쓰지 않는다 — "진" 으로 제품명을
 * 찾으면 "진로" 까지 잡힌다. 검색어와 제품명이 통째로 같으면 100.
 */
@Component
class IngredientProductMatcher(
    private val products: DistributedProductRepository,
    private val matches: IngredientProductMatchRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    /** 새로 만든 매핑 수. 이미 있는 쌍은 건너뛴다 (`uq_ingredient_product_match__pair`). */
    fun suggest(ingredient: Ingredient): Int {
        val known = matches.productIdsOf(ingredient.id).toMutableSet()
        var created = 0

        val keywords =
            ingredient.brandKeywords.map { it to BRAND_CONFIDENCE } +
                ingredient.aliases.map { it to ALIAS_CONFIDENCE }

        for ((raw, baseConfidence) in keywords) {
            val keyword = raw.trim()
            if (keyword.length < MIN_KEYWORD_LENGTH) continue

            for (product in products.searchByName(escapeLike(keyword), PageRequest.of(0, PER_KEYWORD_LIMIT))) {
                if (!known.add(product.id)) continue
                matches.save(
                    IngredientProductMatch(
                        ingredient = ingredient,
                        product = product,
                        matchedKeyword = keyword,
                        confidence = confidence(keyword, product, baseConfidence).toShort(),
                    ),
                )
                created += 1
            }
        }
        return created
    }

    /**
     * 승인된 매핑으로 유통 여부를 제안한다.
     *
     * | 최근 [RECENT_YEARS]년 안 신고된 승인 매핑 | 제안 |
     * |---|---|
     * | 3건 이상 | `common` |
     * | 1~2건 | `specialty` |
     * | 0건 (승인은 있으나 오래됨 포함) | `import_only` |
     *
     * `unavailable` 은 제안하지 않는다 — "신고가 없다" 와 "국내에 없다" 는 다르다.
     */
    fun propose(all: List<IngredientProductMatch>): AvailabilityProposal {
        val approved = all.filter { it.status == MatchStatus.APPROVED }
        val cutoff = LocalDate.now(clock).minusYears(RECENT_YEARS)
        val recent = approved.filter { it.product.lastReportedOn?.isAfter(cutoff) == true }
        val latest = approved.mapNotNull { it.product.lastReportedOn }.maxOrNull()

        val (availability, reason) = when {
            recent.size >= COMMON_THRESHOLD ->
                DomesticAvailability.COMMON to "최근 ${RECENT_YEARS}년 안에 신고된 승인 제품이 ${recent.size}건"
            recent.isNotEmpty() ->
                DomesticAvailability.SPECIALTY to "최근 ${RECENT_YEARS}년 안에 신고된 승인 제품이 ${recent.size}건뿐"
            approved.isNotEmpty() ->
                DomesticAvailability.IMPORT_ONLY to "승인 제품 ${approved.size}건이 전부 ${RECENT_YEARS}년 넘게 신고가 없음 (마지막 $latest)"
            else ->
                DomesticAvailability.IMPORT_ONLY to "승인된 유통 제품이 없음"
        }
        return AvailabilityProposal(
            availability = availability.slug,
            approvedCount = approved.size,
            recentApprovedCount = recent.size,
            latestReportedOn = latest,
            reason = reason,
        )
    }

    private fun confidence(keyword: String, product: DistributedProduct, base: Int): Int {
        val k = squash(keyword)
        return if (squash(product.nameKo) == k || squash(product.nameEn ?: "") == k) EXACT_CONFIDENCE else base
    }

    /** 띄어쓰기·대소문자를 무시하고 비교한다. */
    private fun squash(s: String) = s.lowercase().replace(Regex("\\s+"), "")

    /** `%`·`_` 가 검색어에 들어 있으면 와일드카드로 읽힌다. */
    private fun escapeLike(s: String) = s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    companion object {
        const val BRAND_CONFIDENCE = 80
        const val ALIAS_CONFIDENCE = 50
        const val EXACT_CONFIDENCE = 100
        const val MIN_KEYWORD_LENGTH = 2
        const val PER_KEYWORD_LIMIT = 200
        const val RECENT_YEARS = 3L
        const val COMMON_THRESHOLD = 3
    }
}
