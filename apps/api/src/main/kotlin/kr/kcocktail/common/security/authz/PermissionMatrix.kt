package kr.kcocktail.common.security.authz

import kr.kcocktail.common.security.Role
import kr.kcocktail.common.security.Role.ADMIN
import kr.kcocktail.common.security.Role.EDITOR
import kr.kcocktail.common.security.Role.MEMBER
import kr.kcocktail.common.security.Role.PARTNER_OWNER

/**
 * SPEC-08 §2 권한 매트릭스. **표 하나 = 코드 한 곳** (ISSUE-006).
 *
 * `if` 문으로 흩뿌리지 않는다. 흩뿌리면 표를 고쳤을 때 어디를 같이 고쳐야 하는지 알 수 없고,
 * 전수 검증도 불가능해진다 — 30여 조합을 눈으로 대조하게 된다.
 *
 * ## 허용 목록이다
 *
 * [ALLOWED] 에 없는 조합은 **거부**다 (RED 4). 금지 목록으로 만들면 새 액션이 기본 허용이 되고,
 * 그 사실이 사고가 난 뒤에 드러난다.
 *
 * ## 역할은 누적되지 않는다
 *
 * `editor` 가 `admin` 권한을 갖지 않는다 (SPEC-08 §1). 계층이 아니라 **집합**이고,
 * 복수 역할은 **합집합**으로 평가된다 — `editor` + `admin` 이면 양쪽 다 된다.
 *
 * ## Phase 1a 범위
 *
 * `partner_owner` 열과 바 관련 6행은 **Phase 1b** 다 (사용자 결정 2026-08-07).
 * 바 도메인이 없어 `partner_owner` 가 소유할 대상 자체가 없다.
 * `Role.PARTNER_OWNER` 와 1b 액션은 enum 에 **정의만** 해 둔다 — 나중에 늘리면 클라이언트가 깨진다.
 */
object PermissionMatrix {

    /**
     * SPEC-08 §2 표를 그대로 옮긴 것. **이 맵이 표다.**
     *
     * 비로그인은 역할이 없는 상태라 [ANONYMOUS] 로 따로 적는다 —
     * 표의 첫 열이 "비로그인"이라서 역할 집합으로는 표현되지 않는다.
     */
    private val ALLOWED: Map<Action, Set<Role>> = mapOf(
        // 발행된 콘텐츠 조회 — 전원 ○
        Action.VIEW_PUBLISHED to setOf(MEMBER, EDITOR, PARTNER_OWNER, ADMIN),

        // draft · archived 조회 — editor · admin
        Action.VIEW_DRAFT to setOf(EDITOR, ADMIN),
        Action.VIEW_ARCHIVED to setOf(EDITOR, ADMIN),

        // 칵테일 · 재료 생성/수정
        Action.WRITE_CONTENT to setOf(EDITOR, ADMIN),

        // 발행 / 회수
        Action.PUBLISH to setOf(EDITOR, ADMIN),

        // 재료 마스터 승인 — admin 만 (DECISIONS §1: 에디터가 요청하고 관리자가 승인)
        Action.APPROVE_INGREDIENT to setOf(ADMIN),

        // 검증 태스크 처리
        Action.RESOLVE_TASK to setOf(EDITOR, ADMIN),

        // 감사 로그 조회 — admin 만
        Action.VIEW_AUDIT_LOG to setOf(ADMIN),

        // 북마크 · 컬렉션 — 로그인한 전원, 단 자기 것만 (◐)
        Action.OWN_BOOKMARK to setOf(MEMBER, EDITOR, PARTNER_OWNER, ADMIN),
    )

    /** 비로그인이 할 수 있는 것. 발행된 콘텐츠 조회 하나뿐이다. */
    private val ANONYMOUS: Set<Action> = setOf(Action.VIEW_PUBLISHED)

    /** `◐` 인 액션 — 소유자 본인만. 남의 것이면 존재를 흘리지 않는다. */
    private val OWNER_SCOPED: Set<Action> = setOf(Action.OWN_BOOKMARK, Action.OWN_STOCK)

    /**
     * 판정.
     *
     * @param roles 빈 집합이면 비로그인이다
     * @param actorId 로그인 사용자 id. `◐` 판정에 쓴다
     */
    fun evaluate(
        roles: Set<Role>,
        action: Action,
        scope: Scope = Scope.Anything,
        actorId: Long? = null,
    ): Decision {
        require(action.isPhase1a) {
            "Phase 1a 매트릭스에 없는 액션이다: $action (EPICS-1B-PHASE2.md 참조)"
        }
        require(scope !is Scope.OwnBar) {
            "bar 스코프는 Phase 1b 다 (EPICS-1B-PHASE2.md 1B-E8). 지금 평가하면 검증되지 않은 판정을 믿게 된다"
        }

        val permitted =
            if (roles.isEmpty()) action in ANONYMOUS
            else roles.any { it in ALLOWED[action].orEmpty() } // 합집합

        if (!permitted) return deny(action)

        // ◐ — 남의 것에는 손댈 수 없고, 그 사실도 알려 주지 않는다.
        if (action in OWNER_SCOPED && scope is Scope.Own && scope.ownerId != actorId) {
            return Decision.Denied.Hidden
        }

        return Decision.Allowed
    }

    fun allows(roles: Set<Role>, action: Action, scope: Scope = Scope.Anything, actorId: Long? = null) =
        evaluate(roles, action, scope, actorId).isAllowed

    private fun deny(action: Action): Decision.Denied = when (action.denial) {
        DenialMode.HIDE -> Decision.Denied.Hidden
        DenialMode.FORBID -> Decision.Denied.Forbidden
    }

    /** 테스트가 표 전체를 훑을 수 있게 열어 둔다. 전수 검증이 이 이슈의 요체다. */
    internal fun rolesFor(action: Action): Set<Role> = ALLOWED[action].orEmpty()

    internal fun anonymousActions(): Set<Action> = ANONYMOUS
}
