package kr.kcocktail.ingredient

import kr.kcocktail.common.web.cache.CacheControlFilter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.get

/**
 * ISSUE-023 — `GET /ingredients` · `GET /ingredients/{slug}` (`FR-INGREDIENT-002`·`005`).
 *
 * ## 이 파일이 지키는 것
 *
 * | | 근거 |
 * |---|---|
 * | 미승인 재료는 공개에 없다 | `FR-INGREDIENT-001` · DECISIONS §1.1 |
 * | 상세에 6개 항목이 전부 있다 | `FR-INGREDIENT-002` (`R-F1.3-1`) |
 * | 브랜드 광고성 표기를 끌 수 없다 | `INV-INGREDIENT-02` · `NFR-L-02` |
 * | 재료 사전은 색인한다 | DECISIONS §1.6 |
 */
class IngredientDictionaryApiTest : IngredientApiSupport() {

    // ── RED 1~6 : 목록 ────────────────────────────────────────────────────

    /**
     * RED 1 — `FR-INGREDIENT-001` 은 신규 재료를 **에디터 승인제**로 뒀다.
     * 승인 전 재료가 사전에 보이면 승인제가 아무것도 막지 못한다.
     */
    @Test
    fun `RED1 - 승인된 재료만 반환된다`() {
        insertIngredient(tag("approved-only"))
        insertIngredient(tag("pending-only"), approved = false)

        val returned = slugsOf(mvc.get(BASE) { param("size", "100") }.andReturn())

        assertThat(returned).isNotEmpty()
        assertThat(approvedSlugs(returned))
            .`as`("응답에 나온 슬러그가 전부 승인된 것인가")
            .containsExactlyInAnyOrderElementsOf(returned.toSet())
    }

    @Test
    fun `RED2 - 미승인 재료가 목록에 없다`() {
        val pending = tag("pending-hidden")
        insertIngredient(pending, approved = false)

        assertThat(allSlugs()).doesNotContain(pending)
    }

    /** RED 3 — `FR-INGREDIENT-006` 카테고리 7종. */
    @ParameterizedTest
    @ValueSource(strings = ["spirit", "liqueur", "bitters", "syrup", "juice", "garnish", "mixer"])
    fun `RED3 - 카테고리 필터가 동작한다`(category: String) {
        val mine = tag("cat-$category")
        insertIngredient(mine, category = category)

        val result = mvc.get(BASE) { param("category", category); param("size", "100") }.andReturn()

        assertThat(slugsOf(result)).contains(mine)
        assertThat(itemsOf(result))
            .`as`("다른 카테고리가 섞이지 않는다")
            .allSatisfy { assertThat(it["category"]).isEqualTo(category) }
    }

    /** RED 4 — `PRIN-P05`. 국내 유통 축이 이 서비스의 정체성이다. */
    @ParameterizedTest
    @ValueSource(strings = ["common", "specialty", "import_only", "unavailable"])
    fun `RED4 - 국내유통 필터가 동작한다`(availability: String) {
        val mine = tag("av-$availability")
        insertIngredient(
            mine,
            availability = availability,
            // INV-INGREDIENT-01 — 미유통이면 대체재 안내가 필수다.
            substituteNote = if (availability in setOf("import_only", "unavailable")) "진으로 대체" else null,
        )

        val result = mvc.get(BASE) { param("availability", availability); param("size", "100") }.andReturn()

        assertThat(slugsOf(result)).contains(mine)
        assertThat(itemsOf(result))
            .allSatisfy { assertThat(it["domesticAvailability"]).isEqualTo(availability) }
    }

    @Test
    fun `RED5 - 페이징된다`() {
        repeat(3) { insertIngredient(tag("paged")) }

        val first = mvc.get(BASE) { param("page", "0"); param("size", "2") }.andReturn()
        val page = pageOf(first)

        assertThat(page.keys).containsExactlyInAnyOrder("number", "size", "totalElements", "totalPages")
        assertThat(page["number"]).isEqualTo(0)
        assertThat(page["size"]).isEqualTo(2)
        assertThat(itemsOf(first)).hasSizeLessThanOrEqualTo(2)

        val total = (page["totalElements"] as Number).toLong()
        assertThat(total).isGreaterThanOrEqualTo(3)
        assertThat(page["totalPages"]).isEqualTo(((total + 1) / 2).toInt())

        // 2페이지가 1페이지와 겹치지 않아야 페이징이라 부를 수 있다.
        val second = mvc.get(BASE) { param("page", "1"); param("size", "2") }.andReturn()
        assertThat(slugsOf(second)).doesNotContainAnyElementsOf(slugsOf(first))
    }

