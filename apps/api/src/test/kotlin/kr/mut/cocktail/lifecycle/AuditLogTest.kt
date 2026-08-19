package kr.mut.cocktail.lifecycle

import kr.mut.cocktail.api.CocktailArchived
import kr.mut.cocktail.api.CocktailRenamed
import kr.mut.cocktail.api.CocktailUnpublished
import kr.mut.cocktail.domain.CocktailStatus
import kr.mut.cocktail.publish.PublishService
import kr.mut.common.audit.AuditAction
import kr.mut.common.audit.AuditRecorder
import kr.mut.support.PostgresSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.support.TransactionTemplate
import java.sql.SQLException

/**
 * ISSUE-014 RED 8~30 — 상태 전이 · 감사 로그 · 이벤트.
 *
 * ## 감사가 없으면 전이도 없다
 *
 * `PRIN-T08` — "되돌릴 수 있어야 하고, 다툼이 생겼을 때 근거가 돼야 한다."
 * 근거는 **전이와 함께 남아야** 의미가 있다. 하나만 남으면 이력이 거짓말을 한다.
 */
@SpringBootTest
@ActiveProfiles(AuditLogTest.PROFILE)
class AuditLogTest {

    @Autowired private lateinit var publish: PublishService
    @Autowired private lateinit var lifecycle: CocktailLifecycleService
    @Autowired private lateinit var auditor: AuditRecorder
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var tx: TransactionTemplate

    @BeforeEach
    fun clear() {
        LifecycleRecorder.clear()
        jdbc.execute("TRUNCATE cocktail, ingredient, audit_log CASCADE")
    }

    // ── RED 8~9 : 전이 매트릭스 (SPEC-02 §8.1, DECISIONS §1.4) ─────────────

    @Test
    fun `RED8-9 - 전이 매트릭스 전수`() {
        assertAll(
            listOf(
                Triple(CocktailStatus.DRAFT, CocktailStatus.PUBLISHED, true),
                Triple(CocktailStatus.PUBLISHED, CocktailStatus.DRAFT, true),
                Triple(CocktailStatus.PUBLISHED, CocktailStatus.ARCHIVED, true),
                Triple(CocktailStatus.ARCHIVED, CocktailStatus.DRAFT, true),
                // RED 9 — 도식에 없다. 보수적으로 거부한다.
                Triple(CocktailStatus.DRAFT, CocktailStatus.ARCHIVED, false),
                Triple(CocktailStatus.ARCHIVED, CocktailStatus.PUBLISHED, false),
                Triple(CocktailStatus.DRAFT, CocktailStatus.DRAFT, false),
            ).map<Triple<CocktailStatus, CocktailStatus, Boolean>, () -> Unit> { (from, to, ok) ->
                {
                    assertThat(CocktailTransition.isAllowed(from, to))
                        .`as`("%s → %s", from.slug, to.slug)
                        .isEqualTo(ok)
                }
            },
        )
    }

    /** RED 9 — `draft → archived` 직행은 서비스에서도 막힌다. 표만 맞고 경로가 열려 있으면 소용없다. */
    @Test
    fun `RED9 - draft 에서 archived 로 직행할 수 없다`() {
        val id = fixture.cocktail()

        assertThatThrownBy { publish.archive(id) }
            .hasMessageContaining("허용되지 않는 상태 전이")

        assertThat(status(id)).isEqualTo("draft")
    }

    // ── RED 10~14 : 전이가 전부 감사에 남는다 ──────────────────────────────

    /**
     * RED 10·13·14 — 네 전이가 각각 다른 `action` 으로 남는다.
     *
     * `→ draft` 가 둘로 갈리는 것이 요점이다. `published → draft` 는 내린 것,
     * `archived → draft` 는 다시 꺼낸 것이라 사후 조사에서 뜻이 다르다.
     */
    @Test
    fun `RED10-13-14 - 네 전이가 전부 감사에 남는다`() {
        val id = fixture.cocktail()

        publish.publish(id) // publish
        publish.unpublish(id) // unpublish
        publish.publish(id) // publish
        publish.archive(id) // archive
        publish.unpublish(id) // restore

        assertThat(actions(id)).containsExactly(
            "publish", "unpublish", "publish", "archive", "restore",
        )
    }

