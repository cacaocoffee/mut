package kr.kcocktail.architecture

import kr.kcocktail.support.PostgresSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.SQLException

/**
 * ISSUE-002 — Flyway 기반 · 공통 컬럼 규약 · `pg_trgm` (SPEC-06 §1 · §5 · §6)
 *
 * ## 린트를 두 스키마에 돌린다
 *
 * `public` 에는 아직 테이블이 하나도 없다. 그래서 "위반 0건"만으로는
 * **린트가 살아 있는지 아무것도 증명하지 못한다** — SQL 에 오타가 나도 0건이다.
 * 같은 규칙을 일부러 어긴 [FIXTURE_SCHEMA] 에 돌려 잡히는 것까지 확인한다.
 * ISSUE-001 의 모듈 경계와 같은 구조다.
 *
 * ## 권한 검사는 앱 역할로 붙는다
 *
 * 슈퍼유저로 `DELETE` 를 시도하면 `REVOKE` 가 있든 없든 성공한다.
 * `PostgresSupport.appConnection()` 이 운영과 같은 권한으로 붙는다.
 */
class SchemaLintTest {

    // ── RED 1~3 : 확장 · 마이그레이션 ──────────────────────────────────────

    /** SPEC-06 §5 — 초성 검색이 트라이그램에 의존한다. 색인 이슈(017)보다 먼저 있어야 한다. */
    @Test
    fun `RED1 - pg_trgm 확장이 설치된다`() {
        migrate().use { conn ->
            assertThat(conn.one("SELECT extname FROM pg_extension WHERE extname = 'pg_trgm'"))
                .isEqualTo("pg_trgm")
        }
    }

    @Test
    fun `RED2 - Flyway 가 V001 을 적용한다`() {
        // `version` 이 `null` 인 것은 **repeatable** 마이그레이션이다 (`R__seed_*`, 이슈 036).
        // 걸러 내지 않으면 여기가 NPE 로 죽는다 — 시드가 들어온 날 실제로 그랬다.
        val applied = PostgresSupport.flyway.info().applied()
            .mapNotNull { it.version?.version }
        assertThat(applied).contains("001")

        migrate().use { conn ->
            assertThat(conn.one("SELECT proname FROM pg_proc WHERE proname = 'set_updated_at'"))
                .`as`("updated_at 트리거 함수가 만들어졌는가")
                .isEqualTo("set_updated_at")
            assertThat(conn.rows("SELECT rolname FROM pg_roles WHERE rolname LIKE 'kcocktail%'"))
                .containsExactlyInAnyOrder("kcocktail_app", "kcocktail_migrate")
        }
    }

    /**
     * SPEC-06 §6 — "적용된 마이그레이션을 수정하지 않는다."
     *
     * 규약을 문서에만 두면 누군가 고친다. Flyway `validate` 가 기동을 막는지 확인한다.
     * 이력의 체크섬을 흔들어 "파일이 바뀐 상태"를 만든다 — 파일을 실제로 고치면
     * 이 테스트가 다른 테스트까지 오염시킨다.
     */
    @Test
    fun `RED3 - 적용된 마이그레이션 체크섬이 바뀌면 기동이 실패한다`() {
        PostgresSupport.flyway // 적용 보장
        migrate().use { conn ->
            val original = conn.one("SELECT checksum FROM flyway_schema_history WHERE version = '001'")
            try {
                conn.exec("UPDATE flyway_schema_history SET checksum = -1 WHERE version = '001'")

                assertThatThrownBy { PostgresSupport.flyway.validate() }
                    .`as`("체크섬이 어긋나면 검증이 실패해야 한다")
                    .hasMessageContaining("checksum")
            } finally {
                conn.exec("UPDATE flyway_schema_history SET checksum = $original WHERE version = '001'")
            }
        }
        PostgresSupport.flyway.validate() // 원복 확인 — 이후 테스트에 영향을 남기지 않는다
    }

    // ── RED 4~11 : 스키마 린트 ─────────────────────────────────────────────

    @Test
    fun `RED4 - 모든 실체테이블에 id created_at updated_at 이 있다`() =
        assertRule(SchemaLint::missingCommonColumns, catches = "cocktails 에 created_at 없음")

