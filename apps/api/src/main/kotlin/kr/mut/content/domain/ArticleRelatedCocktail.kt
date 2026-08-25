package kr.mut.content.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable

/**
 * 아티클 ↔ 칵테일 링크 (SPEC-06 §3.6 · V028). 상세의 "이 글의 칵테일".
 * `cocktail_id` 는 cocktail(id) FK 라 없는 칵테일을 가리키지 못한다. `position` 이 노출 순서.
 */
@Entity
@Table(name = "article_related_cocktail")
@IdClass(ArticleRelatedCocktailId::class)
class ArticleRelatedCocktail(
    @Id
    @Column(name = "article_id")
    var articleId: Long,

    @Id
    @Column(name = "cocktail_id")
    var cocktailId: Long,

    @Column(nullable = false)
    var position: Short = 0,
)

data class ArticleRelatedCocktailId(
    var articleId: Long = 0,
    var cocktailId: Long = 0,
) : Serializable
