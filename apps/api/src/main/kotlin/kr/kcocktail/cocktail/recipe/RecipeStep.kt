package kr.kcocktail.cocktail.recipe

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.io.Serializable

/** 만드는 순서. PK 가 `(recipe_id, step_no)` 라 번호가 곧 순서다. */
@Entity
@Table(name = "recipe_step")
@IdClass(RecipeStepId::class)
class RecipeStep(
    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    val recipe: Recipe,

    @Id
    @Column(name = "step_no", nullable = false)
    val stepNo: Short,

    @Column(name = "text", nullable = false)
    var text: String,

    /** 툴팁 용어 키 (`FR-COCKTAIL-022`, P1). 선택이다. */
    @Column(name = "technique_ref", length = 40)
    var techniqueRef: String? = null,
)

data class RecipeStepId(val recipe: Long = 0, val stepNo: Short = 0) : Serializable
