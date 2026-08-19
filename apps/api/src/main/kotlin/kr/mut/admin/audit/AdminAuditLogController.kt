package kr.mut.admin.audit

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import jakarta.servlet.http.HttpServletRequest
import kr.mut.admin.content.AdminActor
import kr.mut.common.security.authz.Action
import kr.mut.common.web.ApiPaths
import kr.mut.common.web.page.PageQuery
import kr.mut.common.web.page.PageResponse
import kr.mut.common.web.page.SortableBy
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * 감사 로그 조회 (ISSUE-029 · SPEC-07 §2.7 · SPEC-08 §2).
 *
 * ## `admin` 만이다. 이 한 줄이 이 이슈의 전부다
 *
 * `editor` 에게 주지 않는다. 이유는 재료 승인(이슈 026)과 같은 뿌리다 —
 * SPEC-08 §2.2 가 권한 분리를 **중립성 장치**로 규정했다:
 *
 * > `editor` 는 큐레이션 리스트를 만드는 사람이고 `partner_tier` 는 매출과 직결된다.
 * > 한 사람이 둘 다 쥐면 `R-F3.3-3` 의 감시자가 사라진다.
 *
 * 감사 조회도 같다 — **감시받는 사람이 감시 기록을 보면 안 된다.**
 * 무엇이 기록되는지 아는 사람은 기록되지 않는 방법도 알게 된다.
 *
 * `Action.VIEW_AUDIT_LOG` 가 매트릭스에서 `admin` 뿐이라 (이슈 006), 여기서는 액션만 고른다.
 * `@PreAuthorize("hasRole('ADMIN')")` 로 적으면 SPEC-08 §2 표가 코드 두 곳에 생긴다.
 *
 * ## 쓰는 경로가 없다
 *
 * 수정·삭제 엔드포인트가 **의도적으로 부재한다** (RED 26·27). `PRIN-T08` 이 요구하는
 * "되돌릴 수 있어야 하고 다툼의 근거가 돼야 한다"는 고쳐 쓸 수 없을 때만 성립한다.
 * 앱 역할에 `UPDATE` · `DELETE` 권한 자체가 없다 (`V014`) — API 를 만들어도 DB 가 막는다.
 *
 * ## `NFR-D-04` 측정이 이 엔드포인트 하나로 된다
 *
 * `?action=slug_change_attempt` 의 `totalElements` 가 그 지표다.
 * 발행 후 슬러그는 못 바꾸지만(`INV-COCKTAIL-05`) **거부된 시도도 남긴다** —
 * 기록이 없으면 "즉시 조사"할 것이 없다.
 */
@RestController
@RequestMapping("${ApiPaths.ADMIN}/audit-logs")
class AdminAuditLogController(
    private val auditLogs: AuditLogService,
    private val actor: AdminActor,
) {

    @GetMapping
    @Operation(
        summary = "감사 로그 조회",
        description = "admin 만 가능하다 (SPEC-08 §2.2 — 감시받는 사람이 감시 기록을 보면 안 된다). " +
            "필터는 AND 로 묶이고 정렬은 최신순 고정이다.",
    )
    fun list(
        @Parameter(description = "cocktail · ingredient 같은 테이블 이름")
        @RequestParam(required = false) entityType: String?,

        @RequestParam(required = false) entityId: Long?,

        @Parameter(description = "publish · unpublish · archive · restore · approve · slug_change_attempt 등")
        @RequestParam(required = false) action: String?,

        @Parameter(description = "행위자 user id. 탈퇴해도 id 는 남는다 (SPEC-08 §5.3)")
        @RequestParam(required = false) actorUserId: Long?,

        @Parameter(description = "ISO-8601. 이 시각 이후")
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,

        @Parameter(description = "ISO-8601. 이 시각 이전")
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,

        @SortableBy("at") page: PageQuery,
        http: HttpServletRequest,
    ): PageResponse<AuditLogItem> {
        actor.require(http, Action.VIEW_AUDIT_LOG)

        return auditLogs.list(
            AuditLogFilter(entityType, entityId, action, actorUserId, from, to),
            page,
        )
    }
}
