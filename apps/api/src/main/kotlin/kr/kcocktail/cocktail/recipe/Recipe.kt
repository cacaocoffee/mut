package kr.kcocktail.cocktail.recipe

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import kr.kcocktail.cocktail.domain.Cocktail
import kr.kcocktail.common.entity.BaseEntity

/**
 * 레시피 (SPEC-02 §2.6 · SPEC-06 §3.1).
 *
 * ## 애그리게이트 경계 = 트랜잭션 경계 (SPEC-02 §1)
 *
 * 칵테일 없는 레시피는 없다. FK 가 `NOT NULL` 이라 DB 가 막는다.
 *
 * ## 표준은 정확히 1개다
 *
 * `INV-COCKTAIL-07` 을 **부분 유니크 인덱스**가 막는다 (`uq_recipe__standard`).
 * `WHERE version_type = 'standard'` 절이 없으면 `bar_signature` 도 하나로 묶여
 * `PRIN-D03` 의 "제휴 바 버전 n개"가 불가능해진다.
 */
@Entity
@Table(name = "recipe")
class Recipe(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cocktail_id", nullable = false, updatable = false)
    val cocktail: Cocktail,

    @Column(name = "version_type", nullable = false, length = 16)
    private var versionTypeSlug: String,

    /** Phase 1b 에 `bar` FK 가 붙는다. `bar_signature` 면 필수 (`ck_recipe__author`). */
    @Column(name = "author_bar_id")
    var authorBarId: Long? = null,

    /** `user` 타입이면 필수. v2. */
    @Column(name = "author_user_id")
    var authorUserId: Long? = null,

    /** DECISIONS §1.2 — 잔 수는 1~8. 임의값이지만 UI 가 그 범위를 쓴다. */
    @Column(name = "serving_count", nullable = false)
    var servingCount: Short = 1,

    @Column(name = "note")
    var note: String? = null,
) : BaseEntity() {

    @OneToMany(mappedBy = "recipe", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("stepNo ASC")
    private val stepRows: MutableList<RecipeStep> = mutableListOf()

    @OneToMany(mappedBy = "recipe", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("position ASC")
    private val ingredientRows: MutableList<RecipeIngredient> = mutableListOf()

    var versionType: RecipeVersionType
        get() = RecipeVersionType.ofSlug(versionTypeSlug)
        set(value) { versionTypeSlug = value.slug }

    val steps: List<RecipeStep> get() = stepRows.sortedBy { it.stepNo }

    val ingredients: List<RecipeIngredient> get() = ingredientRows.sortedBy { it.position }

    val isStandard: Boolean get() = versionType == RecipeVersionType.STANDARD

    /**
     * 스텝을 통째로 갈아 끼운다. 번호는 **1부터 연속**으로 다시 매긴다 —
     * 중간을 지웠을 때 구멍이 남으면 화면이 "1, 2, 4" 를 보여준다.
     */
    fun setSteps(texts: List<StepDraft>) {
        stepRows.clear()
        texts.forEachIndexed { index, draft ->
            stepRows += RecipeStep(
                recipe = this,
                stepNo = (index + 1).toShort(),
                text = draft.text,
                techniqueRef = draft.techniqueRef,
            )
        }
    }

    /** 재료도 마찬가지로 `position` 을 1부터 다시 매긴다. */
    fun setIngredients(drafts: List<IngredientDraft>) {
        ingredientRows.clear()
        drafts.forEachIndexed { index, draft ->
            ingredientRows += RecipeIngredient(
                recipe = this,
                ingredientId = draft.ingredientId,
                position = (index + 1).toShort(),
                amount = draft.amount,
                unitSlug = draft.unit?.slug,
                amountLabel = draft.amountLabel,
                roleSlug = draft.role?.slug,
                isOptional = draft.isOptional,
                substituteIngredientId = draft.substituteIngredientId,
                substituteNote = draft.substituteNote,
                countsForStock = draft.countsForStock,
            )
        }
    }

    data class StepDraft(val text: String, val techniqueRef: String? = null)

    /**
     * [countsForStock] 은 **호출자가 이미 정해서** 넘긴다.
     *
     * 기본값은 `IngredientFacade.defaultCountsForStock` 에서 오는데, 엔티티가 그것을
     * 직접 부르면 `cocktail` → `ingredient` 의존이 도메인 계층에 박힌다 (`PRIN-T03`).
     * 채우는 것은 [RecipeAssembler] 의 몫이다.
     */
    data class IngredientDraft(
        val ingredientId: Long,
        val countsForStock: Boolean,
        val amount: java.math.BigDecimal? = null,
        val unit: MeasureUnit? = null,
        val amountLabel: String? = null,
        val role: IngredientRole? = null,
        val isOptional: Boolean = false,
        val substituteIngredientId: Long? = null,
        val substituteNote: String? = null,
    )
}
