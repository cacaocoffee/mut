package kr.mut.common.security.authz

import kr.mut.common.security.Role
import kr.mut.common.security.Role.ADMIN
import kr.mut.common.security.Role.EDITOR
import kr.mut.common.security.Role.MEMBER
import kr.mut.common.web.error.ResourceNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.security.access.AccessDeniedException

/**
 * ISSUE-006 — 권한 매트릭스 (SPEC-08 §2).
 *
 * ## 표를 손으로 다시 적는다
 *
 * [table] 은 SPEC-08 §2 를 **문서에서 읽어 옮긴 것**이지 구현에서 가져온 것이 아니다.
 * `PermissionMatrix` 의 맵을 그대로 쓰면 검증이 아니라 동어반복이 된다 —
 * 표를 잘못 옮겨도 테스트는 초록이다.
 *
 * 옮겨 적는 비용이 이 테스트의 값어치다.
 */
class PermissionMatrixTest {

    /**
     * SPEC-08 §2 표 — Phase 1a 분.
     *
     * ```
     * — 불가 · ○ 가능 · ◐ 자기 것만
     * ```
     *
     * 취소선 6행과 `partner_owner` 열은 Phase 1b 라 여기 없다 (사용자 결정 2026-08-07).
     */
    private val table: List<Row> = listOf(
        //                              비로그인  member  editor  admin
        row(Action.VIEW_PUBLISHED, /**/ "○", /**/ "○", /**/ "○", /**/ "○"),
        row(Action.VIEW_DRAFT, /*    */ "—", /**/ "—", /**/ "○", /**/ "○"),
        row(Action.VIEW_ARCHIVED, /* */ "—", /**/ "—", /**/ "○", /**/ "○"),
        row(Action.WRITE_CONTENT, /* */ "—", /**/ "—", /**/ "○", /**/ "○"),
        row(Action.PUBLISH, /*       */ "—", /**/ "—", /**/ "○", /**/ "○"),
        row(Action.APPROVE_INGREDIENT, "—", /**/ "—", /**/ "—", /**/ "○"),
        row(Action.RESOLVE_TASK, /*  */ "—", /**/ "—", /**/ "○", /**/ "○"),
        row(Action.VIEW_AUDIT_LOG, /**/ "—", /**/ "—", /**/ "—", /**/ "○"),
        row(Action.OWN_BOOKMARK, /*  */ "—", /**/ "◐", /**/ "◐", /**/ "◐"),
    )

    // ── RED 1~5 : 매트릭스 전수 ────────────────────────────────────────────

    /** 9행 × 4열 = 36조합. 하나라도 어긋나면 어느 칸인지 나온다. */
    @Test
    fun `RED1 - 권한매트릭스 1a분 전수 검증`() {
        val checks: List<() -> Unit> = table.flatMap { row ->
            row.cells.map { (actor, cell) ->
                {
                    val allowed = PermissionMatrix.allows(
                        roles = actor.roles,
                        action = row.action,
                        scope = if (cell == "◐") Scope.Own(ME) else Scope.Anything,
                        actorId = ME,
                    )
                    assertThat(allowed)
                        .`as`("SPEC-08 §2 — %s × %s = %s", row.action, actor.label, cell)
                        .isEqualTo(cell != "—")
                }
            }
        }

        assertThat(checks).hasSize(36)
        assertAll(checks)
    }

    /** SPEC-08 §1 — `editor` 가 `admin` 권한을 갖지 않는다. 계층이 아니라 집합이다. */
    @Test
    fun `RED2 - 역할이 누적되지 않는다`() {
        val adminOnly = listOf(Action.APPROVE_INGREDIENT, Action.VIEW_AUDIT_LOG)

        assertAll(
            adminOnly.map<Action, () -> Unit> { action ->
                {
                    assertThat(PermissionMatrix.allows(setOf(EDITOR), action))
                        .`as`("editor 는 %s 를 못 한다", action)
                        .isFalse()
                    assertThat(PermissionMatrix.allows(setOf(ADMIN), action)).isTrue()
                }
            },
        )
    }

    @Test
    fun `RED3 - 복수역할 보유시 합집합으로 평가된다`() {
        val both = setOf(EDITOR, ADMIN)

        assertThat(PermissionMatrix.allows(both, Action.PUBLISH)).`as`("editor 쪽").isTrue()
        assertThat(PermissionMatrix.allows(both, Action.VIEW_AUDIT_LOG)).`as`("admin 쪽").isTrue()

        // member 를 더해도 잃는 것이 없다 — 합집합이지 교집합이 아니다.
        assertThat(PermissionMatrix.allows(both + MEMBER, Action.VIEW_AUDIT_LOG)).isTrue()
    }

