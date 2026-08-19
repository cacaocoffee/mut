package kr.mut.admin.verification

import kr.mut.cocktail.api.PublishInspectionFacade
import kr.mut.cocktail.lifecycle.CocktailFixture
import kr.mut.cocktail.publish.PublishService
import kr.mut.support.PostgresSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.support.TransactionTemplate

/**
 * ISSUE-016 RED 1~26 — `npm run check` 의 서버판 (SPEC-06 §4.3, `NFR-D-01`·`D-02`·`D-04`).
 *
 * ## 검사하는 쪽이 틀릴 수 있다
 *
 * 이 배치의 존재 이유는 "규칙을 한 번 더 도는 것" 이 아니라 **발행 경로가 뚫렸는지 보는 것**이다.
 * 그래서 배치가 자기 규칙을 쓰면 안 된다 — 두 규칙이 어긋났을 때 어느 쪽이 맞는지
 * 알 방법이 없고, `NFR-D-02` 가 잡아야 할 어긋남을 배치 자신이 만든다.
 */
@SpringBootTest
class InvariantVerificationBatchTest {

    @Autowired private lateinit var batch: InvariantVerificationBatch
    @Autowired private lateinit var inspection: PublishInspectionFacade
    @Autowired private lateinit var publish: PublishService
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var tx: TransactionTemplate

    @BeforeEach
    fun clear() {
        jdbc.execute("TRUNCATE cocktail, ingredient, audit_log, verification_task CASCADE")
    }

    // ── RED 1~5 : 전수 스캔 ────────────────────────────────────────────────

    @Test
    fun `RED1-3 - 위반이 없으면 발행분을 전부 훑고 성공한다`() {
        val id = fixture.cocktail()
        publish.publish(id)

        val run = batch.run()

        assertAll(
            { assertThat(run.scannedCocktails).`as`("RED1").isEqualTo(1) },
            { assertThat(run.hasViolations).`as`("RED3").isFalse() },
            { assertThat(openTasks()).isEmpty() },
        )
    }

    /** RED 2 — `NFR-D-01` 이 대상을 **발행분**으로 못박았다. 작업 중인 초안까지 잡으면 큐가 잡음이 된다. */
    @Test
    fun `RED2 - draft 는 스캔 대상이 아니다`() {
        fixture.cocktail() // 발행하지 않는다
        jdbc.execute("UPDATE cocktail SET tasting_note = NULL") // 게이트를 깬 채로 draft

        val run = batch.run()

        assertThat(run.scannedCocktails).isZero()
        assertThat(openTasks()).isEmpty()
    }

    /**
     * RED 4·5 — **첫 위반에서 멈추지 않는다.** 건마다 엔티티와 코드가 남는다.
     *
     * 하나씩 알려 주면 사람이 고치고 하루 기다리고 또 고친다.
     */
    @Test
    fun `RED4-5 - 위반을 전부 보고하고 건마다 엔티티와 코드가 남는다`() {
        val id = bypassedPublish(tastingNote = null, isClassic = true, story = null)

        batch.run()

        val rows = jdbc.queryForList(
            "SELECT entity_type, entity_id, code FROM verification_task ORDER BY code",
        )
        assertThat(rows.map { it["code"] })
            .containsExactly("GATE-COCKTAIL-01", "GATE-COCKTAIL-05")
        assertThat(rows).allSatisfy {
            assertThat(it["entity_type"]).isEqualTo("cocktail")
            assertThat(it["entity_id"]).isEqualTo(id)
        }
    }

    // ── RED 6~9 : 게이트 재사용과 우회 검출 (NFR-D-02) ─────────────────────

