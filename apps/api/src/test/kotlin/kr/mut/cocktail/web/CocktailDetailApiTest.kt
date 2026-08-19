package kr.mut.cocktail.web

import com.fasterxml.jackson.databind.ObjectMapper
import kr.mut.common.web.ApiPaths
import kr.mut.common.web.cache.CacheControlFilter
import kr.mut.ingredient.api.IngredientFacade
import kr.mut.ingredient.api.IngredientView
import kr.mut.support.PostgresSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.stereotype.Component
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.get
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.atomic.AtomicInteger

/**
 * ISSUE-020 — `GET /cocktails/{slug}` 상세 (`FR-COCKTAIL-017`·`018`, SPEC-07 §2.1·§5).
 *
 * ## 왜 통합 테스트인가
 *
 * 이 이슈가 지켜야 하는 것 대부분이 **조립 결과**다 — 404 정책 · 캐시 헤더 · 노출 범위는
 * 컨트롤러 단위 테스트로는 보이지 않는다. 필터 순서가 틀리면 `ETag` 가 조용히 안 붙고,
 * DTO 필드 하나가 새면 내부 `id` 가 그대로 나간다 (SPEC-07 §5).
 *
 * ## 미결은 여기서 판단하지 않는다
 *
 * `abv` 표시값 하나 · `is_classic` 노출 · `status` 미노출 · `counts_for_stock` 미노출은
 * 전부 [DECISIONS §1.5](../../../../../../docs/issues/DECISIONS.md) 확정분이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(CocktailDetailApiTest.PROFILE)
class CocktailDetailApiTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var json: ObjectMapper
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var tx: TransactionTemplate

    @BeforeEach
    fun reset() {
        jdbc.execute("TRUNCATE cocktail, ingredient CASCADE")
        CountingIngredientFacade.bulkCalls.set(0)
        CountingIngredientFacade.singleCalls.set(0)
    }

    // ── RED 1~5 : 조회 ─────────────────────────────────────────────────────

    @Test
    fun `RED1 - slug 로 상세를 조회한다`() {
        seed()

        mvc.get(detailUri("negroni")).andExpect {
            status { isOk() }
            jsonPath("$.slug") { value("negroni") }
            jsonPath("$.hero.nameKo") { value("네그로니") }
        }
    }

    @Test
    fun `RED2 - 없는 slug 는 404`() {
        seed()

        mvc.get(detailUri("이런-칵테일은-없다")).andExpect { status { isNotFound() } }
    }

    /**
     * RED 3 — **403 이 아니다.** 403 이면 "그 슬러그는 존재한다"가 새어 나간다 (SPEC-07 §5).
     * 없는 것과 숨긴 것이 응답에서 구별되지 않아야 한다.
     */
    @Test
    fun `RED3 - draft 는 404 다`() {
        seed(slug = "draft-only", status = "draft")

        val hidden = mvc.get(detailUri("draft-only")).andReturn()
        val missing = mvc.get(detailUri("아예-없다")).andReturn()

        assertThat(hidden.response.status).isEqualTo(404)

        // `instance` 는 요청 경로를 그대로 담아 당연히 다르다 (RFC 9457).
        // 지켜야 할 성질은 **그 외 전부가 같다** 는 것이다 — 하나라도 다르면
        // 그 차이로 "있는데 안 보여 주는 것" 과 "없는 것" 을 구분할 수 있다 (SPEC-07 §5).
        assertThat(withoutInstance(hidden)).isEqualTo(withoutInstance(missing))
    }

    /** RFC 9457 의 `instance` 만 뺀다. 나머지가 같아야 존재가 새지 않는다. */
    private fun withoutInstance(result: MvcResult): Map<*, *> =
        (json.readValue(body(result), Map::class.java)) - "instance"

    @Test
    fun `RED4 - archived 도 404 다`() {
        seed(slug = "archived-only", status = "archived")

        mvc.get(detailUri("archived-only")).andExpect { status { isNotFound() } }
    }

    /**
     * RED 5 — **공개 경로는 역할과 무관하게 404 다.**
     *
     * SPEC-08 §2 가 "draft 콘텐츠 조회 = editor ○" 라고 했지만 그것은 **어드민 경로**
     * (이슈 025)의 이야기다. 공개 엔드포인트에 역할 분기를 넣으면 SSG 빌드와 브라우저가
     * 같은 URL 에서 다른 것을 보게 되고, 그 순간 캐시가 무엇을 담고 있는지 아무도 모른다.
     */
    @Test
    fun `RED5 - editor 라도 공개 경로에서는 draft 를 볼 수 없다`() {
        seed(slug = "draft-only", status = "draft")

        mvc.get(detailUri("draft-only")) { with(user("editor").roles("EDITOR")) }
            .andExpect { status { isNotFound() } }
    }

    // ── RED 6~13 : 필수 블록 (FR-COCKTAIL-017) ────────────────────────────

    @Test
    fun `RED6 - 응답에 히어로 정보가 있다`() {
        seed()

        mvc.get(detailUri("negroni")).andExpect {
            jsonPath("$.hero.nameKo") { value("네그로니") }
            jsonPath("$.hero.nameEn") { value("Negroni") }
            jsonPath("$.hero.summary") { exists() }
            // 대표 이미지는 media_asset(이슈 044·045) 도입 전까지 항상 null 이다 — G-07 · D-6.
            jsonPath("$.hero") { value(org.hamcrest.Matchers.hasKey("imageUrl")) }
        }
    }

    @Test
    fun `RED7 - 응답에 분류 3축이 있다`() {
        seed()

        mvc.get(detailUri("negroni")).andExpect {
            jsonPath("$.classification.base.slug") { value("gin") }
            jsonPath("$.classification.stylePrimary.slug") { value("spirit-forward") }
            jsonPath("$.classification.method.slug") { value("stir") }
        }
    }

    @Test
    fun `RED8 - 응답에 스펙이 있다`() {
        seed()

        mvc.get(detailUri("negroni")).andExpect {
            jsonPath("$.spec.abv") { value(24.0) }
            jsonPath("$.spec.sweetness.slug") { value("semi_dry") }
            jsonPath("$.spec.glassType") { value("락 글라스") }
        }
    }

    @Test
    fun `RED9 - 응답에 재료 목록이 있다`() {
        seed()

        mvc.get(detailUri("negroni")).andExpect {
            jsonPath("$.ingredients.length()") { value(4) }
            jsonPath("$.ingredients[0].slug") { value("gin") }
            jsonPath("$.ingredients[0].nameKo") { value("진") }
            jsonPath("$.ingredients[0].amount") { value(30.0) }
            jsonPath("$.ingredients[0].unit") { value("ml") }
        }
    }

    @Test
    fun `RED10 - 응답에 만드는 법 스텝이 있다`() {
        seed()

        mvc.get(detailUri("negroni")).andExpect {
            jsonPath("$.steps.length()") { value(3) }
            jsonPath("$.steps[0].stepNo") { value(1) }
            jsonPath("$.steps[0].text") { value("믹싱글라스에 얼음을 채운다") }
        }
    }

    @Test
    fun `RED11 - 응답에 향과 맛 서술이 있다`() {
        seed()

        mvc.get(detailUri("negroni")).andExpect {
            jsonPath("$.tastingNote.note") { value(TASTING_NOTE) }
            // 서술과 별개로 필터 태그도 같은 블록에 싣는다 (PRD 6.3).
            jsonPath("$.tastingNote.aromaTags[*].slug") {
                value(org.hamcrest.Matchers.containsInAnyOrder("bitter", "herbal"))
            }
        }
    }

    /** RED 12 — `PRIN-P05`. 이 블록 하나가 해외 DB 번역판과 우리를 가른다. */
    @Test
    fun `RED12 - 응답에 국내 구매 가이드가 있다`() {
        seed()

        mvc.get(detailUri("negroni")).andExpect {
            jsonPath("$.purchaseGuide.length()") { value(4) }
            jsonPath("$.purchaseGuide[?(@.slug=='gin')].availability.slug") {
                value(org.hamcrest.Matchers.contains("common"))
            }
            jsonPath("$.purchaseGuide[?(@.slug=='gin')].priceBand") {
                value(org.hamcrest.Matchers.contains("2-3만원"))
            }
            jsonPath("$.purchaseGuide[?(@.slug=='sweet-vermouth')].substituteNote") {
                value(org.hamcrest.Matchers.contains(VERMOUTH_SUBSTITUTE))
            }
            jsonPath("$.purchaseGuide[?(@.slug=='gin')].brands[0].isSponsored") {
                value(org.hamcrest.Matchers.contains(false))
            }
        }
    }

    /** RED 13 — **하나라도 없으면 실패**. `FR-COCKTAIL-017` 의 8개 블록이 곧 계약이다. */
    @ParameterizedTest(name = "RED13 - 필수 블록 {0}")
    @ValueSource(
        strings = [
            "hero", "classification", "spec", "ingredients",
            "steps", "tastingNote", "purchaseGuide", "actions",
        ],
    )
    fun `RED13 - 8개 블록이 전부 있다`(block: String) {
        seed()

        val body = detailBody()

        assertThat(body)
            .`as`("FR-COCKTAIL-017 필수 블록 — $block")
            .containsKey(block)
        assertThat(body[block]).isNotNull()
    }

    // ── RED 14~18 : 카테고리 링크 (FR-COCKTAIL-018) ───────────────────────

    @Test
    fun `RED14 - 분류 3축에 각각 slug 가 포함된다`() {
        seed()

        @Suppress("UNCHECKED_CAST")
        val classification = detailBody()["classification"] as Map<String, Any>

        assertThat(classification.keys)
            .containsExactlyInAnyOrder("base", "stylePrimary", "styles", "method")
        listOf("base", "stylePrimary", "method").forEach { axis ->
            @Suppress("UNCHECKED_CAST")
            val ref = classification[axis] as Map<String, Any>
            assertThat(ref["slug"]).`as`("$axis 의 링크 대상 slug").isNotNull()
            // DECISIONS §1.10 — 한국어 레이블은 API 응답이 준다. 정본이 Kotlin 이라서다.
            assertThat(ref["labelKo"]).isNotNull()
        }
    }

    /** RED 15 — ADR-0002 §4 가 PRD 5.1 에서 고친 두 곳이 그대로 나가는가. */
    @Test
    fun `RED15 - base slug 가 ADR-0002 확정값이다`() {
        seed(slug = "makgeolli-highball", baseSpirit = "korean", abv = "6")

        mvc.get(detailUri("makgeolli-highball")).andExpect {
            jsonPath("$.classification.base.slug") { value("korean") }
            jsonPath("$.classification.base.labelKo") { value("전통주") }
        }
        assertThat(rawDetail("makgeolli-highball")).doesNotContain("soju")
    }

    @Test
    fun `RED16 - style primary slug 가 포함된다`() {
        seed()

        mvc.get(detailUri("negroni")).andExpect {
            jsonPath("$.classification.stylePrimary.slug") { value("spirit-forward") }
            jsonPath("$.classification.stylePrimary.labelKo") { value("스피릿 포워드") }
        }
    }

    @Test
    fun `RED17 - method slug 가 포함된다`() {
        seed()

        mvc.get(detailUri("negroni")).andExpect {
            jsonPath("$.classification.method.slug") { value("stir") }
            // 바에서 쓰는 말을 그대로 쓴다 (G-32) — 풀어 쓴 "휘저어 섞기" 가 아니다
            jsonPath("$.classification.method.labelKo") { value("스터") }
        }
    }

    /**
     * RED 18 — **표시용이다. 링크는 `stylePrimary` 하나만** (`R-C-2`).
     * 전부 링크하면 같은 칵테일이 여러 카테고리의 정본처럼 보이고 색인이 갈린다.
     */
    @Test
    fun `RED18 - styles 전체도 포함된다`() {
        seed()

        mvc.get(detailUri("negroni")).andExpect {
            jsonPath("$.classification.styles[*].slug") {
                value(org.hamcrest.Matchers.containsInAnyOrder("spirit-forward", "sour"))
            }
        }
    }

    // ── RED 19~24 : 레시피 (FR-COCKTAIL-003) ──────────────────────────────

    /** RED 19 — SPEC-02 §2.6. `bar_signature` 가 있어도 상세는 표준을 보여준다. */
    @Test
    fun `RED19 - 기본 노출은 standard 레시피다`() {
        seed(withBarSignature = true)

        mvc.get(detailUri("negroni")).andExpect {
            jsonPath("$.steps.length()") { value(3) }
            jsonPath("$.steps[0].text") { value("믹싱글라스에 얼음을 채운다") }
            jsonPath("$.ingredients.length()") { value(4) }
        }
    }

    @Test
    fun `RED20 - recipes 엔드포인트가 버전 목록을 반환한다`() {
        seed(withBarSignature = true)

        mvc.get(recipesUri("negroni")).andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(2) }
            jsonPath("$.items[0].versionType") { value("standard") }
            jsonPath("$.items[0].isDefault") { value(true) }
            jsonPath("$.items[0].servingCount") { value(1) }
            jsonPath("$.items[0].steps.length()") { value(3) }
        }
        mvc.get(recipesUri("draft-only")).andExpect { status { isNotFound() } }
    }

    /** RED 21 — `bar_signature` 는 Phase 1b (BAR 의존)다. 1a 코퍼스에는 표준만 있다. */
    @Test
    fun `RED21 - Phase 1a 에는 standard 만 존재한다`() {
        seed()

        mvc.get(recipesUri("negroni")).andExpect {
            jsonPath("$.items.length()") { value(1) }
            jsonPath("$.items[*].versionType") {
                value(org.hamcrest.Matchers.contains("standard"))
            }
        }
    }

    /** RED 22 — `FR-COCKTAIL-021`. 대체재를 못 찾으면 그 레시피는 읽을거리일 뿐이다. */
    @Test
    fun `RED22 - 재료에 대체재 정보가 포함된다`() {
        seed()

        mvc.get(detailUri("negroni")).andExpect {
            jsonPath("$.ingredients[?(@.slug=='campari')].substitute.slug") {
                value(org.hamcrest.Matchers.contains("aperol"))
            }
            jsonPath("$.ingredients[?(@.slug=='campari')].substitute.nameKo") {
                value(org.hamcrest.Matchers.contains("아페롤"))
            }
            jsonPath("$.ingredients[?(@.slug=='campari')].substitute.note") {
                value(org.hamcrest.Matchers.contains(CAMPARI_SUBSTITUTE))
            }
        }

        // 대체재가 없으면 `null` 이다. 빈 객체로 두면 프론트가 "대체 가능 ⓘ" 를 띄운다.
        @Suppress("UNCHECKED_CAST")
        val gin = (detailBody()["ingredients"] as List<Map<String, Any?>>)
            .first { it["slug"] == "gin" }

        assertThat(gin).containsKey("substitute")
        assertThat(gin["substitute"]).isNull()
    }

    /**
     * RED 23 — `FR-COCKTAIL-019`. 환산은 FE(이슈 043)가 하지만 **무엇이 배수 대상인가**는
     * 서버가 판정한다. 두 곳이 다르게 판단하면 어드민 미리보기와 화면이 어긋난다.
     */
    @Test
    fun `RED23 - 재료에 amount label 이 포함된다`() {
        seed()

        mvc.get(detailUri("negroni")).andExpect {
            jsonPath("$.ingredients[?(@.slug=='orange-peel')].amountLabel") {
                value(org.hamcrest.Matchers.contains("1조각"))
            }
            jsonPath("$.ingredients[?(@.slug=='orange-peel')].isScalable") {
                value(org.hamcrest.Matchers.contains(false))
            }
            jsonPath("$.ingredients[?(@.slug=='gin')].isScalable") {
                value(org.hamcrest.Matchers.contains(true))
            }
        }
    }

    /** RED 24 — DECISIONS §1.5. Phase 2 역검색용이라 지금 내보내면 쓸데없다. */
    @Test
    fun `RED24 - 재료에 counts for stock 이 노출되지 않는다`() {
        seed()

        assertThat(rawDetail("negroni"))
            .doesNotContain("countsForStock")
            .doesNotContain("counts_for_stock")
    }

    // ── RED 25~28 : 노출 범위 (SPEC-07 §5) ────────────────────────────────

    /** RED 25 — 공개 리소스는 `slug` 만 (`PRIN-D02` · SPEC-07 §1.1). */
    @Test
    fun `RED25 - 내부 id 가 없다`() {
        seed()

        val idKeys = keysOf(json.readValue(rawDetail("negroni"), Map::class.java))
            .filter { it == "id" || it.endsWith("Id") }

        assertThat(idKeys).isEmpty()
        assertThat(keysOf(json.readValue(rawRecipes("negroni"), Map::class.java)))
            .doesNotContain("id")
    }

    /** RED 26 — 표시값 하나만. 넣으면 프론트가 언젠가 쓴다 (DECISIONS §1.5). */
    @Test
    fun `RED26 - abv calculated 와 abv override 가 없다`() {
        seed()
        jdbc.update("UPDATE cocktail SET abv_override = 22.0 WHERE slug = 'negroni'")

        val raw = rawDetail("negroni")

        assertThat(raw)
            .doesNotContain("abvCalculated")
            .doesNotContain("abvOverride")
            .doesNotContain("abv_calculated")
            .doesNotContain("abv_override")
        mvc.get(detailUri("negroni")).andExpect { jsonPath("$.spec.abv") { value(22.0) } }
    }

    /** RED 27 — DECISIONS §1.5. 클래식 배지는 콘텐츠 성격이라 내보낸다. */
    @Test
    fun `RED27 - is classic 이 노출된다`() {
        seed()

        mvc.get(detailUri("negroni")).andExpect { jsonPath("$.isClassic") { value(true) } }
    }

    /** RED 28 — `published` 만 나오므로 무의미하다 (DECISIONS §1.5). */
    @Test
    fun `RED28 - status 가 노출되지 않는다`() {
        seed()

        assertThat(keysOf(json.readValue(rawDetail("negroni"), Map::class.java)))
            .doesNotContain("status")
        assertThat(rawDetail("negroni")).doesNotContain("published")
    }

    // ── RED 29~32 : 캐싱 (SPEC-07 §1.6) ───────────────────────────────────

    @Test
    fun `RED29 - ETag 가 붙는다`() {
        seed()

        assertThat(mvc.get(detailUri("negroni")).andReturn().response.getHeader(HttpHeaders.ETAG))
            .isNotBlank()
    }

    @Test
    fun `RED30 - Cache-Control max-age 60 swr 600 이 붙는다`() {
        seed()

        val header = mvc.get(detailUri("negroni"))
            .andReturn().response.getHeader(HttpHeaders.CACHE_CONTROL)

        assertThat(header).isEqualTo(CacheControlFilter.PUBLIC_CACHE)
        assertThat(header).contains("max-age=60", "stale-while-revalidate=600")
    }

    /** RED 31 — SSG 빌드가 500종을 반복 호출한다. 그대로면 304 로 끝나야 한다. */
    @Test
    fun `RED31 - If-None-Match 일치시 304`() {
        seed()
        val etag = mvc.get(detailUri("negroni")).andReturn().response.getHeader(HttpHeaders.ETAG)!!

        val second = mvc.get(detailUri("negroni")) {
            header(HttpHeaders.IF_NONE_MATCH, etag)
        }.andReturn()

        assertThat(second.response.status).isEqualTo(304)
        assertThat(second.response.contentAsString).isEmpty()
    }

    @Test
    fun `RED32 - 내용이 바뀌면 ETag 가 바뀐다`() {
        seed()
        val before = mvc.get(detailUri("negroni")).andReturn().response.getHeader(HttpHeaders.ETAG)

        jdbc.update("UPDATE cocktail SET tasting_note = ? WHERE slug = 'negroni'", "완전히 다른 서술이다")

        assertThat(mvc.get(detailUri("negroni")).andReturn().response.getHeader(HttpHeaders.ETAG))
            .isNotEqualTo(before)
    }

    // ── RED 33 : 색인 (NFR-S-01) ──────────────────────────────────────────

    /** RED 33 — 상세는 색인 대상이다 (DECISIONS §1.6). 필터 결과(이슈 018)와 정반대다. */
    @Test
    fun `RED33 - noindex 가 붙지 않는다`() {
        seed()

        // 헤더가 **아예 없는 것**이 정상이다. 상세는 색인 대상이라 붙일 이유가 없다.
        // `null` 을 그대로 doesNotContain 에 넘기면 AssertJ 가 값이 없다고 실패한다 —
        // 없어서 통과해야 할 검사가 없어서 실패하면 뜻이 뒤집힌다.
        assertThat(mvc.get(detailUri("negroni")).andReturn().response.getHeader("X-Robots-Tag").orEmpty())
            .doesNotContainIgnoringCase("noindex")
    }

    // ── DoD : N+1 없음 ────────────────────────────────────────────────────

    /**
     * 재료 4종을 한 번에 가져와야 한다. 재료 수만큼 조회가 나가면 500종 SSG 빌드에서
     * 그대로 곱해진다 (`NFR-P-05` TTFB 200ms).
     */
    @Test
    fun `DoD - 재료 조회가 재료 수만큼 나가지 않는다`() {
        seed()

        mvc.get(detailUri("negroni")).andExpect { status { isOk() } }

        assertThat(CountingIngredientFacade.bulkCalls.get())
            .`as`("벌크 조회 한 번으로 끝난다")
            .isEqualTo(1)
        assertThat(CountingIngredientFacade.singleCalls.get())
            .`as`("재료 하나씩 도는 경로가 없다")
            .isZero()
    }

    // ── 헬퍼 ───────────────────────────────────────────────────────────────

    private fun detailUri(slug: String) = "${ApiPaths.BASE}/cocktails/$slug"

    private fun recipesUri(slug: String) = "${ApiPaths.BASE}/cocktails/$slug/recipes"

    private fun rawDetail(slug: String) =
        mvc.get(detailUri(slug)).andReturn().response.getContentAsString(Charsets.UTF_8)

    private fun rawRecipes(slug: String) =
        mvc.get(recipesUri(slug)).andReturn().response.getContentAsString(Charsets.UTF_8)

    @Suppress("UNCHECKED_CAST")
    private fun detailBody(): Map<String, Any> =
        json.readValue(rawDetail("negroni"), Map::class.java) as Map<String, Any>

    private fun body(result: MvcResult) = result.response.getContentAsString(Charsets.UTF_8)

    /** 중첩까지 훑는다. 최상위만 보면 `ingredients[0].id` 를 놓친다. */
    private fun keysOf(node: Any?): List<String> = when (node) {
        is Map<*, *> -> node.flatMap { (key, value) -> listOf(key.toString()) + keysOf(value) }
        is List<*> -> node.flatMap { keysOf(it) }
        else -> emptyList()
    }

    // ── 픽스처 ─────────────────────────────────────────────────────────────

    /**
     * 한 트랜잭션으로 묶는다. `fk_cocktail__style_primary` 가
     * `DEFERRABLE INITIALLY DEFERRED` 라 커밋 시점에 함께 봐야 한다 (`V009__cocktail.sql`).
     */
    private fun seed(
        slug: String = "negroni",
        status: String = "published",
        baseSpirit: String = "gin",
        abv: String = "24",
        withBarSignature: Boolean = false,
    ) = tx.execute { insertAll(slug, status, baseSpirit, abv, withBarSignature) }!!

    private fun insertAll(
        slug: String,
        status: String,
        baseSpirit: String,
        abv: String,
        withBarSignature: Boolean,
    ): Long {
        val gin = ingredient("gin", "진", "Dry Gin", "spirit", "common", priceBand = "2-3만원")
        val campari = ingredient("campari", "캄파리", "Campari", "liqueur", "specialty", priceBand = "3-4만원")
        val aperol = ingredient("aperol", "아페롤", "Aperol", "liqueur", "specialty", priceBand = "3-4만원")
        val vermouth = ingredient(
            "sweet-vermouth", "스위트 베르무트", "Sweet Vermouth", "liqueur", "import_only",
            substituteNote = VERMOUTH_SUBSTITUTE, priceBand = "2-3만원",
        )
        val peel = ingredient("orange-peel", "오렌지 필", "Orange Peel", "garnish", "common")

        jdbc.update(
            "INSERT INTO ingredient_brand (ingredient_id, name, purchase_url, is_sponsored) " +
                "VALUES (?, ?, ?, false)",
            gin, "탱커레이", "https://example.kr/tanqueray",
        )

        val cocktailId = jdbc.queryForObject(
            """
            INSERT INTO cocktail (slug, name_ko, name_en, summary,
                base_spirit, style_primary, method, sweetness, glass_type, prep_time_min,
                abv_calculated, tasting_note, story, is_classic,
                origin_year, origin_place, origin_creator, status, published_at)
            VALUES (?, '네그로니', 'Negroni', '진·캄파리·베르무트를 같은 비율로 젓는다',
                ?, 'spirit-forward', 'stir', 'semi_dry', '락 글라스', 3,
                CAST(? AS NUMERIC), ?, '1919년 피렌체에서 시작됐다고 전해진다', true,
                '1919', '피렌체', '카미요 네그로니 백작', ?, now())
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            slug, baseSpirit, abv, TASTING_NOTE, status,
        )!!

        // RED 18 — 표시용 styles 는 복수다. 링크는 style_primary 하나만 만든다 (R-C-2).
        jdbc.update("INSERT INTO cocktail_style VALUES (?, 'spirit-forward')", cocktailId)
        jdbc.update("INSERT INTO cocktail_style VALUES (?, 'sour')", cocktailId)
        jdbc.update("INSERT INTO cocktail_aroma_tag VALUES (?, 'bitter')", cocktailId)
        jdbc.update("INSERT INTO cocktail_aroma_tag VALUES (?, 'herbal')", cocktailId)

        val standard = recipe(cocktailId, "standard")
        line(standard, gin, 1, amount = "30", unit = "ml", role = "base")
        line(
            standard, campari, 2, amount = "30", unit = "ml", role = "modifier",
            substituteId = aperol, substituteNote = CAMPARI_SUBSTITUTE,
        )
        line(standard, vermouth, 3, amount = "30", unit = "ml", role = "modifier")
        line(standard, peel, 4, amountLabel = "1조각", role = "garnish", countsForStock = false)
        step(standard, 1, "믹싱글라스에 얼음을 채운다")
        step(standard, 2, "세 가지 재료를 같은 비율로 넣고 젓는다", techniqueRef = "stir")
        step(standard, 3, "락 글라스에 옮기고 오렌지 필을 짜 넣는다")

        if (withBarSignature) {
            // Phase 1b 의 모양을 미리 넣어 "기본 노출이 standard 인가"를 실제로 가른다.
            // bar 테이블이 아직 없어 author_bar_id 에 FK 가 걸려 있지 않다 (V010__recipe.sql).
            val signature = recipe(cocktailId, "bar_signature", authorBarId = 1)
            line(signature, gin, 1, amount = "45", unit = "ml", role = "base")
            step(signature, 1, "바 참의 방식으로 젓는다")
        }

        return cocktailId
    }

    private fun ingredient(
        slug: String,
        nameKo: String,
        nameEn: String,
        category: String,
        availability: String,
        substituteNote: String? = null,
        priceBand: String? = null,
    ): Long = jdbc.queryForObject(
        """
        INSERT INTO ingredient (slug, name_ko, name_en, category, domestic_availability,
            is_approved, substitute_note, price_band)
        VALUES (?, ?, ?, ?, ?, true, ?, ?) RETURNING id
        """.trimIndent(),
        Long::class.java,
        slug, nameKo, nameEn, category, availability, substituteNote, priceBand,
    )!!

    private fun recipe(cocktailId: Long, versionType: String, authorBarId: Long? = null): Long =
        jdbc.queryForObject(
            "INSERT INTO recipe (cocktail_id, version_type, author_bar_id, serving_count) " +
                "VALUES (?, ?, ?, 1) RETURNING id",
            Long::class.java,
            cocktailId, versionType, authorBarId,
        )!!

    @Suppress("LongParameterList")
    private fun line(
        recipeId: Long,
        ingredientId: Long,
        position: Int,
        amount: String? = null,
        unit: String? = null,
        amountLabel: String? = null,
        role: String? = null,
        isOptional: Boolean = false,
        substituteId: Long? = null,
        substituteNote: String? = null,
        countsForStock: Boolean = true,
    ) {
        jdbc.update(
            """
            INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit,
                amount_label, role, is_optional, substitute_ingredient_id, substitute_note,
                counts_for_stock)
            VALUES (?, ?, ?, CAST(? AS NUMERIC), ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            recipeId, ingredientId, position, amount, unit,
            amountLabel, role, isOptional, substituteId, substituteNote, countsForStock,
        )
    }

    private fun step(recipeId: Long, stepNo: Int, text: String, techniqueRef: String? = null) {
        jdbc.update(
            "INSERT INTO recipe_step (recipe_id, step_no, text, technique_ref) VALUES (?, ?, ?, ?)",
            recipeId, stepNo, text, techniqueRef,
        )
    }

    companion object {
        const val PROFILE = "cocktail-detail-api"

        const val TASTING_NOTE =
            "첫 모금은 오렌지 껍질의 쓴 향, 중간은 베르무트의 단맛, 끝은 진의 주니퍼가 길게 남는다."
        const val CAMPARI_SUBSTITUTE = "아페롤로 바꾸면 도수와 쓴맛이 낮아진다"
        const val VERMOUTH_SUBSTITUTE = "직구가 필요하다. 국내 유통 레드 와인 + 허브로 대체할 수 있다"

        @JvmStatic
        @DynamicPropertySource
        fun datasource(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { PostgresSupport.container.jdbcUrl }
            registry.add("spring.datasource.username") { PostgresSupport.container.username }
            registry.add("spring.datasource.password") { PostgresSupport.container.password }
            registry.add("spring.flyway.enabled") { true }
            registry.add("spring.flyway.user") { PostgresSupport.container.username }
            registry.add("spring.flyway.password") { PostgresSupport.container.password }
            registry.add("spring.jpa.hibernate.ddl-auto") { "none" }
        }
    }
}

