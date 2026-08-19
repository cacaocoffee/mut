package kr.mut.search.query

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kr.mut.search.api.SearchDocumentDraft
import kr.mut.search.api.SearchEntityType
import kr.mut.search.api.SearchIndexSync
import kr.mut.support.PostgresSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
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
 * ISSUE-024 — `GET /search` · `/search/suggest` (SPEC-07 §2.4).
 *
 * ## 색인을 API 로 넣는다
 *
 * `search_document` 에 직접 INSERT 하지 않고 이슈 017 의 [SearchIndexSync] 를 쓴다.
 * 별칭 정리와 초성 분해가 **저장 시점에** 일어나므로(`SearchDocumentText`), 직접 넣으면
 * 운영과 다른 색인 위에서 검색을 검증하게 된다 — 그러면 초성이 안 걸려도 테스트는 통과한다.
 *
 * ## 코퍼스
 *
 * | 이름 | 영문 | 별칭 | 초성 |
 * |---|---|---|---|
 * | 올드패션드 | Old Fashioned | 올드 패션드 · 올패 | `ㅇㄷㅍㅅㄷ ㅇㅍ` |
 * | 마르가리타 | Margarita | — | `ㅁㄹㄱㄹㅌ` |
 * | 네그로니 | Negroni | — | `ㄴㄱㄹㄴ` |
 * | 진 (재료) | Gin | — | `ㅈ` |
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SearchApiTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var json: ObjectMapper
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var sync: SearchIndexSync
    @Autowired private lateinit var tx: TransactionTemplate

    @BeforeAll
    fun seed() {
        jdbc.execute("TRUNCATE search_document")
        tx.execute {
            index(1, "old-fashioned", "올드패션드", "Old Fashioned", listOf("올드 패션드", "올패"))
            index(2, "margarita", "마르가리타", "Margarita")
            index(3, "negroni", "네그로니", "Negroni")
            index(4, "gin", "진", "Gin", type = SearchEntityType.INGREDIENT)
            // 발행되지 않은 것 — RED 19·20
            index(9, "draft-only", "초안칵테일", "Draft Only", published = false)
        }
    }

    // ── RED 1~7 : 매칭 (R-F2.1-3) ─────────────────────────────────────────

    /**
     * RED 7 — `R-F2.1-3` 이 요구한 **네 가지 표기가 같은 결과**를 준다.
     *
     * 이 한 줄이 요구사항 그대로다: `올드패션드` / `올드 패션드` / `Old Fashioned` / `올패`.
     */
    @ParameterizedTest
    @ValueSource(strings = ["올드패션드", "올드 패션드", "Old Fashioned", "올패", "old fashioned", "네그로"])
    fun `RED1-7 - 표기 변형이 모두 매칭된다`(q: String) {
        assertThat(slugs(search(q)))
            .`as`("질의 %s", q)
            .isNotEmpty()
    }

    @Test
    fun `RED7 - 네 표기가 같은 결과를 준다`() {
        val results = listOf("올드패션드", "올드 패션드", "Old Fashioned", "올패").map { slugs(search(it)) }

        assertThat(results).allSatisfy { assertThat(it).containsExactly("old-fashioned") }
    }

    // ── RED 8~11 : 초성 (R-F2.1-4) ────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = ["ㅁㄹㄱㄹㅌ", "ㅁㄹㄱ"])
    fun `RED8-9 - 초성과 초성 프리픽스가 매칭된다`(q: String) {
        assertThat(slugs(search(q))).contains("margarita")
    }

    @Test
    fun `RED10 - 초성 검색인지 응답이 알려 준다`() {
        assertAll(
            { assertThat(search("ㅁㄹㄱ")["hadChosung"].asBoolean()).isTrue() },
            { assertThat(search("마르가리타")["hadChosung"].asBoolean()).isFalse() },
        )
    }

    /**
     * RED 11 **결정** — 섞인 입력은 **일반 검색**이다 (DECISIONS §1.9).
     *
     * 초성 컬럼에서 찾으면 `마` 때문에 아무것도 안 걸린다. 초성으로 치다 만 것인지
     * 오타인지 알 수 없으므로 일반 경로로 보낸다.
     */
    @Test
    fun `RED11 - 초성이 섞인 입력은 일반 검색이다`() {
        assertThat(search("마ㄹㄱ")["hadChosung"].asBoolean()).isFalse()
    }

    /** RED 12 — 초성은 GIN 트라이그램을 탄다 (SPEC-05 §6 · G-13). */
    @Test
    fun `RED12 - 초성 매칭이 GIN 인덱스를 탄다`() {
        jdbc.execute("ANALYZE search_document")
        val plan = jdbc.query(
            "EXPLAIN SELECT slug FROM search_document " +
                "WHERE is_published AND chosung LIKE '%ㅁㄹㄱ%'",
        ) { rs, _ -> rs.getString(1) }.joinToString("\n")

        // 4행짜리 테이블이라 플래너가 seq scan 을 고를 수 있다. 인덱스의 존재를 함께 본다 —
        // 계획만 단언하면 코퍼스 크기에 따라 흔들린다 (이슈 023 RED 28 에서 겪었다).
        assertThat(
            jdbc.query("SELECT indexname FROM pg_indexes WHERE tablename = 'search_document'") { rs, _ ->
                rs.getString(1)
            },
        ).`as`("계획:\n%s", plan).contains("ix_search_document__chosung")
    }

    // ── RED 13~18 : 타입별 그룹핑 (R-F5-1) ────────────────────────────────

    /**
     * RED 13·15·18 — **4종 자리를 항상 채운다.**
     *
     * `bar` 는 Phase 1b, `article` 은 Phase 2 라 늘 비어 있지만 키는 있어야 한다.
     * 나중에 키가 생기면 클라이언트의 그룹 렌더링이 그때 깨진다.
     */
    @Test
    fun `RED13-15-18 - 4종 그룹이 항상 있고 빈 그룹도 포함된다`() {
        val groups = search("진")["groups"]

        assertThat(groups.fieldNames().asSequence().toList())
            .containsExactlyInAnyOrder("cocktail", "ingredient", "bar", "article")
        assertThat(groups["bar"]["items"]).isEmpty()
        assertThat(groups["bar"]["count"].asInt()).isZero()
    }

    @Test
    fun `RED14-16 - 그룹마다 건수가 있다`() {
        val groups = search("진")["groups"]

        assertThat(groups["ingredient"]["count"].asInt()).isEqualTo(1)
        assertThat(groups["ingredient"]["items"]).hasSize(1)
    }

    /** RED 17 — 그룹 내 정렬은 `weight` 다. 산정식은 미정이고 타입별 고정값이다 (G-13). */
    @Test
    fun `RED17 - weight 가 응답에 있다`() {
        val hit = search("네그로니")["groups"]["cocktail"]["items"][0]

        assertThat(hit["weight"].asInt()).isEqualTo(SearchEntityType.COCKTAIL.defaultWeight)
    }

    // ── RED 19~21 : 발행분만 ──────────────────────────────────────────────

    @Test
    fun `RED19-21 - 발행되지 않은 것은 검색되지 않는다`() {
        assertThat(slugs(search("초안칵테일"))).isEmpty()
    }

    // ── RED 22~24 : search_miss 재료 (SPEC-10 §4.3) ───────────────────────

    /**
     * RED 22~24 — **0건의 원인이 둘**이라 구분해야 한다.
     *
     * 콘텐츠가 없는 것과 **초성 색인이 고장난 것**은 대응이 다르다.
     * 구분하지 못하면 장애를 "수요 없음" 으로 읽고 영영 못 고친다.
     */
    @Test
    fun `RED22-24 - 0건일 때 원인을 구분할 재료가 응답에 있다`() {
        val plain = search("없는칵테일이름")
        val chosung = search("ㅋㅋㅋㅋㅋ")

        assertAll(
            { assertThat(plain["matchedCount"].asInt()).isZero() },
            { assertThat(plain["hadChosung"].asBoolean()).isFalse() },
            { assertThat(chosung["matchedCount"].asInt()).isZero() },
            { assertThat(chosung["hadChosung"].asBoolean()).`as`("초성 0건이 구분된다").isTrue() },
            { assertThat(plain["query"].asText()).isEqualTo("없는칵테일이름") },
        )
    }

    // ── RED 25~30 : 보안 · 방어 ───────────────────────────────────────────

    /**
     * RED 25·26 — 바인딩이 인젝션을 막지만 **`%` 와 `_` 는 바인딩 안에서도 살아 있다.**
     * `q=%` 하나로 전 코퍼스를 긁어 가는 것을 막는 것은 이스케이프뿐이다.
     */
    @ParameterizedTest
    @ValueSource(strings = ["%", "_", "%%", "' OR 1=1 --", "ㅁ%"])
    fun `RED25-26 - 와일드카드와 인젝션이 막힌다`(q: String) {
        val body = search(q)

        assertThat(body["matchedCount"].asInt())
            .`as`("질의 %s 가 전 코퍼스를 긁지 않는다", q)
            .isZero()
    }

    @Test
    fun `RED28 - 빈 q 는 400 이다`() {
        assertAll(
            { assertThat(status("")).isEqualTo(400) },
            { assertThat(status("   ")).isEqualTo(400) },
            { assertThat(mvc.get(SEARCH).andReturn().response.status).`as`("파라미터 자체가 없어도").isEqualTo(400) },
        )
    }

    @Test
    fun `RED29 - q 길이 상한이 있다`() {
        assertThat(status("가".repeat(SearchQuery.MAX_LENGTH + 1))).isEqualTo(400)
    }

    /** RED 30 — 페이징하지 않는 대신 그룹당 상한을 둔다. 통합 검색은 훑어보는 화면이다. */
    @Test
    fun `RED30 - 그룹당 상한이 있다`() {
        assertThat(SearchController.GROUP_LIMIT).isPositive()
        assertThat(search("ㅇ")["groups"]["cocktail"]["items"].size())
            .isLessThanOrEqualTo(SearchController.GROUP_LIMIT)
    }

    // ── RED 31~32 : 자동완성 ──────────────────────────────────────────────

    @Test
    fun `RED31 - suggest 가 프리픽스 매칭이다`() {
        val prefix = json.readTree(raw("$SUGGEST?q=올드"))
        val middle = json.readTree(raw("$SUGGEST?q=패션드"))

        assertAll(
            { assertThat(prefix.map { it["slug"].asText() }).contains("old-fashioned") },
            { assertThat(middle).`as`("가운데 일치는 자동완성이 아니다").isEmpty() },
        )
    }

    @Test
    fun `RED32 - suggest 결과에 상한이 있다`() {
        assertThat(SearchController.SUGGEST_LIMIT).isBetween(1, 20)
    }

    // ── RED 34~35 : 규약 ──────────────────────────────────────────────────

    @Test
    fun `RED34 - 내부 id 가 없다`() {
        assertThat(raw("$SEARCH?q=네그로니")).doesNotContain("\"id\"", "entityId")
    }

    /** RED 35 — 검색 결과는 색인 대상이 아니다 (`PRIN-P06`). 상세·카테고리와 정반대다. */
    @Test
    fun `RED35 - noindex 가 붙는다`() {
        assertAll(
            { assertThat(header("$SEARCH?q=네그로니")).containsIgnoringCase("noindex") },
            { assertThat(header("$SUGGEST?q=네그")).containsIgnoringCase("noindex") },
        )
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────

    private fun index(
        id: Long,
        slug: String,
        nameKo: String,
        nameEn: String?,
        aliases: List<String> = emptyList(),
        type: SearchEntityType = SearchEntityType.COCKTAIL,
        published: Boolean = true,
    ) = sync.index(SearchDocumentDraft(type, id, slug, nameKo, nameEn, aliases, published))

    private fun search(q: String): JsonNode = json.readTree(raw(SEARCH) { param("q", q) })

    private fun slugs(body: JsonNode): List<String> =
        body["groups"].flatMap { group -> group["items"].map { it["slug"].asText() } }

    private fun raw(url: String, block: org.springframework.test.web.servlet.MockHttpServletRequestDsl.() -> Unit = {}) =
        mvc.get(url) { block() }.andReturn().response.getContentAsString(Charsets.UTF_8)

    private fun header(url: String) = mvc.get(url).andReturn().response.getHeader("X-Robots-Tag")

    private fun status(q: String) = mvc.get(SEARCH) { param("q", q) }.andReturn().response.status

    companion object {
        private const val SEARCH = "/api/v1/search"
        private const val SUGGEST = "/api/v1/search/suggest"

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
