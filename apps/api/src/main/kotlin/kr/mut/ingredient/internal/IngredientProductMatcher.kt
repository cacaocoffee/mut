package kr.mut.ingredient.internal

import kr.mut.ingredient.api.AvailabilityProposal
import kr.mut.ingredient.domain.DistributedProduct
import kr.mut.ingredient.domain.DomesticAvailability
import kr.mut.ingredient.domain.Ingredient
import kr.mut.ingredient.domain.IngredientProductMatch
import kr.mut.ingredient.domain.MatchStatus
import kr.mut.ingredient.repository.DistributedProductRepository
import kr.mut.ingredient.repository.IngredientProductMatchRepository
import kr.mut.common.audit.AuditAction
import kr.mut.common.audit.AuditRecorder
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate

/**
 * 재료 ↔ 유통 제품 매칭 (#195 · #213).
 *
 * ## 브랜드는 승인, 별명은 제안
 *
 * `brandKeywords`(상표 — "탱커레이") 가 제품명에 있으면 사람이 판단할 게 없다 → `approved`.
 * `aliases`("럼"·"소다" 처럼 넓은 말) 는 엉뚱한 것도 잡으므로 `suggested` 까지만.
 * 사람이 `rejected` 한 쌍은 유니크 제약이 다시 만들지 못하게 막는다 (`PRIN-T07`).
 *
 * ## 유통 여부는 올리기만 한다
 *
 * [apply] 는 승인 매핑으로 계산한 제안이 `common`·`specialty` 일 때만 재료를 바꾼다.
 * `import_only`·`unavailable` 로 내리는 건 대체재가 필수라(`INV-INGREDIENT-01`) 사람만 한다.
 *
 * ## 무엇으로 찾나
 *
 * 재료 이름 자체는 쓰지 않는다 — "진" 으로 제품명을 찾으면 "진로" 까지 잡힌다.
 * 검색어와 제품명이 통째로 같으면 신뢰도 100.
 */
@Component
class IngredientProductMatcher(
    private val products: DistributedProductRepository,
    private val matches: IngredientProductMatchRepository,
    private val audit: AuditRecorder,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    /** 한 재료를 훑은 결과. */
    data class Suggested(val created: Int, val autoApproved: Int)

    /** 새 매핑을 만든다. 브랜드 일치는 승인 상태로, 별명 일치는 제안 상태로. 이미 있는 쌍은 건너뛴다. */
    fun suggest(ingredient: Ingredient): Suggested {
        val known = matches.productIdsOf(ingredient.id).toMutableSet()
        var created = 0
        var autoApproved = 0

        val keywords =
            ingredient.brandKeywords.map { it to BRAND_CONFIDENCE } +
                ingredient.aliases.map { it to ALIAS_CONFIDENCE }

        for ((raw, baseConfidence) in keywords) {
            val keyword = raw.trim()
            if (keyword.length < MIN_KEYWORD_LENGTH) continue

            for (product in products.searchByName(escapeLike(keyword), PageRequest.of(0, PER_KEYWORD_LIMIT))) {
                if (!known.add(product.id)) continue
                val match = IngredientProductMatch(
                    ingredient = ingredient,
                    product = product,
                    matchedKeyword = keyword,
                    confidence = confidence(keyword, product, baseConfidence).toShort(),
                )
                // 상표 일치는 사람 손을 안 거친다 (#213 · 사용자 결정 2026-09-22)
                if (baseConfidence >= BRAND_CONFIDENCE) {
                    match.approve()
                    autoApproved += 1
                }
                matches.save(match)
                created += 1
            }
        }
        return Suggested(created, autoApproved)
    }

    /**
     * 승인 매핑으로 계산한 제안을 재료에 반영한다 — **올리는 방향만**. 바꿨으면 true.
     * 감사에 남긴다: 배치가 바꾼 값은 "왜 갑자기 common 이 됐나" 에 답할 것이 이것뿐이다.
     */
    fun apply(ingredient: Ingredient): Boolean {
        val proposal = propose(matches.findAllForIngredient(ingredient.id))
        val proposed = DomesticAvailability.ofSlug(proposal.availability)
        if (proposed != DomesticAvailability.COMMON && proposed != DomesticAvailability.SPECIALTY) return false
        val before = ingredient.domesticAvailability
        if (before == proposed) return false
        // specialty → common 은 올리는 것, common → specialty 는 내리는 것이다. 내리지 않는다.
        if (before == DomesticAvailability.COMMON) return false

        ingredient.domesticAvailability = proposed
        audit.record(
            entityType = "ingredient",
            entityId = ingredient.id,
            action = AuditAction.AVAILABILITY_CHANGE,
            before = mapOf("domesticAvailability" to before.slug),
            after = mapOf(
                "domesticAvailability" to proposed.slug,
                "slug" to ingredient.slug,
                "reason" to proposal.reason,
            ),
        )
        return true
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
