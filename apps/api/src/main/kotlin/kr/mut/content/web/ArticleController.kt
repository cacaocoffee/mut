package kr.mut.content.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import kr.mut.content.api.ArticleDetail
import kr.mut.content.api.ArticleFacade
import kr.mut.content.api.ArticleSummary
import kr.mut.common.web.ApiPaths
import kr.mut.common.web.error.ResourceNotFoundException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 공개 아티클 (ADR-0011 · SPEC-07 §2). **발행분만** 나간다 — draft·archived 는 404.
 *
 * 재료 사전과 같다: 콘텐츠라 색인 가치가 있어 `noindex` 를 붙이지 않고,
 * 캐시·ETag 는 공개 조회 필터가 이미 붙인다.
 */
@RestController
@RequestMapping("${ApiPaths.BASE}/articles")
class ArticleController(private val articles: ArticleFacade) {

    @GetMapping
    @Operation(summary = "아티클 목록 (발행분)", description = "category 로 거른다 (cocktail·bar·spirits).")
    fun list(
        @Parameter(description = "카테고리 슬러그. 없으면 전부")
        @RequestParam(required = false) category: String?,
    ): List<ArticleSummary> = articles.listPublished(category)

    @GetMapping("/{slug}")
    @Operation(summary = "아티클 상세 (발행분)")
    fun detail(@PathVariable slug: String): ArticleDetail =
        articles.findPublished(slug) ?: throw ResourceNotFoundException()
}