    // ── RED 15~18 : 기록의 모양 (SPEC-06 §3.8) ─────────────────────────────

    @Test
    fun `RED15-18 - before after actor at entity 가 기록된다`() {
        val id = fixture.cocktail()

        publish.publish(id)

        val row = jdbc.queryForMap("SELECT * FROM audit_log WHERE entity_id = $id")
        assertAll(
            { assertThat(row["entity_type"]).`as`("RED18").isEqualTo("cocktail") },
            { assertThat(row["entity_id"]).`as`("RED18").isEqualTo(id) },
            { assertThat(row["created_at"]).`as`("RED17").isNotNull() },
            {
                assertThat(row["actor_user_id"])
                    .`as`("RED16 — 요청 밖이라 주체가 없다. 거짓 주체를 지어내지 않는다")
                    .isNull()
            },
        )

        val json = jdbc.queryForMap(
            "SELECT before->>'status' AS b, after->>'status' AS a FROM audit_log WHERE entity_id = $id",
        )
        assertThat(json["b"]).`as`("RED15 — JSONB 로 읽힌다").isEqualTo("draft")
        assertThat(json["a"]).isEqualTo("published")
    }

    /** RED 16 — 세션이 있으면 그 사용자가 주체다. 요청 밖에서는 `null` 이다 (위 테스트). */
    @Test
    fun `RED16 - 주체가 있으면 actor_user_id 에 남는다`() {
        val id = fixture.cocktail()
        StubActor.userId = 42L

        publish.publish(id)

        assertThat(jdbc.queryForObject("SELECT actor_user_id FROM audit_log WHERE entity_id = $id", Long::class.java))
            .isEqualTo(42L)
    }

    // ── RED 19 : append-only (PRIN-T08) ────────────────────────────────────

    /**
     * RED 19 — **고칠 수 있는 이력은 이력이 아니다.**
     *
     * 앱 역할로 붙어야 의미가 있다. 슈퍼유저로 시도하면 `REVOKE` 가 있든 없든 성공한다.
     */
    @Test
    fun `RED19 - 감사 로그를 앱이 수정하거나 지울 수 없다`() {
        val id = fixture.cocktail()
        publish.publish(id)

        PostgresSupport.appConnection().use { conn ->
            assertAll(
                {
                    assertThatThrownBy {
                        conn.createStatement().use { it.execute("UPDATE audit_log SET action = 'verify'") }
                    }.isInstanceOf(SQLException::class.java).hasMessageContaining("permission denied")
                },
                {
                    assertThatThrownBy {
                        conn.createStatement().use { it.execute("DELETE FROM audit_log") }
                    }.isInstanceOf(SQLException::class.java).hasMessageContaining("permission denied")
                },
                {
                    conn.createStatement().use { st ->
                        st.executeQuery("SELECT count(*) FROM audit_log").use { rs ->
                            rs.next()
                            assertThat(rs.getInt(1)).`as`("읽기와 쓰기는 된다").isPositive()
                        }
                    }
                },
            )
        }
    }

    // ── RED 20 : 조회 인덱스 (SPEC-06 §5) ──────────────────────────────────

    @Test
    fun `RED20 - 엔티티별 시간순 조회 인덱스가 있다`() {
        val indexes = jdbc.queryForList(
            "SELECT indexdef FROM pg_indexes WHERE tablename = 'audit_log'",
            String::class.java,
        )

        assertThat(indexes).anySatisfy { def ->
            assertThat(def.replace(" ", ""))
                .contains("(entity_type,entity_id,created_at)")
        }
    }

    // ── RED 21 : 탈퇴해도 주체가 남는다 (SPEC-08 §5.3) ─────────────────────

