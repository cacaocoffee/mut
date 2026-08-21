package kr.mut.cocktail

import kr.mut.support.PostgresSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.sql.Connection
import java.sql.SQLException

/**
 * ISSUE-009 — 칵테일 스키마와 DB 강제 불변식 (SPEC-06 §3.1 · §4.1 · §4.2).
 *
 * ## 복합 FK 가 이 이슈의 요체다
 *
 * `style_primary ∈ styles`(`INV-COCKTAIL-03`)를 **DB 가 막는다.**
 * 배열이었으면 앱에서만 막을 수 있었고, 배치·마이그레이션이 그것을 우회했을 것이다.
 * SPEC-06 §1.4 가 배열 대신 조인 테이블을 택한 가장 큰 이유가 이것이다.
 */
class CocktailSchemaTest {

    // ── RED 1~7 : 3축 NOT NULL · CHECK ────────────────────────────────────

    @Test
    fun `RED1-3 - 3축이 없으면 저장이 거부된다`() {
        assertAll(
            listOf("base_spirit", "style_primary", "method").map<String, () -> Unit> { column ->
                {
                    assertThatThrownBy { insertMissing(column) }
                        .`as`("%s 없이 저장", column)
                        .isInstanceOf(SQLException::class.java)
                }
            },
        )
    }

    @Test
    fun `RED4 - 3축이 전부 있으면 저장된다`() {
        val id = insertCocktail("gin-tonic", styles = setOf("highball"), primary = "highball")
        assertThat(one("SELECT slug FROM cocktail WHERE id = $id")).isEqualTo("gin-tonic")
    }

    /** ADR-0002 확정 슬러그 전수. `soju` 가 아니라 `korean` 이다. */
    @Test
    fun `RED5 - base_spirit 10종만 허용한다`() {
        val allowed = listOf(
            "gin", "vodka", "whisky", "rum", "agave",
            "brandy", "liqueur", "wine", "korean", "non-alcoholic",
        )
        assertAll(
            allowed.map<String, () -> Unit> { base ->
                {
                    insertCocktail(
                        "base-$base",
                        base = base,
                        // INV-COCKTAIL-06 — 무알콜이면 abv 가 0 이어야 한다
                        abv = if (base == "non-alcoholic") "0" else "12",
                    )
                }
            },
        )

        assertThatThrownBy { insertCocktail("base-soju", base = "soju") }
            .`as`("ADR-0002 §4 — 막걸리·문배주를 소주로 부르는 건 부정확하다")
            .isInstanceOf(SQLException::class.java)
            .hasMessageContaining("ck_cocktail__base_spirit")
    }

    /**
     * G-23 — **슬러그다.** 이슈 본문 SQL 은 PascalCase(`Build`)였으나
     * SPEC-06 §3.1 의 이 컬럼이 슬러그를 저장하고 계약(OpenAPI)도 슬러그다.
     */
    @Test
    fun `RED6 - method 5종만 허용한다`() {
        assertAll(
            listOf("build", "shake", "stir", "blend", "etc").map<String, () -> Unit> { method ->
                { insertCocktail("method-$method", method = method) }
            },
        )

        assertThatThrownBy { insertCocktail("method-pascal", method = "Build") }
            .`as`("PascalCase 는 프로토타입 산물이다 (G-23)")
            .isInstanceOf(SQLException::class.java)
            .hasMessageContaining("ck_cocktail__method")
    }

    @Test
    fun `RED7 - style 9종만 허용한다`() {
        val allowed = listOf(
            "highball", "sour", "spirit-forward", "spritz", "tiki",
            "creamy", "hot", "frozen", "shot",
        )
        assertAll(
            allowed.map<String, () -> Unit> { style ->
                { insertCocktail("style-$style", styles = setOf(style), primary = style) }
            },
        )

        val id = insertCocktail("style-bogus", styles = setOf("highball"), primary = "highball")
        assertThatThrownBy { exec("INSERT INTO cocktail_style VALUES ($id, 'martini')") }
            .isInstanceOf(SQLException::class.java)
            .hasMessageContaining("ck_cocktail_style__style")
    }

    // ── RED 8~12 : style_primary ∈ styles (복합 FK) ───────────────────────

    @Test
    fun `RED8 - style_primary 가 styles 에 있으면 저장된다`() {
        insertCocktail("primary-ok", styles = setOf("highball", "sour"), primary = "sour")
    }

    /** **앱 검증이 아니라 DB 다.** 배치가 직접 INSERT 해도 막힌다 (`PRIN-T05`). */
    @Test
    fun `RED9 - style_primary 가 styles 에 없으면 DB 가 거부한다`() {
        assertThatThrownBy {
            insertCocktail("primary-missing", styles = setOf("highball"), primary = "tiki")
        }.isInstanceOf(SQLException::class.java)
            .hasMessageContaining("fk_cocktail__style_primary")
    }

