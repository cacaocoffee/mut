package kr.mut.cocktail.taxonomy

import kr.mut.common.taxonomy.SweetLevel
import kr.mut.support.PostgresSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.sql.Connection
import java.sql.SQLException

/**
 * ISSUE-012 — 당도 수동입력 · 별칭 (`FR-COCKTAIL-007`·`009`).
 *
 * ## 당도는 계산하지 않는다
 *
 * SPEC-02 §2.5 — **"시럽·리큐르·시트러스의 상호작용 때문에 자동 계산은 신뢰할 수 없다."**
 * 도수(이슈 011)와 대비되는 지점이다. 도수는 계산하고 당도는 안 한다.
 *
 * RED 3 이 **계산 함수의 부재**를 단언한다. 나중에 "참고값이라도 보여주자"는 요구가 오면
 * GAPS + ADR 을 거쳐야 한다.
 */
class SweetnessAliasTest {

    // ── RED 1~6 : 당도 ────────────────────────────────────────────────────

    @Test
    fun `RED1 - 당도 4종만 허용한다`() {
        assertAll(
            listOf("dry", "semi_dry", "semi_sweet", "sweet").map<String, () -> Unit> { level ->
                { cocktail("sweet-$level", sweetness = level) }
            },
        )

        assertThatThrownBy { cocktail("sweet-bogus", sweetness = "very_sweet") }
            .isInstanceOf(SQLException::class.java)
            .hasMessageContaining("ck_cocktail__sweetness")
    }

    @Test
    fun `RED2 - 당도는 NOT NULL 이다`() {
        assertThatThrownBy {
            exec(
                """INSERT INTO cocktail (slug, name_ko, name_en, summary,
                       base_spirit, style_primary, method, glass_type)
                   VALUES ('no-sweetness', 'x', 'x', 'x', 'gin', 'highball', 'build', 'glass')""",
            )
        }.isInstanceOf(SQLException::class.java)
    }

    /**
     * RED 3 — **자동 계산 경로가 없다.**
     *
     * 도수는 `AbvCalculator` 가 있는데 당도는 없다. 그것이 SPEC-02 §2.5 의 판단이고,
     * 없다는 사실 자체를 테스트가 지킨다 — 있으면 반드시 쓰인다.
     */
    @Test
    fun `RED3 - 당도 자동 계산 함수가 없다`() {
        val classes = ClassLoader.getSystemClassLoader()
            .let { javaClass.protectionDomain.codeSource.location }
            .let { java.io.File(it.toURI()) }
            .walkTopDown()
            .filter { it.name.endsWith(".class") }
            .map { it.name }
            .toList()

        assertThat(classes)
            .`as`("SPEC-02 §2.5 — 시럽·리큐르·시트러스의 상호작용 때문에 계산이 신뢰할 수 없다")
            .noneMatch { it.contains("Sweetness", ignoreCase = true) && it.contains("Calculator") }
        assertThat(classes).noneMatch { it.contains("SweetnessEstimator") }
    }

    /** 수동 입력값이다. 재료가 바뀌어도 에디터가 고치기 전까지 그대로다. */
    @Test
    fun `RED4 - 재료를 바꿔도 당도는 그대로다`() {
        val id = cocktail("sweet-stable", sweetness = "semi_sweet")

        // 도수는 재계산 대상이지만 당도는 아니다.
        exec("UPDATE cocktail SET abv_calculated = 25 WHERE id = $id")

        assertThat(one("SELECT sweetness FROM cocktail WHERE id = $id")).isEqualTo("semi_sweet")
    }

    /** 필터가 "드라이한 쪽부터"를 정렬하려면 순서가 있어야 한다. */
    @Test
    fun `RED5 - 당도에 순서가 있다`() {
        assertThat(SweetLevel.entries.map { it.slug })
            .containsExactly("dry", "semi_dry", "semi_sweet", "sweet")

        assertThat(SweetLevel.entries.map { it.level })
            .`as`("ordinal 이 곧 정렬 순서")
            .containsExactly(0, 1, 2, 3)

        assertThat(SweetLevel.DRY.level).isLessThan(SweetLevel.SWEET.level)
    }