    /** RED 6 — SPEC-07 §1.1 · §5. 공개 식별자는 `slug` 다 (`PRIN-D02`). */
    @Test
    fun `RED6 - 내부 id가 없다`() {
        insertIngredient(tag("no-id"))

        assertThat(itemsOf(mvc.get(BASE) { param("size", "100") }.andReturn()))
            .isNotEmpty()
            .allSatisfy {
                assertThat(it).containsKey("slug")
                assertThat(it).doesNotContainKey("id")
            }
    }

    // ── RED 7~15 : 상세 (FR-INGREDIENT-002) ───────────────────────────────

    @Test
    fun `RED7 - 설명이 포함된다`() {
        val slug = tag("with-description")
        insertIngredient(slug, description = "주니퍼베리로 향을 낸 증류주")

        assertThat(detail(slug)["description"]).isEqualTo("주니퍼베리로 향을 낸 증류주")
    }

    @Test
    fun `RED8 - 도수가 포함된다`() {
        val slug = tag("with-abv")
        insertIngredient(slug, abv = "47.3")

        assertThat(detail(slug)["abv"].toString()).isEqualTo("47.3")
    }

    @Test
    fun `RED9 - 대표 브랜드가 포함된다`() {
        val slug = tag("with-brand")
        val id = insertIngredient(slug)
        insertBrand(id, "탱커레이", purchaseUrl = "https://example.kr/tanqueray")

        @Suppress("UNCHECKED_CAST")
        val brands = detail(slug)["brands"] as List<Map<String, Any>>

        assertThat(brands).extracting("name").containsExactly("탱커레이")
        assertThat(brands.single()["purchaseUrl"]).isEqualTo("https://example.kr/tanqueray")
    }

    /** RED 10 — `PRIN-P05`. 이 서비스가 해외 DB 의 번역판이 아닌 이유가 이 한 축이다. */
    @Test
    fun `RED10 - 국내 유통 여부가 포함된다`() {
        val slug = tag("with-availability")
        insertIngredient(slug, availability = "specialty")

        assertThat(detail(slug)["domesticAvailability"]).isEqualTo("specialty")
    }

    @Test
    fun `RED11 - 가격대가 포함된다`() {
        val slug = tag("with-price")
        insertIngredient(slug, priceBand = "high")

        assertThat(detail(slug)["priceBand"]).isEqualTo("high")
    }

    /** RED 12 — `R-F1.3-1`. 사전이 사전으로만 끝나면 그래프가 흐르지 않는다 (`PRIN-P01`). */
    @Test
    fun `RED12 - 이 재료를 쓰는 칵테일 목록이 제공된다`() {
        val slug = tag("used-by")
        val id = insertIngredient(slug)
        val cocktail = tag("uses-it")
        insertRecipeIngredient(insertRecipe(insertCocktail(cocktail)), id)

        val result = mvc.get("$BASE/$slug/cocktails").andReturn()

        assertThat(result.response.status).isEqualTo(200)
        assertThat(slugsOf(result)).contains(cocktail)
    }

    /** RED 13 — `FR-INGREDIENT-002` 가 요구한 6개 항목. 하나라도 빠지면 상세가 아니다. */
    @ParameterizedTest
    @ValueSource(strings = ["description", "abv", "brands", "domesticAvailability", "priceBand", "cocktails"])
    fun `RED13 - 6개 항목이 전부 있다`(field: String) {
        val slug = tag("six-fields")
        val id = insertIngredient(slug)
        insertBrand(id, "봄베이 사파이어")
        insertRecipeIngredient(insertRecipe(insertCocktail(tag("six-cocktail"))), id)

        if (field == "cocktails") {
            // 6번째 항목은 별도 경로다 (SPEC-07 §2.2).
            assertThat(itemsOf(mvc.get("$BASE/$slug/cocktails").andReturn())).isNotEmpty()
        } else {
            assertThat(detail(slug)).containsKey(field)
            assertThat(detail(slug)[field]).`as`("%s 가 비어 있다", field).isNotNull()
        }
    }

