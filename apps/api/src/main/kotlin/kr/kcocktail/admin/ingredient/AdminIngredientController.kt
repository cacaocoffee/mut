package kr.kcocktail.admin.ingredient

import io.swagger.v3.oas.annotations.Operation
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import kr.kcocktail.admin.content.AdminActor
import kr.kcocktail.common.security.authz.Action
import kr.kcocktail.common.web.ApiPaths
import kr.kcocktail.ingredient.api.AdminIngredientResponse
import kr.kcocktail.ingredient.api.CreateIngredientRequest
import kr.kcocktail.ingredient.api.IngredientAdminFacade
import kr.kcocktail.ingredient.api.IngredientCapacity
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 어드민 재료 (ISSUE-026 · SPEC-07 §2.2 · SPEC-08 §2).
 *
 * ## 요점은 아래 두 [Action] 이 다르다는 것 하나다
 *
 * | 행위 | 액션 | 허용 |
 * |---|---|---|
 * | [create] | [Action.WRITE_CONTENT] | `editor` · `admin` |
 * | [approve] | [Action.APPROVE_INGREDIENT] | **`admin` 만** |
 *
 * SPEC-08 §2.2 가 권한 분리를 **중립성 장치**로 규정했다. 만드는 사람과 통과시키는 사람이
 * 같으면 승인제가 형식만 남는다 — 마스터 오염을 막는 게 `PRIN-D01` 의 전제인데
 * 그 방어선이 자기 검토가 된다.
 *
 * `FR-ADMIN-007` 은 "에디터 승인 단계"라고 적었지만 SPEC-07 §1.3 이 스코프를 SPEC-08 로
 * **명시적으로 위임**했고 그 표가 `admin` 이다. "에디터가 만들고 관리자가 승인"의 축약으로
 * 읽는다 (GAPS G-29 · DECISIONS §1.1).
 *
 * ## 판정은 매트릭스가 하고 여기서는 액션만 고른다
 *
 * `@PreAuthorize("hasRole('ADMIN')")` 로 적으면 SPEC-08 §2 표가 코드 두 곳에 생긴다.
 * 표를 고쳤을 때 어디를 같이 고쳐야 하는지 알 수 없어지고, 이슈 006 의 전수 검증도
 * 이 어노테이션까지는 훑지 못한다. **표는 [kr.kcocktail.common.security.authz.PermissionMatrix]
 * 한 곳이다.**
 *
 * ## 이 컨트롤러가 하는 일은 인증과 HTTP 뿐이다
 *
 * 재료를 아는 코드는 전부 `ingredient` 모듈에 있다 ([IngredientAdminFacade]).
 * `admin` 이 엔티티나 리포지토리를 직접 보면 경계 테스트(이슈 001)가 막는다 —
 * 방향표상 `ADMIN ──governs──▶ 전부` 지만 **경로는 `api` 뿐**이다.
 *
 * ## 어드민은 `id` 를 쓴다
 *
 * 공개 경로는 `slug` 다 (SPEC-07 §1.1 · 이슈 023). 승인 전 재료의 슬러그는 아직 고칠 수 있고,
 * 고치는 순간 열려 있던 편집 화면의 URL 이 죽는다.
 */
@RestController
@RequestMapping("${ApiPaths.ADMIN}/ingredients")
class AdminIngredientController(
    private val ingredients: IngredientAdminFacade,
    private val actor: AdminActor,
) {

    /**
     * 생성. **항상 승인 대기다** (SPEC-07 §2.2).
     *
     * `isApproved` 를 요청 타입에 두지 않았다 — 서비스에서 걸러 내는 것보다 강하다.
     * 에디터가 스스로 승인 상태로 만들 수 있으면 승인제가 아무것도 막지 못한다.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "재료 생성", description = "editor 이상. 생성 시점은 항상 승인 대기(is_approved=false)다.")
    fun create(
        @Valid @RequestBody request: CreateIngredientRequest,
        http: HttpServletRequest,
    ): AdminIngredientResponse {
        actor.require(http, Action.WRITE_CONTENT)
        return ingredients.create(request)
    }

    /**
     * 승인. **`admin` 만** (SPEC-08 §2 · RED 5·6).
     *
     * 재승인은 409 다 (DECISIONS §1.11) — 멱등하게 넘기면 "언제 통과했나"가 흐려진다.
     * **승인 취소는 제공하지 않는다** — 스펙에 없고, 이미 발행된 레시피가 참조 중일 수 있다.
     */
    @PostMapping("/{id}/approve")
    @Operation(
        summary = "재료 승인",
        description = "admin 만 가능하다 (SPEC-08 §2). 재승인은 409. 감사에 남는다.",
    )
    fun approve(@PathVariable id: Long, http: HttpServletRequest): AdminIngredientResponse {
        actor.require(http, Action.APPROVE_INGREDIENT)
        return ingredients.approve(id)
    }

    /** 미승인 포함 조회. 공개 사전(이슈 023)은 승인된 것만 내보낸다. */
    @GetMapping("/{id}")
    @Operation(summary = "재료 조회 (미승인 포함)")
    fun find(@PathVariable id: Long, http: HttpServletRequest): AdminIngredientResponse {
        actor.require(http, Action.WRITE_CONTENT)
        return ingredients.find(id)
    }

    /**
     * 승인 대기 큐 (RED 11). **`editor` 도 본다** — 자기가 올린 것이 어디까지 갔는지
     * 알아야 기다릴지 다른 걸 할지 정한다. 통과시키는 것만 `admin` 이다.
     */
    @GetMapping("/pending")
    @Operation(summary = "승인 대기 큐", description = "editor·admin. 이름순 고정.")
    fun pending(http: HttpServletRequest): List<AdminIngredientResponse> {
        actor.require(http, Action.WRITE_CONTENT)
        return ingredients.pending()
    }

    /**
     * 승인된 재료 수와 상한 (RED 17~20).
     *
     * `warning` 이 `true` 여도 승인은 된다 — SPEC-02 §3 의 상한 근거가 "역검색 UX"이지
     * 데이터 무결성이 아니다 (DECISIONS §1.2). 어드민 UI(이슈 045)가 이 값을 띄운다.
     */
    @GetMapping("/capacity")
    @Operation(summary = "승인 재료 수와 상한", description = "상한 초과는 경고다. 승인을 막지 않는다.")
    fun capacity(http: HttpServletRequest): IngredientCapacity {
        actor.require(http, Action.WRITE_CONTENT)
        return ingredients.capacity()
    }
}