    /**
     * 규약 대상은 **실체** 테이블이다 (SPEC-06 §1.2).
     *
     * `user_role` 처럼 복합 PK 를 가진 연관 테이블에 대리키를 붙이면 복합 PK 가 의미를 잃고
     * 같은 조합이 두 번 들어간다 — 규약을 지키려다 무결성을 깬다.
     *
     * 예외가 **과하지도 모자라지도 않은지** 양쪽으로 본다.
     */
    @Test
    fun `연관 테이블은 공통 컬럼 규약 대상이 아니다`() {
        val violations = fixtureViolations(SchemaLint::missingCommonColumns)

        assertThat(violations)
            .`as`("복합 PK 는 관계다 — 잡으면 안 된다")
            .noneSatisfy { assertThat(it).contains("good_link") }

        assertThat(violations)
            .`as`("단일 PK 인데 공통 컬럼이 없으면 실체 테이블의 위반이다")
            .anySatisfy { assertThat(it).contains("bad_entity") }
    }

    @Test
    fun `RED5 - id 는 GENERATED ALWAYS AS IDENTITY 다`() =
        assertRule(SchemaLint::idNotAlwaysIdentity, catches = "cocktails.id")

    @Test
    fun `RED6 - updated_at 갱신 트리거가 붙어 있다`() =
        assertRule(SchemaLint::missingUpdatedAtTrigger, catches = "bad_column 에 set_updated_at 트리거 없음")

    @Test
    fun `RED7 - 테이블명은 snake_case 단수형이다`() =
        assertRule(SchemaLint::badTableNames, catches = "cocktails")

    @Test
    fun `RED8 - 불리언 컬럼은 is_ 또는 has_ 접두다`() =
        assertRule(SchemaLint::badBooleanNames, catches = "bad_column.sponsored")

    @Test
    fun `RED9 - 시각 컬럼은 at 접미 날짜는 on 접미다`() {
        assertRule(SchemaLint::badTemporalNames, catches = "bad_column.published")
        assertThat(fixtureViolations(SchemaLint::badTemporalNames))
            .`as`("날짜 쪽도 잡는가")
            .anySatisfy { assertThat(it).contains("bad_column.expiry", "_on") }
    }

    @Test
    fun `RED10 - Postgres 네이티브 ENUM 타입이 0개다`() =
        assertRule(SchemaLint::nativeEnumTypes, catches = "bad_enum")

    @Test
    fun `RED11 - 시각 컬럼은 timestamptz 다`() =
        assertRule(SchemaLint::naiveTimestamps, catches = "bad_column.published")

    // ── RED 12~13 : DELETE 권한 회수 ───────────────────────────────────────

    /**
     * SPEC-06 §4.1 — 보호 대상 넷은 상수로 유지하고 **존재하는 것만** 검사한다.
     * Phase 1a 에는 아직 하나도 없다. 목록이 사라지지 않았는지부터 고정한다.
     */
    @Test
    fun `RED12 - 앱 역할에 DELETE 권한이 없는 테이블 목록이 정의된다`() {
        assertThat(SchemaLint.PROTECTED_TABLES)
            .containsExactly("cocktail", "bar", "article", "curation_list")

        migrate().use { conn ->
            assertThat(SchemaLint.protectedTablesWithDeleteGrant(conn, REAL_SCHEMA))
                .`as`("아직 하나도 없으니 위반도 없다")
                .isEmpty()

            // 규칙이 존재하는 테이블에서 실제로 동작하는지 — 픽스처가 증명한다.
            assertThat(
                SchemaLint.protectedTablesWithDeleteGrant(
                    conn, FIXTURE_SCHEMA, listOf("unprotected_row"),
                ),
            ).`as`("REVOKE 를 빠뜨린 보호 테이블은 잡혀야 한다")
                .anySatisfy { assertThat(it).contains("unprotected_row", "DELETE 권한이 남아 있다") }

            assertThat(
                SchemaLint.protectedTablesWithDeleteGrant(
                    conn, FIXTURE_SCHEMA, listOf("protected_row"),
                ),
            ).`as`("REVOKE 한 쪽은 통과해야 한다")
                .isEmpty()
        }
    }