    /**
     * RED 21 — `actor_user_id` 에 **FK 가 없다.**
     *
     * SPEC-08 §5.3 은 "`user` 행 즉시 삭제" 와 "`actor_user_id` 유지" 를 동시에 요구한다.
     * FK 가 있으면 둘 다 못 한다 — 삭제가 막히거나 `ON DELETE` 가 이력을 지운다.
     * 콘텐츠 발행 이력은 법적 근거이자 신뢰 기록이라 지울 수 없다.
     */
    @Test
    fun `RED21 - 없는 사용자 id 도 기록되고 남는다`() {
        val id = fixture.cocktail()
        StubActor.userId = 999_999L // 존재하지 않는다 — 탈퇴했다고 보면 된다

        publish.publish(id)

        assertThat(jdbc.queryForObject("SELECT actor_user_id FROM audit_log WHERE entity_id = $id", Long::class.java))
            .`as`("FK 가 없으니 들어가고, 지워지지도 않는다")
            .isEqualTo(999_999L)

        assertThat(
            jdbc.queryForList(
                "SELECT conname FROM pg_constraint WHERE conrelid = 'audit_log'::regclass AND contype = 'f'",
                String::class.java,
            ),
        ).`as`("audit_log 에 외래키가 하나도 없다").isEmpty()
    }

    // ── RED 12·22 : action 목록 ────────────────────────────────────────────

    /**
     * RED 12·22 — SPEC-06 §3.8 의 5종 + 전이용 2종 + 시도 1종.
     *
     * Phase 1b·2 값을 지금 넣어 두는 이유는 나중에 늘리면 **읽는 쪽이 그때 깨져서**다.
     */
    @Test
    fun `RED12-22 - action 열거와 DB CHECK 가 같다`() {
        val inCode = AuditAction.entries.map { it.slug }.toSet()

        assertThat(inCode).containsExactlyInAnyOrder(
            "publish", "unpublish", "archive", "restore",
            "tier_change", "rank_change", "verify",
            "slug_change_attempt",
            // 이슈 026 — 재료 승인 (DECISIONS §1.3). 마스터 오염을 되짚으려면 필요하다
            "approve",
        )

        val check = jdbc.queryForObject(
            """
            SELECT pg_get_constraintdef(oid) FROM pg_constraint
            WHERE conrelid = 'audit_log'::regclass AND conname = 'ck_audit_log__action'
            """.trimIndent(),
            String::class.java,
        )!!

        assertThat(inCode)
            .`as`("코드의 값이 DB 에서 전부 허용된다 — 한쪽만 늘면 런타임에 터진다")
            .allSatisfy { slug -> assertThat(check).contains("'$slug'") }
    }

    // ── RED 23~24 : 재구성 (NFR-O-05) ──────────────────────────────────────

    /**
     * RED 23·24 — "누가 · 언제 · 무엇을" 을 시간순으로 재구성한다.
     *
     * 이것이 감사 로그의 존재 이유다. 한 줄씩 남는 것으로는 부족하고,
     * **이어 붙였을 때 이야기가 돼야** 한다.
     */
    @Test
    fun `RED23-24 - 한 칵테일의 이력을 시간순으로 재구성한다`() {
        val id = fixture.cocktail()
        StubActor.userId = 7L
        publish.publish(id)
        StubActor.userId = 9L
        publish.unpublish(id)

        val history = jdbc.queryForList(
            """
            SELECT action, actor_user_id, before->>'status' AS b, after->>'status' AS a
            FROM audit_log WHERE entity_type = 'cocktail' AND entity_id = $id
            ORDER BY created_at, id
            """.trimIndent(),
        )

        assertThat(history).hasSize(2)
        assertThat(history[0]).containsEntry("action", "publish")
            .containsEntry("actor_user_id", 7L)
            .containsEntry("b", "draft").containsEntry("a", "published")
        assertThat(history[1]).containsEntry("action", "unpublish")
            .containsEntry("actor_user_id", 9L)
            .containsEntry("b", "published").containsEntry("a", "draft")
    }

    // ── RED 25~27 : 도메인 이벤트 (이슈 017 이 구독) ───────────────────────

    @Test
    fun `RED25 - 회수시 CocktailUnpublished 가 발행된다`() {
        val id = fixture.cocktail()
        publish.publish(id)

        publish.unpublish(id)

        assertThat(LifecycleRecorder.unpublished).singleElement()
            .satisfies({ assertThat(it.entityId).isEqualTo(id) })
    }

    @Test
    fun `RED26 - 보관시 CocktailArchived 가 발행된다`() {
        val id = fixture.cocktail()
        publish.publish(id)

        publish.archive(id)

        assertThat(LifecycleRecorder.archived).singleElement()
            .satisfies({ assertThat(it.entityId).isEqualTo(id) })
    }

