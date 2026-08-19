package kr.mut.ingredient.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import kr.mut.common.entity.BaseEntity

/**
 * `INV-INGREDIENT-02` · `FR-INGREDIENT-004` — 브랜드 언급의 광고성 구분.
 *
 * ## `isSponsored` 에 "모름"이 없다
 *
 * `NOT NULL` 이고 기본이 `false` 다. "정해지지 않음"이라는 상태가 있으면 라벨을 붙일지
 * 결정할 수 없고, 그 순간 공정위 의무를 어길 여지가 생긴다 (`INV-PARTNER-04` 와 같은 취지).
 *
 * ## ⚠️ Phase 1a 에서 켜지 않는다
 *
 * `true` 로 켜는 순간 ADR-0004 가 지목한 **주류 광고 규제 접점**이 생기고
 * `NFR-L-05`(주류광고 자문)가 선행돼야 한다. 컬럼과 플래그는 만들되 데이터는 전부 `false` 다.
 */
@Entity
@Table(name = "ingredient_brand")
class IngredientBrand(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false, updatable = false)
    val ingredient: Ingredient,

    @Column(name = "name", nullable = false, length = 80)
    var name: String,

    @Column(name = "purchase_url")
    var purchaseUrl: String? = null,

    @Column(name = "is_sponsored", nullable = false)
    var isSponsored: Boolean = false,
) : BaseEntity() {

    /**
     * 라벨을 붙여야 하는가. 표현은 FE 가 하지만 **판단은 서버가 내린다** —
     * 클라이언트에 맡기면 붙이지 않는 클라이언트가 생긴다.
     */
    val requiresAdLabel: Boolean get() = isSponsored
}
