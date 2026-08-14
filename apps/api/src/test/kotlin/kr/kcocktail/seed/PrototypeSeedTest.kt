package kr.kcocktail.seed

import kr.kcocktail.support.PostgresSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/**
 * ISSUE-036 — 프로토타입 시드 이관 (SPEC-01 §6 · SPEC-06 §6 · `NFR-D-01`).
 *
 * ## 이관은 복사가 아니라 변환이다
 *
 * 프로토타입 타입과 SPEC-06 스키마가 여러 곳에서 다르다 — 기주가 한국어이고, 당도가 숫자이고,
 * 재료가 인라인 객체다. **이 파일은 그 변환이 맞았는지 확인한다.**
 *
 * 변환 규칙 자체는 `scripts/seed-from-prototype.ts` 에 있고, SQL 은 그 산출물이다.
 * SQL 을 손으로 고치면 여기가 통과해도 다음 생성에서 되돌아간다.
 *
 * ## `draft` 로 들어간다
 *
 * `tasting_note` 가 발행 필수인데(`GATE-COCKTAIL-01`) 프로토타입에 그 필드가 없다.
 * **자동 생성하지 않는다** — `PRIN-P03` 이 "만들어보지 않은 것은 쓰지 않는다" 이고,
 * 향과 맛 서술이야말로 그 원칙이 지키려는 바로 그 값이다.
 *
 * 그래서 여기서 확인하는 것은 "발행됐다" 가 아니라
 * **"서술만 채우면 발행된다"** 다 — 나머지 게이트가 전부 통과하는지 본다.
 */
@SpringBootTest
class PrototypeSeedTest {

    @Autowired private lateinit var jdbc: JdbcTemplate

    /**
     * **시드를 매번 다시 깐다.**
     *
     * Testcontainers 는 컨테이너 하나를 전체 테스트가 공유한다. 다른 파일들이
     * `TRUNCATE cocktail, ingredient …` 로 자기 픽스처를 정리하는데,
     * 그 순간 **시드도 함께 지워진다** — 실제로 이 파일이 실행 순서에 따라
     * 초록이었다 빨갰다 했다.
     *
     * "다른 테스트가 지우지 않게 하자" 는 답이 아니다. 격리는 지우는 쪽의 권리이고,
     * **자기 상태를 세우는 것이 이쪽의 일**이다.
     */
    @BeforeEach
    fun reseed() {
        jdbc.execute(
            "TRUNCATE cocktail, ingredient, recipe, recipe_ingredient, recipe_step, " +
                "cocktail_style, cocktail_aroma_tag, search_document CASCADE",
        )
        replaySeed("R__seed_01_ingredient.sql")
        replaySeed("R__seed_02_cocktail.sql")
    }

    // ── RED 1~7 : 재료 마스터 (PRIN-D01) ──────────────────────────────────

    @Test
    fun `RED1,2,3 - 인라인 재료에서 마스터가 중복 없이 만들어진다`() {
        val masters = count("SELECT count(*) FROM ingredient")
        val slugs = jdbc.queryForList("SELECT slug FROM ingredient", String::class.java)

        assertAll(
            { assertThat(masters).`as`("RED1").isGreaterThan(50) },
            { assertThat(slugs).`as`("RED2·3 — slug 가 곧 유니크 키다").doesNotHaveDuplicates() },
            {
                assertThat(slugs)
                    .allSatisfy { assertThat(it).matches("^[a-z0-9]+(-[a-z0-9]+)*$") }
            },
        )
    }