    /**
     * `DEFERRABLE INITIALLY DEFERRED` 가 없으면 이것이 불가능하다 —
     * 칵테일 행을 넣는 순간 아직 없는 스타일 행을 가리키게 된다.
     */
    @Test
    fun `RED10 - 같은 트랜잭션에서 cocktail 과 style 을 삽입할 수 있다`() {
        conn().use { c ->
            c.autoCommit = false
            c.createStatement().use { st ->
                val id = st.executeQuery(insertSql("deferred-ok", primary = "tiki"))
                    .use { rs -> rs.next(); rs.getLong(1) }
                st.execute("INSERT INTO cocktail_style VALUES ($id, 'tiki')")
            }
            c.commit() // 여기서 한 번 검사한다
        }

        assertThat(one("SELECT slug FROM cocktail WHERE slug = 'deferred-ok'")).isEqualTo("deferred-ok")
    }

    @Test
    fun `RED11 - styles 에서 style_primary 를 제거하면 거부된다`() {
        val id = insertCocktail("primary-remove", styles = setOf("highball", "sour"), primary = "sour")

        assertThatThrownBy { exec("DELETE FROM cocktail_style WHERE cocktail_id = $id AND style = 'sour'") }
            .isInstanceOf(SQLException::class.java)

        // 대표가 아닌 것은 지울 수 있다.
        exec("DELETE FROM cocktail_style WHERE cocktail_id = $id AND style = 'highball'")
    }

    /**
     * 복합 FK 가 가리킬 **유일성 제약**이 `(cocktail_id, style)` 위에 있어야 한다.
     *
     * `contype` 을 `'u'` 로 못박지 않는다 — PK 도 유효한 FK 참조 대상이고,
     * SPEC-06 §4.2 처럼 PK 와 UNIQUE 를 둘 다 걸면 Postgres 가 중복 인덱스를 PK 로 합쳐
     * 실제 타입이 `'p'` 가 된다. 확인할 것은 이름이나 타입이 아니라 **그 자리에 유일성이 있는가**다.
     */
    @Test
    fun `RED12 - cocktail_style 에 복합 유일성 제약이 있다`() {
        assertThat(rows("""
            SELECT conname FROM pg_constraint
            WHERE conrelid = 'cocktail_style'::regclass
              AND contype IN ('p', 'u')
              AND conkey = ARRAY[
                  (SELECT attnum FROM pg_attribute
                   WHERE attrelid = 'cocktail_style'::regclass AND attname = 'cocktail_id'),
                  (SELECT attnum FROM pg_attribute
                   WHERE attrelid = 'cocktail_style'::regclass AND attname = 'style')
              ]::smallint[]
        """.trimIndent()))
            .`as`("복합 FK 가 가리킬 유일성이 없으면 INV-COCKTAIL-03 을 DB 가 못 막는다")
            .isNotEmpty()

        // 그 유일성이 실제로 동작하는지 — 제약이 있어도 컬럼이 어긋나면 소용없다.
        val id = insertCocktail("uniq-works", styles = setOf("tiki"), primary = "tiki")
        assertThatThrownBy { exec("INSERT INTO cocktail_style VALUES ($id, 'tiki')") }
            .isInstanceOf(SQLException::class.java)
    }

    @Test
    fun `RED16 - styles 중복은 거부된다`() {
        val id = insertCocktail("style-dup", styles = setOf("highball"), primary = "highball")

        assertThatThrownBy { exec("INSERT INTO cocktail_style VALUES ($id, 'highball')") }
            .isInstanceOf(SQLException::class.java)
    }

    // ── RED 21~22 : 향 태그 ───────────────────────────────────────────────

    @Test
    fun `RED21 - flavor 10종만 허용한다`() {
        val id = insertCocktail("flavor-all", styles = setOf("sour"), primary = "sour")
        val allowed = listOf(
            "citrus", "sour", "fruity", "floral", "herbal",
            "spicy", "smoky", "bitter", "nutty", "creamy",
        )
        allowed.forEach { exec("INSERT INTO cocktail_aroma_tag VALUES ($id, '$it')") }

        assertThatThrownBy { exec("INSERT INTO cocktail_aroma_tag VALUES ($id, 'umami')") }
            .isInstanceOf(SQLException::class.java)
            .hasMessageContaining("ck_cocktail_aroma_tag__tag")
    }

    @Test
    fun `RED22 - aroma_tags 중복은 거부된다`() {
        val id = insertCocktail("aroma-dup", styles = setOf("sour"), primary = "sour")
        exec("INSERT INTO cocktail_aroma_tag VALUES ($id, 'citrus')")

        assertThatThrownBy { exec("INSERT INTO cocktail_aroma_tag VALUES ($id, 'citrus')") }
            .isInstanceOf(SQLException::class.java)
    }

    // ── RED 23~26 : 무알콜 정합 (INV-COCKTAIL-06) ─────────────────────────

    @Test
    fun `RED23-24 - 무알콜과 abv 0 은 양방향이다`() {
        insertCocktail("na-ok", base = "non-alcoholic", abv = "0")
        insertCocktail("alcoholic-ok", base = "gin", abv = "12.5")
    }

    @Test
    fun `RED25 - non_alcoholic 인데 abv 15 면 거부된다`() {
        assertThatThrownBy { insertCocktail("na-with-abv", base = "non-alcoholic", abv = "15") }
            .isInstanceOf(SQLException::class.java)
            .hasMessageContaining("ck_cocktail__non_alcoholic")
    }

