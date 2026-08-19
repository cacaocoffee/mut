package kr.mut.search.facet

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kr.mut.support.PostgresSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.support.TransactionTemplate

/**
 * ISSUE-019 — `GET /cocktails/facets` (SPEC-07 §3.2 · `FR-SEARCH-002`).
 *
 * ## 이 파일이 지키는 것
 *
 * `R-F2.1-2` 는 모든 필터 값 옆에 개수를 요구하고 0건은 비활성 처리하라고 한다.
 * PRD 가 **"초기부터 넣지 않으면 나중에 UI 를 다시 짜야 한다"** 고 못박은 항목이다.
 *
 * 요체는 **축마다 계산이 다르다**는 것이다 (SPEC-07 §3.2).
 *
 * | 축 | 계산 |
 * |---|---|
 * | 기주 · 스타일 · 메이킹 · 당도 · 도수 | 같은 축의 현재 선택을 **무시** |
 * | 향·맛 | 현재 선택에 **더했을 때** |
 *
 * 코퍼스는 이슈 018 의 8종을 그대로 쓴다 — 두 API 가 같은 데이터를 봐야
 * RED 14(카운트와 실제 결과 일치)가 뜻을 갖는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FacetApiTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var json: ObjectMapper
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var tx: TransactionTemplate

    @BeforeAll
    fun seed() {
        jdbc.execute("TRUNCATE cocktail, ingredient CASCADE")
        FacetCorpus.rows().forEach { insert(it) }
        insert(FacetCorpus.rows().first().copy(slug = "draft-only"), status = "draft")
    }

    // ── RED 1~5 : 같은 축 선택을 무시한다 ─────────────────────────────────

    /**
     * RED 1 — `base=gin` 을 고른 상태에서도 `vodka` 카운트가 살아 있어야 한다.
     *
     * 자기 선택을 반영하면 고르지 않은 값이 전부 0 이 되고, 사용자는 **다시 고를 수 없다.**
     * OR 축이라 하나 더 고르면 결과가 늘어난다는 사실이 카운트에 보여야 한다.
     */
    @Test
    fun `RED1 - 기주 카운트는 같은 축 선택을 무시한다`() {
        val all = facets()["base"]
        val filtered = facets("base" to "gin")["base"]

        assertThat(count(filtered, "vodka"))
            .`as`("진을 골랐어도 보드카는 여전히 고를 수 있다")
            .isEqualTo(count(all, "vodka"))
    }

    @ParameterizedTest
    @CsvSource(
        "style,  sour,            style",
        "method, build,           method",
        "sweet,  dry,             sweet",
        "abv,    mid,             abv",
    )
    fun `RED2-5 - 나머지 OR 축도 같은 축 선택을 무시한다`(
        param: String,
        value: String,
        axis: String,
    ) {
        val all = facets()[axis]
        val filtered = facets(param to value)[axis]

        assertThat(filtered).`as`("축 %s", axis).isEqualTo(all)
    }

    // ── RED 6~7 : 향·맛만 다르다 ──────────────────────────────────────────

    /**
     * RED 6·7 — 향·맛은 AND 라 **현재 선택을 유지한 채** 더했을 때의 수다.
     *
     * `flavor=citrus` 상태의 `herbal` 카운트는 "citrus 그리고 herbal" 이다.
     * 이것이 `FR-SEARCH-009`("조합 불가능한 값이 즉시 0으로 떨어져야 한다")를 만든다.
     */
    @Test
    fun `RED6-7 - 향맛 카운트는 현재 선택에 더했을 때의 수다`() {
        val all = facets()["flavor"]
        val withCitrus = facets("flavor" to "citrus")["flavor"]

        assertAll(
            {
                assertThat(count(withCitrus, "herbal"))
                    .`as`("citrus AND herbal 은 herbal 단독보다 적거나 같다")
                    .isLessThanOrEqualTo(count(all, "herbal"))
            },
            {
                assertThat(count(withCitrus, "citrus"))
                    .`as`("이미 고른 태그도 남는다 — 해제 가능한 상태로 그려야 한다")
                    .isPositive()
            },
        )
    }

    /** RED 8 — 다른 축의 선택은 모든 카운트에 반영된다. 무시하는 것은 **같은 축**뿐이다. */
    @Test
    fun `RED8 - 다른 축 선택은 모든 카운트에 반영된다`() {
        val all = facets()["style"]
        val ginOnly = facets("base" to "gin")["style"]

        assertThat(sum(ginOnly)).`as`("진 한정이라 총합이 줄어든다").isLessThan(sum(all))
    }

    // ── RED 9~11 : 0 과 부재 ──────────────────────────────────────────────

    /**
     * RED 9·11 — **0 인 값도 키로 있어야** 클라이언트가 비활성 칩을 그린다 (`NFR-A-06`).
     * 키가 없는 것과 값이 0 인 것은 뜻이 다르다.
     */
    @Test
    fun `RED9-11 - 0 인 값도 키로 남고 부재와 구분된다`() {
        // tiki 는 코퍼스에 있지만 gin 과 함께인 것은 없다
        val ginOnly = facets("base" to "gin")["style"]

        assertThat(ginOnly.has("tiki")).`as`("코퍼스에 있으니 키는 있다").isTrue()
        assertThat(count(ginOnly, "tiki")).`as`("조합이 없으니 0 이다").isZero()
    }

    /** RED 10 — 코퍼스에 아예 없는 값은 필터에 띄우지 않는다 (ADR-0002 §5). */
    @Test
    fun `RED10 - 코퍼스에 없는 값은 키 자체가 없다`() {
        val style = facets()["style"]

        assertThat(style.has("frozen"))
            .`as`("frozen 은 코퍼스에 0건이라 고를 이유가 없다")
            .isFalse()
    }

    // ── RED 12~14 : 카운트가 실제 결과와 일치한다 ─────────────────────────

    /**
     * RED 13·14 — **이 테스트가 패싯의 정확성을 보장한다.**
     *
     * 카운트는 "그 값을 고르면 몇 건" 이라는 약속이다. 목록 API 가 다른 수를 주면
     * 그 약속이 거짓이고, 사용자는 5 라고 쓰인 칩을 눌러 3 건을 본다.
     */
    @ParameterizedTest
    @CsvSource(
        "base,   gin",
        "base,   vodka",
        "style,  sour",
        "style,  highball",
        "method, shake",
        "sweet,  semi_sweet",
        "abv,    mid",
        "flavor, citrus",
    )
    fun `RED13-14 - 카운트가 실제 목록 결과와 일치한다`(axis: String, value: String) {
        val expected = count(facets()[axis], value)

        val actual = json.readTree(
            mvc.get("/api/v1/cocktails") { param(axis, value); param("size", "100") }
                .andReturn().response.getContentAsString(Charsets.UTF_8),
        )["page"]["totalElements"].asLong()

        assertThat(actual).`as`("%s=%s", axis, value).isEqualTo(expected)
    }

    /** RED 12 — 스타일과 메이킹은 상관관계가 높아 빈 조합이 빈발한다 (`FR-SEARCH-009`). */
    @Test
    fun `RED12 - 조합 불가능한 값이 0 이 된다`() {
        val counts = facets("style" to "creamy")["method"]

        assertThat(count(counts, "stir"))
            .`as`("크리미 + 스터 조합은 코퍼스에 없다")
            .isZero()
    }

    // ── RED 15~16 : 발행분만 ──────────────────────────────────────────────

    @Test
    fun `RED15-16 - draft 는 집계되지 않는다`() {
        val total = sum(facets()["base"])
        val published = jdbc.queryForObject(
            "SELECT count(*) FROM cocktail WHERE status = 'published'",
            Long::class.java,
        )!!

        assertThat(total).isEqualTo(published)
    }

    // ── RED 20~22 : 계약 ──────────────────────────────────────────────────

    @Test
    fun `RED20 - 응답이 축별 맵이다`() {
        assertThat(facetsRaw().fieldNames().asSequence().toList())
            .containsExactlyInAnyOrder("base", "style", "method", "sweet", "abv", "flavor")
    }

    /** RED 21 — 목록 API 와 **같은 쿼리스트링**을 받는다 (SPEC-05 §5). 모르는 값도 같이 400 이다. */
    @Test
    fun `RED21 - 필터 파라미터가 목록 API 와 동일하다`() {
        assertThat(
            mvc.get(FACETS) { param("base", "whiskey") }.andReturn().response.status,
        ).`as`("오타는 조용히 무시되지 않는다").isEqualTo(400)
    }

    /** RED 22 — 필터 계열이라 색인하지 않는다 (`PRIN-P06` · DECISIONS §1.6). */
    @Test
    fun `RED22 - noindex 가 붙는다`() {
        assertThat(mvc.get(FACETS).andReturn().response.getHeader("X-Robots-Tag"))
            .containsIgnoringCase("noindex")
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────

    private fun facets(vararg params: Pair<String, String>): Map<String, JsonNode> {
        val node = facetsRaw(*params)
        return node.fieldNames().asSequence().associateWith { node[it] }
    }

    private fun facetsRaw(vararg params: Pair<String, String>): JsonNode =
        json.readTree(
            mvc.get(FACETS) { params.forEach { (k, v) -> param(k, v) } }
                .andReturn().response.getContentAsString(Charsets.UTF_8),
        )

    private fun count(axis: JsonNode?, slug: String): Long = axis?.get(slug)?.asLong() ?: -1

    private fun sum(axis: JsonNode?): Long =
        axis?.fieldNames()?.asSequence()?.sumOf { axis[it].asLong() } ?: 0

    private fun JsonNode?.has(slug: String) = this?.has(slug) == true

    private fun insert(row: FacetCorpus.Row, status: String = "published") {
        tx.execute { FacetCorpus.insert(jdbc, row, status) }
    }

    companion object {
        private const val FACETS = "/api/v1/cocktails/facets"

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { PostgresSupport.container.jdbcUrl }
            registry.add("spring.datasource.username") { PostgresSupport.container.username }
            registry.add("spring.datasource.password") { PostgresSupport.container.password }
            registry.add("spring.flyway.enabled") { true }
            registry.add("spring.flyway.user") { PostgresSupport.container.username }
            registry.add("spring.flyway.password") { PostgresSupport.container.password }
            registry.add("mut.verification.scheduled") { false }
        }
    }
}