    /**
     * `V001` 의 `ALTER DEFAULT PRIVILEGES` 가 실제로 걸렸는지.
     *
     * 픽스처는 자기 스키마에 직접 `GRANT` 를 하므로 [RED13] 만으로는 이 줄을 검증하지 못한다 —
     * 통째로 빠져도 초록이 뜬다. 앞으로 만들어지는 테이블에 앱 권한이 자동으로 붙지 않으면
     * 그 사실이 **첫 INSERT 까지** 안 보이므로 여기서 못박는다.
     *
     * `FOR ROLE` 을 붙이지 않았기 때문에 대상은 "이 마이그레이션을 돌린 계정"이다.
     */
    @Test
    fun `V001 이 앞으로 만들어질 테이블의 앱 권한을 예약한다`() {
        migrate().use { conn ->
            val acl = conn.rows(
                """
                SELECT array_to_string(d.defaclacl, ' ')
                FROM pg_default_acl d
                JOIN pg_namespace n ON n.oid = d.defaclnamespace
                WHERE n.nspname = '$REAL_SCHEMA' AND d.defaclobjtype = 'r'
                """.trimIndent(),
            )

            assertThat(acl)
                .`as`("public 스키마 테이블에 대한 기본 권한 항목이 있다")
                .isNotEmpty()
            assertThat(acl.joinToString(" "))
                .`as`("kcocktail_app 에 SELECT·INSERT·UPDATE·DELETE (arwd)")
                .contains("kcocktail_app=arwd")
        }
    }

    /** `PRIN-T05` — 앱이 아니라 DB 가 막는다. 앱 역할로 붙어 실제로 거부되는지 본다. */
    @Test
    fun `RED13 - DELETE 시도가 DB 에서 거부된다`() {
        PostgresSupport.appConnection().use { app ->
            // 같은 역할로 SELECT 는 되어야 한다 — 커넥션 자체가 막힌 게 아님을 확인한다.
            app.exec("SELECT count(*) FROM $FIXTURE_SCHEMA.protected_row")

            assertThatThrownBy { app.exec("DELETE FROM $FIXTURE_SCHEMA.protected_row") }
                .isInstanceOf(SQLException::class.java)
                .hasMessageContaining("permission denied")

            // 회수하지 않은 테이블은 지워진다 — 거부가 권한 때문이지 다른 이유가 아님을 가른다.
            app.exec("DELETE FROM $FIXTURE_SCHEMA.unprotected_row")
        }
    }

    // ── 기준선 ─────────────────────────────────────────────────────────────

    /** 전 규칙을 실제 스키마에 한 번에. 위반이 있으면 **전부** 보여준다. */
    @Test
    fun `실제 스키마는 전 규칙을 통과한다`() {
        migrate().use { conn ->
            assertThat(SchemaLint.runAll(conn, REAL_SCHEMA)).isEmpty()

            // 린트가 무언가를 실제로 보고 있는지 — 대상이 0건이면 위 단언은 공허하다.
            assertThat(conn.rows("SELECT extname FROM pg_extension"))
                .`as`("public 스키마에 접근 자체는 되는가")
                .contains("pg_trgm")
        }
    }

    /** 픽스처에서는 9개 규칙 중 8개가 걸린다. 규칙이 통째로 죽으면 여기가 먼저 빨개진다. */
    @Test
    fun `픽스처는 8개 규칙에 걸린다`() {
        migrate().use { conn ->
            assertThat(SchemaLint.runAll(conn, FIXTURE_SCHEMA).keys)
                .containsExactlyInAnyOrder(
                    "공통 컬럼", "id identity", "updated_at 트리거", "테이블 명명",
                    "불리언 명명", "시각·날짜 명명", "네이티브 ENUM", "timestamptz",
                )
        }
    }

    // ── 헬퍼 ───────────────────────────────────────────────────────────────

    private fun assertRule(rule: Rule, catches: String) {
        assertThat(realViolations(rule)).`as`("실제 스키마에는 위반이 없다").isEmpty()
        assertThat(fixtureViolations(rule))
            .`as`("규칙이 실제로 잡는지")
            .anySatisfy { assertThat(it).contains(catches) }
    }

    private fun realViolations(rule: Rule) = migrate().use { rule(it, REAL_SCHEMA) }

    private fun fixtureViolations(rule: Rule) = migrate().use { rule(it, FIXTURE_SCHEMA) }

    private companion object {
        const val REAL_SCHEMA = "public"
        const val FIXTURE_SCHEMA = "lint_fixture"

        fun migrate(): Connection = PostgresSupport.migrateConnection()

        @JvmStatic
        @BeforeAll
        fun setUp() {
            PostgresSupport.flyway // V001 적용 + 앱 로그인 계정
            LintFixtures.create()
        }
    }
}

private typealias Rule = (Connection, String) -> List<String>

private fun Connection.exec(sql: String) = createStatement().use { it.execute(sql) }

private fun Connection.one(sql: String): String? =
    createStatement().use { st -> st.executeQuery(sql).use { if (it.next()) it.getString(1) else null } }

private fun Connection.rows(sql: String): List<String> =
    createStatement().use { st ->
        st.executeQuery(sql).use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
    }
