package kr.kcocktail.admin.content

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kr.kcocktail.common.security.session.AbsoluteExpiryFilter
import kr.kcocktail.common.security.session.SessionPolicy
import kr.kcocktail.common.web.ApiPaths
import kr.kcocktail.support.PostgresSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.time.Instant

/**
 * ISSUE-025 — 어드민 CRUD + `violations` 전부 (SPEC-07 §2.1·§3.4 · SPEC-08 §2).
 *
 * ## 목표는 `NFR-O-01` 하나다
 *
 * > 에디터가 개발자 없이 발행 — **신규 1건을 어드민만으로 완료**
 *
 * RED 36 이 그 인수 시나리오다. 나머지는 그 길에 놓인 문들이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminCocktailApiTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var json: ObjectMapper
    @Autowired private lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun clear() {
        jdbc.execute("TRUNCATE cocktail, ingredient, audit_log, search_document CASCADE")
        jdbc.execute("""TRUNCATE user_role, "user" CASCADE""")
    }

    // ── RED 1~6 : 권한 (SPEC-08 §2) ───────────────────────────────────────

    @ParameterizedTest
    @CsvSource("editor, 201", "admin, 201", "member, 403", "partner_owner, 403")
    fun `RED1-3 - 생성은 editor 이상만 된다`(role: String, expected: Int) {
        assertThat(create(session(role)).response.status).isEqualTo(expected)
    }

    /**
     * RED 6 — **미인증은 401 이고 403 이 아니다.**
     *
     * 둘을 합치면 사용자가 로그인해야 하는지 권한을 요청해야 하는지 모른다.
     */
    @Test
    fun `RED6 - 미인증은 401 이다`() {
        assertThat(create(null).response.status).isEqualTo(401)
    }

    @ParameterizedTest
    @CsvSource("editor, 200", "member, 403")
    fun `RED4-5 - 발행·회수도 editor 이상만 된다`(role: String, expected: Int) {
        val id = publishable(session("editor"))

        assertThat(publish(id, session(role)).response.status).isEqualTo(expected)
    }

    // ── RED 7~10 : 어드민 식별자와 노출 범위 ──────────────────────────────

    /**
     * RED 7~10 — 공개 응답과 셋이 다르다.
     *
     * `id`(어드민 경로가 지목하는 키) · `status`(에디터가 지금 상태를 알아야 한다) ·
     * **`abvCalculated` 와 `abvOverride` 구분**(오버라이드가 걸렸는지 알아야 고칠 수 있다).
     */
    @Test
    fun `RED7-10 - 어드민 응답은 id·status·abv 구분을 담는다`() {
        val body = json.readTree(create(session("editor")).response.getContentAsString(Charsets.UTF_8))

        assertAll(
            { assertThat(body.has("id")).`as`("RED7·8").isTrue() },
            { assertThat(body["status"].asText()).`as`("RED9").isEqualTo("draft") },
            { assertThat(body.has("abvCalculated")).`as`("RED10").isTrue() },
            { assertThat(body.has("abvOverride")).isTrue() },
        )
    }

    // ── RED 11~13 : draft 조회 ────────────────────────────────────────────

    @Test
    fun `RED11-13 - draft 는 어드민에서만 보인다`() {
        val id = idOf(create(session("editor")))
        val slug = slugOf(id)

        assertAll(
            {
                assertThat(mvc.get("$ADMIN/$id") { session = session("editor")!! }.andReturn().response.status)
                    .`as`("RED11 editor 는 본다").isEqualTo(200)
            },
            {
                // **404 다, 403 이 아니다.** `VIEW_DRAFT` 의 거부는 `HIDE` 라
                // (`Action.VIEW_DRAFT`) 403 으로 막으면 "그 id 는 존재한다" 가 새어 나간다.
                // RED 12 는 "접근할 수 없다" 를 요구하고, 404 가 그것을 더 강하게 만족한다.
                assertThat(mvc.get("$ADMIN/$id") { session = session("member")!! }.andReturn().response.status)
                    .`as`("RED12 member 는 못 본다 — 존재도 숨긴다").isEqualTo(404)
            },
            {
                assertThat(mvc.get("${ApiPaths.BASE}/cocktails/$slug").andReturn().response.status)
                    .`as`("RED13 공개 경로는 여전히 404").isEqualTo(404)
            },
        )
    }

    // ── RED 14~20 : violations 전부 (FR-ADMIN-003) — 요체 ─────────────────

    /**
     * RED 14~20 — **첫 실패에서 멈추지 않는다.**
     *
     * 하나씩 알려 주면 에디터가 저장·실패를 여섯 번 반복한다. 그게 `FR-ADMIN-003` 이
     * 막으려는 것이고, 이 이슈에서 가장 중요한 계약이다.
     */
    @Test
    fun `RED14-20 - 게이트 실패는 422 이고 violations 를 전부 담는다`() {
        // 향과 맛 서술이 없고(GATE-01) 클래식인데 story 도 없다(GATE-05)
        val id = idOf(create(session("editor"), tastingNote = null, isClassic = true))

        val result = publish(id, session("editor"))
        val body = json.readTree(result.response.getContentAsString(Charsets.UTF_8))
        val codes = body["violations"].map { it["code"].asText() }

        assertAll(
            { assertThat(result.response.status).`as`("RED20 — 400 이 아니다").isEqualTo(422) },
            { assertThat(codes).`as`("RED15·17").hasSizeGreaterThanOrEqualTo(2) },
            { assertThat(codes).contains("GATE-COCKTAIL-01", "GATE-COCKTAIL-05") },
            {
                assertThat(body["violations"]).allSatisfy { v ->
                    assertThat(v.has("code")).`as`("RED18").isTrue()
                    assertThat(v.has("message")).isTrue()
                }
            },
            { assertThat(codes).`as`("RED19 — 결정론적 순서").isEqualTo(codes.sorted()) },
        )
    }

    // ── RED 21~27 : 발행 ─────────────────────────────────────────────────

    @Test
    fun `RED21 - 게이트를 통과하면 200 과 slug·status·publishedAt`() {
        val id = publishable(session("editor"))

        val body = json.readTree(publish(id, session("editor")).response.getContentAsString(Charsets.UTF_8))

        assertAll(
            { assertThat(body["status"].asText()).isEqualTo("published") },
            { assertThat(body["slug"].asText()).isNotBlank() },
            { assertThat(body["publishedAt"].isNull).isFalse() },
        )
    }

    @Test
    fun `RED22 - 이미 발행됐으면 409`() {
        val id = publishable(session("editor"))
        publish(id, session("editor"))

        assertThat(publish(id, session("editor")).response.status).isEqualTo(409)
    }

    @Test
    fun `RED23-24 - 발행 성공시 감사와 색인이 남는다`() {
        val id = publishable(session("editor"))

        publish(id, session("editor"))

        assertAll(
            {
                assertThat(count("SELECT count(*) FROM audit_log WHERE entity_id = $id AND action = 'publish'"))
                    .`as`("RED23").isEqualTo(1)
            },
            {
                assertThat(count("SELECT count(*) FROM search_document WHERE entity_id = $id AND is_published"))
                    .`as`("RED24").isEqualTo(1)
            },
        )
    }

    /** RED 26 — "성공 시에만" 이다. 실패하면 부수효과가 **전혀** 없어야 한다. */
    @Test
    fun `RED26 - 발행 실패시 부수효과가 없다`() {
        val id = idOf(create(session("editor"), tastingNote = null))

        publish(id, session("editor"))

        assertAll(
            { assertThat(count("SELECT count(*) FROM audit_log WHERE entity_id = $id")).isZero() },
            { assertThat(count("SELECT count(*) FROM search_document WHERE entity_id = $id")).isZero() },
            { assertThat(statusOf(id)).isEqualTo("draft") },
        )
    }

    // ── RED 28~32 : 회수 · 전이 ──────────────────────────────────────────

    @Test
    fun `RED28-32 - 회수와 보관과 복원이 된다`() {
        val id = publishable(session("editor"))
        publish(id, session("editor"))

        assertAll(
            {
                unpublish(id)
                assertThat(statusOf(id)).`as`("RED28 회수").isEqualTo("draft")
            },
            {
                publish(id, session("editor"))
                archive(id)
                assertThat(statusOf(id)).`as`("RED31 보관").isEqualTo("archived")
            },
            {
                unpublish(id)
                assertThat(statusOf(id)).`as`("RED32 복원").isEqualTo("draft")
            },
            {
                assertThat(count("SELECT count(*) FROM audit_log WHERE entity_id = $id"))
                    .`as`("RED30 — 전이가 전부 남는다").isGreaterThanOrEqualTo(4)
            },
        )
    }

    // ── RED 33~35 : 우회 불가 (PRIN-T05) ─────────────────────────────────

    /**
     * RED 33·35 — **타입에 없으면 우회할 수 없다.**
     *
     * `status` · `publishedAt` 을 보내도 무시된다. 서비스에서 걸러 내는 것보다 강한 방식이라
     * 요청 DTO 자체에 필드를 두지 않았다.
     */
    @Test
    fun `RED33-35 - PATCH 로 status 나 publishedAt 을 바꿀 수 없다`() {
        val id = idOf(create(session("editor")))

        mvc.patch("$ADMIN/$id") {
            with(csrf())
            session = session("editor")!!
            contentType = MediaType.APPLICATION_JSON
            content = """{"status":"published","publishedAt":"2020-01-01T00:00:00Z","nameKo":"바뀐이름"}"""
        }.andReturn()

        assertAll(
            { assertThat(statusOf(id)).isEqualTo("draft") },
            { assertThat(count("SELECT count(*) FROM cocktail WHERE id = $id AND published_at IS NULL")).isEqualTo(1) },
            { assertThat(nameOf(id)).`as`("나머지 필드는 반영된다").isEqualTo("바뀐이름") },
        )
    }

    /** RED 34 — 최초 발행 이후 `slug` 는 못 바꾼다 (`INV-COCKTAIL-05`). */
    @Test
    fun `RED34 - 발행 후 PATCH 로 slug 를 바꿀 수 없다`() {
        val id = publishable(session("editor"))
        publish(id, session("editor"))
        val before = slugOf(id)

        val result = mvc.patch("$ADMIN/$id") {
            with(csrf())
            session = session("editor")!!
            contentType = MediaType.APPLICATION_JSON
            content = """{"slug":"changed-slug"}"""
        }.andReturn()

        assertAll(
            { assertThat(result.response.status).isEqualTo(422) },
            { assertThat(slugOf(id)).isEqualTo(before) },
        )
    }

    // ── RED 36 : 인수 시나리오 (NFR-O-01) ────────────────────────────────

    /**
     * RED 36 — **개발자 없이 신규 1건을 발행한다.**
     *
     * 생성 → 레시피 등록 → 게이트 통과 → 발행. 마이그레이션도 배포도 없다.
     * 이 시나리오가 도는 것이 이 이슈의 존재 이유다 (`NFR-O-01`).
     */
    @Test
    fun `RED36 - API 만으로 신규 1건을 발행한다`() {
        val editor = session("editor")

        val id = publishable(editor)
        val published = publish(id, editor)

        assertAll(
            { assertThat(published.response.status).isEqualTo(200) },
            { assertThat(statusOf(id)).isEqualTo("published") },
            {
                assertThat(count("SELECT count(*) FROM cocktail WHERE id = $id AND published_at IS NOT NULL"))
                    .isEqualTo(1)
            },
        )
    }

    // ── RED 37 : 캐싱 ───────────────────────────────────────────────────

    @Test
    fun `RED37 - 어드민 응답에 캐시 헤더가 없다`() {
        val response = create(session("editor")).response

        assertAll(
            { assertThat(response.getHeader(HttpHeaders.ETAG)).isNull() },
            {
                assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL).orEmpty())
                    .doesNotContain("public", "max-age=60")
            },
        )
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────

    private var seq = 0

    /**
     * 파라미터 이름이 `login` 인 이유가 있다. `session` 으로 두면 DSL 람다 안에서
     * **`MockHttpServletRequestDsl.session` 프로퍼티가 이름을 가져간다** —
     * 파라미터가 아니라 항상 `null` 인 그쪽을 읽어 세션이 영영 안 붙는다.
     */
    private fun create(
        login: MockHttpSession?,
        tastingNote: String? = "쌉싸름한 향에 단맛이 얹힌다",
        isClassic: Boolean = false,
    ) = mvc.post(ADMIN) {
        // CSRF 는 세션 바인딩이라(SecurityConfig) 토큰을 따로 발급받아야 한다.
        // 여기서 보려는 것은 권한이지 CSRF 가 아니다 — 그쪽은 이슈 003 이 검증한다.
        with(csrf())
        login?.let { this.session = it }
        contentType = MediaType.APPLICATION_JSON
        content = json.writeValueAsString(
            mapOf(
                "slug" to "admin-test-${seq++}",
                "nameKo" to "테스트", "nameEn" to "Test", "summary" to "요약",
                "baseSpirit" to "gin", "stylePrimary" to "highball", "method" to "build",
                "sweetness" to "dry", "glassType" to "하이볼 글라스",
                "styles" to listOf("highball"), "aromaTags" to listOf("citrus"),
                "tastingNote" to tastingNote, "isClassic" to isClassic,
            ),
        )
    }.andReturn()

    /** 게이트를 전부 통과하는 상태까지 만든다 — 레시피·재료 포함. */
    private fun publishable(session: MockHttpSession?): Long {
        val id = idOf(create(session))
        val ingredientId = jdbc.queryForObject(
            """INSERT INTO ingredient (slug, name_ko, name_en, category, domestic_availability, is_approved)
               VALUES ('gin-${seq++}', '진', 'gin', 'spirit', 'common', true) RETURNING id""",
            Long::class.java,
        )!!
        val recipeId = jdbc.queryForObject(
            "INSERT INTO recipe (cocktail_id, version_type) VALUES ($id, 'standard') RETURNING id",
            Long::class.java,
        )!!
        jdbc.execute(
            "INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit) " +
                "VALUES ($recipeId, $ingredientId, 1, 45, 'ml')",
        )
        jdbc.execute("INSERT INTO recipe_step (recipe_id, step_no, text) VALUES ($recipeId, 1, '얼음을 채운다')")
        return id
    }

    private fun publish(id: Long, login: MockHttpSession?) =
        mvc.post("$ADMIN/$id/publish") { with(csrf()); login?.let { this.session = it } }.andReturn()

    private fun unpublish(id: Long) =
        mvc.post("$ADMIN/$id/unpublish") { with(csrf()); session = session("editor")!! }.andReturn()

    private fun archive(id: Long) =
        mvc.post("$ADMIN/$id/archive") { with(csrf()); session = session("editor")!! }.andReturn()

    /** 역할을 가진 세션. `null` 이면 비로그인이다. */
    private fun session(role: String): MockHttpSession? {
        val userId = jdbc.queryForObject(
            """INSERT INTO "user" (provider, provider_uid, display_name)
               VALUES ('kakao', 'uid-${seq++}-${System.nanoTime()}', '테스터') RETURNING id""",
            Long::class.java,
        )!!
        jdbc.update("INSERT INTO user_role (user_id, role) VALUES (?, ?)", userId, role)

        // 발급 시각과 역할을 함께 심는다. 없으면 `AbsoluteExpiryFilter` 가 승격 세션을
        // **절대 만료로 간주해 무효화**한다 — "언제 만들어졌는지 모르는 세션은 신뢰하지
        // 않는다"가 SPEC-08 §4.1 의 의도다. 로그인(이슈 030)이 실제로 심어 줄 값들이다.
        return MockHttpSession().apply {
            setAttribute(AbsoluteExpiryFilter.USER_ID, userId)
            setAttribute(SessionPolicy.ISSUED_AT, Instant.now())
            setAttribute(SessionPolicy.ISSUED_ROLES, setOf(role))
        }
    }

    private fun idOf(result: org.springframework.test.web.servlet.MvcResult): Long {
        val body = result.response.getContentAsString(Charsets.UTF_8)
        val node = json.readTree(body)["id"]
            ?: error("생성 응답에 id 가 없다 (status=${result.response.status}): $body")
        return node.asLong()
    }

    private fun slugOf(id: Long) =
        jdbc.queryForObject("SELECT slug FROM cocktail WHERE id = $id", String::class.java)

    private fun nameOf(id: Long) =
        jdbc.queryForObject("SELECT name_ko FROM cocktail WHERE id = $id", String::class.java)

    private fun statusOf(id: Long) =
        jdbc.queryForObject("SELECT status FROM cocktail WHERE id = $id", String::class.java)

    private fun count(sql: String) = jdbc.queryForObject(sql, Long::class.java)!!

    private fun JsonNode.map(block: (JsonNode) -> String) = elements().asSequence().map(block).toList()

    companion object {
        private val ADMIN = "${ApiPaths.ADMIN}/cocktails"

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

            // `spring-session-jdbc` 의 `SessionRepositoryFilter` 는 요청 세션을 자기 것으로
            // 갈아끼운다 — `MockHttpSession` 에 심은 속성이 **컨트롤러까지 가지 않고**
            // 전부 401 이 된다. 원인이 권한처럼 보이지만 세션 저장소 문제다.
            //
            // `spring.session.store-type=none` 은 Boot 3 에 없는 값이라 조용히 무시된다.
            // 오토컨피그를 직접 뺀다.
            //
            // 이 파일이 보는 것은 **권한 판정**(역할 → 401·403·200)이지 세션 저장 방식이
            // 아니다. 저장·만료·강등 반영은 `SessionIntegrationTest` 가 실제 경로로 검증한다.
            registry.add("spring.autoconfigure.exclude") {
                "org.springframework.boot.autoconfigure.session.SessionAutoConfiguration"
            }
        }
    }
}
