package kr.mut.cocktail.recipe

import kr.mut.support.PostgresSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.sql.Connection
import java.sql.SQLException

/**
 * ISSUE-010 — 레시피 스키마 (SPEC-06 §3.1).
 *
 * ## 부분 유니크 인덱스가 요체다
 *
 * `INV-COCKTAIL-07`(표준 레시피 정확히 1개)을 `WHERE version_type = 'standard'` 절이 막는다.
 * 절이 없으면 `bar_signature` 도 하나로 묶여 `PRIN-D03` 의 "제휴 바 버전 n개"가 불가능해진다.
 */
class RecipeSchemaTest {

    // ── RED 1~7 : 표준 레시피 1개 (INV-COCKTAIL-07) ───────────────────────

    @Test
    fun `RED1 - version_type 3종만 허용한다`() {
        val c1 = cocktail("vt-standard")
        insertRecipe(c1, "standard")

        val c2 = cocktail("vt-bar")
        insertRecipe(c2, "bar_signature", barId = 1)

        val c3 = cocktail("vt-user")
        insertRecipe(c3, "user", userId = insertUser())

        // 어느 제약이 먼저 걸리는지는 Postgres 가 정한다 — 알 수 없는 타입은
        // ck_recipe__author 의 세 갈래 어디에도 맞지 않아 그쪽이 먼저 잡기도 한다.
        // **둘 다 올바른 거부**이므로 이름을 못박지 않는다.
        assertThatThrownBy { insertRecipe(cocktail("vt-bogus"), "draft") }
            .isInstanceOf(SQLException::class.java)
            .hasMessageContaining("ck_recipe__")
    }

    @Test
    fun `RED2 - standard 가 1개면 통과한다`() {
        val id = cocktail("std-one")
        insertRecipe(id, "standard")

        assertThat(one("SELECT count(*) FROM recipe WHERE cocktail_id = $id")).isEqualTo("1")
    }

    /** **부분 유니크 인덱스**가 막는다. 앱 검증이 아니라 DB 다 (`PRIN-T05`). */
    @Test
    fun `RED3 - standard 레시피 2개는 DB 가 거부한다`() {
        val id = cocktail("std-two")
        insertRecipe(id, "standard")

        assertThatThrownBy { insertRecipe(id, "standard") }
            .isInstanceOf(SQLException::class.java)
            .hasMessageContaining("uq_recipe__standard")
    }

    /**
     * `PRIN-D03` 의 요점 — 제휴 바 버전은 **여럿**이다.
     * `WHERE` 절 없는 유니크였으면 여기가 막혔을 것이다.
     */
    @Test
    fun `RED4 - bar_signature 는 여러 개 가능하다`() {
        val id = cocktail("bar-many")
        insertRecipe(id, "standard")
        insertRecipe(id, "bar_signature", barId = 1)
        insertRecipe(id, "bar_signature", barId = 2)
        insertRecipe(id, "bar_signature", barId = 3)

        assertThat(one("SELECT count(*) FROM recipe WHERE cocktail_id = $id")).isEqualTo("4")
    }

    /**
     * SPEC-06 §3.1 이 "`bar_signature` 일 때만"·"`user` 일 때만"이라고 적었다.
     * 명시된 제약은 아니지만 DB 로 표현 가능하므로 걸었다 (§4 서두).
     */
    @Test
    fun `RED6-7 - 작성자 조합이 version_type 과 맞아야 한다`() {
        assertAll(
            listOf<() -> Unit>(
                {
                    assertThatThrownBy { insertRecipe(cocktail("bar-no-author"), "bar_signature") }
                        .`as`("bar_signature 인데 바가 없다")
                        .isInstanceOf(SQLException::class.java)
                        .hasMessageContaining("ck_recipe__author")
                },
                {
                    assertThatThrownBy { insertRecipe(cocktail("user-no-author"), "user") }
                        .`as`("user 인데 작성자가 없다")
                        .isInstanceOf(SQLException::class.java)
                },
                {
                    assertThatThrownBy { insertRecipe(cocktail("std-with-author"), "standard", barId = 1) }
                        .`as`("표준에 작성자가 붙으면 그것은 이미 표준이 아니다")
                        .isInstanceOf(SQLException::class.java)
                },
            ),
        )
    }