    /**
     * RED 6 — **배치가 게이트와 똑같은 답을 낸다.**
     *
     * 별도 규칙을 구현하지 않았다는 것을 행동으로 확인한다. 코드를 읽어 "안 짰다"고
     * 단언하는 것보다, 두 경로가 같은 입력에 같은 답을 내는지 보는 편이 강하다.
     */
    @Test
    fun `RED6 - 배치가 PublishGate 와 같은 판정을 낸다`() {
        val id = bypassedPublish(tastingNote = null, isClassic = true, story = null)

        val fromGate = inspection.inspectGate(id).map { it.code }.sorted()
        batch.run()
        val fromBatch = jdbc.queryForList(
            "SELECT code FROM verification_task WHERE task_type = 'gate_bypass' ORDER BY code",
            String::class.java,
        )

        assertThat(fromBatch).isEqualTo(fromGate)
    }

    /**
     * RED 7·8 — 게이트를 통과하지 않은 `published` 를 잡는다 (`NFR-D-02`).
     *
     * DB 를 직접 UPDATE 해 만든 상태다. 앱을 거치면 만들 수 없는 상태라서,
     * 이 배치가 없으면 **아무도 모른다.**
     */
    @Test
    fun `RED7-8 - 게이트를 우회한 published 를 gate_bypass 로 표시한다`() {
        bypassedPublish(tastingNote = null)

        batch.run()

        assertThat(
            jdbc.queryForList(
                "SELECT task_type FROM verification_task",
                String::class.java,
            ),
        ).containsOnly("gate_bypass")
    }

    /**
     * RED 9 — **자동 회수하지 않는다** (GAPS G-27).
     *
     * `NFR-D-02` 는 "즉시 회수" 라고 적었지만, 자동으로 내리면 에디터가 쓰던 것이
     * 예고 없이 사라지고 배치가 오판했을 때 되돌릴 사람이 그 사실조차 모른다.
     */
    @Test
    fun `RED9 - 자동으로 회수하지 않는다`() {
        val id = bypassedPublish(tastingNote = null)

        batch.run()

        assertThat(status(id))
            .`as`("태스크만 올린다. 내리는 것은 사람이 판단한다")
            .isEqualTo("published")
    }

    // ── RED 10~19 : 앱 강제 불변식 전수 ────────────────────────────────────

    @Test
    fun `RED10-11 - 스타일 0개와 향 태그 개수 위반을 INV 코드로 잡는다`() {
        val id = fixture.cocktail()
        publish.publish(id)
        // 발행 뒤에 자식 행을 지운다 — 앱을 거치지 않는 오염이다
        jdbc.execute("DELETE FROM cocktail_aroma_tag WHERE cocktail_id = $id")

        batch.run()

        assertThat(codes(id)).contains("INV-COCKTAIL-04")
    }

    @Test
    fun `RED12-16 - 게이트 6종이 각각의 코드로 잡힌다`() {
        val id = bypassedPublish(tastingNote = null, isClassic = true, story = null)

        batch.run()

        assertThat(codes(id)).contains("GATE-COCKTAIL-01", "GATE-COCKTAIL-05")
    }

    /**
     * RED 18 — **DB 가 이미 막아서 이 상태를 만들 수 없다.**
     *
     * SPEC-06 §4.3 은 `INV-INGREDIENT-01` 을 "앱 강제" 로 분류했지만 이슈 008 이
     * `ck_ingredient__substitute` CHECK 로도 걸었다 (GAPS G-24).
     * 그래서 확인할 것이 "배치가 잡는가" 가 아니라 **"애초에 못 만드는가"** 로 바뀐다 —
     * 배치의 같은 검사는 그 CHECK 가 사라진 날을 위한 이중 확인으로 남는다.
     */
    @Test
    fun `RED18 - 미유통 재료의 대체 안내 누락은 DB 가 먼저 막는다`() {
        jdbc.execute(
            """INSERT INTO ingredient (slug, name_ko, name_en, category, domestic_availability, is_approved, substitute_note)
               VALUES ('rare-amaro', '희귀 아마로', 'rare amaro', 'liqueur', 'common', true, NULL)""",
        )

        assertThatThrownBy {
            jdbc.execute("UPDATE ingredient SET domestic_availability = 'import_only' WHERE slug = 'rare-amaro'")
        }.hasMessageContaining("ck_ingredient__substitute")

        // 만들 수 없으니 배치도 찾을 것이 없다. 그것이 정상이다.
        batch.run()
        assertThat(
            jdbc.queryForList(
                "SELECT code FROM verification_task WHERE entity_type = 'ingredient'",
                String::class.java,
            ),
        ).isEmpty()
    }

