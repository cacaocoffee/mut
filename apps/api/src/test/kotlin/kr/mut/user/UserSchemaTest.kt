package kr.mut.user

import kr.mut.support.PostgresSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.SQLException

/**
 * ISSUE-005 RED 1~11 — `user` · `user_role` 스키마 (SPEC-06 §3.5).
 *
 * ## 없는 것을 검사한다
 *
 * RED 5·6 은 컬럼의 **부재**를 단언한다. 주석으로만 두면 다음 이슈가 조용히 추가한다 —
 * 성인 인증(ADR-0004)과 위치(`PRIN-D04`)는 "안 만들기로 한" 것이라서
 * 만들어졌다는 사실 자체가 결정을 뒤집는 것이다.
 *
 * 제약은 DB 가 강제해야 한다 (`PRIN-T05`). 앱에서만 막으면 배치·마이그레이션이 우회한다.
 */
class UserSchemaTest {

    // ── RED 1~4 : user ─────────────────────────────────────────────────────

    @Test
    fun `RED1 - provider 는 kakao naver apple 3종만 허용한다`() {
        listOf("kakao", "naver", "apple").forEach { insertUser(provider = it, uid = "uid-$it") }

        assertThatThrownBy { insertUser(provider = "google", uid = "uid-google") }
            .isInstanceOf(SQLException::class.java)
            .hasMessageContaining("ck_user__provider")
    }

    @Test
    fun `RED2 - provider 와 provider_uid 조합이 유일하다`() {
        insertUser(provider = "kakao", uid = "duplicate-me")

        assertThatThrownBy { insertUser(provider = "kakao", uid = "duplicate-me") }
            .isInstanceOf(SQLException::class.java)

        // 제공자가 다르면 같은 uid 가 와도 된다 — 카카오 12345 와 네이버 12345 는 남남이다.
        insertUser(provider = "naver", uid = "duplicate-me")
    }

    @Test
    fun `RED3 - display_name 은 필수다`() {
        assertThatThrownBy {
            exec("""INSERT INTO "user" (provider, provider_uid) VALUES ('kakao', 'no-name')""")
        }.isInstanceOf(SQLException::class.java)
    }

    /** SPEC-08 §4.2 — 애플 비공개 릴레이는 이메일을 주지 않는다. NOT NULL 이면 로그인이 막힌다. */
    @Test
    fun `RED4 - email 은 NULL 을 허용한다`() {
        val id = insertUser(provider = "apple", uid = "private-relay")
        assertThat(one("SELECT email FROM \"user\" WHERE id = $id")).isNull()
    }

    // ── RED 5~6 : 없는 것 ──────────────────────────────────────────────────

    /** ADR-0004 — 판매를 하지 않으므로 전면 성인 인증을 요구하지 않는다. */
    @Test
    fun `RED5 - 성인 인증 관련 컬럼이 없다`() {
        assertThat(columnsOf("user"))
            .`as`("ADR-0004 를 뒤집으려면 문서를 먼저 고친다")
            .doesNotContainAnyElementsOf(
                listOf("birth_date", "birthdate", "age", "age_verified", "adult_verified", "ci", "di"),
            )
    }

    /** `PRIN-D04` — 좌표는 요청 스코프에만 산다. 세션에도 저장하지 않는다 (DECISIONS §1). */
    @Test
    fun `RED6 - 위치 좌표 컬럼이 없다`() {
        assertThat(columnsOf("user"))
            .doesNotContainAnyElementsOf(
                listOf("lat", "lng", "latitude", "longitude", "location", "geom", "address"),
            )
    }

    /** DECISIONS §1 — 제공자 갱신 토큰을 저장하지 않는다. 세션이 우리 것이다. */
    @Test
    fun `제공자 토큰 컬럼이 없다`() {
        assertThat(columnsOf("user"))
            .doesNotContainAnyElementsOf(
                listOf("access_token", "refresh_token", "provider_token", "id_token"),
            )
    }

    // ── RED 7~11 : user_role ───────────────────────────────────────────────

    @Test
    fun `RED7 - user_role PK 가 user_id 와 role 복합이다`() {
        assertThat(primaryKeyOf("user_role")).containsExactly("user_id", "role")
    }