    // ── RED 8~11 : 재료 참조 (PRIN-D01) ───────────────────────────────────

    @Test
    fun `RED8 - ingredient_id 가 NOT NULL 이다`() {
        val r = recipe("ing-null")

        assertThatThrownBy {
            exec("INSERT INTO recipe_ingredient (recipe_id, position) VALUES ($r, 1)")
        }.isInstanceOf(SQLException::class.java)
    }

    /** `PRIN-D01` — **있으면 반드시 쓰인다.** 프리텍스트 입력란을 제공하지 않는다. */
    @Test
    fun `RED9 - 프리텍스트 재료명 컬럼이 없다`() {
        assertThat(columnsOf("recipe_ingredient"))
            .`as`("문자열로 저장하면 역검색과 바 연결이 전부 불가능해진다")
            .doesNotContainAnyElementsOf(
                listOf("ingredient_name", "name", "ingredient_text", "raw_ingredient", "free_text"),
            )
    }

    @Test
    fun `RED10 - 없는 ingredient_id 는 FK 가 거부한다`() {
        val r = recipe("ing-fk")

        assertThatThrownBy {
            exec("INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position) VALUES ($r, 999999, 1)")
        }.isInstanceOf(SQLException::class.java)
    }

    /** DECISIONS §1.1 — 승인을 기다리면 에디터 작업이 끊긴다. 발행에서 막는다. */
    @Test
    fun `RED11 - 미승인 재료도 draft 레시피에 넣을 수 있다`() {
        val r = recipe("ing-pending")
        val pending = ingredient("pending-gin", approved = false)

        exec("INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position) VALUES ($r, $pending, 1)")

        assertThat(one("SELECT count(*) FROM recipe_ingredient WHERE recipe_id = $r")).isEqualTo("1")
    }

    // ── RED 12~16 : 단위 · 역할 ───────────────────────────────────────────

    @Test
    fun `RED12 - unit 5종만 허용한다`() {
        val r = recipe("units")
        val i = ingredient("unit-gin")

        listOf("ml", "dash", "barspoon", "piece", "top_up").forEachIndexed { idx, unit ->
            exec(
                "INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, unit) " +
                    "VALUES ($r, $i, ${idx + 1}, '$unit')",
            )
        }

        assertThatThrownBy {
            exec(
                "INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, unit) " +
                    "VALUES ($r, $i, 99, 'oz')",
            )
        }.isInstanceOf(SQLException::class.java).hasMessageContaining("ck_recipe_ingredient__unit")
    }

    @Test
    fun `RED13 - role 5종만 허용한다`() {
        val r = recipe("roles")
        val i = ingredient("role-gin")

        listOf("base", "modifier", "sweetener", "citrus", "garnish").forEachIndexed { idx, role ->
            exec(
                "INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, role) " +
                    "VALUES ($r, $i, ${idx + 1}, '$role')",
            )
        }

        assertThatThrownBy {
            exec(
                "INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, role) " +
                    "VALUES ($r, $i, 99, 'topping')",
            )
        }.isInstanceOf(SQLException::class.java).hasMessageContaining("ck_recipe_ingredient__role")
    }

    @Test
    fun `RED14 - amount 가 음수면 거부된다`() {
        val r = recipe("amount-negative")
        val i = ingredient("neg-gin")

        assertThatThrownBy {
            exec(
                "INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount) " +
                    "VALUES ($r, $i, 1, -10)",
            )
        }.isInstanceOf(SQLException::class.java).hasMessageContaining("ck_recipe_ingredient__amount")
    }

    /** `top_up` 은 "채운다"라 수량이 없다. ml 로 고정하면 잔 크기에 종속된다. */
    @Test
    fun `RED16 - top_up 은 amount 가 없어도 된다`() {
        val r = recipe("top-up")
        val i = ingredient("tonic")

        exec(
            "INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, unit) " +
                "VALUES ($r, $i, 1, 'top_up')",
        )

        assertThat(one("SELECT amount FROM recipe_ingredient WHERE recipe_id = $r")).isNull()
    }

    // ── RED 20~23 : counts_for_stock · 대체재 ─────────────────────────────