    /**
     * RED 2 보강 — **표기 흔들림이 흡수됐다.**
     *
     * 프로토타입이 `쿠앵트로`/`코앵트로`, `탄산수`/`소다` 처럼 같은 재료를 다르게 적었다.
     * 문자열로 들고 있을 때는 안 보이다가 **마스터를 만드는 순간 드러난다** —
     * `PRIN-D01` 이 "재료는 문자열이 아니라 참조" 라고 한 근거가 이것이다.
     */
    @Test
    fun `RED2 - 같은 재료의 다른 표기가 별칭으로 흡수됐다`() {
        val aliased = jdbc.queryForList(
            "SELECT name_ko, aliases FROM ingredient WHERE cardinality(aliases) > 0",
        )

        assertAll(
            { assertThat(aliased).`as`("드리프트가 실제로 있었다").isNotEmpty() },
            {
                // 별칭이 남의 대표 이름이면 검색이 엉뚱한 재료를 준다 (`R-F2.1-3`).
                val primary = jdbc.queryForList("SELECT name_ko FROM ingredient", String::class.java).toSet()
                val leaked = jdbc.queryForList(
                    "SELECT unnest(aliases) FROM ingredient", String::class.java,
                ).filter { it in primary }

                assertThat(leaked)
                    .`as`("별칭은 '같은 것의 다른 이름' 이지 '비슷한 것의 이름' 이 아니다")
                    .isEmpty()
            },
        )
    }

    /** RED 4·5·6·7 — 카테고리·유통·승인. 추론하지 않고 매핑표로 정한 것들이다. */
    @Test
    fun `RED4,5,6,7 - 카테고리·유통·승인이 지정됐다`() {
        assertAll(
            {
                assertThat(
                    count("SELECT count(*) FROM ingredient WHERE category NOT IN " +
                        "('spirit','liqueur','bitters','syrup','juice','garnish','mixer')"),
                ).`as`("RED4 — 7종 밖이 없다").isZero()
            },
            {
                assertThat(count("SELECT count(*) FROM ingredient WHERE category = 'garnish'"))
                    .`as`("RED5 — 가니시가 실제로 분류됐다 (counts_for_stock 이 false 인 부류)")
                    .isGreaterThan(5)
            },
            {
                assertThat(count("SELECT count(*) FROM ingredient WHERE domestic_availability IS NULL"))
                    .`as`("RED6 — NOT NULL 이라 DB 도 막지만 기본값이 실제로 들어갔는지 본다")
                    .isZero()
            },
            {
                assertThat(count("SELECT count(*) FROM ingredient WHERE is_approved = false"))
                    .`as`("RED7 — 시드는 승인된 상태다. 미승인이면 발행이 막힌다 (GATE-COCKTAIL-04)")
                    .isZero()
            },
        )
    }

    // ── RED 8~13 : 값 변환 ────────────────────────────────────────────────

    /**
     * RED 8·9·10 — 기주 한국어가 슬러그가 됐다.
     *
     * RED 10 이 요점이다: 전통주는 `korean` 이지 `soju` 가 아니다 (ADR-0002) —
     * 막걸리·문배주를 소주로 부르는 것은 부정확하다.
     */
    @Test
    fun `RED8,9,10 - 기주가 슬러그로 변환됐고 전통주가 korean 이다`() {
        val bases = jdbc.queryForList(
            "SELECT DISTINCT base_spirit FROM cocktail", String::class.java,
        )

        assertAll(
            {
                assertThat(bases).allSatisfy {
                    assertThat(it).isIn(
                        "gin", "vodka", "whisky", "rum", "agave",
                        "brandy", "liqueur", "wine", "korean", "non-alcoholic",
                    )
                }
            },
            { assertThat(bases).`as`("RED10 — ADR-0002").doesNotContain("soju") },
            {
                assertThat(count("SELECT count(*) FROM cocktail WHERE base_spirit = 'korean'"))
                    .`as`("전통주 항목이 실제로 있다")
                    .isPositive()
            },
        )
    }

    @Test
    fun `RED11,12,13 - 당도·슬러그·출처가 변환됐다`() {
        assertAll(
            {
                assertThat(
                    jdbc.queryForList("SELECT DISTINCT sweetness FROM cocktail", String::class.java),
                ).allSatisfy { assertThat(it).isIn("dry", "semi_dry", "semi_sweet", "sweet") }
            },
            {
                assertThat(jdbc.queryForList("SELECT slug FROM cocktail", String::class.java))
                    .`as`("RED12 — 프로토타입 id 가 slug 가 됐다")
                    .contains("negroni", "martini", "gimlet")
            },
            {
                assertThat(count("SELECT count(*) FROM cocktail WHERE origin_year IS NOT NULL"))
                    .`as`("RED13 — origin 이 3개 컬럼으로 분해됐다")
                    .isPositive()
            },
        )
    }

