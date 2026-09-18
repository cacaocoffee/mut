package kr.mut.ingredient.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import kr.mut.common.entity.BaseEntity
import kr.mut.common.web.error.ConflictException

/** 매핑 상태. DB CHECK `ck_ingredient_product_match__status` 와 같은 셋이다. */
enum class MatchStatus(val slug: String) {
    SUGGESTED("suggested"),
    APPROVED("approved"),
    REJECTED("rejected"),
    ;

    companion object {
        fun ofSlug(slug: String): MatchStatus =
            entries.firstOrNull { it.slug == slug } ?: error("알 수 없는 match status: $slug")
    }
}

/**
 * 재료 ↔ 유통 제품 한 쌍 (#195 · `V030__ingredient_product_match.sql`).
 *
 * 배치가 만들 때는 항상 [MatchStatus.SUGGESTED] 다. 승인·거절은 어드민만 한다 —
 * 제품명 표기가 제각각이라 자동 판정을 믿을 수 없어서다.
 */
@Entity
@Table(name = "ingredient_product_match")
class IngredientProductMatch(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false, updatable = false)
    val ingredient: Ingredient,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    val product: DistributedProduct,

    /** 어느 검색어가 잡았나. "왜 이게 떴지" 에 답한다. */
    @Column(name = "matched_keyword", nullable = false)
    val matchedKeyword: String,

    /** 0~100. 브랜드 검색어 80 · 별칭 50 · 이름이 통째로 같으면 100. */
    @Column(name = "confidence", nullable = false)
    val confidence: Short,

    @Column(name = "matched_by", nullable = false, length = 16)
    val matchedBy: String = "batch",
) : BaseEntity() {

    @Column(name = "status", nullable = false, length = 12)
    private var statusSlug: String = MatchStatus.SUGGESTED.slug

    val status: MatchStatus get() = MatchStatus.ofSlug(statusSlug)

    /** 재승인은 409 — 재료 승인(DECISIONS §1.11)과 같은 이유로 이력을 흐리지 않는다. */
    fun approve() {
        if (status == MatchStatus.APPROVED) throw ConflictException("이미 승인된 매핑입니다")
        statusSlug = MatchStatus.APPROVED.slug
    }

    fun reject() {
        if (status == MatchStatus.REJECTED) throw ConflictException("이미 거절된 매핑입니다")
        statusSlug = MatchStatus.REJECTED.slug
    }
}
