package kr.mut.cocktail.abv

import kr.mut.cocktail.api.CocktailSummary
import kr.mut.support.PostgresSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.SQLException

/**
 * ISSUE-011 RED 17~30 — 오버라이드 · 생성 컬럼 · 노출.
 *
 * ## `abv` 는 앱이 쓰지 못한다
 *
 * 생성 컬럼이라 DB 가 `COALESCE(abv_override, abv_calculated)` 로 채운다.
 * 조회·필터가 **항상 표시값을 보게** 하려는 것이다 — 매 쿼리에서 `COALESCE` 를 쓰면
 * 인덱스가 안 붙는다 (SPEC-06 §3.1).
 */
class AbvIntegrationTest {

    // ── RED 17~21 : 오버라이드 ────────────────────────────────────────────

    @Test
    fun `RED17-18 - override 가 있으면 그것이 표시값이다`() {
        val id = cocktail("abv-override", calculated = "27.6", override = "30.0")
        assertThat(one("SELECT abv FROM cocktail WHERE id = $id")).isEqualTo("30.0")

        val plain = cocktail("abv-calculated", calculated = "27.6")
        assertThat(one("SELECT abv FROM cocktail WHERE id = $plain")).isEqualTo("27.6")
    }

    /** 앱이 직접 쓰면 표시값과 원본이 어긋난다. DB 가 막는다. */
    @Test
    fun `RED19 - abv 는 생성 컬럼이라 직접 쓸 수 없다`() {
        val id = cocktail("abv-generated", calculated = "20.0")

        assertThatThrownBy { exec("UPDATE cocktail SET abv = 99 WHERE id = $id") }
            .isInstanceOf(SQLException::class.java)

        assertThat(one("""
            SELECT is_generated FROM information_schema.columns
            WHERE table_name = 'cocktail' AND column_name = 'abv'
        """.trimIndent())).isEqualTo("ALWAYS")
    }

    @Test
    fun `RED20 - override 를 지우면 계산값으로 돌아간다`() {
        val id = cocktail("abv-clear", calculated = "27.6", override = "30.0")
        assertThat(one("SELECT abv FROM cocktail WHERE id = $id")).isEqualTo("30.0")

        exec("UPDATE cocktail SET abv_override = NULL WHERE id = $id")

        assertThat(one("SELECT abv FROM cocktail WHERE id = $id")).isEqualTo("27.6")
    }

    /**
     * RED 21 — `NUMERIC(4,1)` 이라 999.9 까지 들어간다.
     *
     * 범위 CHECK 를 **걸지 않았다.** `ck_cocktail__non_alcoholic` 이 이미 `abv` 를 참조하는데
     * 제약을 하나 더 걸면 어느 것이 먼저 터질지 예측할 수 없고(이슈 010 에서 겪었다),
     * 무엇보다 **음수·초과는 계산기가 이미 잘라 낸다** ([AbvCalculator] RED 10·11).
     * 어드민 입력은 이슈 025 의 `violations` 가 막는다 — 거기서 에디터에게 이유를 알려 줄 수 있다.
     */
    @Test
    fun `RED21 - 범위는 계산기와 어드민 검증이 막는다`() {
        assertThat(one("""
            SELECT numeric_precision || ',' || numeric_scale FROM information_schema.columns
            WHERE table_name = 'cocktail' AND column_name = 'abv_override'
        """.trimIndent())).isEqualTo("4,1")
    }

    // ── RED 26~28 : 무알콜 정합 (INV-COCKTAIL-06) ─────────────────────────

    /** 이슈 009 의 DB CHECK 가 최종 방어선이다. 계산 결과가 그것과 어긋나면 저장 자체가 막힌다. */
    @Test
    fun `RED26-28 - 무알콜 정합을 DB 가 막는다`() {
        assertThatThrownBy { cocktail("na-mismatch", base = "non-alcoholic", calculated = "12.0") }
            .`as`("무알콜인데 계산값이 0 이 아니다")
            .isInstanceOf(SQLException::class.java)
            .hasMessageContaining("ck_cocktail__non_alcoholic")

        assertThatThrownBy { cocktail("gin-zero", base = "gin", calculated = "0.0") }
            .`as`("계산값이 0 인데 기주가 무알콜이 아니다")
            .isInstanceOf(SQLException::class.java)
    }

    /** 오버라이드로도 우회할 수 없다 — CHECK 가 보는 것은 표시값이다. */
    @Test
    fun `오버라이드로 무알콜 정합을 우회할 수 없다`() {
        assertThatThrownBy {
            cocktail("na-override", base = "non-alcoholic", calculated = "0.0", override = "15.0")
        }.isInstanceOf(SQLException::class.java)
            .hasMessageContaining("ck_cocktail__non_alcoholic")
    }

    // ── RED 29~30 : 노출 (SPEC-07 §5) ─────────────────────────────────────

    /** 계산인지 수동인지는 **내부 사정**이다. 나가면 클라이언트가 그것에 의존하기 시작한다. */
    @Test
    fun `RED29 - 공개 응답에 abv 하나만 있다`() {
        val fields = CocktailSummary::class.java.declaredFields.map { it.name }

        assertThat(fields).contains("abv")
        assertThat(fields).doesNotContain("abvCalculated", "abvOverride")
    }

    /**
     * RED 30 — 어드민은 셋 다 봐야 한다. 에디터가 **오버라이드가 걸려 있는지** 알아야
     * 레시피를 고쳐도 표시값이 안 바뀌는 이유를 안다.
     *
     * 어드민 DTO 는 이슈 025 다. 여기서는 **엔티티가 셋을 다 들고 있는지**까지 확인한다.
     */
    @Test
    fun `RED30 - 엔티티는 셋을 다 들고 있다`() {
        val id = cocktail("admin-view", calculated = "27.6", override = "30.0")

        assertThat(one("SELECT abv_calculated FROM cocktail WHERE id = $id")).isEqualTo("27.6")
        assertThat(one("SELECT abv_override FROM cocktail WHERE id = $id")).isEqualTo("30.0")
        assertThat(one("SELECT abv FROM cocktail WHERE id = $id")).isEqualTo("30.0")
    }

    /** SPEC-06 §5 — 도수 구간 필터(ADR-0003)가 이 인덱스를 탄다. */
    @Test
    fun `도수 구간 필터 인덱스가 있다`() {
        assertThat(rows("""
            SELECT indexname FROM pg_indexes
            WHERE tablename = 'cocktail' AND indexdef LIKE '%abv%'
        """.trimIndent()))
            .contains("ix_cocktail__status_abv")
    }

    // ── 헬퍼 ───────────────────────────────────────────────────────────────

    private var seq = 0

    private fun cocktail(
        slug: String,
        base: String = "gin",
        calculated: String? = null,
        override: String? = null,
    ): Long = conn().use { c ->
        c.autoCommit = false
        val id = c.createStatement().use { st ->
            st.executeQuery(
                """
                INSERT INTO cocktail (slug, name_ko, name_en, summary,
                    base_spirit, style_primary, method, sweetness, glass_type,
                    abv_calculated, abv_override)
                VALUES ('$slug-${seq++}', '테스트', 'test', '요약',
                    '$base', 'highball', 'build', 'dry', '하이볼 글라스',
                    ${calculated ?: "NULL"}, ${override ?: "NULL"})
                RETURNING id
                """.trimIndent(),
            ).use { rs -> rs.next(); rs.getLong(1) }
        }
        c.createStatement().use { it.execute("INSERT INTO cocktail_style VALUES ($id, 'highball')") }
        c.commit()
        id
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
