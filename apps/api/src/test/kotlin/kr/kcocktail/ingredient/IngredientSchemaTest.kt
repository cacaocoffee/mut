package kr.kcocktail.ingredient

import kr.kcocktail.support.PostgresSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.sql.Connection
import java.sql.SQLException

/**
 * ISSUE-008 — 재료 마스터 스키마 (SPEC-06 §3.2).
 *
 * ## DB 가 막는 것과 앱이 막는 것
 *
 * `INV-INGREDIENT-01` 은 **양쪽**에 있다. SPEC-06 §4.3 은 "앱 강제"로 분류했지만
 * 조건부 CHECK 로 쓸 수 있고, §4 서두가 "DB 로 강제할 수 있는 것은 DB 에서 한다"고 했다.
 * 여기서는 DB 쪽을 본다 — 앱 쪽은 `IngredientDomainTest` 다. 문서 차이는 GAPS G-24.
 */
class IngredientSchemaTest {

    // ── RED 1 · 5~6 : CHECK 제약 ──────────────────────────────────────────

    @Test
    fun `RED1 - 카테고리 7종만 허용한다`() {
        assertAll(
            listOf("spirit", "liqueur", "bitters", "syrup", "juice", "garnish", "mixer")
                .map<String, () -> Unit> { category ->
                    { insert(slug = "cat-$category", category = category) }
                },
        )

        assertThatThrownBy { insert(slug = "cat-bogus", category = "powder") }
            .isInstanceOf(SQLException::class.java)
            .hasMessageContaining("ck_ingredient__category")
    }

    @Test
    fun `RED5 - domestic_availability 4종만 허용한다`() {
        assertAll(
            listOf("common", "specialty").map<String, () -> Unit> { availability ->
                { insert(slug = "av-$availability", availability = availability) }
            } + listOf("import_only", "unavailable").map<String, () -> Unit> { availability ->
                { insert(slug = "av-$availability", availability = availability, note = "진으로 대체") }
            },
        )

        assertThatThrownBy { insert(slug = "av-bogus", availability = "maybe") }
            .isInstanceOf(SQLException::class.java)
            .hasMessageContaining("ck_ingredient__availability")
    }

    @Test
    fun `RED6 - domestic_availability 는 NOT NULL 이다`() {
        assertThatThrownBy {
            exec(
                """INSERT INTO ingredient (slug, name_ko, name_en, category)
                   VALUES ('no-availability', '무엇', 'something', 'spirit')""",
            )
        }.isInstanceOf(SQLException::class.java)
    }

    // ── RED 7~10 : INV-INGREDIENT-01 (DB 쪽) ──────────────────────────────

    /** `R-F1.3-2` — 국내에서 못 구하는 재료를 안내 없이 두면 레시피가 읽을거리가 된다. */
    @Test
    fun `RED7-8 - 미유통이면 substitute_note 가 필수다`() {
        assertAll(
            listOf("import_only", "unavailable").map<String, () -> Unit> { availability ->
                {
                    assertThatThrownBy { insert(slug = "no-note-$availability", availability = availability) }
                        .`as`("%s 인데 대체재 안내가 없다", availability)
                        .isInstanceOf(SQLException::class.java)
                        .hasMessageContaining("ck_ingredient__substitute")
                }
            },
        )
    }

    @Test
    fun `RED9 - common 과 specialty 는 안내가 없어도 된다`() {
        insert(slug = "common-no-note", availability = "common")
        insert(slug = "specialty-no-note", availability = "specialty")
    }

    /** `NOT NULL` 만 걸면 에디터가 스페이스 하나로 통과시킨다. */
    @Test
    fun `RED10 - 공백만 있는 안내는 없는 것으로 친다`() {
        assertAll(
            listOf("", "   ", "\t\n").map<String, () -> Unit> { blank ->
                {
                    assertThatThrownBy {
                        insert(slug = "blank-${blank.length}", availability = "import_only", note = blank)
                    }.isInstanceOf(SQLException::class.java)
                        .hasMessageContaining("ck_ingredient__substitute")
                }
            },
        )
    }

