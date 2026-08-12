package kr.kcocktail.cocktail.category

import com.fasterxml.jackson.databind.ObjectMapper
import kr.kcocktail.common.web.cache.CacheControlFilter
import kr.kcocktail.support.PostgresSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.get
import org.springframework.transaction.support.TransactionTemplate

/**
 * ISSUE-022 — `GET /categories` (SPEC-07 §2.1, `FR-COCKTAIL-029` · `FR-COCKTAIL-030`).
 *
 * ## 코퍼스가 판정 기준이다
 *
 * 이 응답의 소비자는 `generateStaticParams` 다 — **어떤 카테고리 경로를 정적 생성할지**를
 * 여기서 정한다. 그래서 "enum 전체" 가 아니라 "발행분이 있는 값" 이 기본이다
 * (DECISIONS §1.11 — 빈 카테고리는 제외). 빈 페이지는 색인 가치가 없다.
 *
 * 픽스처 셋이 이 판정을 전부 만든다.
 *
 * | | 상태 | 기주 | `style_primary` | `styles` | 메이킹 |
 * |---|---|---|---|---|---|
 * | c1 | `published` | `gin` | `highball` | `highball`·`creamy` | `build` |
 * | c2 | `published` | `gin` | `sour` | `sour` | `shake` |
 * | c3 | `draft` | `vodka` | `tiki` | `tiki` | `stir` |
 *
 * `creamy` 가 요점이다 — 발행분의 `styles` 에는 있지만 누구의 `style_primary` 도 아니다.
 * 카테고리에 나오면 `R-C-3`(primary 가 대표)을 어긴 것이다 (RED 14·15).
 */