    @Test
    fun `RED20 - counts_for_stock 은 NOT NULL 이다`() {
        val r = recipe("cfs-null")
        val i = ingredient("cfs-gin")

        assertThatThrownBy {
            exec(
                "INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, counts_for_stock) " +
                    "VALUES ($r, $i, 1, NULL)",
            )
        }.isInstanceOf(SQLException::class.java)
    }

    @Test
    fun `RED21 - 대체재가 자기 자신이면 거부된다`() {
        val r = recipe("self-sub")
        val i = ingredient("self-gin")

        assertThatThrownBy {
            exec(
                "INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, substitute_ingredient_id) " +
                    "VALUES ($r, $i, 1, $i)",
            )
        }.isInstanceOf(SQLException::class.java)
            .hasMessageContaining("ck_recipe_ingredient__self_substitute")
    }

    @Test
    fun `RED22-23 - 대체재와 안내는 선택이다`() {
        val r = recipe("sub-optional")
        val gin = ingredient("sub-gin")
        val vodka = ingredient("sub-vodka")

        // 없어도 된다
        exec("INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position) VALUES ($r, $gin, 1)")

        // 있으면 함께 저장된다
        exec(
            """INSERT INTO recipe_ingredient
               (recipe_id, ingredient_id, position, substitute_ingredient_id, substitute_note)
               VALUES ($r, $gin, 2, $vodka, '보드카로 대체 가능')""",
        )

        assertThat(one("SELECT substitute_note FROM recipe_ingredient WHERE recipe_id = $r AND position = 2"))
            .isEqualTo("보드카로 대체 가능")
    }

    // ── RED 24~27 : 스텝 · 순서 ───────────────────────────────────────────

    @Test
    fun `RED24 - step_no 가 중복되면 거부된다`() {
        val r = recipe("step-dup")
        exec("INSERT INTO recipe_step (recipe_id, step_no, text) VALUES ($r, 1, '얼음을 채운다')")

        assertThatThrownBy {
            exec("INSERT INTO recipe_step (recipe_id, step_no, text) VALUES ($r, 1, '진을 붓는다')")
        }.isInstanceOf(SQLException::class.java)
    }

    @Test
    fun `RED25 - 스텝 텍스트가 비면 거부된다`() {
        val r = recipe("step-blank")

        assertAll(
            listOf("", "   ", "\t\n").map<String, () -> Unit> { blank ->
                {
                    assertThatThrownBy {
                        execParam("INSERT INTO recipe_step (recipe_id, step_no, text) VALUES ($r, 1, ?)", blank)
                    }.`as`("길이 %d", blank.length)
                        .isInstanceOf(SQLException::class.java)
                }
            },
        )
    }

    @Test
    fun `RED26 - technique_ref 는 선택이다`() {
        val r = recipe("step-no-ref")
        exec("INSERT INTO recipe_step (recipe_id, step_no, text) VALUES ($r, 1, '가볍게 젓는다')")

        assertThat(one("SELECT technique_ref FROM recipe_step WHERE recipe_id = $r")).isNull()
    }

    @Test
    fun `RED27 - position 이 레시피 안에서 유일하다`() {
        val r = recipe("pos-dup")
        val i = ingredient("pos-gin")
        exec("INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position) VALUES ($r, $i, 1)")

        assertThatThrownBy {
            exec("INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position) VALUES ($r, $i, 1)")
        }.isInstanceOf(SQLException::class.java)
    }

    // ── RED 28~30 : 인덱스 · 애그리게이트 ─────────────────────────────────

    /** SPEC-06 §5 — 역검색과 재료 사전("이 재료를 쓰는 칵테일")이 이 인덱스를 탄다. */
    @Test
    fun `RED28 - recipe_ingredient 에 ingredient_id 인덱스가 있다`() {
        assertThat(rows("""
            SELECT indexdef FROM pg_indexes
            WHERE tablename = 'recipe_ingredient' AND indexdef LIKE '%(ingredient_id)%'
        """.trimIndent()))
            .isNotEmpty()
    }

    /** SPEC-02 §1 — 애그리게이트 경계 = 트랜잭션 경계. */
    @Test
    fun `RED29 - 칵테일 없이 레시피를 만들 수 없다`() {
        assertThatThrownBy {
            exec("INSERT INTO recipe (cocktail_id, version_type) VALUES (999999, 'standard')")
        }.isInstanceOf(SQLException::class.java)

        assertThatThrownBy {
            exec("INSERT INTO recipe (version_type) VALUES ('standard')")
        }.isInstanceOf(SQLException::class.java)
    }

