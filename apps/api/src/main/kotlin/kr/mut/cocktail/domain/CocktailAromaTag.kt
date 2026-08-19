package kr.mut.cocktail.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import kr.mut.common.taxonomy.FlavorKey
import java.io.Serializable

/**
 * 향·맛 태그 1~3개 (`INV-COCKTAIL-04` · `R-F1.2-1`).
 *
 * **카테고리가 아니라 필터다** (`PRIN-P06`). 경로가 되지 않고 색인하지 않는다.
 */
@Entity
@Table(name = "cocktail_aroma_tag")
@IdClass(CocktailAromaTagId::class)
class CocktailAromaTag(
    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cocktail_id", nullable = false)
    val cocktail: Cocktail,

    @Id
    @Column(name = "aroma_tag", nullable = false, length = 24)
    val tagSlug: String,
) {
    val tag: FlavorKey get() = FlavorKey.ofSlug(tagSlug)
}

data class CocktailAromaTagId(val cocktail: Long = 0, val tagSlug: String = "") : Serializable