    // ── RED 14~17 : 조인 테이블 분해 ─────────────────────────────────────

    /**
     * RED 14·15 — `stylePrimary` 가 `styles` 에 포함된다 (`INV-COCKTAIL-03`).
     *
     * 복합 FK 가 강제하므로 이 단언이 실패할 일은 없다. **그래도 둔다** —
     * 실패한다면 FK 가 사라진 것이고, 그것이 더 큰 사건이다.
     */
    @Test
    fun `RED14,15 - styles 가 분해되고 stylePrimary 가 그 안에 있다`() {
        val orphan = count(
            """
            SELECT count(*) FROM cocktail c
             WHERE NOT EXISTS (
                SELECT 1 FROM cocktail_style s
                 WHERE s.cocktail_id = c.id AND s.style = c.style_primary)
            """.trimIndent(),
        )

        assertAll(
            { assertThat(count("SELECT count(*) FROM cocktail_style")).isPositive() },
            { assertThat(orphan).`as`("INV-COCKTAIL-03").isZero() },
        )
    }

    /** RED 16·17 — 향 태그가 1~3개다 (`INV-COCKTAIL-04` · `R-F1.2-1`). */
    @Test
    fun `RED16,17 - 향 태그가 1개 이상 3개 이하다`() {
        val violations = jdbc.queryForList(
            """
            SELECT c.slug, count(t.aroma_tag) AS n
              FROM cocktail c LEFT JOIN cocktail_aroma_tag t ON t.cocktail_id = c.id
             GROUP BY c.slug HAVING count(t.aroma_tag) NOT BETWEEN 1 AND 3
            """.trimIndent(),
        )

        assertThat(violations).`as`("INV-COCKTAIL-04").isEmpty()
    }

    // ── RED 18~23 : 레시피 ───────────────────────────────────────────────

    /** RED 18 — 칵테일마다 `standard` 레시피가 **정확히 하나**다 (`INV-COCKTAIL-07`). */
    @Test
    fun `RED18 - 표준 레시피가 칵테일마다 정확히 하나다`() {
        val wrong = jdbc.queryForList(
            """
            SELECT c.slug, count(r.id) AS n
              FROM cocktail c LEFT JOIN recipe r
                ON r.cocktail_id = c.id AND r.version_type = 'standard'
             GROUP BY c.slug HAVING count(r.id) <> 1
            """.trimIndent(),
        )

        assertThat(wrong).`as`("INV-COCKTAIL-07").isEmpty()
    }

    /**
     * RED 20 — **프리텍스트 재료가 0건이다** (`PRIN-D01`).
     *
     * 이 이슈에서 가장 중요한 단언이다. 재료가 문자열로 남으면 역검색(내 술장)과
     * 바 연결이 전부 불가능해지고, 나중에 정규화하려면 마이그레이션 비용이 크다.
     */
    @Test
    fun `RED19,20,21,22,23 - 레시피가 FK 로 연결되고 계량이 옮겨졌다`() {
        assertAll(
            {
                assertThat(
                    count(
                        """
                        SELECT count(*) FROM recipe_ingredient ri
                         WHERE NOT EXISTS (SELECT 1 FROM ingredient i WHERE i.id = ri.ingredient_id)
                        """.trimIndent(),
                    ),
                ).`as`("RED20 — FK 가 강제하지만 실제로 연결됐는지 본다").isZero()
            },
            {
                // RED 19 — 스텝 번호가 1부터다. 0부터면 화면이 "0단계" 를 보여 준다.
                assertThat(count("SELECT count(*) FROM recipe_step WHERE step_no < 1")).isZero()
                assertThat(
                    count(
                        """
                        SELECT count(*) FROM recipe r
                         WHERE NOT EXISTS (SELECT 1 FROM recipe_step s
                                            WHERE s.recipe_id = r.id AND s.step_no = 1)
                        """.trimIndent(),
                    ),
                ).`as`("모든 레시피가 1번 스텝을 갖는다").isZero()
            },
            {
                assertThat(count("SELECT count(*) FROM recipe_ingredient WHERE unit = 'ml'"))
                    .`as`("RED21 — ml 이 amount·unit 으로 들어갔다").isPositive()
            },
            {
                assertThat(count("SELECT count(*) FROM recipe_ingredient WHERE amount_label IS NOT NULL"))
                    .`as`("RED22 — '1조각' 같은 비계량 표기").isPositive()
            },
            {
                assertThat(count("SELECT count(*) FROM recipe_ingredient WHERE substitute_note IS NOT NULL"))
                    .`as`("RED23 — 대체 안내가 옮겨졌다").isPositive()
            },
        )
    }

