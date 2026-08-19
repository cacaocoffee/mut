package kr.mut.cocktail.recipe

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.io.Serializable
import java.math.BigDecimal

/**
 * 레시피의 재료 한 줄 (SPEC-02 §2.7).
 *
 * ## 재료는 참조다 (`PRIN-D01`)
 *
 * [ingredientId] 가 `NOT NULL` 이고 **프리텍스트 컬럼이 없다.**
 * 문자열로 두면 역검색(내 술장)과 바 연결이 전부 불가능해진다 (`R-F1.1-1`).
 *
 * `ingredient` 엔티티를 잡지 않고 **id 만** 들고 있는 것은 모듈 경계 때문이다 —
 * `cocktail` 이 `ingredient.domain` 을 참조하면 경계 테스트가 막는다 (`PRIN-T03`).
 * 재료 정보가 필요하면 `IngredientFacade` 로 조회한다.
 */
@Entity
@Table(name = "recipe_ingredient")
@IdClass(RecipeIngredientId::class)
class RecipeIngredient(
    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    val recipe: Recipe,

    @Id
    @Column(name = "position", nullable = false)
    val position: Short,

    @Column(name = "ingredient_id", nullable = false)
    val ingredientId: Long,

    @Column(name = "amount", precision = 6, scale = 2)
    var amount: BigDecimal? = null,

    @Column(name = "unit", length = 12)
    private var unitSlug: String? = null,

    /**
     * `1조각` 처럼 **배수 계산에서 제외**하는 표기 (`FR-COCKTAIL-019`).
     * 잔 수를 2배로 해도 "1조각"이 "2조각"이 되지 않는다.
     */
    @Column(name = "amount_label", length = 40)
    var amountLabel: String? = null,

    @Column(name = "role", length = 16)
    private var roleSlug: String? = null,

    @Column(name = "is_optional", nullable = false)
    var isOptional: Boolean = false,

    @Column(name = "substitute_ingredient_id")
    var substituteIngredientId: Long? = null,

    @Column(name = "substitute_note")
    var substituteNote: String? = null,

    /**
     * `R-F2.2-5` — 역검색 판정 대상인가.
     *
     * 기본값은 재료 카테고리에서 온다 (`IngredientFacade.defaultCountsForStock`).
     * **레시피가 덮어쓸 수 있다** — 가니시가 그 칵테일의 정체성인 경우가 있다.
     */
    @Column(name = "counts_for_stock", nullable = false)
    var countsForStock: Boolean = true,
) {
    var unit: MeasureUnit?
        get() = unitSlug?.let(MeasureUnit::ofSlug)
        set(value) { unitSlug = value?.slug }

    var role: IngredientRole?
        get() = roleSlug?.let(IngredientRole::ofSlug)
        set(value) { roleSlug = value?.slug }

    /**
     * `FR-COCKTAIL-019` — 잔 수를 바꿀 때 이 줄의 수량을 배수로 곱할 것인가.
     *
     * **환산 자체는 FE(이슈 043)가 한다.** 여기서는 "무엇이 배수 대상인가"의 판정만 제공한다 —
     * 그래야 FE 와 어드민 미리보기가 같은 규칙을 쓴다. 두 곳이 다르게 판단하면
     * 에디터가 미리보기에서 본 것과 사용자가 보는 것이 달라진다.
     */
    val isScalable: Boolean get() = isScalable(amount, amountLabel)

    companion object {
        /**
         * 순수 술어다. 엔티티 없이 부를 수 있어야 배치 검증(016)과
         * 어드민 미리보기가 같은 규칙을 쓴다.
         */
        fun isScalable(amount: BigDecimal?, amountLabel: String?): Boolean =
            amountLabel == null && amount != null
    }
}

data class RecipeIngredientId(val recipe: Long = 0, val position: Short = 0) : Serializable