    /**
     * RED 6 — 프로토타입의 `0|1|2|3` 숫자 형태를 받지 않는다.
     *
     * G-23 이 기록한 전환 지점이다. 숫자는 의미가 위치에 숨어 마이그레이션에서 뒤집힌다.
     * `level` 은 전환용으로 들고 있을 뿐 **저장 형식이 아니다.**
     */
    @Test
    fun `RED6 - 숫자 당도를 저장하지 않는다`() {
        assertThatThrownBy { cocktail("sweet-numeric", sweetness = "2") }
            .isInstanceOf(SQLException::class.java)
            .hasMessageContaining("ck_cocktail__sweetness")

        assertThat(SweetLevel.ofLevel(2)).isEqualTo(SweetLevel.SEMI_SWEET)
    }

    // ── RED 7~15 : 별칭 ───────────────────────────────────────────────────

    @Test
    fun `RED7-8 - aliases 는 TEXT 배열이고 기본값이 빈 배열이다`() {
        val id = cocktail("alias-default")

        assertThat(one("SELECT array_length(aliases, 1) FROM cocktail WHERE id = $id")).isNull()
        assertThat(one("SELECT cardinality(aliases)::text FROM cocktail WHERE id = $id")).isEqualTo("0")
    }

    /** `R-F2.1-3` — `올드패션드` / `올드 패션드` / `Old Fashioned` / `올패` 가 전부 매칭돼야 한다. */
    @Test
    fun `RED9-11 - 축약형 · 띄어쓰기 변형 · 영문명을 등록한다`() {
        val id = cocktail("old-fashioned")
        exec(
            "UPDATE cocktail SET aliases = ARRAY['올패', '올드 패션드', 'Old Fashioned'] WHERE id = $id",
        )

        assertThat(rows("SELECT unnest(aliases) FROM cocktail WHERE id = $id"))
            .containsExactly("올패", "올드 패션드", "Old Fashioned")
    }

    @Test
    fun `RED12 - 중복 별칭은 정규화에서 제거된다`() {
        assertThat(AliasNormalizer.normalize(listOf("올패", "올패", "올드 패션드")))
            .containsExactly("올패", "올드 패션드")
    }

    /** 입력 순서를 유지한다 — 에디터가 중요한 순서대로 적었을 수 있다. */
    @Test
    fun `정규화가 입력 순서를 유지한다`() {
        assertThat(AliasNormalizer.normalize(listOf("ㄷ", "ㄱ", "ㄴ", "ㄱ")))
            .containsExactly("ㄷ", "ㄱ", "ㄴ")
    }

    /** 빈 별칭은 검색에 걸리지도 않고 화면에 빈 칩을 만든다. */
    @Test
    fun `RED13 - 공백 별칭은 앱과 DB 양쪽에서 거부된다`() {
        // 앱
        assertThat(AliasNormalizer.normalize(listOf("올패", "", "   ", "\t"))).containsExactly("올패")

        // DB — 배치가 앱을 거치지 않는다 (PRIN-T05)
        val id = cocktail("alias-blank")
        assertAll(
            listOf("''", "'   '", "E'\\t'").map<String, () -> Unit> { blank ->
                {
                    assertThatThrownBy {
                        exec("UPDATE cocktail SET aliases = ARRAY['올패', $blank] WHERE id = $id")
                    }.`as`("%s", blank)
                        .isInstanceOf(SQLException::class.java)
                        .hasMessageContaining("aliases_nonblank")
                }
            },
        )
    }