    /** RED 14 — `INV-INGREDIENT-01` (`R-F1.3-2`). 못 구하는 재료는 안내가 있어야 레시피가 된다. */
    @Test
    fun `RED14 - 대체재 안내가 포함된다`() {
        val slug = tag("with-substitute")
        insertIngredient(slug, availability = "import_only", substituteNote = "국산 진으로 대체할 수 있다")

        assertThat(detail(slug)["substituteNote"]).isEqualTo("국산 진으로 대체할 수 있다")
    }

    /** RED 15 — `FR-INGREDIENT-005` (`R-F2.1-3`). 색인은 이슈 017, 여기서는 노출이다. */
    @Test
    fun `RED15 - 별칭이 포함된다`() {
        val slug = tag("with-aliases")
        insertIngredient(slug, aliases = listOf("올드톰", "런던드라이"))

        assertThat(detail(slug)["aliases"] as List<*>)
            .containsExactlyInAnyOrder("올드톰", "런던드라이")
    }

    @Test
    fun `RED16 - 없는 slug는 404`() {
        mvc.get("$BASE/그런-재료-없다").andExpect { status { isNotFound() } }
    }

    /**
     * RED 17 — SPEC-07 §5 · CONVENTIONS §3.3.
     *
     * `403` 이면 "그 슬러그는 존재하지만 아직 승인 전"이 새어 나간다.
     * 없는 것과 숨긴 것이 **구별되지 않아야** 한다.
     */
    @Test
    fun `RED17 - 미승인 재료 상세는 404다`() {
        val slug = tag("pending-detail")
        insertIngredient(slug, approved = false)

        val hidden = mvc.get("$BASE/$slug").andReturn()
        val missing = mvc.get("$BASE/아예-없는-슬러그").andReturn()

        assertThat(hidden.response.status).`as`("403 이 아니다").isEqualTo(404)
        assertThat(bodyOf(hidden)["title"]).isEqualTo(bodyOf(missing)["title"])
        assertThat(bodyOf(hidden)["detail"]).isEqualTo(bodyOf(missing)["detail"])
    }

    // ── RED 18~21 : 브랜드 광고성 (INV-INGREDIENT-02 · NFR-L-02) ──────────

    @Test
    fun `RED18 - 브랜드마다 is_sponsored가 응답에 있다`() {
        val slug = tag("brand-flag")
        val id = insertIngredient(slug)
        insertBrand(id, "비피터")
        insertBrand(id, "고든스")

        assertThat(brandsOf(slug))
            .hasSize(2)
            .allSatisfy { assertThat(it).containsKey("isSponsored") }
    }

    /**
     * RED 19 — `NFR-L-02` 는 라벨을 **끌 수 없게** 표기하라고 했다.
     * 표현은 FE 가 하지만 **판단은 서버가 내린다** — 클라이언트에 맡기면 안 붙이는 클라이언트가 생긴다.
     *
     * ⚠️ 켜진 브랜드를 남기지 않는다. 이슈 008 의 "1a 데이터에 0건"이 테이블 전체를 본다.
     */
    @Test
    fun `RED19 - is_sponsored true면 라벨 표기 플래그가 참이다`() {
        val slug = tag("sponsored-label")
        val id = insertIngredient(slug)
        val plain = insertBrand(id, "일반 브랜드")
        val sponsored = insertBrand(id, "광고 브랜드", isSponsored = true)

        try {
            val brands = brandsOf(slug).associateBy { it["name"] }

            assertThat(brands["광고 브랜드"]!!["isSponsored"]).isEqualTo(true)
            assertThat(brands["광고 브랜드"]!!["requiresAdLabel"])
                .`as`("공정위 추천·보증 심사지침상 의무다 (R-F1.3-3)")
                .isEqualTo(true)
            assertThat(brands["일반 브랜드"]!!["requiresAdLabel"]).isEqualTo(false)
        } finally {
            deleteBrand(sponsored)
            deleteBrand(plain)
        }
    }

