package kr.kcocktail.admin.task

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import kr.kcocktail.admin.content.AdminActor
import kr.kcocktail.common.security.authz.Action
import kr.kcocktail.common.web.ApiPaths
import kr.kcocktail.common.web.page.PageQuery
import kr.kcocktail.common.web.page.PageResponse
import kr.kcocktail.common.web.page.SortableBy
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 검증 태스크 큐 (ISSUE-028 · SPEC-07 §2.7 · SPEC-08 §2).
 *
 * ## 이 큐가 없으면 `NFR-D-01` 이 성립하지 않는다
 *
 * 배치(이슈 016)가 일 1회 전수 스캔해 불변식 위반을 쌓는다. **찾기만 하고 처리 창구가
 * 없으면 "위반 0건"은 목표가 아니라 희망이다** — 24종일 때는 눈으로 보이지만
 * 500종이 되면 이것 말고 확인할 방법이 없다 (SPEC-06 §4.3).
 *
 * ## 조회와 해소가 같은 권한이다
 *
 * 둘 다 `editor` 다 (SPEC-08 §2 — 검증 태스크 처리). 재료 승인(이슈 026)과 다른데,
 * 그쪽은 **마스터를 오염시킬 수 있어서** 권한을 나눴다. 여기서 닫는 것은 이미 일어난
 * 위반의 처리 표시라 중립성과 무관하다.
 */
@RestController
@RequestMapping("${ApiPaths.ADMIN}/tasks")
class AdminTaskController(
    private val tasks: VerificationTaskService,
    private val actor: AdminActor,
) {

    @GetMapping
    @Operation(
        summary = "검증 태스크 큐",
        description = "기본은 open 만. 정렬은 최근 탐지순 고정이다 (인덱스가 그 순서다).",
    )
    fun list(
        @Parameter(description = "open · resolved · dismissed. 기본 open")
        @RequestParam(required = false) status: String?,

        @Parameter(description = "invariant_violation · gate_bypass · slug_changed (1b: hours_expired · instagram_signal)")
        @RequestParam(required = false) taskType: String?,

        @Parameter(description = "cocktail · ingredient")
        @RequestParam(required = false) entityType: String?,

        @SortableBy("detectedAt") page: PageQuery,
        http: HttpServletRequest,
    ): PageResponse<VerificationTaskItem> {
        actor.require(http, Action.RESOLVE_TASK)
        return tasks.list(status, taskType, entityType, page)
    }

    @GetMapping("/{id}")
    @Operation(summary = "태스크 상세")
    fun find(@PathVariable id: Long, http: HttpServletRequest): VerificationTaskItem {
        actor.require(http, Action.RESOLVE_TASK)
        return tasks.find(id)
    }

    /**
     * 해소. `dismiss=true` 면 **사유가 필수**다 (RED 18).
     *
     * 이미 닫힌 태스크는 409 다 — 멱등하게 넘기면 "누가 언제 닫았나"가 나중 사람의 것으로 덮인다.
     */
    @PostMapping("/{id}/resolve")
    @Operation(
        summary = "태스크 해소",
        description = "dismiss=true 는 사유 필수. 이미 처리된 태스크는 409. 감사에는 남기지 않는다 (테이블 자체가 이력).",
    )
    fun resolve(
        @PathVariable id: Long,
        @Valid @RequestBody(required = false) request: ResolveTaskRequest?,
        http: HttpServletRequest,
    ): VerificationTaskItem {
        val who = actor.require(http, Action.RESOLVE_TASK)
        return tasks.resolve(id, request ?: ResolveTaskRequest(), who.userId)
    }
}