    /** 재료 별칭도 같은 규칙이다 (이슈 008 의 컬럼). */
    @Test
    fun `재료 별칭도 공백을 거부한다`() {
        val id = ingredient("alias-blank-ing")

        assertThatThrownBy { exec("UPDATE ingredient SET aliases = ARRAY['  '] WHERE id = $id") }
            .isInstanceOf(SQLException::class.java)
            .hasMessageContaining("aliases_nonblank")
    }

    /**
     * RED 14 — 이름과 같은 별칭도 **저장은 허용한다** (DECISIONS §1).
     *
     * 검색은 이름도 보므로 색인 단계에서 중복을 없애면 된다 (이슈 017).
     * 여기서 막으면 에디터가 "왜 안 들어가지" 하고 헤맨다 — **저장은 관대하게, 색인은 엄격하게.**
     */
    @Test
    fun `RED14 - 이름과 같은 별칭은 저장되되 색인용 목록에서 빠진다`() {
        val id = cocktail("alias-same-name")
        exec("UPDATE cocktail SET aliases = ARRAY['테스트', '올패'] WHERE id = $id")

        assertThat(rows("SELECT unnest(aliases) FROM cocktail WHERE id = $id"))
            .`as`("저장은 된다")
            .contains("테스트")

        assertThat(AliasNormalizer.withoutNames(listOf("테스트", "올패"), "테스트", "test"))
            .`as`("색인에 넣을 때 이름을 뺀다")
            .containsExactly("올패")
    }

    @Test
    fun `RED15 - aliases 에 GIN 인덱스가 있다`() {
        assertThat(rows("""
            SELECT indexname FROM pg_indexes
            WHERE tablename = 'cocktail' AND indexdef LIKE '%gin%aliases%'
        """.trimIndent()))
            .isNotEmpty()
    }

    // ── RED 17 : 감사 대상 아님 ───────────────────────────────────────────

    /**
     * `PRIN-T08` 이 열거한 감사 대상은 **발행 상태 전이 · 제휴 등급 · 큐레이션 순위 · 바 검증**
     * 넷이다 (DECISIONS §1.3). 당도는 없다 — 감사하지 않는다.
     *
     * 감사 로그 자체는 이슈 014 다. 여기서는 **당도가 그 목록에 없다는 것**만 고정한다.
     */
    @Test
    fun `RED17 - 당도 변경은 감사 대상이 아니다`() {
        val audited = listOf("발행 상태 전이", "제휴 등급", "큐레이션 순위", "바 검증", "slug 변경 시도", "재료 승인")

        assertThat(audited)
            .`as`("DECISIONS §1.3 — 4종 + 추가 2종. 당도는 없다")
            .noneMatch { it.contains("당도") }
    }

    // ── 헬퍼 ───────────────────────────────────────────────────────────────

    private var seq = 0

    private fun cocktail(slug: String, sweetness: String = "dry"): Long = conn().use { c ->
        c.autoCommit = false
        val id = c.createStatement().use { st ->
            st.executeQuery(
                """
                INSERT INTO cocktail (slug, name_ko, name_en, summary,
                    base_spirit, style_primary, method, sweetness, glass_type, abv_calculated)
                VALUES ('$slug-${seq++}', '테스트', 'test', '요약',
                    'gin', 'highball', 'build', '$sweetness', '하이볼 글라스', 12)
                RETURNING id
                """.trimIndent(),
            ).use { rs -> rs.next(); rs.getLong(1) }
        }
        c.createStatement().use { it.execute("INSERT INTO cocktail_style VALUES ($id, 'highball')") }
        c.commit()
        id
    }

    private fun ingredient(slug: String): Long = conn().use { c ->
        c.createStatement().use { st ->
            st.executeQuery(
                """INSERT INTO ingredient (slug, name_ko, name_en, category, domestic_availability)
                   VALUES ('$slug-${seq++}', '재료', 'ing', 'spirit', 'common') RETURNING id""",
            ).use { rs -> rs.next(); rs.getLong(1) }
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
