package kr.mut.content.internal

import kr.mut.content.api.ArticleDetail
import kr.mut.content.api.ArticleFacade
import kr.mut.content.api.ArticleSummary
import kr.mut.content.api.ArticleWrite
import kr.mut.content.api.RelatedCocktail
import kr.mut.content.domain.Article
import kr.mut.content.domain.ArticleRelatedCocktail
import kr.mut.content.domain.ArticleStatus
import kr.mut.content.repository.ArticleRelatedCocktailRepository
import kr.mut.content.repository.ArticleRepository
import kr.mut.common.web.error.ConflictException
import kr.mut.common.web.error.ResourceNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

/**
 * 아티클 CRUD·상태 전이 (ADR-0011). 칵테일과 달리 발행 게이트가 없다 —
 * 아티클은 존재하면 발행할 수 있다.
 */
@Service
class ArticleService(
    private val articles: ArticleRepository,
    private val related: ArticleRelatedCocktailRepository,
    private val clock: Clock,
) : ArticleFacade {

    @Transactional(readOnly = true)
    override fun listPublished(category: String?): List<ArticleSummary> =
        articles.findPublished(category).map { it.toSummary() }

    @Transactional(readOnly = true)
    override fun findPublished(slug: String): ArticleDetail? =
        articles.findBySlugAndStatus(slug, ArticleStatus.PUBLISHED.slug)?.let { detail(it) }

    @Transactional(readOnly = true)
    override fun listForAdmin(status: String?): List<ArticleSummary> =
        articles.findForAdmin(status).map { it.toSummary() }

    @Transactional(readOnly = true)
    override fun findForAdmin(id: Long): ArticleDetail? =
        articles.findById(id).orElse(null)?.let { detail(it) }

    @Transactional
    override fun create(request: ArticleWrite): ArticleDetail {
        if (articles.findBySlug(request.slug) != null) {
            throw ConflictException("이미 있는 slug 입니다: ${request.slug}")
        }
        val saved = articles.save(
            Article(
                slug = request.slug,
                category = request.category,
                title = request.title,
                dek = request.dek,
                hero = request.hero,
                sourceUrl = request.sourceUrl,
                isSponsored = request.isSponsored,
                body = request.body,
                // 생성은 항상 draft 다 (칵테일과 같은 규약).
                status = ArticleStatus.DRAFT.slug,
            ),
        )
        replaceRelated(saved.id, request.relatedCocktailSlugs)
        return detail(saved)
    }

    @Transactional
    override fun update(id: Long, request: ArticleWrite): ArticleDetail {
        val a = articles.findById(id).orElseThrow { ResourceNotFoundException() }
        // slug·status 는 여기서 못 바꾼다 — 발행 뒤 slug 는 불변(PRIN-D02), 상태는 transition 만.
        a.category = request.category
        a.title = request.title
        a.dek = request.dek
        a.hero = request.hero
        a.sourceUrl = request.sourceUrl
        a.isSponsored = request.isSponsored
        a.body = request.body
        replaceRelated(id, request.relatedCocktailSlugs)
        return detail(a)
    }

    @Transactional
    override fun transition(id: Long, to: String): ArticleDetail {
        val a = articles.findById(id).orElseThrow { ResourceNotFoundException() }
        val from = ArticleStatus.ofSlug(a.status)
        val target = ArticleStatus.ofSlug(to)
        if (!ArticleStatus.isAllowed(from, target)) {
            throw ConflictException("허용되지 않는 상태 전이: ${from.slug} → ${target.slug}")
        }
        a.status = target.slug
        // 처음 발행할 때만 발행일을 찍는다. 되돌렸다 다시 올려도 최초 발행일을 지운다 —
        // 아티클은 원문 발행일(published_at)이 곧 정렬 키라 매번 갱신하지 않는다.
        if (target == ArticleStatus.PUBLISHED && a.publishedAt == null) {
            a.publishedAt = Instant.now(clock)
        }
        return detail(a)
    }

    /** 관련 칵테일을 통째로 다시 건다. 편집이 준 slug 중 코퍼스에 있는 것만 남는다. */
    private fun replaceRelated(articleId: Long, slugs: List<String>) {
        related.deleteByArticleId(articleId)
        if (slugs.isEmpty()) return
        val idBySlug = related.resolveCocktailIds(slugs)
            .associate { (it[1] as String) to (it[0] as Number).toLong() }
        slugs.forEachIndexed { i, slug ->
            val cocktailId = idBySlug[slug] ?: return@forEachIndexed
            related.save(ArticleRelatedCocktail(articleId, cocktailId, i.toShort()))
        }
    }

    private fun detail(a: Article): ArticleDetail = ArticleDetail(
        id = a.id,
        slug = a.slug,
        category = a.category,
        title = a.title,
        dek = a.dek,
        hero = a.hero,
        sourceUrl = a.sourceUrl,
        isSponsored = a.isSponsored,
        body = a.body,
        relatedCocktails = related.findRelatedCocktails(a.id).map {
            RelatedCocktail(it[0] as String, it[1] as String, it[2] as String)
        },
        status = a.status,
        publishedAt = a.publishedAt,
    )

    private fun Article.toSummary() = ArticleSummary(
        id = id,
        slug = slug,
        category = category,
        title = title,
        dek = dek,
        hero = hero,
        isSponsored = isSponsored,
        status = status,
        publishedAt = publishedAt,
    )
}
