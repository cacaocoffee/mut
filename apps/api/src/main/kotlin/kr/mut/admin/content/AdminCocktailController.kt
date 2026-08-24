package kr.mut.admin.content

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import kr.mut.cocktail.api.AdminCocktailListResponse
import kr.mut.cocktail.api.AdminCocktailResponse
import kr.mut.cocktail.api.CocktailAdminFacade
import kr.mut.cocktail.api.CreateCocktailRequest
import kr.mut.cocktail.api.PublishResponse
import kr.mut.cocktail.api.UpdateCocktailRequest
import kr.mut.common.security.authz.Action
import kr.mut.common.web.ApiPaths
import kr.mut.common.web.error.ValidationProblemResponse
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
 * 어드민 칵테일 (ISSUE-025 · SPEC-07 §2.1 · SPEC-08 §2).
 *
 * ## 이 이슈의 목표는 `NFR-O-01` 하나다
 *
 * > 에디터가 개발자 없이 발행 — **신규 1건을 어드민만으로 완료**
 *
 * 생성 → 레시피 등록 → 게이트 통과 → 발행이 **마이그레이션·배포 없이** 되어야 한다.
 *
 * ## 이 컨트롤러가 하는 일은 인증과 HTTP 뿐이다
 *
 * 칵테일을 아는 코드는 전부 `cocktail` 모듈에 있다 ([CocktailAdminFacade]).
 * `admin` 이 엔티티나 리포지토리를 직접 보면 경계 테스트가 막는다 —
 * 방향표상 `ADMIN ──governs──▶ 전부` 지만 **경로는 `api` 뿐**이다 (RED 3).
 *
 * 처음에 서비스를 `admin/content` 에 두었다가 그 규칙에 걸렸다. 규칙이 옳다 —
 * 어드민이 도메인 내부를 알면 도메인 규칙이 어드민에도 생긴다.
 *
 * ## 게이트 실패는 422 이고 violations 를 전부 담는다
 *
 * `FR-ADMIN-003` — "실패한 항목을 전부 한 번에 보여준다. **하나씩 고치게 하지 않는다**".
 * `DomainViolationException` 을 이슈 003 의 핸들러가 옮긴다. 컨트롤러가 잡아서
 * 다시 만들지 않는다 — 그러면 응답 형태가 두 곳에 생긴다.
 *
 * ## 어드민은 `id` 를 쓴다
 *
 * 공개 경로는 `slug` 다 (SPEC-07 §1.1). 어드민이 `slug` 를 쓰면 **발행 전에 슬러그를
 * 고치는 순간 경로가 바뀐다** — 편집 화면이 열려 있는 채로 URL 이 죽는다.
 */
@RestController
@RequestMapping("${ApiPaths.ADMIN}/cocktails")
class AdminCocktailController(
    private val cocktails: CocktailAdminFacade,
    private val actor: AdminActor,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "칵테일 생성", description = "editor 이상. 생성 시점은 항상 draft 다.")
    fun create(
        @Valid @RequestBody request: CreateCocktailRequest,
        http: HttpServletRequest,
    ): AdminCocktailResponse {
        actor.require(http, Action.WRITE_CONTENT)
        return cocktails.create(request)
    }

    @PatchMapping("/{id}")
    @Operation(
        summary = "칵테일 수정",
        description = "status·publishedAt 은 요청 타입에 없다. 발행은 전용 엔드포인트만이 한다 (PRIN-T05).",
    )
    fun update(
        @PathVariable id: Long,
        @RequestBody request: UpdateCocktailRequest,
        http: HttpServletRequest,
    ): AdminCocktailResponse {
        actor.require(http, Action.WRITE_CONTENT)
        return cocktails.update(id, request)
    }

    /** `draft` 조회는 어드민 경로에서만 된다. 공개 경로는 여전히 404 다 (이슈 020 RED 3). */
    @GetMapping("/{id}")
    @Operation(summary = "칵테일 조회 (draft 포함)")
    fun find(@PathVariable id: Long, http: HttpServletRequest): AdminCocktailResponse {
        actor.require(http, Action.VIEW_DRAFT)
        return cocktails.find(id)
    }

    /**
     * 목록 (`FR-ADMIN-002`). 웹 어드민 목록 화면(ISSUE-047)이 이 경로를 읽는데,
     * 화면이 먼저 만들어지고 경로가 없었다 — 실배포에서야 드러난 공백이다 (2026-08-24).
     */
    @GetMapping
    @Operation(
        summary = "칵테일 목록 (draft 포함)",
        description = "editor 이상. 최근에 손댄 것부터. status 로 거른다 (draft·published·archived).",
    )
    fun list(
        @Parameter(description = "상태 슬러그. 없으면 전부") @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "100") size: Int,
        http: HttpServletRequest,
    ): AdminCocktailListResponse {
        actor.require(http, Action.VIEW_DRAFT)
        return cocktails.list(status, size)
    }

    @PostMapping("/{id}/publish")
    @Operation(
        summary = "발행",
        description = "게이트 실패는 422 + violations 전부. 이미 발행됐으면 409.",
    )
    // 실패 응답의 모양을 계약에 싣는다 (G-39). 이것이 없으면 생성 TS 에 `violations` 가
    // 안 나오고, 프론트가 손으로 적은 타입은 서버가 필드명을 바꿔도 안 깨진다.
    @ApiResponse(
        responseCode = "422",
        description = "발행 게이트 실패. violations 에 남은 조건이 전부 담긴다",
        content = [Content(schema = Schema(implementation = ValidationProblemResponse::class))],
    )
    fun publish(@PathVariable id: Long, http: HttpServletRequest): PublishResponse {
        actor.require(http, Action.PUBLISH)
        return cocktails.publish(id).toPublishResponse()
    }

    /** 회수. **게이트를 검사하지 않는다** — 내리는 데 조건을 걸 이유가 없다. */
    @PostMapping("/{id}/unpublish")
    @Operation(summary = "회수", description = "published → draft. 게이트를 검사하지 않는다.")
    fun unpublish(@PathVariable id: Long, http: HttpServletRequest): PublishResponse {
        actor.require(http, Action.PUBLISH)
        return cocktails.unpublish(id).toPublishResponse()
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "보관", description = "published → archived.")
    fun archive(@PathVariable id: Long, http: HttpServletRequest): PublishResponse {
        actor.require(http, Action.PUBLISH)
        return cocktails.archive(id).toPublishResponse()
    }
}

private fun AdminCocktailResponse.toPublishResponse() = PublishResponse(slug, status, publishedAt)