    // ── RED 24~28 : 발행 게이트 (NFR-D-01) ───────────────────────────────

    /**
     * RED 24 — **서술만 채우면 발행된다.**
     *
     * 이슈는 "전부가 발행 게이트를 통과한다" 를 요구했다. 그런데 `tasting_note` 는
     * 사람이 쓰는 값이라 없는 것을 지어낼 수 없다 (`PRIN-P03`).
     *
     * `summary` 가 향·맛 서술인 41종을 발행하고, 만드는 법만 적힌 8종은 draft 로 뒀다.
     * 여기서는 **발행된 것이 게이트 여섯을 전부 통과하는지** 본다.
     */
    @Test
    fun `RED24,26,27,28 - 발행분이 모든 게이트를 통과한다`() {
        assertAll(
            {
                // GATE-COCKTAIL-03 — 표준 레시피에 재료·스텝이 각각 1개 이상.
                assertThat(
                    jdbc.queryForList(
                        """
                        SELECT c.slug FROM cocktail c
                          JOIN recipe r ON r.cocktail_id = c.id AND r.version_type = 'standard'
                         WHERE NOT EXISTS (SELECT 1 FROM recipe_ingredient ri WHERE ri.recipe_id = r.id)
                            OR NOT EXISTS (SELECT 1 FROM recipe_step s WHERE s.recipe_id = r.id)
                        """.trimIndent(),
                        String::class.java,
                    ),
                ).`as`("RED28 · GATE-COCKTAIL-03").isEmpty()
            },
            {
                // GATE-COCKTAIL-04 — 모든 재료가 승인된 마스터 참조.
                assertThat(
                    count(
                        """
                        SELECT count(*) FROM recipe_ingredient ri
                          JOIN ingredient i ON i.id = ri.ingredient_id
                         WHERE i.is_approved = false
                        """.trimIndent(),
                    ),
                ).`as`("GATE-COCKTAIL-04").isZero()
            },
            {
                // GATE-COCKTAIL-01 — 발행분에 향·맛 서술이 있다.
                assertThat(
                    jdbc.queryForList(
                        "SELECT slug FROM cocktail WHERE status = 'published' AND tasting_note IS NULL",
                        String::class.java,
                    ),
                ).`as`("RED25 · GATE-COCKTAIL-01").isEmpty()
            },
            {
                // GATE-COCKTAIL-05 — 클래식이면 story 필수.
                assertThat(
                    jdbc.queryForList(
                        "SELECT slug FROM cocktail WHERE is_classic = true AND (story IS NULL OR story !~ '\\S')",
                        String::class.java,
                    ),
                ).`as`("RED26 · GATE-COCKTAIL-05").isEmpty()
            },
            {
                // GATE-COCKTAIL-06 — 미유통 재료에 대체 안내.
                assertThat(
                    jdbc.queryForList(
                        """
                        SELECT DISTINCT c.slug
                          FROM cocktail c
                          JOIN recipe r ON r.cocktail_id = c.id AND r.version_type = 'standard'
                          JOIN recipe_ingredient ri ON ri.recipe_id = r.id
                          JOIN ingredient i ON i.id = ri.ingredient_id
                         WHERE i.domestic_availability IN ('import_only', 'unavailable')
                           AND ri.substitute_ingredient_id IS NULL
                           AND (ri.substitute_note IS NULL OR ri.substitute_note !~ '\\S')
                        """.trimIndent(),
                        String::class.java,
                    ),
                ).`as`("RED27 · GATE-COCKTAIL-06").isEmpty()
            },
        )
    }

