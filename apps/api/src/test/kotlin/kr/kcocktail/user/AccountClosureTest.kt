package kr.kcocktail.user

import kr.kcocktail.support.PostgresSupport
import kr.kcocktail.user.internal.AccountClosureService
import kr.kcocktail.user.internal.ClosureHook
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/**
 * ISSUE-005 RED 23~26 — 탈퇴 (SPEC-08 §5.3).
 *
 * **탈퇴는 "흔적을 전부 지운다"가 아니다.** 개인을 식별할 수 없게 만들되
 * 일어난 일의 기록은 남긴다 — 그래서 테이블마다 처리가 다르다.
 *
 * RED 24~26 은 대상 테이블이 아직 없다. `@Disabled` 로 두고 해당 이슈가 푼다.
 * 지우지 않는 이유는, 지우면 그 이슈가 탈퇴 처리를 잊고 **잊었다는 사실도 드러나지 않기** 때문이다.
 */
@SpringBootTest
@ActiveProfiles(AccountClosureTest.PROFILE)
class AccountClosureTest {

    @Autowired private lateinit var closure: AccountClosureService
    @Autowired private lateinit var jdbc: JdbcTemplate

    @Test
    fun `RED23 - 탈퇴시 user 행이 삭제된다`() {
        val id = insertUser("close-me")

        closure.close(id)

        assertThat(count("""SELECT count(*) FROM "user" WHERE id = $id""")).isZero()
    }

    /** 역할은 `ON DELETE CASCADE` 다. 남으면 고아 행이 권한 조회에 걸린다. */
    @Test
    fun `탈퇴시 역할이 CASCADE 삭제된다`() {
        val id = insertUser("close-with-roles")
        jdbc.execute("INSERT INTO user_role (user_id, role) VALUES ($id, 'editor')")

        closure.close(id)

        assertThat(count("SELECT count(*) FROM user_role WHERE user_id = $id")).isZero()
    }

    /** 훅이 실제로 불리는지. 안 불리면 이슈 014·034 의 구현이 조용히 죽는다. */
    @Test
    fun `탈퇴 훅이 user 행 삭제 전에 불린다`() {
        val id = insertUser("hook-order")
        RecordingHook.calls.clear()

        closure.close(id)

        assertThat(RecordingHook.calls).containsExactly(id)
        assertThat(RecordingHook.userExistedAtCall)
            .`as`("훅 시점에는 user 행이 아직 있어야 FK 로 연결된 것을 찾을 수 있다")
            .isTrue()
    }

    @Test
    @Disabled("북마크 테이블은 이슈 031 (#33) 이 만든다")
    fun `RED24 - 탈퇴시 북마크가 CASCADE 삭제된다`() = Unit

    @Test
    @Disabled("audit_log 는 이슈 014 (#16) 가 만든다 — actor_user_id 는 유지한다")
    fun `RED25 - 탈퇴해도 audit_log actor_user_id 는 유지된다`() = Unit

    @Test
    @Disabled("analytics_event 는 이슈 034 (#36) 가 만든다 — user_id 를 NULL 로 익명화한다")
    fun `RED26 - 탈퇴시 analytics_event user_id 는 NULL 로 익명화된다`() = Unit

    // ── 헬퍼 ───────────────────────────────────────────────────────────────

    private fun insertUser(uid: String): Long = jdbc.queryForObject(
        """INSERT INTO "user" (provider, provider_uid, display_name)
           VALUES ('kakao', ?, '테스터') RETURNING id""",
        Long::class.java,
        "$uid-${System.nanoTime()}",
    )!!

    private fun count(sql: String): Int = jdbc.queryForObject(sql, Int::class.java) ?: 0

    companion object {
        const val PROFILE = "closure-probe"

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { PostgresSupport.container.jdbcUrl }
            registry.add("spring.datasource.username") { PostgresSupport.container.username }
            registry.add("spring.datasource.password") { PostgresSupport.container.password }
            registry.add("spring.flyway.enabled") { true }
            registry.add("spring.flyway.user") { PostgresSupport.container.username }
            registry.add("spring.flyway.password") { PostgresSupport.container.password }
        }
    }
}

/**
 * 훅 호출을 기록한다.
 *
 * `@SpringBootTest` 클래스의 **중첩 클래스는 컴포넌트 스캔에서 제외된다**
 * (`TypeExcludeFilter`). 최상위로 빼되 `@Profile` 로 가둔다 —
 * 그냥 두면 모든 통합 테스트의 탈퇴 경로에 이 훅이 딸려 들어간다.
 */
@Profile(AccountClosureTest.PROFILE)
@Component
class RecordingHook(private val jdbc: JdbcTemplate) : ClosureHook {
    override fun onAccountClosing(userId: Long) {
        calls += userId
        userExistedAtCall =
            (jdbc.queryForObject("""SELECT count(*) FROM "user" WHERE id = $userId""", Int::class.java) ?: 0) > 0
    }

    companion object {
        val calls = mutableListOf<Long>()
        var userExistedAtCall = false
    }
}
