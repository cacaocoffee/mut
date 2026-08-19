package kr.mut.common.security.session

import kr.mut.common.security.Role
import kr.mut.common.security.Role.ADMIN
import kr.mut.common.security.Role.EDITOR
import kr.mut.common.security.Role.MEMBER
import kr.mut.common.security.Role.PARTNER_OWNER
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

/**
 * ISSUE-005 RED 15~18 — 역할별 세션 수명 (SPEC-08 §4.1).
 *
 * DB 없이 도는 단위 테스트다. 정책은 순수 함수라 전수로 확인할 수 있고,
 * 전수로 확인할 수 있으면 그렇게 한다 (CONVENTIONS §3.2).
 */
class SessionPolicyTest {

    private val issued = Instant.parse("2026-08-10T09:00:00Z")

    @Test
    fun `RED15 - 일반 사용자 세션은 30일 rolling 이다`() {
        assertThat(SessionPolicy.lifetime(setOf(MEMBER))).isEqualTo(Duration.ofDays(30))
        assertThat(SessionPolicy.isAbsolute(setOf(MEMBER))).isFalse()

        // rolling 이라 발급 시각으로 끊지 않는다 — 1년 뒤여도 절대 만료 대상이 아니다.
        assertThat(SessionPolicy.isExpired(setOf(MEMBER), issued, issued.plus(Duration.ofDays(365))))
            .isFalse()
    }

    @Test
    fun `RED16 - editor 세션은 8시간 절대다`() = assertAbsolute8h(setOf(EDITOR))

    @Test
    fun `RED17 - admin 세션은 8시간 절대다`() = assertAbsolute8h(setOf(ADMIN))

    /**
     * DECISIONS §1 — **짧은 쪽이 이긴다.**
     *
     * 긴 쪽을 택하면 `member` 를 함께 가진 에디터가 8시간 규칙을 우회한다.
     * 그리고 소셜 로그인 기본 역할이 `member` 라(DECISIONS §1) 에디터는 거의 항상 둘을 갖는다 —
     * 즉 이 판단을 잘못하면 **8시간 규칙이 사실상 아무에게도 적용되지 않는다.**
     */
    @Test
    fun `RED18 - editor 와 member 를 둘 다 가지면 8시간이 적용된다`() {
        assertAbsolute8h(setOf(EDITOR, MEMBER))
        assertAbsolute8h(setOf(MEMBER, ADMIN))
        assertAbsolute8h(setOf(MEMBER, EDITOR, ADMIN, PARTNER_OWNER))
    }

    /** `partner_owner` 는 발행 권한이 없다. 짧게 가져갈 이유가 없다. */
    @Test
    fun `partner_owner 는 30일이다`() {
        assertThat(SessionPolicy.lifetime(setOf(PARTNER_OWNER, MEMBER)))
            .isEqualTo(Duration.ofDays(30))
    }

    /** 역할 조합 전수. 승격 역할이 하나라도 있으면 8시간이다. */
    @Test
    fun `역할 조합 전수 - 승격 역할이 있으면 8시간이다`() {
        val all = Role.entries.toSet()
        val subsets = (1 until (1 shl all.size)).map { mask ->
            all.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.toSet()
        }

        assertThat(subsets).hasSize(15)
        subsets.forEach { roles ->
            val expected = if (roles.any { it == EDITOR || it == ADMIN }) {
                Role.ABSOLUTE_8H
            } else {
                Role.ROLLING_30D
            }
            assertThat(SessionPolicy.lifetime(roles))
                .`as`("역할 %s", roles.map(Role::code))
                .isEqualTo(expected)
        }
    }

    /** SPEC-08 §1 — 역할은 누적되지 않는다. `editor` 가 `admin` 을 포함하지 않는다. */
    @Test
    fun `역할은 계층이 아니라 집합이다`() {
        assertThat(EDITOR.isElevated).isTrue()
        assertThat(ADMIN.isElevated).isTrue()
        assertThat(MEMBER.isElevated).isFalse()
        assertThat(PARTNER_OWNER.isElevated).isFalse()
    }

    private fun assertAbsolute8h(roles: Set<Role>) {
        assertThat(SessionPolicy.lifetime(roles)).isEqualTo(Duration.ofHours(8))
        assertThat(SessionPolicy.isAbsolute(roles)).isTrue()

        // 경계: 7시간 59분은 살아 있고, 정각 8시간부터 죽는다.
        assertThat(SessionPolicy.isExpired(roles, issued, issued.plus(Duration.ofMinutes(479))))
            .isFalse()
        assertThat(SessionPolicy.isExpired(roles, issued, issued.plus(Duration.ofHours(8))))
            .isTrue()

        // "절대"의 뜻 — 방금 활동했어도 발급 후 9시간이면 죽는다.
        assertThat(SessionPolicy.isExpired(roles, issued, issued.plus(Duration.ofHours(9))))
            .`as`("활동해도 연장되지 않는다")
            .isTrue()
    }
}