    @Test
    fun `RED8 - 역할 4종만 허용한다`() {
        val id = insertUser(provider = "kakao", uid = "roles-4")
        listOf("member", "editor", "partner_owner", "admin").forEach { grant(id, it) }

        assertThatThrownBy { grant(id, "superuser") }
            .isInstanceOf(SQLException::class.java)
            .hasMessageContaining("ck_user_role__role")
    }

    /** SPEC-06 §3.5 — 팀이 작아 한 사람이 에디터이면서 관리자인 경우가 실제로 생긴다. */
    @Test
    fun `RED9 - 한 사용자가 복수 역할을 가질 수 있다`() {
        val id = insertUser(provider = "kakao", uid = "multi-role")
        grant(id, "editor")
        grant(id, "admin")

        assertThat(rows("SELECT role FROM user_role WHERE user_id = $id ORDER BY role"))
            .containsExactly("admin", "editor")
    }

    @Test
    fun `RED10 - 같은 역할 중복 부여는 거부된다`() {
        val id = insertUser(provider = "kakao", uid = "dup-role")
        grant(id, "editor")

        assertThatThrownBy { grant(id, "editor") }.isInstanceOf(SQLException::class.java)
    }

    @Test
    fun `RED11 - granted_by 와 granted_at 이 기록된다`() {
        val admin = insertUser(provider = "kakao", uid = "granter")
        val target = insertUser(provider = "kakao", uid = "grantee")

        exec("INSERT INTO user_role (user_id, role, granted_by) VALUES ($target, 'editor', $admin)")

        assertThat(one("SELECT granted_by FROM user_role WHERE user_id = $target"))
            .isEqualTo(admin.toString())
        assertThat(one("SELECT granted_at FROM user_role WHERE user_id = $target")).isNotNull()
    }

    /** 부여자가 탈퇴해도 이력은 남는다. `ON DELETE SET NULL` 이 그 뜻이다. */
    @Test
    fun `부여자가 삭제돼도 역할 행은 남는다`() {
        val admin = insertUser(provider = "kakao", uid = "granter-leaves")
        val target = insertUser(provider = "kakao", uid = "grantee-stays")
        exec("INSERT INTO user_role (user_id, role, granted_by) VALUES ($target, 'editor', $admin)")

        exec("""DELETE FROM "user" WHERE id = $admin""")

        assertThat(one("SELECT role FROM user_role WHERE user_id = $target")).isEqualTo("editor")
        assertThat(one("SELECT granted_by FROM user_role WHERE user_id = $target")).isNull()
    }

    // ── 헬퍼 ───────────────────────────────────────────────────────────────

    private var seq = 0

    private fun insertUser(provider: String, uid: String): Long {
        val unique = "$uid-${seq++}"
        return conn().use { c ->
            c.prepareStatement(
                """INSERT INTO "user" (provider, provider_uid, display_name) VALUES (?, ?, ?) RETURNING id""",
            ).use { st ->
                st.setString(1, provider)
                st.setString(2, if (uid.startsWith("duplicate")) uid else unique)
                st.setString(3, "테스터")
                st.executeQuery().use { rs -> rs.next(); rs.getLong(1) }
            }
        }
    }

    private fun grant(userId: Long, role: String) =
        exec("INSERT INTO user_role (user_id, role) VALUES ($userId, '$role')")

    private fun columnsOf(table: String): List<String> = rows(
        "SELECT column_name FROM information_schema.columns " +
            "WHERE table_schema = 'public' AND table_name = '$table'",
    )

    private fun primaryKeyOf(table: String): List<String> = rows(
        """
        SELECT a.attname
        FROM pg_index i
        JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey)
        WHERE i.indrelid = '$table'::regclass AND i.indisprimary
        ORDER BY array_position(i.indkey, a.attnum)
        """.trimIndent(),
    )

    private fun conn(): Connection = PostgresSupport.migrateConnection()

    private fun exec(sql: String) = conn().use { it.createStatement().use { st -> st.execute(sql) } }

    private fun one(sql: String): String? = conn().use { c ->
        c.createStatement().use { st -> st.executeQuery(sql).use { if (it.next()) it.getString(1) else null } }
    }

    private fun rows(sql: String): List<String> = conn().use { c ->
        c.createStatement().use { st ->
            st.executeQuery(sql).use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
        }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun migrate() {
            PostgresSupport.flyway
        }
    }
}