    /**
     * RED 25 — **서술이 있는 것만 발행된다.**
     *
     * `PRIN-P03` 이 `tasting_note` 를 발행 필수로 만든 이유는 **직접 만들어 보고 쓴 내용**
     * 이어야 해서다. 프로토타입의 `summary` 가 그 자리를 대신하고 있었고
     * (`validate.ts` 의 주석이 그렇게 적었다), 에디터 본인이 쓴 문장이라 옮겨도 어긋나지 않는다.
     *
     * **8종은 옮기지 않았다.** `summary` 에 만드는 법을 적어 둔 것들이라
     * (`"온도는 −3℃ 이하로 유지한다"`) 향·맛 서술이 아니다. 게이트를 글자로만 통과시키지 않는다.
     *
     * 이 테스트가 지키는 것은 **서술 없이 발행된 것이 하나도 없다**는 쪽이다 —
     * 그 반대(빈 것을 채우는 일)는 사람이 하고, 하면 발행 수가 늘어난다.
     */
    @Test
    fun `RED25 - 서술 없이 발행된 것이 없다`() {
        assertAll(
            {
                assertThat(
                    jdbc.queryForList(
                        "SELECT slug FROM cocktail WHERE status = 'published' " +
                            "AND (tasting_note IS NULL OR tasting_note !~ '\\S')",
                        String::class.java,
                    ),
                ).`as`("GATE-COCKTAIL-01 — 서술이 없으면 발행되지 않는다").isEmpty()
            },
            {
                assertThat(count("SELECT count(*) FROM cocktail WHERE status = 'draft'"))
                    .`as`("만드는 법만 적힌 8종은 draft 로 남는다")
                    .isEqualTo(8)
            },
            {
                assertThat(count("SELECT count(*) FROM cocktail WHERE status = 'published'"))
                    .isEqualTo(41)
            },
            {
                assertThat(count("SELECT count(*) FROM cocktail"))
                    .`as`("프로토타입 전량이 들어왔다")
                    .isEqualTo(49)
            },
        )
    }

    // ── RED 29~31 : 도수 ─────────────────────────────────────────────────

    /**
     * RED 29·30·31 — 프로토타입의 도수는 **실측값**이라 `abv_override` 로 들어간다.
     *
     * `abv_calculated` 에 넣으면 레시피에서 재계산될 때 덮인다 — 사람이 재어 본 값이
     * 계산값에 밀리면 안 된다 (`INV-COCKTAIL-06`).
     */
    @Test
    fun `RED29,30,31 - 도수가 override 로 들어가고 무알콜이 0이다`() {
        assertAll(
            {
                assertThat(count("SELECT count(*) FROM cocktail WHERE abv_override IS NOT NULL"))
                    .`as`("RED29 — 실측값이다")
                    .isEqualTo(49)
            },
            {
                assertThat(
                    jdbc.queryForList(
                        "SELECT slug FROM cocktail WHERE base_spirit = 'non-alcoholic' AND abv <> 0",
                        String::class.java,
                    ),
                ).`as`("RED30").isEmpty()
            },
            {
                // DB CHECK 가 같은 것을 막지만, 시드가 그 조건을 만족하는지 확인한다.
                assertThat(
                    jdbc.queryForList(
                        "SELECT slug FROM cocktail WHERE abv = 0 AND base_spirit <> 'non-alcoholic'",
                        String::class.java,
                    ),
                ).`as`("RED31").isEmpty()
            },
        )
    }

    // ── RED 32~33 : 멱등 ─────────────────────────────────────────────────