    /** 허용 목록이다. 금지 목록으로 만들면 새 액션이 기본 허용이 된다. */
    @Test
    fun `RED4 - 매트릭스에 없는 조합은 기본 거부다`() {
        // member 는 표에서 ○ 가 하나도 없는 액션들에 대해 전부 거부여야 한다.
        val memberDenied = Action.entries
            .filter { it.isPhase1a }
            .filterNot { PermissionMatrix.allows(setOf(MEMBER), it, Scope.Own(ME), ME) }

        assertThat(memberDenied).containsExactlyInAnyOrder(
            Action.VIEW_DRAFT, Action.VIEW_ARCHIVED, Action.WRITE_CONTENT,
            Action.PUBLISH, Action.APPROVE_INGREDIENT, Action.RESOLVE_TASK,
            Action.VIEW_AUDIT_LOG,
        )
    }

    @Test
    fun `RED5 - 비로그인은 발행된 콘텐츠만 조회한다`() {
        val anonymousAllowed = Action.entries
            .filter { it.isPhase1a }
            .filter { PermissionMatrix.allows(emptySet(), it) }

        assertThat(anonymousAllowed).containsExactly(Action.VIEW_PUBLISHED)
    }

    // ── RED 6~10 : draft 격리 ──────────────────────────────────────────────

    /**
     * 403 이 아니라 **404** 다. 403 이면 "그 슬러그는 존재한다"가 새어 나간다 (SPEC-07 §1.4).
     */
    @Test
    fun `RED6-7 - draft 는 비로그인과 member 에게 404 다`() {
        assertAll(
            listOf(emptySet<Role>(), setOf(MEMBER)).map<Set<Role>, () -> Unit> { roles ->
                {
                    assertThat(PermissionMatrix.evaluate(roles, Action.VIEW_DRAFT))
                        .`as`("%s 에게 draft 는 없는 것과 같다", roles)
                        .isEqualTo(Decision.Denied.Hidden)
                }
            },
        )
    }

    @Test
    fun `RED8-9 - draft 는 editor 와 admin 에게 보인다`() {
        assertThat(PermissionMatrix.evaluate(setOf(EDITOR), Action.VIEW_DRAFT))
            .isEqualTo(Decision.Allowed)
        assertThat(PermissionMatrix.evaluate(setOf(ADMIN), Action.VIEW_DRAFT))
            .isEqualTo(Decision.Allowed)
    }

    /** SPEC-07 §5 — 폐기된 것도 공개 API 에서는 없는 것이다. */
    @Test
    fun `RED10 - archived 도 공개 API 에서 404 다`() {
        assertThat(PermissionMatrix.evaluate(emptySet(), Action.VIEW_ARCHIVED))
            .isEqualTo(Decision.Denied.Hidden)
        assertThat(PermissionMatrix.evaluate(setOf(MEMBER), Action.VIEW_ARCHIVED))
            .isEqualTo(Decision.Denied.Hidden)
    }

    // ── RED 11~14 : editor ≠ admin ────────────────────────────────────────

    @Test
    fun `RED11 - editor 는 재료 마스터를 승인할 수 없다`() {
        assertThat(PermissionMatrix.evaluate(setOf(EDITOR), Action.APPROVE_INGREDIENT))
            .`as`("어드민 엔드포인트는 존재를 숨길 것이 없다 — 403")
            .isEqualTo(Decision.Denied.Forbidden)
    }

    @Test
    fun `RED12 - editor 는 감사로그를 조회할 수 없다`() {
        assertThat(PermissionMatrix.evaluate(setOf(EDITOR), Action.VIEW_AUDIT_LOG))
            .isEqualTo(Decision.Denied.Forbidden)
    }

    @Test
    fun `RED13 - admin 도 발행할 수 있다`() {
        assertThat(PermissionMatrix.allows(setOf(ADMIN), Action.PUBLISH)).isTrue()
        assertThat(PermissionMatrix.allows(setOf(ADMIN), Action.WRITE_CONTENT)).isTrue()
    }

    /**
     * SPEC-08 §2.2 — **권한 분리 자체가 중립성 장치다.**
     *
     * `editor` 는 큐레이션 리스트를 만드는 사람이고 `partner_tier` 는 매출과 직결된다.
     * 한 사람이 둘 다 쥐면 `R-F3.3-3` 의 감시자가 사라진다.
     */
    @Test
    @Disabled("제휴 등급은 Phase 1b — EPICS-1B-PHASE2.md 1B-E4 (partner 도메인)")
    fun `RED14 - editor 는 제휴등급을 변경할 수 없다`() = Unit

    // ── RED 15~16 : 노출 규칙 부재 (PRIN-P02) ─────────────────────────────

    /**
     * **`admin` 도 못 바꾼다.** 바꾸려면 코드를 고치고 배포해야 한다.
     *
     * > 그게 의도다 — 영업 압박이 들어오는 순간 "어드민에서 잠깐만"이 가능하면 반드시 그렇게 된다.
     *
     * 엔드포인트 부재는 이슈 027 이 라우트 스캔으로 본다. 여기서는 **액션 자체가 없는지** 지킨다 —
     * 액션이 생기는 순간 그 규칙이 존재하게 되고, 엔드포인트는 그다음이다.
     */
    @Test
    fun `RED16 - 권한 enum 에 노출규칙 관련 액션이 없다`() {
        val forbidden = listOf(
            "BOOST", "SLOT", "TIER_LIMIT", "EXPOSURE", "RANK", "PROMOTE", "FEATURE",
        )

        assertAll(
            Action.entries.map<Action, () -> Unit> { action ->
                {
                    assertThat(forbidden)
                        .`as`("PRIN-P02 — %s 가 노출 규칙을 표현한다", action)
                        .noneMatch { action.name.contains(it) }
                }
            },
        )
    }