/**
 * 재료 조회 횟수를 센다. **N+1 은 통과/실패로 보이지 않는다** — 응답은 똑같고
 * 느려질 뿐이라 리뷰에서 놓친다. 세는 자리를 두는 것이 유일하게 확실한 방법이다.
 */
@Profile(CocktailDetailApiTest.PROFILE)
@Primary
@Component
class CountingIngredientFacade(
    @Qualifier("ingredientService") private val delegate: IngredientFacade,
) : IngredientFacade {

    override fun findApproved(ids: Collection<Long>): List<IngredientView> {
        bulkCalls.incrementAndGet()
        return delegate.findApproved(ids)
    }

    override fun findAll(ids: Collection<Long>): List<IngredientView> {
        bulkCalls.incrementAndGet()
        return delegate.findAll(ids)
    }

    override fun defaultCountsForStock(ingredientId: Long): Boolean {
        singleCalls.incrementAndGet()
        return delegate.defaultCountsForStock(ingredientId)
    }

    override fun requiresSubstitute(ingredientId: Long): Boolean {
        singleCalls.incrementAndGet()
        return delegate.requiresSubstitute(ingredientId)
    }

    override fun findApprovedBySlug(slug: String) = delegate.findApprovedBySlug(slug)

    companion object {
        val bulkCalls = AtomicInteger(0)
        val singleCalls = AtomicInteger(0)
    }
}
