package kr.mut.admin.content

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import jakarta.servlet.http.HttpServletRequest
import kr.mut.content.api.ArticleDetail
import kr.mut.content.api.ArticleFacade
import kr.mut.content.api.ArticleSummary
import kr.mut.content.api.ArticleWrite
import kr.mut.common.security.authz.Action
import kr.mut.common.web.ApiPaths
import kr.mut.common.web.error.ResourceNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 어드민 아티클 (ADR-0011 · SPEC-08 §2). 칵테일 어드민과 같은 규약:
 * 목록·상세는 draft 포함, 저장은 editor 이상, 발행·삭제(archive)는 전용 경로.
 *
 * 이 컨트롤러는 인증과 HTTP 만 한다 — 아티클을 아는 코드는 content 모듈이다 (경계 규칙).
 */
@RestController
@RequestMapping("${ApiPaths.ADMIN}/articles")
class AdminArticleController(
    private val articles: ArticleFacade,
    private val actor: AdminActor,
) {

    @GetMapping
    @Operation(summary = "아티클 목록 (draft 포함)", description = "editor 이상. status 로 거른다.")
    fun list(
        @Parameter(description = "상태 슬러그. 없으면 전부") @RequestParam(required = false) status: String?,
        http: HttpServletRequest,
    ): List<ArticleSummary> {
        actor.require(http, Action.VIEW_DRAFT)
        return articles.listForAdmin(status)
    }

    @GetMapping("/{id}")
    @Operation(summary = "아티클 조회 (draft 포함)")
    fun find(@PathVariable id: Long, http: HttpServletRequest): ArticleDetail {
        actor.require(http, Action.VIEW_DRAFT)
        return articles.findForAdmin(id) ?: throw ResourceNotFoundException()
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "아티클 생성", description = "editor 이상. 생성 시점은 항상 draft.")
    fun create(@RequestBody request: ArticleWrite, http: HttpServletRequest): ArticleDetail {
        actor.require(http, Action.WRITE_CONTENT)
        return articles.create(request)
    }

    @PatchMapping("/{id}")
    @Operation(summary = "아티클 수정", description = "status 는 요청에 없다 — 발행은 전용 경로만 (PRIN-T05).")
    fun update(
        @PathVariable id: Long,
        @RequestBody request: ArticleWrite,
        http: HttpServletRequest,
    ): ArticleDetail {
        actor.require(http, Action.WRITE_CONTENT)
        return articles.update(id, request)
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "발행", description = "draft → published. 아티클은 발행 게이트가 없다.")
    fun publish(@PathVariable id: Long, http: HttpServletRequest): ArticleDetail {
        actor.require(http, Action.PUBLISH)
        return articles.transition(id, "published")
    }

    @PostMapping("/{id}/unpublish")
    @Operation(summary = "회수 · 되돌리기", description = "published·archived → draft.")
    fun unpublish(@PathVariable id: Long, http: HttpServletRequest): ArticleDetail {
        actor.require(http, Action.PUBLISH)
        return articles.transition(id, "draft")
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "삭제 (보관)", description = "draft·published → archived. 목록에서 사라진다.")
    fun archive(@PathVariable id: Long, http: HttpServletRequest): ArticleDetail {
        actor.require(http, Action.PUBLISH)
        return articles.transition(id, "archived")
    }
}
