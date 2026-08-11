package kr.kcocktail.cocktail.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import kr.kcocktail.common.taxonomy.StyleKey
import java.io.Serializable

/**
 * 축 2 · 스타일 (복수). 배열이 아니라 조인 테이블인 이유는 SPEC-06 §1.4 참조.
 *
 * [kr.kcocktail.common.entity.BaseEntity] 를 상속하지 않는다 — 복합 PK 인 연관 테이블이라
 * 대리키를 붙이면 같은 조합이 두 번 들어간다.
 */
@Entity
@Table(name = "cocktail_style")
@IdClass(CocktailStyleId::class)
class CocktailStyle(
    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cocktail_id", nullable = false)
    val cocktail: Cocktail,

    @Id
    @Column(name = "style", nullable = false, length = 24)
    val styleSlug: String,
) {
    val style: StyleKey get() = StyleKey.ofSlug(styleSlug)
}

data class CocktailStyleId(val cocktail: Long = 0, val styleSlug: String = "") : Serializable