    // ── RED 17 : 403 / 404 구분 ───────────────────────────────────────────

    /**
     * **어디에 403 을 쓰고 어디에 404 를 쓰는지가 이 이슈의 판단이다.**
     *
     * 헷갈리면 이렇게 묻는다 — 거부 사실을 알려 주는 것만으로 무언가가 새는가?
     */
    @Test
    fun `RED17 - 403 과 404 구분 규칙이 코드로 표현된다`() {
        val hidden = Action.entries.filter { it.denial == DenialMode.HIDE }
        val forbidden = Action.entries.filter { it.denial == DenialMode.FORBID }

        assertThat(hidden)
            .`as`("존재가 비밀인 것")
            .contains(Action.VIEW_DRAFT, Action.VIEW_ARCHIVED, Action.OWN_BOOKMARK)
        assertThat(forbidden)
            .`as`("리소스는 공개인데 액션 권한이 없는 것")
            .contains(Action.WRITE_CONTENT, Action.PUBLISH, Action.APPROVE_INGREDIENT)

        // 판정이 예외까지 이어지는지 — 호출부가 상태 코드를 다시 고르면 반드시 어긋난다.
        assertThatThrownBy { PermissionMatrix.evaluate(setOf(MEMBER), Action.VIEW_DRAFT).orThrow() }
            .isInstanceOf(ResourceNotFoundException::class.java)
        assertThatThrownBy { PermissionMatrix.evaluate(setOf(MEMBER), Action.PUBLISH).orThrow() }
            .isInstanceOf(AccessDeniedException::class.java)
    }

    // ── RED 18~19 : 본인 것 (◐) ───────────────────────────────────────────

    @Test
    fun `RED18 - member 는 자기 북마크만 조회한다`() {
        assertThat(PermissionMatrix.evaluate(setOf(MEMBER), Action.OWN_BOOKMARK, Scope.Own(ME), ME))
            .isEqualTo(Decision.Allowed)
    }

    /** 남의 것이면 **404** 다. 403 이면 "그 사람이 그것을 저장했다"가 새어 나간다. */
    @Test
    fun `RED19 - 타인의 북마크 접근은 404 다`() {
        assertThat(PermissionMatrix.evaluate(setOf(MEMBER), Action.OWN_BOOKMARK, Scope.Own(SOMEONE_ELSE), ME))
            .isEqualTo(Decision.Denied.Hidden)

        // admin 도 예외가 아니다 — 표가 admin 에게도 ◐ 를 줬다.
        assertThat(PermissionMatrix.evaluate(setOf(ADMIN), Action.OWN_BOOKMARK, Scope.Own(SOMEONE_ELSE), ME))
            .`as`("SPEC-08 §2 표는 admin 에게도 ◐ 다")
            .isEqualTo(Decision.Denied.Hidden)
    }

    // ── 1b 이월분이 실수로 평가되지 않게 ───────────────────────────────────

    /**
     * 1b 액션을 지금 평가하면 **검증되지 않은 판정을 다른 이슈가 믿고 쓰게 된다.**
     * 조용히 `false` 를 돌려주는 것보다 터지는 편이 낫다.
     */
    @Test
    fun `Phase 1b 액션은 평가를 거부한다`() {
        assertThatThrownBy { PermissionMatrix.evaluate(setOf(ADMIN), Action.CHANGE_TIER) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("EPICS-1B-PHASE2")

        assertThatThrownBy {
            PermissionMatrix.evaluate(setOf(ADMIN), Action.VIEW_PUBLISHED, Scope.OwnBar(1))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("1B-E8")
    }

    /** `Role.PARTNER_OWNER` 정의는 남는다 — 나중에 늘리면 생성 타입이 바뀌어 클라이언트가 깨진다. */
    @Test
    fun `partner_owner 는 enum 에 정의돼 있다`() {
        assertThat(Role.entries.map(Role::code))
            .containsExactly("member", "editor", "partner_owner", "admin")
    }

    // ── 표 표기 ────────────────────────────────────────────────────────────

    private data class Actor(val label: String, val roles: Set<Role>)

    private data class Row(val action: Action, val cells: List<Pair<Actor, String>>)

    private fun row(action: Action, anonymous: String, member: String, editor: String, admin: String) =
        Row(
            action,
            listOf(
                Actor("비로그인", emptySet()) to anonymous,
                Actor("member", setOf(MEMBER)) to member,
                Actor("editor", setOf(EDITOR)) to editor,
                Actor("admin", setOf(ADMIN)) to admin,
            ),
        )

    private companion object {
        const val ME = 1L
        const val SOMEONE_ELSE = 2L
    }
}
