package kr.kcocktail.search.list

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kr.kcocktail.common.web.page.PageQuery
import kr.kcocktail.support.PostgresSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal

/**
 * ISSUE-018 — `GET /cocktails` 목록 · 필터 (SPEC-07 §3.1).
 *
 * ## 코퍼스를 한 번만 만든다
 *
 * 축이 여섯이고 결합 규칙이 축마다 달라서, 테스트마다 데이터를 다시 만들면
 * **무엇이 걸러졌는지**가 아니라 **무엇을 넣었는지**를 매번 읽어야 한다.
 * 8종 고정 코퍼스를 클래스당 한 번 넣고 전부 그 위에서 단언한다.
 *
 * | 슬러그 | 기주 | 스타일(전체) | 방법 | 당도 | 도수 | 향·맛 |
 * |---|---|---|---|---|---|---|
 * | `negroni` | gin | spirit-forward | stir | semi_dry | 24 | bitter · herbal |
 * | `gin-tonic` | gin | highball | build | dry | **10** | citrus |
 * | `moscow-mule` | vodka | highball · **sour** | build | semi_sweet | 12 | citrus · spicy |
 * | `whisky-sour` | whisky | sour | shake | semi_dry | **20** | sour · citrus |
 * | `espresso-martini` | vodka | spirit-forward | shake | semi_sweet | 22 | bitter · nutty |
 * | `pina-colada` | rum | creamy · tiki | blend | sweet | 13 | fruity · creamy |
 * | `makgeolli-punch` | korean | sour | shake | semi_sweet | 8 | fruity |
 * | `virgin-mojito` | non-alcoholic | highball | build | sweet | 0 | citrus · herbal |
 *
 * 도수 `10`·`20`·`0` 은 구간 경계를 노린 값이다 (RED 17~20).
 * `moscow-mule` 의 `style_primary` 는 `highball` 인데 `sour` 도 갖고 있다 —
 * 필터가 `styles` 전체를 보는지(DECISIONS §1.11) 이 한 줄이 판별한다.
 *
 * 여기에 발행되지 않은 두 건(`draft-*` · `archived-*`)을 더 넣는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CocktailListApiTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var json: ObjectMapper
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var named: NamedParameterJdbcTemplate
    @Autowired private lateinit var tx: TransactionTemplate

    @BeforeAll
    fun seed() {
        jdbc.execute("TRUNCATE cocktail, ingredient CASCADE")
        CORPUS.forEach { insert(it) }
        insert(NEGRONI.copy(slug = "draft-negroni", nameKo = "초안 네그로니"), status = "draft")
        insert(NEGRONI.copy(slug = "archived-negroni", nameKo = "보관 네그로니"), status = "archived")
        jdbc.execute("ANALYZE cocktail")
    }

    // ── RED 1~7 : 축별 필터 (FR-SEARCH-001 6종) ────────────────────────────

    @Test
    fun `RED1 - base_필터가_동작한다`() {
        assertThat(slugs("?base=gin")).containsExactlyInAnyOrder("negroni", "gin-tonic")
    }

    /**
     * RED 2 — **`style_primary` 가 아니라 보유 스타일 전체**와 맞춘다 (DECISIONS §1.11).
     *
     * `moscow-mule` 의 대표 스타일은 `highball` 이다. 그래도 `sour` 필터에 걸려야 한다 —
     * 카테고리(경로)는 대표 하나지만 필터는 "이 성격을 갖고 있는가" 를 묻는다.
     */
    @Test
    fun `RED2 - style_필터가_동작한다`() {
        assertThat(slugs("?style=sour"))
            .containsExactlyInAnyOrder("moscow-mule", "whisky-sour", "makgeolli-punch")
    }

    @Test
    fun `RED3 - method_필터가_동작한다`() {
        assertThat(slugs("?method=build"))
            .containsExactlyInAnyOrder("gin-tonic", "moscow-mule", "virgin-mojito")
    }

    @Test
    fun `RED4 - sweet_필터가_동작한다`() {
        assertThat(slugs("?sweet=sweet")).containsExactlyInAnyOrder("pina-colada", "virgin-mojito")
    }

    @Test
    fun `RED5 - abv_구간_필터가_동작한다`() {
        assertThat(slugs("?abv=low")).containsExactlyInAnyOrder("gin-tonic", "makgeolli-punch")
    }

    @Test
    fun `RED6 - flavor_필터가_동작한다`() {
        assertThat(slugs("?flavor=citrus"))
            .containsExactlyInAnyOrder("gin-tonic", "moscow-mule", "whisky-sour", "virgin-mojito")
    }

    /**
     * RED 7 — 6축 밖의 파라미터.
     *
     * **모르는 파라미터는 무시하고, 모르는 _값_ 은 400 이다.** 둘을 같게 다루면 안 된다 —
     * 추적 파라미터(`utm_*`)가 붙었다고 400 을 내면 공유 링크가 깨지고,
     * 반대로 오타 난 값을 무시하면 필터가 걸린 줄 알고 전체 목록을 본다.
     */
    @Test
    fun `RED7 - 6개_축_외의_파라미터는_무시되거나_400이다`() {
        assertThat(slugs("?utm_source=kakao&isClassic=true")).hasSize(CORPUS.size)

        assertAll(
            { assertThat(status("?base=whiskey")).isEqualTo(400) },
            { assertThat(status("?style=classic")).isEqualTo(400) },
            { assertThat(status("?abv=strong")).isEqualTo(400) },
            { assertThat(status("?flavor=umami")).isEqualTo(400) },
        )
    }

    // ── RED 8~14 : OR / AND (SPEC-07 §3.1 — 이 이슈의 요체) ─────────────────

    @Test
    fun `RED8 - base가_복수면_OR다`() {
        assertThat(slugs("?base=gin,vodka"))
            .containsExactlyInAnyOrder("negroni", "gin-tonic", "moscow-mule", "espresso-martini")
    }

    @Test
    fun `RED9 - style이_복수면_OR다`() {
        assertThat(slugs("?style=sour,creamy"))
            .containsExactlyInAnyOrder("moscow-mule", "whisky-sour", "makgeolli-punch", "pina-colada")
    }

    @Test
    fun `RED10 - method가_복수면_OR다`() {
        assertThat(slugs("?method=build,blend"))
            .containsExactlyInAnyOrder("gin-tonic", "moscow-mule", "virgin-mojito", "pina-colada")
    }

    @Test
    fun `RED11 - abv가_복수면_OR다`() {
        assertThat(slugs("?abv=na,high"))
            .containsExactlyInAnyOrder("virgin-mojito", "negroni", "espresso-martini")
    }

    /** DECISIONS §1.11 — 복수 지정 시 **400**. 첫 값만 쓰지 않는다. */
    @Test
    fun `RED12 - sweet는_단일값이다`() {
        assertThat(slugs("?sweet=dry")).containsExactly("gin-tonic")
        assertThat(status("?sweet=dry,sweet")).isEqualTo(400)
    }

    /**
     * RED 13 — **이 이슈에서 가장 틀리기 쉬운 한 줄** (SPEC-07 §3.1).
     *
     * `citrus,herbal` 은 "시트러스 **그리고** 허브" 다. OR 로 짜면 4건이 나오고
     * 그것도 그럴듯해 보이기 때문에 리뷰에서 놓친다.
     */
    @Test
    fun `RED13 - flavor가_복수면_AND다`() {
        assertThat(slugs("?flavor=citrus,herbal"))
            .`as`("둘 다 가진 것만")
            .containsExactly("virgin-mojito")

        assertThat(slugs("?flavor=citrus"))
            .`as`("OR 였다면 이만큼 나온다 — 대조")
            .hasSize(4)

        assertThat(slugs("?flavor=citrus,herbal,spicy"))
            .`as`("셋 다 가진 것은 없다")
            .isEmpty()
    }

    @Test
    fun `RED14 - 축이_다르면_AND로_결합된다`() {
        assertThat(slugs("?base=gin&style=spirit-forward")).containsExactly("negroni")
        assertThat(slugs("?base=gin&method=build")).containsExactly("gin-tonic")
        assertThat(slugs("?base=gin&abv=na")).isEmpty()
    }

    // ── RED 21 : 연속값 파라미터 부재 (FR-SEARCH-003 · ADR-0003) ────────────

    /**
     * 계약에 없어야 한다. 코드에서 안 읽는 것만으로는 부족하다 —
     * 프론트가 생성 타입에서 `abvMin` 을 보면 붙일 수 있다고 믿는다.
     */
    @Test
    fun `RED21 - 연속값_파라미터를_받지_않는다`() {
        assertThat(slugs("?abvMin=10&abvMax=20"))
            .`as`("있지도 않은 파라미터가 결과를 바꾸면 안 된다")
            .hasSize(CORPUS.size)

        assertThat(parameterNames())
            .contains("abv")
            .doesNotContain("abvMin", "abvMax", "minAbv", "maxAbv")
    }

    // ── RED 22~24 : 발행분만 (SPEC-07 §2.1·§5) ─────────────────────────────

    @Test
    fun `RED22 - published만_반환된다`() {
        val all = slugs("?size=100")

        assertThat(all).hasSize(CORPUS.size)
        assertThat(total("?size=100")).isEqualTo(CORPUS.size.toLong())
    }

    @Test
    fun `RED23 - draft가_결과에_없다`() {
        assertThat(slugs("?size=100")).doesNotContain("draft-negroni")
        assertThat(slugs("?q=초안")).isEmpty()
    }

    @Test
    fun `RED24 - archived가_결과에_없다`() {
        assertThat(slugs("?size=100")).doesNotContain("archived-negroni")
        assertThat(slugs("?q=보관")).isEmpty()
    }

    // ── RED 25~26 : noindex (R-F2.1-1 · NFR-S-02) ──────────────────────────

    @Test
    fun `RED25 - 응답에_X_Robots_Tag_noindex가_있다`() {
        val response = mvc.get("$LIST?base=gin").andReturn().response

        assertThat(response.getHeader("X-Robots-Tag")).isEqualTo("noindex")
    }

    /**
     * RED 26 — **전역으로 붙이지 않는다.**
     *
     * 카테고리 경로(이슈 022)와 상세(이슈 020)는 색인해야 한다 (`NFR-S-01`·`S-02` — 배포 차단 조건).
     * 필터 응답에 헤더를 붙이는 방법이 필터 체인이면 그 두 경로까지 색인에서 사라진다.
     * 아직 없는 경로를 여기서 부를 수는 없으니, **다른 공개 응답에 헤더가 없다**는 것으로
     * 전역이 아님을 고정한다. 이슈 022 가 카테고리 경로로 같은 단언을 이어받는다.
     */
    @Test
    fun `RED26 - 카테고리_경로_응답에는_noindex가_없다`() {
        val other = mvc.get("/api/v1/auth/csrf").andReturn().response

        assertThat(other.status).isEqualTo(200)
        assertThat(other.getHeader("X-Robots-Tag"))
            .`as`("noindex 가 공개 응답 전체에 붙으면 카테고리 경로가 색인에서 사라진다")
            .isNull()
    }

    // ── RED 27~31 : 규약 (SPEC-07 §1.5·§5) ─────────────────────────────────

    @Test
    fun `RED27 - 페이징이_동작한다`() {
        val first = body("?size=3&page=0")
        val second = body("?size=3&page=1")

        assertAll(
            { assertThat(first["items"].size()).isEqualTo(3) },
            { assertThat(first["page"]["number"].asInt()).isEqualTo(0) },
            { assertThat(first["page"]["size"].asInt()).isEqualTo(3) },
            { assertThat(first["page"]["totalElements"].asLong()).isEqualTo(CORPUS.size.toLong()) },
            { assertThat(first["page"]["totalPages"].asInt()).isEqualTo(3) },
            {
                assertThat(second["items"].map { it["slug"].asText() })
                    .`as`("페이지가 겹치지 않는다")
                    .doesNotContainAnyElementsOf(first["items"].map { it["slug"].asText() })
            },
        )
    }

    /** 상한을 넘기면 400 이 아니라 절삭이다 (ISSUE-003 규약). */
    @Test
    fun `RED28 - 응답이_items와_page_형태다`() {
        val body = body("")

        assertThat(body.fieldNames().asSequence().toList()).containsExactlyInAnyOrder("items", "page")
        assertThat(body["page"].fieldNames().asSequence().toList())
            .containsExactlyInAnyOrder("number", "size", "totalElements", "totalPages")
        assertThat(body["page"]["size"].asInt()).isEqualTo(PageQuery.DEFAULT_SIZE)
        assertThat(body("?size=9999")["page"]["size"].asInt()).isEqualTo(PageQuery.MAX_SIZE)
    }

    @Test
    fun `RED29 - 공개_응답에_내부_id가_없다`() {
        val item = body("?base=gin")["items"].first()

        assertThat(fields(item)).doesNotContain("id", "cocktailId", "status")
        assertThat(item["slug"].asText()).isNotBlank()
    }

    /** SPEC-07 §5 · DECISIONS §1.5 — 표시값 하나뿐. 무엇으로 정해졌는지는 내부 사정이다. */
    @Test
    fun `RED30 - abv_calculated_override가_노출되지_않는다`() {
        val item = body("?base=gin&size=1")["items"].first()

        assertThat(fields(item))
            .contains("abv")
            .doesNotContain("abvCalculated", "abvOverride", "abv_calculated", "abv_override", "countsForStock")
    }

    @Test
    fun `RED31 - q_파라미터로_이름_검색이_된다`() {
        assertAll(
            { assertThat(slugs("?q=네그로니")).containsExactly("negroni") },
            { assertThat(slugs("?q=Negroni")).`as`("영문명도 본다").containsExactly("negroni") },
            { assertThat(slugs("?q=사워")).containsExactly("whisky-sour") },
            { assertThat(status("?q=")).`as`("빈 q 는 400 이다 (DECISIONS §1.9)").isEqualTo(400) },
        )
    }

    // ── RED 32~33 : 성능 ───────────────────────────────────────────────────

    /**
     * RED 32 — **인덱스를 탄다** (SPEC-06 §5).
     *
     * 코퍼스가 8건이면 계획기는 당연히 순차 스캔을 고른다 — 그래서 `enable_seqscan` 을 끄고
     * **인덱스로 풀 수 있는 형태인지**를 본다. 판별하려는 것은 통계가 아니라 쿼리의 모양이다.
     * `WHERE lower(base_spirit) = …` 처럼 컬럼을 감싸는 순간 이 단언이 깨진다.
     */
    @Test
    fun `RED32 - 인덱스를_탄다`() {
        assertThat(plan(CocktailFilterParser.parse(base = "gin")))
            .`as`("(status, base_spirit)")
            .contains("ix_cocktail__status_base")

        assertThat(plan(CocktailFilterParser.parse(abv = "high")))
            .`as`("(status, abv) — 표시값이 생성 컬럼이라 인덱스가 붙는다")
            .contains("ix_cocktail__status_abv")
    }

    /** 인덱스 없는 컬럼으로 정렬을 받으면 풀스캔이 열린다 (ISSUE-003 RED 21). */
    @Test
    fun `RED33 - 정렬_파라미터가_허용목록으로_제한된다`() {
        assertAll(
            { assertThat(status("?sort=abv,desc")).isEqualTo(200) },
            { assertThat(status("?sort=name,asc")).isEqualTo(200) },
            { assertThat(status("?sort=story,asc")).isEqualTo(400) },
            { assertThat(status("?sort=abv,sideways")).isEqualTo(400) },
        )

        val descending = body("?sort=abv,desc&size=100")["items"].map { it["abv"].decimalValue() }
        assertThat(descending).isSortedAccordingTo(compareByDescending<BigDecimal> { it })
    }

    // ── 헬퍼 ───────────────────────────────────────────────────────────────

    private fun body(queryString: String): JsonNode =
        json.readTree(mvc.get("$LIST$queryString").andReturn().response.getContentAsString(Charsets.UTF_8))

    private fun slugs(queryString: String): List<String> =
        body(queryString)["items"].map { it["slug"].asText() }

    private fun total(queryString: String): Long = body(queryString)["page"]["totalElements"].asLong()

    private fun status(queryString: String): Int = mvc.get("$LIST$queryString").andReturn().response.status

    private fun fields(node: JsonNode): List<String> = node.fieldNames().asSequence().toList()

    /** 프로덕션이 실제로 날리는 문장을 그대로 EXPLAIN 한다. 테스트용 쿼리를 따로 쓰면 의미가 없다. */
    private fun plan(filter: CocktailFilter): String = tx.execute {
        jdbc.execute("SET LOCAL enable_seqscan = off")
        val sql = CocktailListSql.select(filter, PageQuery(0, 24, emptyList()))
        named.query("EXPLAIN ${sql.text}", sql.params) { rs, _ -> rs.getString(1) }.joinToString("\n")
    }!!

    /** OpenAPI 가 이 엔드포인트에 선언한 쿼리 파라미터 이름. */
    private fun parameterNames(): List<String> {
        val spec = json.readTree(
            mvc.get("/v3/api-docs").andReturn().response.getContentAsString(Charsets.UTF_8),
        )
        return spec.path("paths").path("/api/v1/cocktails").path("get").path("parameters")
            .map { it["name"].asText() }
    }

    // ── 픽스처 ─────────────────────────────────────────────────────────────

    private fun insert(row: Row, status: String = "published") {
        tx.execute {
            val id = jdbc.queryForObject(
                """
                INSERT INTO cocktail (slug, name_ko, name_en, summary,
                    base_spirit, style_primary, method, sweetness, glass_type,
                    abv_calculated, tasting_note, status, published_at)
                VALUES (?, ?, ?, '한 줄 요약', ?, ?, ?, ?, '글라스', ?, '향과 맛', ?,
                    CASE WHEN ? = 'draft' THEN NULL ELSE now() END)
                RETURNING id
                """.trimIndent(),
                Long::class.java,
                row.slug, row.nameKo, row.nameEn,
                row.base, row.styles.first(), row.method, row.sweet, row.abv,
                status, status,
            )!!
            row.styles.forEach { jdbc.update("INSERT INTO cocktail_style VALUES (?, ?)", id, it) }
            row.flavors.forEach { jdbc.update("INSERT INTO cocktail_aroma_tag VALUES (?, ?)", id, it) }
        }
    }

    /** `styles.first()` 가 `style_primary` 다 — 복합 FK 가 포함을 강제한다 (V009). */
    private data class Row(
        val slug: String,
        val nameKo: String,
        val nameEn: String,
        val base: String,
        val styles: List<String>,
        val method: String,
        val sweet: String,
        val abv: Int,
        val flavors: List<String>,
    )

    companion object {
        private const val LIST = "/api/v1/cocktails"

        private val NEGRONI = Row(
            "negroni", "네그로니", "Negroni", "gin",
            listOf("spirit-forward"), "stir", "semi_dry", 24, listOf("bitter", "herbal"),
        )

        private val CORPUS = listOf(
            NEGRONI,
            Row("gin-tonic", "진토닉", "Gin and Tonic", "gin",
                listOf("highball"), "build", "dry", 10, listOf("citrus")),
            Row("moscow-mule", "모스코 뮬", "Moscow Mule", "vodka",
                listOf("highball", "sour"), "build", "semi_sweet", 12, listOf("citrus", "spicy")),
            Row("whisky-sour", "위스키 사워", "Whisky Sour", "whisky",
                listOf("sour"), "shake", "semi_dry", 20, listOf("sour", "citrus")),
            Row("espresso-martini", "에스프레소 마티니", "Espresso Martini", "vodka",
                listOf("spirit-forward"), "shake", "semi_sweet", 22, listOf("bitter", "nutty")),
            Row("pina-colada", "피나 콜라다", "Pina Colada", "rum",
                listOf("creamy", "tiki"), "blend", "sweet", 13, listOf("fruity", "creamy")),
            Row("makgeolli-punch", "막걸리 펀치", "Makgeolli Punch", "korean",
                listOf("sour"), "shake", "semi_sweet", 8, listOf("fruity")),
            // INV-COCKTAIL-06 — 무알콜 ⟺ abv = 0. DB CHECK 가 양방향으로 강제한다.
            Row("virgin-mojito", "버진 모히토", "Virgin Mojito", "non-alcoholic",
                listOf("highball"), "build", "sweet", 0, listOf("citrus", "herbal")),
        )

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { PostgresSupport.container.jdbcUrl }
            registry.add("spring.datasource.username") { PostgresSupport.container.username }
            registry.add("spring.datasource.password") { PostgresSupport.container.password }
            registry.add("spring.flyway.enabled") { true }
            registry.add("spring.flyway.user") { PostgresSupport.container.username }
            registry.add("spring.flyway.password") { PostgresSupport.container.password }
        }
    }
}
