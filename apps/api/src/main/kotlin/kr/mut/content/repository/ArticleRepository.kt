package kr.mut.content.repository

import kr.mut.content.domain.Article
import kr.mut.content.domain.ArticleRelatedCocktail
import kr.mut.content.domain.ArticleRelatedCocktailId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ArticleRepository : JpaRepository<Article, Long> {

    fun findBySlug(slug: String): Article?

    /** 공개 목록 — 발행분만. 카테고리는 있으면 거른다. 최신 발행순. */
    @Query(
        """
        SELECT a FROM Article a
         WHERE a.status = 'published'
           AND (:category IS NULL OR a.category = cast(:category as string))
         ORDER BY a.publishedAt DESC, a.slug
        """,
    )
    fun findPublished(@Param("category") category: String?): List<Article>

    /** 공개 상세 — 발행분만. draft·archived 는 공개 경로에서 404 다 (SPEC-07 §5). */
    fun findBySlugAndStatus(slug: String, status: String): Article?

    /** 북마크 목록이 저장한 아티클을 id 로 한 번에 되읽는다 — 발행분만 (BookmarkService). */
    fun findByIdInAndStatus(ids: Collection<Long>, status: String): List<Article>

    /** 어드민 목록 — draft 포함. 최근에 손댄 것부터. */
    @Query(
        """
        SELECT a FROM Article a
         WHERE (:status IS NULL OR a.status = cast(:status as string))
         ORDER BY a.updatedAt DESC
        """,
    )
    fun findForAdmin(@Param("status") status: String?): List<Article>
}

/** 관련 칵테일 링크 저장소. 편집 때 갈아끼운다 — DELETE 가 열려 있다. */
interface ArticleRelatedCocktailRepository :
    JpaRepository<ArticleRelatedCocktail, ArticleRelatedCocktailId> {

    fun findByArticleIdOrderByPosition(articleId: Long): List<ArticleRelatedCocktail>

    fun deleteByArticleId(articleId: Long)

    /**
     * 상세가 보여 줄 관련 칵테일 (slug·이름). cocktail 테이블은 다른 모듈이라 엔티티로
     * 끌어오지 않고 네이티브로 필요한 칸만 읽는다 — 모듈 경계를 넘지 않는다.
     */
    @Query(
        value = """
        SELECT c.slug, c.name_ko, c.name_en
        FROM article_related_cocktail arc
        JOIN cocktail c ON c.id = arc.cocktail_id
        WHERE arc.article_id = :articleId
        ORDER BY arc.position
        """,
        nativeQuery = true,
    )
    fun findRelatedCocktails(@Param("articleId") articleId: Long): List<Array<Any>>

    /** 편집이 준 칵테일 slug 를 id 로 바꾼다. 없는 slug 는 빠진다 — FK 를 못 건다. */
    @Query(
        value = "SELECT id, slug FROM cocktail WHERE slug IN (:slugs)",
        nativeQuery = true,
    )
    fun resolveCocktailIds(@Param("slugs") slugs: Collection<String>): List<Array<Any>>
}