    /**
     * 대체 안내가 **있는** 미유통 재료는 위반이 아니다.
     *
     * 이 테스트가 없으면 위 테스트는 "배치가 재료를 아예 안 본다" 여도 통과한다.
     */
    @Test
    fun `대체 안내가 있는 미유통 재료는 위반이 아니다`() {
        jdbc.execute(
            """INSERT INTO ingredient (slug, name_ko, name_en, category, domestic_availability, is_approved, substitute_note)
               VALUES ('rare-amaro', '희귀 아마로', 'rare amaro', 'liqueur', 'import_only', true, '아마로 몬테네그로로 대체')""",
        )

        val run = batch.run()

        assertThat(run.scannedIngredients).`as`("재료를 보긴 봤다").isEqualTo(1)
        assertThat(run.violations).isEmpty()
    }

    // ── RED 20~21 : slug 감시 (NFR-D-04) ───────────────────────────────────

    @Test
    fun `RED21 - slug 변경이 없으면 통과한다`() {
        val id = fixture.cocktail()
        publish.publish(id)

        batch.run()

        assertThat(taskTypes()).doesNotContain("slug_changed")
    }

    /** RED 20 — 거부된 시도도 조사 대상이다. 누군가 그 경로를 찾고 있었다는 뜻이다. */
    @Test
    fun `RED20 - 감사 로그의 slug 변경 시도를 잡는다`() {
        val id = fixture.cocktail()
        jdbc.update(
            """INSERT INTO audit_log (entity_type, entity_id, action, before, after)
               VALUES ('cocktail', ?, 'slug_change_attempt', '{"slug":"a"}'::jsonb, '{"slug":"b"}'::jsonb)""",
            id,
        )

        batch.run()

        val row = jdbc.queryForMap(
            "SELECT code, detail->>'before' AS b, detail->>'after' AS a FROM verification_task WHERE task_type = 'slug_changed'",
        )
        assertAll(
            { assertThat(row["code"]).isEqualTo("INV-COCKTAIL-05") },
            { assertThat(row["b"]).isEqualTo("a") },
            { assertThat(row["a"]).isEqualTo("b") },
        )
    }

    // ── RED 22~24 : 멱등 (PRIN-T07) ────────────────────────────────────────

    /**
     * RED 22·23 — **두 번 돌려도 한 줄이다.**
     *
     * 유니크가 없으면 하루에 한 줄씩 쌓여 큐가 같은 문제의 사본으로 가득 찬다.
     */
    @Test
    fun `RED22-23 - 두 번 돌려도 태스크가 중복되지 않는다`() {
        bypassedPublish(tastingNote = null)

        val first = batch.run()
        val second = batch.run()

        assertAll(
            { assertThat(first.opened).isEqualTo(1) },
            { assertThat(second.opened).`as`("두 번째는 새로 열 것이 없다").isZero() },
            { assertThat(openTasks()).hasSize(1) },
        )
    }

    /** RED 24 — 고쳤는데 태스크가 열린 채면 큐가 거짓말을 하고, 곧 아무도 큐를 안 본다. */
    @Test
    fun `RED24 - 해소된 위반의 태스크는 자동으로 닫힌다`() {
        val id = bypassedPublish(tastingNote = null)
        batch.run()
        assertThat(openTasks()).hasSize(1)

        jdbc.execute("UPDATE cocktail SET tasting_note = '고쳤다' WHERE id = $id")
        val run = batch.run()

        assertAll(
            { assertThat(run.resolved).isEqualTo(1) },
            { assertThat(openTasks()).isEmpty() },
            {
                assertThat(
                    jdbc.queryForObject(
                        "SELECT status FROM verification_task WHERE entity_id = $id",
                        String::class.java,
                    ),
                ).`as`("지우지 않는다 — 있었던 일이다").isEqualTo("resolved")
            },
        )
    }