    @Test
    fun `RED26 - gin 인데 abv 0 이면 거부된다`() {
        assertThatThrownBy { insertCocktail("gin-zero", base = "gin", abv = "0") }
            .isInstanceOf(SQLException::class.java)
            .hasMessageContaining("ck_cocktail__non_alcoholic")
    }

    /**
     * ⚠️ 의도된 구멍이다. `abv` 가 생성 컬럼이라 원본 둘이 NULL 이면 `abv` 도 NULL 이고
     * CHECK 가 `(x) = (NULL)` → NULL 이라 **통과한다.**
     *
     * draft 단계에서는 레시피를 다 쓰기 전이라 도수가 없다. 여기서 막으면
     * 칵테일 행을 만들 수조차 없다 — **발행 게이트(이슈 013)가 다시 확인한다.**
     */
    @Test
    fun `abv 가 아직 없으면 무알콜 검사를 통과한다`() {
        insertCocktail("draft-no-abv", base = "gin", abv = null)

        assertThat(one("SELECT abv FROM cocktail WHERE slug = 'draft-no-abv'"))
            .`as`("이슈 013 이 발행 시점에 막아야 한다")
            .isNull()
    }

    // ── RED 27~30 : 상태 · 슬러그 · 삭제 ──────────────────────────────────

    @Test
    fun `RED27 - status 3종만 허용한다`() {
        val id = insertCocktail("status-check", styles = setOf("sour"), primary = "sour")
        listOf("draft", "published", "archived").forEach {
            exec("UPDATE cocktail SET status = '$it' WHERE id = $id")
        }

        assertThatThrownBy { exec("UPDATE cocktail SET status = 'deleted' WHERE id = $id") }
            .isInstanceOf(SQLException::class.java)
            .hasMessageContaining("ck_cocktail__status")
    }

    @Test
    fun `RED28 - 생성시 status 는 draft 다`() {
        val id = insertCocktail("new-cocktail")
        assertThat(one("SELECT status FROM cocktail WHERE id = $id")).isEqualTo("draft")
    }

    @Test
    fun `RED29 - slug 가 유일하다`() {
        insertCocktail("unique-slug")
        assertThatThrownBy { insertCocktail("unique-slug") }.isInstanceOf(SQLException::class.java)
    }

    /** `PRIN-D05` — 삭제가 아니라 상태 전이다. 앱 역할에서 권한 자체를 회수했다. */
    @Test
    fun `RED30 - 앱 역할에 cocktail DELETE 권한이 없다`() {
        assertThat(
            one("SELECT has_table_privilege('mut_app', 'cocktail', 'DELETE')::text"),
        ).isEqualTo("false")

        // 읽기·쓰기는 된다 — 삭제만 막혔다.
        assertThat(one("SELECT has_table_privilege('mut_app', 'cocktail', 'SELECT')::text"))
            .isEqualTo("true")
        assertThat(one("SELECT has_table_privilege('mut_app', 'cocktail', 'UPDATE')::text"))
            .isEqualTo("true")
    }

    // ── 헬퍼 ───────────────────────────────────────────────────────────────

    private fun insertSql(
        slug: String,
        base: String = "gin",
        primary: String = "highball",
        method: String = "build",
        abv: String? = "12",
    ) = """
        INSERT INTO cocktail (
            slug, name_ko, name_en, summary,
            base_spirit, style_primary, method, sweetness, glass_type, abv_calculated
        ) VALUES (
            '$slug', '테스트', 'test', '요약',
            '$base', '$primary', '$method', 'dry', '하이볼 글라스', ${abv ?: "NULL"}
        ) RETURNING id
    """.trimIndent()

    /** 스타일 행까지 한 트랜잭션에서 넣는다 — `DEFERRABLE` 이라 순서를 신경 쓰지 않는다. */
    private fun insertCocktail(
        slug: String,
        base: String = "gin",
        styles: Set<String> = setOf("highball"),
        primary: String = "highball",
        method: String = "build",
        abv: String? = "12",
    ): Long = conn().use { c ->
        c.autoCommit = false
        val id = c.createStatement().use { st ->
            st.executeQuery(insertSql(slug, base, primary, method, abv)).use { rs -> rs.next(); rs.getLong(1) }
        }
        c.createStatement().use { st ->
            styles.forEach { st.execute("INSERT INTO cocktail_style VALUES ($id, '$it')") }
        }
        c.commit()
        id
    }

    private fun insertMissing(column: String) {
        val columns = mutableMapOf(
            "base_spirit" to "'gin'",
            "style_primary" to "'highball'",
            "method" to "'build'",
        )
        columns.remove(column)
        exec(
            """
            INSERT INTO cocktail (slug, name_ko, name_en, summary, sweetness, glass_type,
                                  ${columns.keys.joinToString(", ")})
            VALUES ('missing-$column', 'x', 'x', 'x', 'dry', 'glass', ${columns.values.joinToString(", ")})
            """.trimIndent(),
        )
    }

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
