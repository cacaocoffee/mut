package kr.mut.user

import kr.mut.support.PostgresSupport
import kr.mut.user.internal.AccountClosureService
import kr.mut.common.account.ClosureHook
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

    /**
     * RED 24 — **북마크와 컬렉션은 즉시 지운다** (SPEC-08 §5.3, 이슈 031 이 풀었다).
     *
     * `audit_log` 와 정반대인데 근거가 다르다. 발행 이력은 법적 근거이자 신뢰 기록이라
     * 남기고, 북마크는 **순수한 개인 취향 기록**이라 남길 이유가 없다 —
     * 남겨 봐야 "이 사람이 무엇을 좋아했나" 만 남는다.
     *
     * `ON DELETE CASCADE` 가 DB 에서 처리한다. 앱이 지우면 새 테이블이 생길 때마다
     * 탈퇴 코드를 고쳐야 하고, 그걸 잊으면 조용히 남는다.
     */
    @Test
    fun `RED24 - 탈퇴시 북마크가 CASCADE 삭제된다`() {
        val id = insertUser("close-with-bookmarks")
        val collectionId = jdbc.queryForObject(
            "INSERT INTO bookmark_collection (user_id, name, share_token) " +
                "VALUES ($id, '내 취향', 'token-$id') RETURNING id",
            Long::class.java,
        )!!
        jdbc.execute(
            "INSERT INTO bookmark (user_id, collection_id, target_type, target_id) " +
                "VALUES ($id, $collectionId, 'cocktail', 1)",
        )

        closure.close(id)

        assertThat(count("SELECT count(*) FROM bookmark WHERE user_id = $id"))
            .`as`("RED24 북마크")
            .isZero()
        assertThat(count("SELECT count(*) FROM bookmark_collection WHERE user_id = $id"))
            .`as`("RED30 컬렉션도 함께")
            .isZero()
    }

    /**
     * RED 25 — **탈퇴해도 감사의 주체는 남는다** (SPEC-08 §5.3, 이슈 014 가 풀었다).
     *
     * 콘텐츠 발행 이력은 법적 근거이자 신뢰 기록이다. 개인을 식별할 수 없게 만드는 것과
     * 일어난 일을 없던 것으로 만드는 것은 다르다.
     *
     * 이것이 가능한 이유는 `audit_log.actor_user_id` 에 **FK 가 없어서**다.
     * FK 가 있으면 삭제가 막히거나 `ON DELETE` 가 이력을 지운다 — 둘 다 §5.3 위반이다.
     */
    @Test
    fun `RED25 - 탈퇴해도 audit_log actor_user_id 는 유지된다`() {
        val id = insertUser("audited-actor")
        jdbc.update(
            """
            INSERT INTO audit_log (entity_type, entity_id, action, actor_user_id, before, after)
            VALUES ('cocktail', 1, 'publish', ?, '{"status":"draft"}'::jsonb, '{"status":"published"}'::jsonb)
            """.trimIndent(),
            id,
        )

        closure.close(id)

        assertThat(count("""SELECT count(*) FROM "user" WHERE id = $id"""))
            .`as`("사용자는 지워졌다")
            .isZero()
        assertThat(count("SELECT count(*) FROM audit_log WHERE actor_user_id = $id"))
            .`as`("누가 발행했는지는 남는다")
            .isEqualTo(1)
    }

    /**
     * RED 26 — **`user_id` 만 지우고 행은 남긴다** (SPEC-08 §5.3 · SPEC-10 §8, 이슈 034 가 풀었다).
     *
     * 북마크(RED 24)와 정반대다. 근거가 다르다:
     *
     * | | 처리 | 왜 |
     * |---|---|---|
     * | 북마크 | 행째 삭제 | 순수한 개인 취향 기록이다 |
     * | 이벤트 | `user_id` 만 `NULL` | 행을 지우면 **집계가 소급해 바뀐다** |
     *
     * 지난달 조회수가 오늘 줄어들면 그 숫자로 아무것도 결정할 수 없다.
     * 개인을 식별할 수 없게 만드는 것과 일어난 일을 없던 것으로 만드는 것은 다르다.
     */
    @Test
    fun `RED26 - 탈퇴시 analytics_event user_id 는 NULL 로 익명화된다`() {
        val id = insertUser("close-with-events")
        val session = java.util.UUID.randomUUID()
        repeat(3) {
            jdbc.update(
                """
                INSERT INTO analytics_event (event_type, session_id, user_id, occurred_at)
                VALUES ('cocktail_view', ?::uuid, ?, now())
                """.trimIndent(),
                session.toString(), id,
            )
        }
        val before = count("SELECT count(*) FROM analytics_event WHERE session_id = '$session'")

        closure.close(id)

        assertThat(count("SELECT count(*) FROM analytics_event WHERE user_id = $id"))
            .`as`("RED26 — 개인 식별자는 사라진다")
            .isZero()
        assertThat(count("SELECT count(*) FROM analytics_event WHERE session_id = '$session'"))
            .`as`("RED36 — 행 수가 그대로다. 집계가 소급해 바뀌지 않는다")
            .isEqualTo(before)
    }

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
