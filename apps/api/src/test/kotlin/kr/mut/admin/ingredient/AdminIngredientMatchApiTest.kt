package kr.mut.admin.ingredient

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kr.mut.common.security.session.AbsoluteExpiryFilter
import kr.mut.common.security.session.SessionPolicy
import kr.mut.common.web.ApiPaths
import kr.mut.support.PostgresSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.time.LocalDate

/**
 * #195 — 재료 ↔ 유통 제품 매핑과 유통 여부 제안.
 *
 * 시드(`R__seed_04`)의 4만 건은 건드리지 않는다 — `manual` 출처로 시험 제품을 넣고,
 * 검색어를 시드에 없는 말("테스트캄파리")로 골라 시드가 잡히지 않게 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminIngredientMatchApiTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var json: ObjectMapper
    @Autowired private lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun clear() {
        jdbc.execute("TRUNCATE cocktail, ingredient, ingredient_product_match, audit_log, search_document CASCADE")
        jdbc.execute("DELETE FROM distributed_product WHERE source = 'manual'")
        jdbc.execute("""TRUNCATE user_role, "user" CASCADE""")
    }

    // ── RED 1·2 : 제안 ────────────────────────────────────────────────────

    @Test
    fun `RED1 - brand_keywords 로 제품명 부분일치하면 suggested 매치가 생긴다`() {
        val id = ingredient(brandKeywords = listOf("테스트캄파리", "TESTCAMPARI"))
        product("테스트캄파리 비터 700ml", "TESTCAMPARI BITTER")
        product("깜빠리아닌것", "OTHER")

        val body = bodyOf(suggest(id, session("editor")))

        assertThat(body["matches"].map { it["status"].asText() }).containsExactly("suggested")
        assertThat(body["matches"][0]["product"]["nameKo"].asText()).isEqualTo("테스트캄파리 비터 700ml")
        assertThat(body["matches"][0]["matchedKeyword"].asText()).isEqualTo("테스트캄파리")
        assertThat(body["matches"][0]["confidence"].asInt()).isEqualTo(80)
    }

    @Test
    fun `RED1 - 두 번 돌려도 같은 쌍은 한 줄이다`() {
        val id = ingredient(brandKeywords = listOf("테스트캄파리"))
        product("테스트캄파리 비터 700ml", null)

        suggest(id, session("editor"))
        val body = bodyOf(suggest(id, session("editor")))

        assertThat(body["matches"]).hasSize(1)
    }

    @Test
    fun `RED2 - SUGGESTED 는 domestic_availability 를 바꾸지 않는다`() {
        val id = ingredient(brandKeywords = listOf("테스트캄파리"), availability = "specialty")
        product("테스트캄파리 비터", null)

        val body = bodyOf(suggest(id, session("editor")))

        assertThat(body["ingredient"]["domesticAvailability"].asText()).isEqualTo("specialty")
        assertThat(availabilityOf(id)).isEqualTo("specialty")
    }

    // ── RED 3·4 : 제안 규칙 ───────────────────────────────────────────────

    @Test
    fun `RED3 - 최근 3년 안 승인 매치가 3건이면 common 을, 1건이면 specialty 를 제안한다`() {
        val id = ingredient(brandKeywords = listOf("테스트캄파리"))
        val recent = LocalDate.now().minusMonths(6)
        repeat(3) { product("테스트캄파리 $it", null, reportedOn = recent) }
        val editor = session("editor")
        val matchIds = bodyOf(suggest(id, editor))["matches"].map { it["id"].asLong() }

        approveMatch(id, matchIds[0], editor)
        assertThat(proposalOf(id, editor)["availability"].asText()).isEqualTo("specialty")

        approveMatch(id, matchIds[1], editor)
        approveMatch(id, matchIds[2], editor)
        val proposal = proposalOf(id, editor)
        assertThat(proposal["availability"].asText()).isEqualTo("common")
        assertThat(proposal["recentApprovedCount"].asInt()).isEqualTo(3)
        // 제안이지 확정이 아니다 — 재료의 기본값 common 이 그대로다
        assertThat(availabilityOf(id)).isEqualTo("common")
    }

    @Test
    fun `RED4 - 매치 없는 재료는 import_only 를 제안하고, 오래된 승인만 있어도 import_only 다`() {
        val id = ingredient(brandKeywords = listOf("테스트캄파리"))
        val editor = session("editor")
        assertThat(proposalOf(id, editor)["availability"].asText()).isEqualTo("import_only")

        product("테스트캄파리 옛날", null, reportedOn = LocalDate.now().minusYears(5))
        val matchId = bodyOf(suggest(id, editor))["matches"][0]["id"].asLong()
        approveMatch(id, matchId, editor)

        val proposal = proposalOf(id, editor)
        assertThat(proposal["availability"].asText()).isEqualTo("import_only")
        assertThat(proposal["approvedCount"].asInt()).isEqualTo(1)
        assertThat(proposal["recentApprovedCount"].asInt()).isEqualTo(0)
    }

    @Test
    fun `매치 재승인은 409 다`() {
        val id = ingredient(brandKeywords = listOf("테스트캄파리"))
        product("테스트캄파리", null)
        val editor = session("editor")
        val matchId = bodyOf(suggest(id, editor))["matches"][0]["id"].asLong()

        assertThat(approveMatch(id, matchId, editor).response.status).isEqualTo(200)
        assertThat(approveMatch(id, matchId, editor).response.status).isEqualTo(409)
    }

    // ── RED 5 : 확정은 어드민이, INV-INGREDIENT-01 그대로 ─────────────────

    @Test
    fun `RED5 - 대체재 없이 unavailable 로 바꾸면 422 로 막힌다 (INV-INGREDIENT-01)`() {
        val id = ingredient(brandKeywords = emptyList())
        val editor = session("editor")

        val denied = distribution(id, editor, """{"domesticAvailability":"unavailable"}""")
        assertThat(denied.response.status).isEqualTo(422)
        assertThat(availabilityOf(id)).isEqualTo("common")

        val ok = distribution(
            id, editor,
            """{"domesticAvailability":"unavailable","substituteNote":"아페롤로 대체","brandKeywords":[" 깜빠리 ","campari",""]}""",
        )
        assertThat(ok.response.status).isEqualTo(200)
        assertThat(availabilityOf(id)).isEqualTo("unavailable")
        assertThat(bodyOf(ok)["brandKeywords"].map { it.asText() }).containsExactly("깜빠리", "campari")
    }

    // ── RED 6 : CSRF · 권한 ───────────────────────────────────────────────

    @Test
    fun `RED6 - 승인 요청은 CSRF 토큰이 없으면 403 이다`() {
        val id = ingredient(brandKeywords = listOf("테스트캄파리"))
        product("테스트캄파리", null)
        val editor = session("editor")
        val matchId = bodyOf(suggest(id, editor))["matches"][0]["id"].asLong()

        val result = mvc.post("$ADMIN/$id/matches/$matchId/approve") { session = editor!! }.andReturn()
        assertThat(result.response.status).isEqualTo(403)
    }

    @Test
    fun `member 는 제안도 승인도 못 한다`() {
        val id = ingredient(brandKeywords = listOf("테스트캄파리"))
        assertThat(suggest(id, session("member")).response.status).isEqualTo(403)
        assertThat(distribution(id, session("member"), """{"domesticAvailability":"common"}""").response.status)
            .isEqualTo(403)
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────

    private fun suggest(id: Long, login: MockHttpSession?): MvcResult =
        mvc.post("$ADMIN/$id/matches/suggest") { with(csrf()); login?.let { session = it } }.andReturn()

    private fun approveMatch(id: Long, matchId: Long, login: MockHttpSession?): MvcResult =
        mvc.post("$ADMIN/$id/matches/$matchId/approve") { with(csrf()); login?.let { session = it } }.andReturn()

    private fun distribution(id: Long, login: MockHttpSession?, body: String): MvcResult =
        mvc.patch("$ADMIN/$id/distribution") {
            with(csrf())
            login?.let { session = it }
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andReturn()

    private fun proposalOf(id: Long, login: MockHttpSession?): JsonNode =
        bodyOf(mvc.get("$ADMIN/$id/matches") { login?.let { session = it } }.andReturn())["proposal"]

    private fun ingredient(brandKeywords: List<String>, availability: String = "common"): Long {
        val kw = brandKeywords.joinToString(",") { "'${it.replace("'", "''")}'" }
        return jdbc.queryForObject(
            """INSERT INTO ingredient (slug, name_ko, name_en, category, domestic_availability, is_approved, brand_keywords)
               VALUES ('test-campari-${seq++}', '테스트 캄파리', 'Test Campari', 'liqueur', '$availability', true,
                       ARRAY[$kw]::TEXT[]) RETURNING id""",
            Long::class.java,
        )!!
    }

    private fun product(nameKo: String, nameEn: String?, reportedOn: LocalDate? = LocalDate.now().minusDays(30)): Long =
        jdbc.queryForObject(
            """INSERT INTO distributed_product (source, source_key, name_ko, name_en, importer_or_maker, food_type, last_reported_on)
               VALUES ('manual', 'test-${seq++}', ?, ?, '테스트수입', '리큐르', ?) RETURNING id""",
            Long::class.java, nameKo, nameEn, reportedOn,
        )!!

    private fun availabilityOf(id: Long) =
        jdbc.queryForObject("SELECT domestic_availability FROM ingredient WHERE id = $id", String::class.java)!!

    private fun session(role: String): MockHttpSession? {
        val userId = jdbc.queryForObject(
            """INSERT INTO "user" (provider, provider_uid, display_name)
               VALUES ('kakao', 'uid-${seq++}-${System.nanoTime()}', '테스터') RETURNING id""",
            Long::class.java,
        )!!
        jdbc.update("INSERT INTO user_role (user_id, role) VALUES (?, ?)", userId, role)
        return MockHttpSession().apply {
            setAttribute(AbsoluteExpiryFilter.USER_ID, userId)
            setAttribute(SessionPolicy.ISSUED_AT, Instant.now())
            setAttribute(SessionPolicy.ISSUED_ROLES, setOf(role))
        }
    }

    private fun bodyOf(result: MvcResult): JsonNode =
        json.readTree(result.response.getContentAsString(Charsets.UTF_8))

    companion object {
        private val ADMIN = "${ApiPaths.ADMIN}/ingredients"
        private var seq = 0L

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
            // MockHttpSession 이 컨트롤러까지 가려면 spring-session-jdbc 를 끈다 (AdminIngredientApiTest 참조)
            registry.add("spring.autoconfigure.exclude") {
                "org.springframework.boot.autoconfigure.session.SessionAutoConfiguration"
            }
        }
    }
}