    /**
     * 칵테일이 지워지면 레시피도 간다.
     * 실제로는 `cocktail` 이 `REVOKE DELETE` 라 앱 역할로는 일어나지 않는다 (`PRIN-D05`).
     */
    @Test
    fun `RED30 - 칵테일 삭제시 레시피가 CASCADE 된다`() {
        val c = cocktail("cascade-me")
        val r = insertRecipe(c, "standard")
        val i = ingredient("cascade-gin")
        exec("INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position) VALUES ($r, $i, 1)")
        exec("INSERT INTO recipe_step (recipe_id, step_no, text) VALUES ($r, 1, '만든다')")

        exec("DELETE FROM cocktail WHERE id = $c") // 마이그레이션 역할이라 가능하다

        assertThat(one("SELECT count(*) FROM recipe WHERE id = $r")).isEqualTo("0")
        assertThat(one("SELECT count(*) FROM recipe_ingredient WHERE recipe_id = $r")).isEqualTo("0")
        assertThat(one("SELECT count(*) FROM recipe_step WHERE recipe_id = $r")).isEqualTo("0")
    }

    // ── 헬퍼 ───────────────────────────────────────────────────────────────

    private var seq = 0

    private fun cocktail(slug: String): Long = conn().use { c ->
        c.autoCommit = false
        val id = c.createStatement().use { st ->
            st.executeQuery(
                """
                INSERT INTO cocktail (slug, name_ko, name_en, summary,
                    base_spirit, style_primary, method, sweetness, glass_type, abv_calculated)
                VALUES ('$slug-${seq++}', '테스트', 'test', '요약',
                    'gin', 'highball', 'build', 'dry', '하이볼 글라스', 12)
                RETURNING id
                """.trimIndent(),
            ).use { rs -> rs.next(); rs.getLong(1) }
        }
        c.createStatement().use { it.execute("INSERT INTO cocktail_style VALUES ($id, 'highball')") }
        c.commit()
        id
    }

    private fun recipe(slug: String): Long = insertRecipe(cocktail(slug), "standard")

    private fun insertRecipe(
        cocktailId: Long,
        versionType: String,
        barId: Long? = null,
        userId: Long? = null,
    ): Long = conn().use { c ->
        c.createStatement().use { st ->
            st.executeQuery(
                """
                INSERT INTO recipe (cocktail_id, version_type, author_bar_id, author_user_id)
                VALUES ($cocktailId, '$versionType', ${barId ?: "NULL"}, ${userId ?: "NULL"})
                RETURNING id
                """.trimIndent(),
            ).use { rs -> rs.next(); rs.getLong(1) }
        }
    }

    private fun ingredient(slug: String, approved: Boolean = true): Long = conn().use { c ->
        c.createStatement().use { st ->
            st.executeQuery(
                """
                INSERT INTO ingredient (slug, name_ko, name_en, category, domestic_availability, is_approved)
                VALUES ('$slug-${seq++}', '재료', 'ingredient', 'spirit', 'common', $approved)
                RETURNING id
                """.trimIndent(),
            ).use { rs -> rs.next(); rs.getLong(1) }
        }
    }

    private fun insertUser(): Long = conn().use { c ->
        c.createStatement().use { st ->
            st.executeQuery(
                """INSERT INTO "user" (provider, provider_uid, display_name)
                   VALUES ('kakao', 'recipe-${seq++}', '테스터') RETURNING id""",
            ).use { rs -> rs.next(); rs.getLong(1) }
        }
    }

    private fun columnsOf(table: String): List<String> = rows(
        "SELECT column_name FROM information_schema.columns " +
            "WHERE table_schema = 'public' AND table_name = '$table'",
    )

    private fun conn(): Connection = PostgresSupport.migrateConnection()

    private fun exec(sql: String) = conn().use { it.createStatement().use { st -> st.execute(sql) } }

    private fun execParam(sql: String, param: String) = conn().use { c ->
        c.prepareStatement(sql).use { st -> st.setString(1, param); st.execute() }
    }

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