@SpringBootTest
@AutoConfigureMockMvc
class CategoryApiTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var json: ObjectMapper
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var tx: TransactionTemplate

    @BeforeEach
    fun seed() {
        jdbc.execute("TRUNCATE cocktail CASCADE")

        cocktail("c1", status = "published", base = "gin", primary = "highball", styles = listOf("highball", "creamy"), method = "build")
        cocktail("c2", status = "published", base = "gin", primary = "sour", styles = listOf("sour"), method = "shake")
        cocktail("c3", status = "draft", base = "vodka", primary = "tiki", styles = listOf("tiki"), method = "stir")
    }

    // ── RED 1~3 : 3축 목록 (FR-COCKTAIL-029) ──────────────────────────────

    @Test
    fun `RED1 - base 카테고리 목록을 반환한다`() {
        assertThat(slugs("base")).containsExactly("gin")
    }

    @Test
    fun `RED2 - style 카테고리 목록을 반환한다`() {
        assertThat(slugs("style")).containsExactlyInAnyOrder("highball", "sour")
    }

    @Test
    fun `RED3 - method 카테고리 목록을 반환한다`() {
        assertThat(slugs("method")).containsExactlyInAnyOrder("build", "shake")
    }

    /** 응답 최상위에 축이 셋뿐이다 — 당도·도수·향맛 자리가 아예 없다 (`PRIN-P06`). */
    @Test
    fun `RED4 - 3축 외의 카테고리가 없다`() {
        assertThat(body().keys).containsExactlyInAnyOrder("base", "style", "method")
    }

    @Test
    fun `RED5 - 각 항목에 slug 와 라벨과 건수가 있다`() {
        allItems().forEach { item ->
            assertThat(item.keys).containsExactlyInAnyOrder("slug", "labelKo", "count", "intro")
            assertThat(item["slug"] as String).isNotBlank()
            assertThat(item["labelKo"] as String).isNotBlank()
            assertThat(item["count"] as Int).isPositive()
        }

        // 라벨의 정본이 Kotlin 이라 API 가 내보낸다 (DECISIONS §1.10).
        assertThat(item("base", "gin")["labelKo"]).isEqualTo("진")
    }

    /** RED 6 — 나가는 슬러그가 전부 ADR-0002 확정값 안에 있다. */
    @Test
    fun `RED6 - slug 가 ADR-0002 확정값이다`() {
        CategoryAxis.entries.forEach { axis ->
            assertThat(slugs(axis.slug)).isSubsetOf(axis.taxonomy.map { it.slug })
        }
    }

    // ── RED 7 · 9 · 19 : 축 조합 금지 · 사이트맵 (R-C-2 · NFR-S-03·S-04) ──

    /** 슬러그에 `/` 가 섞여 나오면 그것만으로 조합 경로가 만들어진다. */
    @Test
    fun `RED7 - 조합 경로가 응답에 없다`() {
        assertThat(allItems().map { it["slug"] as String })
            .allSatisfy { assertThat(it).matches("^[a-z][a-z0-9-]*$") }
    }

    /** RED 9 — 응답에서 조립되는 경로가 전부 단일 축이다 (`NFR-S-03` 배포 차단). */
    @Test
    fun `RED9 - 사이트맵에 조합 경로가 0개다`() {
        assertThat(paths()).isNotEmpty()
        assertThat(paths()).allSatisfy {
            assertThat(it).matches("^/cocktails/(base|style|method)/[a-z0-9-]+$")
        }
    }

    /** RED 19 — `NFR-S-04`. 발행분이 만든 축값이 하나도 빠지지 않는다. */
    @Test
    fun `RED19 - 사이트맵에 카테고리 경로가 포함된다`() {
        assertThat(paths()).containsExactlyInAnyOrder(
            "/cocktails/base/gin",
            "/cocktails/style/highball",
            "/cocktails/style/sour",
            "/cocktails/method/build",
            "/cocktails/method/shake",
        )
    }

    // ── RED 10~13 : 코퍼스 존재 여부 (DECISIONS §1.11) ────────────────────

    /** `generateStaticParams` 가 빈 페이지를 만들면 안 된다. */
    @Test
    fun `RED10 - 발행분이 없는 카테고리는 제외된다`() {
        assertThat(slugs("base")).doesNotContain("whisky", "rum", "korean", "non-alcoholic")
        assertThat(slugs("style")).doesNotContain("spritz", "frozen", "shot")
        assertThat(slugs("method")).doesNotContain("blend", "etc")
        assertThat(allItems()).allSatisfy { assertThat(it["count"] as Int).isPositive() }
    }

    @Test
    fun `RED11 - 건수가 정확하다`() {
        assertThat(item("base", "gin")["count"]).isEqualTo(2)
        assertThat(item("style", "highball")["count"]).isEqualTo(1)
        assertThat(item("style", "sour")["count"]).isEqualTo(1)
        assertThat(item("method", "build")["count"]).isEqualTo(1)
        assertThat(item("method", "shake")["count"]).isEqualTo(1)
    }

    /** c3 는 `draft` 다. 발행 전 데이터가 URL 을 만들면 안 된다 (SPEC-07 §5). */
    @Test
    fun `RED12 - draft 만 있는 카테고리는 제외된다`() {
        assertThat(slugs("base")).doesNotContain("vodka")
        assertThat(slugs("style")).doesNotContain("tiki")
        assertThat(slugs("method")).doesNotContain("stir")

        jdbc.execute("UPDATE cocktail SET status = 'published' WHERE slug = 'c3'")
        assertThat(slugs("base")).contains("vodka")
    }

    /** RED 13 — 필터 UI 는 전체가 필요할 수 있다. `?include=all` 이 그 자리다. */
    @Test
    fun `RED13 - include all 이 enum 전체를 낸다`() {
        CategoryAxis.entries.forEach { axis ->
            assertThat(slugs(axis.slug, includeAll = true))
                .containsExactlyElementsOf(axis.taxonomy.map { it.slug })
        }

        // 전체 목록이어도 건수는 발행분 기준이다.
        assertThat(item("base", "gin", includeAll = true)["count"]).isEqualTo(2)
        assertThat(item("base", "whisky", includeAll = true)["count"]).isEqualTo(0)
        assertThat(item("base", "vodka", includeAll = true)["count"])
            .`as`("draft 는 세지 않는다")
            .isEqualTo(0)
    }

    // ── RED 14·15 : style 축은 style_primary 기준 (R-C-3, DECISIONS §1.11) ─

    /**
     * RED 14 — `creamy` 는 c1 의 `styles` 에 있지만 `style_primary` 가 아니다.
     * 전부 카테고리로 올리면 같은 칵테일이 여러 카테고리의 정본처럼 보이고 색인이 갈린다.
     */
    @Test
    fun `RED14 - style 카테고리는 style_primary 기준이다`() {
        assertThat(item("style", "highball")["count"])
            .`as`("primary 가 highball 인 것은 c1 하나")
            .isEqualTo(1)

        assertThat(CategoryAxis.STYLE.countColumn).isEqualTo("style_primary")
    }

    /** RED 15 — RED 14 의 반대편. `styles` 에만 있는 값은 카테고리가 되지 않는다. */
    @Test
    fun `RED15 - styles 에만 있고 primary 가 아닌 값은 카테고리가 아니다`() {
        assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM cocktail_style s JOIN cocktail c ON c.id = s.cocktail_id " +
                    "WHERE s.style = 'creamy' AND c.status = 'published'",
                Int::class.java,
            ),
        ).`as`("발행분의 styles 에 creamy 가 실제로 있다").isEqualTo(1)

        assertThat(slugs("style")).doesNotContain("creamy")
    }

    // ── RED 16·17 : 소개 문구 (FR-COCKTAIL-031 · NFR-S-07, D-1) ───────────

    @Test
    fun `RED16 - 카테고리마다 소개 문구 필드가 있다`() {
        jdbc.update(
            "INSERT INTO category_intro (axis, slug, intro) VALUES ('base', 'gin', ?) " +
                "ON CONFLICT (axis, slug) DO UPDATE SET intro = EXCLUDED.intro",
            "주니퍼 향이 중심을 잡는 기주다.",
        )

        assertThat(item("base", "gin")["intro"]).isEqualTo("주니퍼 향이 중심을 잡는 기주다.")
    }

    /**
     * RED 17 — **문구가 없어도 카테고리 페이지는 나온다.**
     *
     * `NFR-S-07` 은 "발행 차단" 이라 했고 `FR-COCKTAIL-031` 은 P1 이다. 충돌이었고,
     * D-1 이 **경고로** 확정했다 (DECISIONS §2, SPEC-04 §3.1).
     * 지금 만드는 것은 **저장 구조**까지다 — 차단은 P1 착수 시다.
     */
    @Test
    fun `RED17 - 소개 문구가 없어도 카테고리는 나온다`() {
        jdbc.execute("DELETE FROM category_intro")

        assertThat(slugs("style")).contains("sour")
        assertThat(item("style", "sour")["intro"]).isNull()
    }

    // ── RED 18 : 색인 (NFR-S-02) ──────────────────────────────────────────

    /** 필터 결과(이슈 018)와 대비되는 지점이다. 카테고리 경로는 **색인 대상**이다. */
    @Test
    fun `RED18 - 카테고리 응답에 noindex 가 붙지 않는다`() {
        val response = call().response

        assertThat(response.getHeader("X-Robots-Tag")).isNull()
        assertThat(response.contentAsString).doesNotContain("noindex")
    }

    // ── RED 20 : 캐싱 (SPEC-07 §1.6) ──────────────────────────────────────

    @Test
    fun `RED20 - ETag 와 Cache-Control 이 붙는다`() {
        val first = call().response

        assertThat(first.getHeader(HttpHeaders.ETAG)).isNotBlank()
        assertThat(first.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo(CacheControlFilter.PUBLIC_CACHE)

        // SSG 빌드가 같은 엔드포인트를 반복 호출한다. 내용이 그대로면 304 로 끝나야 한다.
        val second = mvc.get(CategoryController.CATEGORIES) {
            header(HttpHeaders.IF_NONE_MATCH, first.getHeader(HttpHeaders.ETAG)!!)
        }.andReturn().response

        assertThat(second.status).isEqualTo(304)
    }

    /** SPEC-07 §1.1 — 공개 응답에 내부 `id` 가 없다. 슬러그가 공개 식별자다. */
    @Test
    fun `공개 응답에 내부 id 가 없다`() {
        assertThat(allItems()).allSatisfy { assertThat(it).doesNotContainKey("id") }
    }

    // ── 헬퍼 ───────────────────────────────────────────────────────────────

    private fun call(includeAll: Boolean = false): MvcResult =
        mvc.get(CategoryController.CATEGORIES) {
            if (includeAll) param("include", CategoryController.INCLUDE_ALL)
        }.andReturn()

    @Suppress("UNCHECKED_CAST")
    private fun body(includeAll: Boolean = false): Map<String, List<Map<String, Any>>> =
        json.readValue(call(includeAll).response.getContentAsString(Charsets.UTF_8), Map::class.java)
            as Map<String, List<Map<String, Any>>>

    private fun axis(name: String, includeAll: Boolean = false): List<Map<String, Any>> =
        body(includeAll)[name] ?: error("응답에 $name 축이 없다")

    private fun slugs(name: String, includeAll: Boolean = false): List<String> =
        axis(name, includeAll).map { it["slug"] as String }

    private fun item(name: String, slug: String, includeAll: Boolean = false): Map<String, Any?> =
        axis(name, includeAll).firstOrNull { it["slug"] == slug } ?: error("$name 축에 $slug 이 없다")

    private fun allItems(): List<Map<String, Any>> = body().values.flatten()

    /** 응답에서 조립한 카테고리 경로. 프론트의 사이트맵·`generateStaticParams` 가 하는 일이다. */
    private fun paths(): List<String> = body().flatMap { (name, items) ->
        val axis = CategoryAxis.ofSlug(name)
        items.map { CategoryPaths.categoryPath(axis, it["slug"] as String) }
    }

    /**
     * `fk_cocktail__style_primary` 가 `DEFERRABLE INITIALLY DEFERRED` 라 한 트랜잭션으로 묶는다
     * (`V009__cocktail.sql`) — 문장마다 커밋하면 첫 INSERT 에서 걸린다.
     */
    private fun cocktail(
        slug: String,
        status: String,
        base: String,
        primary: String,
        styles: List<String>,
        method: String,
    ): Unit = tx.execute {
        val id = jdbc.queryForObject(
            """
            INSERT INTO cocktail (slug, name_ko, name_en, summary,
                base_spirit, style_primary, method, sweetness, glass_type, status, published_at)
            VALUES (?, '테스트', 'test', '요약', ?, ?, ?, 'dry', '하이볼 글라스', ?,
                CASE WHEN ? = 'published' THEN now() END)
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            slug, base, primary, method, status, status,
        )!!
        styles.forEach { jdbc.execute("INSERT INTO cocktail_style VALUES ($id, '$it')") }
    }!!

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun datasource(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { PostgresSupport.container.jdbcUrl }
            registry.add("spring.datasource.username") { PostgresSupport.container.username }
            registry.add("spring.datasource.password") { PostgresSupport.container.password }
            registry.add("spring.flyway.enabled") { true }
            registry.add("spring.flyway.user") { PostgresSupport.container.username }
            registry.add("spring.flyway.password") { PostgresSupport.container.password }
        }
    }
}