    /**
     * RED 20 — **억제 파라미터를 만들지 않는다.**
     *
     * 쿼리스트링·헤더 어느 쪽으로도 라벨 판단을 뒤집을 수 없어야 한다.
     * 하나라도 통하면 `NFR-L-02`("끌 수 없게")가 무너지고 배포 차단 조건이 된다.
     */
    @Test
    fun `RED20 - 라벨 플래그를 끌 수 있는 파라미터가 없다`() {
        val slug = tag("no-suppress")
        val id = insertIngredient(slug)
        val sponsored = insertBrand(id, "광고 브랜드", isSponsored = true)

        try {
            val attempts = listOf<Pair<String, String>>(
                "adLabel" to "off",
                "hideAdLabel" to "true",
                "isSponsored" to "false",
                "requiresAdLabel" to "false",
                "sponsored" to "hide",
                "fields" to "name",
            )

            attempts.forEach { (name, value) ->
                val brands = brandsOf(slug) { param(name, value) }.single { it["name"] == "광고 브랜드" }
                assertThat(brands["requiresAdLabel"])
                    .`as`("쿼리 %s=%s 로 라벨이 꺼졌다", name, value)
                    .isEqualTo(true)
            }

            val viaHeader = brandsOf(slug) { header("X-Ad-Label", "off") }
                .single { it["name"] == "광고 브랜드" }
            assertThat(viaHeader["requiresAdLabel"]).`as`("헤더로도 꺼지지 않는다").isEqualTo(true)
        } finally {
            deleteBrand(sponsored)
        }
    }

    /** RED 21 — "정해지지 않음"이 있으면 라벨을 붙일지 결정할 수 없다. 옵셔널이면 프론트가 빠뜨린다. */
    @Test
    fun `RED21 - is_sponsored가 null이 아니다`() {
        val slug = tag("never-null")
        val id = insertIngredient(slug)
        insertBrand(id, "탱커레이")

        assertThat(brandsOf(slug)).isNotEmpty().allSatisfy { brand ->
            assertThat(brand["isSponsored"]).isInstanceOf(Boolean::class.javaObjectType)
            assertThat(brand["requiresAdLabel"]).isInstanceOf(Boolean::class.javaObjectType)
        }
    }

    // ── RED 29~30 : 캐싱 · 색인 ───────────────────────────────────────────

    /** RED 29 — SPEC-07 §1.6. SSG 빌드가 같은 경로를 반복 호출한다. */
    @Test
    fun `RED29 - ETag와 Cache-Control이 붙는다`() {
        val slug = tag("cacheable")
        insertIngredient(slug)

        listOf(BASE, "$BASE/$slug", "$BASE/$slug/cocktails").forEach { path ->
            val response = mvc.get(path).andReturn().response

            assertThat(response.getHeader(HttpHeaders.ETAG)).`as`(path).isNotBlank()
            assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL))
                .`as`(path)
                .isEqualTo(CacheControlFilter.PUBLIC_CACHE)
        }
    }

    /**
     * RED 30 — DECISIONS §1.6. **재료 사전은 콘텐츠다.**
     *
     * SPEC-05 §4 렌더링 표에 `/ingredients` 가 없고, SPEC-07 §3.1 이 `noindex` 를 붙이라 한 것은
     * **필터 결과**(`PRIN-P06`)다. 사전을 거기 묶으면 색인 가치를 버린다.
     */
    @Test
    fun `RED30 - noindex가 붙지 않는다`() {
        val slug = tag("indexable")
        insertIngredient(slug)

        listOf(BASE, "$BASE/$slug", "$BASE/$slug/cocktails").forEach { path ->
            // 헤더가 **아예 없는 것**이 정상이다. 재료 사전은 색인 대상이라 붙일 이유가 없다.
            // `null` 을 그대로 넘기면 AssertJ 가 값이 없다고 실패한다 —
            // 없어서 통과해야 할 검사가 없어서 실패하면 뜻이 뒤집힌다.
            val robots = mvc.get(path).andReturn().response.getHeader("X-Robots-Tag").orEmpty()
            assertThat(robots).`as`(path).doesNotContainIgnoringCase("noindex")
        }
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────

    private fun allSlugs(): List<String> =
        slugsOf(mvc.get(BASE) { param("size", "100") }.andReturn())

    private fun detail(slug: String): Map<String, Any> = bodyOf(mvc.get("$BASE/$slug").andReturn())

    @Suppress("UNCHECKED_CAST")
    private fun brandsOf(
        slug: String,
        customize: MockHttpServletRequestDsl.() -> Unit = {},
    ): List<Map<String, Any>> =
        bodyOf(mvc.get("$BASE/$slug", dsl = customize).andReturn())["brands"] as List<Map<String, Any>>
}