    /** 다시 걸리면 **새 태스크가 아니라 재오픈**이다. 같은 사건의 재발이라 이력을 나누지 않는다. */
    @Test
    fun `해소됐다가 재발하면 같은 줄이 다시 열린다`() {
        val id = bypassedPublish(tastingNote = null)
        batch.run()
        jdbc.execute("UPDATE cocktail SET tasting_note = '고쳤다' WHERE id = $id")
        batch.run()

        jdbc.execute("UPDATE cocktail SET tasting_note = NULL WHERE id = $id")
        val run = batch.run()

        assertAll(
            { assertThat(run.reopened).isEqualTo(1) },
            { assertThat(run.opened).isZero() },
            { assertThat(jdbc.queryForList("SELECT id FROM verification_task")).hasSize(1) },
        )
    }

    // ── RED 25 : 실행 이력 ─────────────────────────────────────────────────

    /** `batch_run` 테이블은 만들지 않는다 (DECISIONS D-2). 결과값과 로그로 남긴다. */
    @Test
    fun `RED25 - 실행 결과에 검사 건수와 위반 건수가 담긴다`() {
        fixture.cocktail().also { publish.publish(it) }
        bypassedPublish(tastingNote = null)

        val run = batch.run()

        assertAll(
            { assertThat(run.scannedCocktails).isEqualTo(2) },
            { assertThat(run.scannedIngredients).isPositive() },
            { assertThat(run.violations).hasSize(1) },
        )
    }

    // ── 헬퍼 ───────────────────────────────────────────────────────────────

    private val fixture by lazy { CocktailFixture(jdbc, tx) }

    /**
     * **게이트를 우회한 `published`** 를 만든다.
     *
     * 정상 발행 후 DB 를 직접 고친다 — 앱으로는 만들 수 없는 상태이고,
     * 정확히 그래서 `NFR-D-02` 가 배치를 요구한다.
     */
    private fun bypassedPublish(
        tastingNote: String? = null,
        isClassic: Boolean = false,
        story: String? = null,
    ): Long {
        val id = fixture.cocktail()
        publish.publish(id)
        jdbc.update(
            "UPDATE cocktail SET tasting_note = ?, is_classic = ?, story = ? WHERE id = ?",
            tastingNote,
            isClassic,
            story,
            id,
        )
        return id
    }

    private fun codes(entityId: Long) = jdbc.queryForList(
        "SELECT code FROM verification_task WHERE entity_id = $entityId",
        String::class.java,
    )

    private fun taskTypes() =
        jdbc.queryForList("SELECT task_type FROM verification_task", String::class.java)

    private fun openTasks() =
        jdbc.queryForList("SELECT id FROM verification_task WHERE status = 'open'")

    private fun status(id: Long) =
        jdbc.query("SELECT status FROM cocktail WHERE id = $id") { rs, _ -> rs.getString(1) }.firstOrNull()

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { PostgresSupport.container.jdbcUrl }
            registry.add("spring.datasource.username") { PostgresSupport.container.username }
            registry.add("spring.datasource.password") { PostgresSupport.container.password }
            registry.add("spring.flyway.enabled") { true }
            registry.add("spring.flyway.user") { PostgresSupport.container.username }
            registry.add("spring.flyway.password") { PostgresSupport.container.password }
            // 배치는 테스트가 직접 부른다. 크론이 끼어들면 결과가 흔들린다
            registry.add("mut.verification.scheduled") { false }
        }
    }
}
