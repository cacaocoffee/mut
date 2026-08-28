package kr.mut.content.api

import java.time.Instant

/**
 * 아티클 모듈의 바깥 계약 (CONVENTIONS §4 — api 만 외부에서 참조 가능).
 * 공개 조회와 어드민 편집이 이 인터페이스·DTO 를 공유한다.
 */
interface ArticleFacade {
    /** 공개 목록 — 발행분만. */
    fun listPublished(category: String?): List<ArticleSummary>

    /** 공개 상세 — 발행분만. 없거나 미발행이면 null. */
    fun findPublished(slug: String): ArticleDetail?

    /**
     * 공개 요약 하나 — 발행분만. 없거나 미발행이면 null.
     *
     * 북마크가 저장할 때 이 요약을 읽어 발행 여부와 제목을 확인한다(USER ──reads──▶ CONTENT,
     * SPEC-05 §3). 본문(body)을 안 싣는 요약이라 상세(findPublished)보다 가볍다.
     */
    fun findPublishedSummary(slug: String): ArticleSummary?

    /**
     * 공개 요약 여럿을 id 로 — 발행분만. 못 찾은 것은 빠진다.
     *
     * 북마크 목록이 저장한 아티클을 한 번에 되읽는다(N+1 회피). 저장 시점엔 있었는데
     * 지금 미발행·삭제된 것은 목록에서 조용히 빠진다(BookmarkService.resolveAll 규약).
     */
    fun findPublishedSummariesByIds(ids: Collection<Long>): List<ArticleSummary>

    /** 어드민 목록 — draft 포함. */
    fun listForAdmin(status: String?): List<ArticleSummary>

    /** 어드민 상세 — draft 포함. */
    fun findForAdmin(id: Long): ArticleDetail?

    fun create(request: ArticleWrite): ArticleDetail

    fun update(id: Long, request: ArticleWrite): ArticleDetail

    /** 상태 전이. 허용되지 않으면 던진다. */
    fun transition(id: Long, to: String): ArticleDetail
}

/** 목록 카드에 필요한 것만. 본문(body)은 싣지 않는다. */
data class ArticleSummary(
    val id: Long,
    val slug: String,
    val category: String,
    val title: String,
    val dek: String,
    val hero: String,
    val isSponsored: Boolean,
    val status: String,
    val publishedAt: Instant?,
)

/** 상세 — 본문과 관련 칵테일까지. */
data class ArticleDetail(
    val id: Long,
    val slug: String,
    val category: String,
    val title: String,
    val dek: String,
    val hero: String,
    val sourceUrl: String?,
    val isSponsored: Boolean,
    val body: List<Map<String, Any?>>,
    val relatedCocktails: List<RelatedCocktail>,
    val status: String,
    val publishedAt: Instant?,
)

data class RelatedCocktail(
    val slug: String,
    val nameKo: String,
    val nameEn: String,
)

/**
 * 생성·수정 요청. slug 는 생성 때만 쓰이고 발행 뒤에는 못 바꾼다 (PRIN-D02) —
 * 서비스가 무시한다. 관련 칵테일은 slug 목록으로 받아 서비스가 id 로 잇는다.
 */
data class ArticleWrite(
    val slug: String,
    val category: String,
    val title: String,
    val dek: String,
    val hero: String,
    val sourceUrl: String? = null,
    val isSponsored: Boolean = false,
    val body: List<Map<String, Any?>> = emptyList(),
    val relatedCocktailSlugs: List<String> = emptyList(),
)