    // ── RED 11~13 : 브랜드 ────────────────────────────────────────────────

    @Test
    fun `RED11-12 - 브랜드의 is_sponsored 기본값은 false 다`() {
        val id = insert(slug = "brand-default")
        exec("INSERT INTO ingredient_brand (ingredient_id, name) VALUES ($id, '탱커레이')")

        assertThat(one("SELECT is_sponsored FROM ingredient_brand WHERE ingredient_id = $id"))
            .isEqualTo("f")
    }

    /**
     * "정해지지 않음"이라는 상태가 있으면 라벨을 붙일지 결정할 수 없고,
     * 그 순간 공정위 의무를 어길 여지가 생긴다.
     */
    @Test
    fun `RED13 - 광고성 여부에 NULL 이 없다`() {
        val id = insert(slug = "brand-not-null")

        assertThatThrownBy {
            exec("INSERT INTO ingredient_brand (ingredient_id, name, is_sponsored) VALUES ($id, '봄베이', NULL)")
        }.isInstanceOf(SQLException::class.java)
    }

    /** ⚠️ Phase 1a 에서 켜지 않는다 — 켜면 주류 광고 규제 접점이 생긴다 (ADR-0004 · `NFR-L-05`). */
    @Test
    fun `Phase 1a 에는 is_sponsored 가 켜진 브랜드가 없다`() {
        assertThat(rows("SELECT name FROM ingredient_brand WHERE is_sponsored = true"))
            .`as`("켜려면 NFR-L-05(주류광고 자문)가 선행돼야 한다")
            .isEmpty()
    }

    // ── RED 15 : 승인제 ───────────────────────────────────────────────────

    @Test
    fun `RED15 - 신규 재료의 is_approved 기본값은 false 다`() {
        val id = insert(slug = "new-ingredient")
        assertThat(one("SELECT is_approved FROM ingredient WHERE id = $id")).isEqualTo("f")
    }

    // ── RED 20~22 : 별칭 · slug ───────────────────────────────────────────

    @Test
    fun `RED20 - aliases 가 TEXT 배열이다`() {
        assertThat(one("""
            SELECT data_type FROM information_schema.columns
            WHERE table_name = 'ingredient' AND column_name = 'aliases'
        """.trimIndent())).isEqualTo("ARRAY")

        val id = insert(slug = "with-aliases")
        exec("UPDATE ingredient SET aliases = ARRAY['올드톰','런던드라이'] WHERE id = $id")

        assertThat(one("SELECT array_length(aliases, 1)::text FROM ingredient WHERE id = $id"))
            .isEqualTo("2")
    }

    @Test
    fun `RED21 - aliases 에 GIN 인덱스가 있다`() {
        assertThat(rows("""
            SELECT indexdef FROM pg_indexes
            WHERE tablename = 'ingredient' AND indexdef LIKE '%gin%aliases%'
        """.trimIndent()))
            .`as`("별칭 검색이 인덱스를 타야 한다 (FR-INGREDIENT-005)")
            .isNotEmpty()
    }

    @Test
    fun `RED22 - slug 가 유일하다`() {
        insert(slug = "unique-me")
        assertThatThrownBy { insert(slug = "unique-me") }.isInstanceOf(SQLException::class.java)
    }

    // ── 헬퍼 ───────────────────────────────────────────────────────────────

    private fun insert(
        slug: String,
        category: String = "spirit",
        availability: String = "common",
        note: String? = null,
    ): Long = conn().use { c ->
        c.prepareStatement(
            """INSERT INTO ingredient (slug, name_ko, name_en, category, domestic_availability, substitute_note)
               VALUES (?, ?, ?, ?, ?, ?) RETURNING id""",
        ).use { st ->
            st.setString(1, slug)
            st.setString(2, "테스트 재료")
            st.setString(3, "test ingredient")
            st.setString(4, category)
            st.setString(5, availability)
            st.setString(6, note)
            st.executeQuery().use { rs -> rs.next(); rs.getLong(1) }
        }
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