    /**
     * RED 32·33 — 시드를 다시 적용해도 **중복되지 않고 덮어쓰지도 않는다.**
     *
     * `R__` 는 체크섬이 바뀌면 재적용된다. 운영에서 에디터가 고친 값을 시드가 되돌리면
     * 안 되므로 `ON CONFLICT DO NOTHING` · `IF NOT EXISTS` 로 **없을 때만 넣는다.**
     *
     * 재적용을 흉내 내기 위해 SQL 을 직접 다시 실행한다 — Flyway 를 두 번 돌릴 수 없다.
     */
    @Test
    fun `RED32,33 - 시드를 두 번 적용해도 중복되지 않는다`() {
        val before = Triple(count("SELECT count(*) FROM cocktail"),
            count("SELECT count(*) FROM ingredient"),
            count("SELECT count(*) FROM recipe_ingredient"))

        // 에디터가 고친 상황을 만든다. 재적용이 이것을 되돌리면 안 된다.
        jdbc.update("UPDATE cocktail SET summary = '에디터가 고친 요약' WHERE slug = 'negroni'")

        // `@BeforeEach` 가 이미 한 번 깔았다. 여기가 **두 번째**다 —
        // 번호 순서가 곧 의존 순서다 (파일명 주석 참조).
        replaySeed("R__seed_01_ingredient.sql")
        replaySeed("R__seed_02_cocktail.sql")

        val after = Triple(count("SELECT count(*) FROM cocktail"),
            count("SELECT count(*) FROM ingredient"),
            count("SELECT count(*) FROM recipe_ingredient"))

        assertAll(
            { assertThat(after).`as`("RED32 — 행 수가 그대로다").isEqualTo(before) },
            {
                assertThat(
                    jdbc.queryForObject(
                        "SELECT summary FROM cocktail WHERE slug = 'negroni'", String::class.java,
                    ),
                ).`as`("RED33 — 운영 데이터를 덮어쓰지 않는다").isEqualTo("에디터가 고친 요약")
            },
        )
    }

    // ── RED 34~35 : 손실 항목 ────────────────────────────────────────────

    /**
     * RED 34 — **`profile` 5축이 살아 있다.**
     *
     * `FR-COCKTAIL-023`(맛 프로필 레이더)은 P1 이라 Phase 1a 에서 안 쓴다.
     * 그래도 지금 넣는 이유: **다시 만들 수 없는 자료다.** 프로토타입을 지우면 사라진다.
     *
     * `cocktail.flavor_profile SMALLINT[5]` 가 이미 있다 (V009) — 이 이슈가 컬럼을
     * 새로 만들 필요는 없었다.
     */
    @Test
    fun `RED34 - profile 5축이 보존됐다`() {
        val withProfile = count("SELECT count(*) FROM cocktail WHERE flavor_profile IS NOT NULL")
        val wrongLength = count(
            "SELECT count(*) FROM cocktail WHERE flavor_profile IS NOT NULL AND cardinality(flavor_profile) <> 5",
        )

        assertAll(
            { assertThat(withProfile).`as`("다시 만들 수 없는 자료다").isPositive() },
            { assertThat(wrongLength).isZero() },
        )
    }

    /** RED 35 — `{title, paragraphs}` 가 마크다운으로 직렬화됐다. 구조를 잃되 읽을 수 있게 잃는다. */
    @Test
    fun `RED35 - story 가 마크다운으로 보존됐다`() {
        val story = jdbc.queryForObject(
            "SELECT story FROM cocktail WHERE slug = 'negroni'", String::class.java,
        )!!

        assertAll(
            { assertThat(story).`as`("제목이 헤딩으로 남았다").startsWith("## ") },
            { assertThat(story).contains("피렌체") },
            { assertThat(story).`as`("JSON 을 그대로 박지 않았다").doesNotContain("\"paragraphs\"") },
        )
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────

    /** 시드 SQL 을 그대로 다시 실행한다. Flyway 를 두 번 돌릴 수 없어서다. */
    private fun replaySeed(name: String) {
        val stream = javaClass.classLoader.getResourceAsStream("db/migration/$name")
            // 파일명이 바뀌면 여기가 `null` 이 되고, 그대로 두면 **멱등을 확인하지 않은 채**
            // 초록이 될 수 있다. 못 찾은 것을 사실대로 말하게 한다.
            ?: error("시드 SQL 을 찾지 못했다: $name — 파일명이 바뀌었나?")

        jdbc.execute(stream.bufferedReader().readText())
    }

    private fun count(sql: String) = jdbc.queryForObject(sql, Long::class.java)!!

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
            registry.add("kcocktail.verification.scheduled") { false }
        }
    }
}