    /** 발행 상태가 그대로여도 **검색어가 바뀌었다** (`R-F2.1-3`). 색인은 갱신돼야 한다. */
    @Test
    fun `RED27 - 이름 별칭 변경시 색인 갱신 이벤트가 발행된다`() {
        val id = fixture.cocktail()
        publish.publish(id)

        lifecycle.rename(id, "진 토닉", "gin and tonic", listOf("GT", "진토닉"))

        assertThat(LifecycleRecorder.renamed).singleElement().satisfies({
            assertThat(it.nameKo).isEqualTo("진 토닉")
            assertThat(it.aliases).containsExactly("GT", "진토닉")
        })
        assertThat(status(id)).`as`("발행 상태는 건드리지 않는다").isEqualTo("published")
    }

    /** RED 28 — 경계는 `ModuleBoundaryTest` 가 지킨다. 여기서는 이벤트가 `api` 에 있음을 고정한다. */
    @Test
    fun `RED28 - 생애 이벤트가 cocktail api 에 공개돼 있다`() {
        assertAll(
            listOf(CocktailUnpublished::class, CocktailArchived::class, CocktailRenamed::class)
                .map<kotlin.reflect.KClass<*>, () -> Unit> { type ->
                    {
                        assertThat(type.java.packageName)
                            .`as`("%s", type.simpleName)
                            .isEqualTo("kr.mut.cocktail.api")
                    }
                },
        )
    }

    // ── RED 29~30 : 트랜잭션 ───────────────────────────────────────────────

    /**
     * RED 30 — **감사 없는 발행은 없다.**
     *
     * `AuditRecorder` 는 `MANDATORY` 라 트랜잭션 밖에서 부르면 터진다.
     * 감사를 트랜잭션 밖으로 빼는 코드는 전이가 롤백돼도 기록을 남기고,
     * 기록이 실패해도 전이를 성공시킨다 — 둘 다 이력을 거짓말로 만든다.
     */
    @Test
    fun `RED30 - 감사는 트랜잭션 밖에서 부를 수 없다`() {
        assertThatThrownBy {
            auditor.record("cocktail", 1L, AuditAction.PUBLISH, null, null)
        }.hasMessageContaining("transaction")
    }

    /** RED 29 — 전이가 롤백되면 감사도 없다. 게이트 실패가 가장 흔한 롤백이다. */
    @Test
    fun `RED29 - 전이가 롤백되면 감사도 남지 않는다`() {
        val id = fixture.cocktail()
        jdbc.execute("UPDATE cocktail SET tasting_note = NULL WHERE id = $id") // 게이트를 깬다

        runCatching { publish.publish(id) }

        assertThat(actions(id)).`as`("발행이 없었으니 기록도 없다").isEmpty()
        assertThat(status(id)).isEqualTo("draft")
    }

    // ── 헬퍼 ───────────────────────────────────────────────────────────────

    private val fixture by lazy { CocktailFixture(jdbc, tx) }

    private fun actions(id: Long) = jdbc.queryForList(
        "SELECT action FROM audit_log WHERE entity_id = $id ORDER BY created_at, id",
        String::class.java,
    )

    private fun status(id: Long) =
        jdbc.query("SELECT status FROM cocktail WHERE id = $id") { rs, _ -> rs.getString(1) }.firstOrNull()

    companion object {
        const val PROFILE = "audit-probe"

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

/** 이슈 017 의 색인 리스너 자리. 여기서는 이벤트 도달만 본다. */
@Profile(AuditLogTest.PROFILE)
@Component
class LifecycleRecorder {

    @EventListener fun on(e: CocktailUnpublished) { unpublished += e }

    @EventListener fun on(e: CocktailArchived) { archived += e }

    @EventListener fun on(e: CocktailRenamed) { renamed += e }

    companion object {
        val unpublished = mutableListOf<CocktailUnpublished>()
        val archived = mutableListOf<CocktailArchived>()
        val renamed = mutableListOf<CocktailRenamed>()

        fun clear() {
            unpublished.clear()
            archived.clear()
            renamed.clear()
            StubActor.userId = null
        }
    }
}
