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
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.Instant

/**
 * #203 — 어드민 유통 제품 목록. 시드 4만 건은 그대로 두고 `manual` 출처로 시험 행을 넣는다.
 * 검색어 "제품목록시험" 은 시드에 없다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminProductApiTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var json: ObjectMapper
    @Autowired private lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun clear() {
        jdbc.execute("DELETE FROM distributed_product WHERE source = 'manual'")
        jdbc.execute("""TRUNCATE user_role, "user" CASCADE""")
    }

    @Test
    fun `RED1,2,3 - 제품명·수입사로 거르고 유형으로 거르며 최근 신고순으로 페이징된다`() {
        product("제품목록시험 위스키 A", "수입사갑", "위스키", "2026-01-01")
        product("제품목록시험 위스키 B", "수입사을", "위스키", "2026-06-01")
        product("제품목록시험 리큐르", "수입사갑", "리큐르", "2026-03-01")
        val editor = session("editor")

        val byName = list("q=제품목록시험", editor)
        assertThat(byName["items"].map { it["nameKo"].asText() })
            .containsExactly("제품목록시험 위스키 B", "제품목록시험 리큐르", "제품목록시험 위스키 A")
        assertThat(byName["page"]["totalElements"].asLong()).isEqualTo(3)

        val byImporter = list("q=수입사갑", editor)
        assertThat(byImporter["items"]).hasSize(2)

        val byType = list("q=제품목록시험&foodType=위스키", editor)
        assertThat(byType["items"].map { it["nameKo"].asText() })
            .containsExactly("제품목록시험 위스키 B", "제품목록시험 위스키 A")

        val paged = list("q=제품목록시험&size=2&page=1", editor)
        assertThat(paged["items"]).hasSize(1)
        assertThat(paged["page"]["totalPages"].asInt()).isEqualTo(2)
        assertThat(paged["foodTypes"].map { it["foodType"].asText() }).contains("위스키", "리큐르")
    }

    @Test
    fun `RED4 - 응답에 구매 링크·가격이 없다`() {
        product("제품목록시험 단품", "수입사갑", "위스키", "2026-01-01")
        val item = list("q=제품목록시험", session("editor"))["items"][0]
        assertThat(item.fieldNames().asSequence().toList()).doesNotContain("purchaseUrl", "price", "priceBand")
    }

    @Test
    fun `RED5 - member 는 403, 비로그인은 401 이다`() {
        assertThat(mvc.get(ADMIN) { session = session("member")!! }.andReturn().response.status).isEqualTo(403)
        assertThat(mvc.get(ADMIN).andReturn().response.status).isEqualTo(401)
    }

    private fun list(query: String, login: MockHttpSession?): JsonNode {
        val result = mvc.get("$ADMIN?$query") { login?.let { session = it } }.andReturn()
        assertThat(result.response.status).`as`(result.response.getContentAsString(Charsets.UTF_8)).isEqualTo(200)
        return json.readTree(result.response.getContentAsString(Charsets.UTF_8))
    }

    private fun product(nameKo: String, importer: String, foodType: String, reportedOn: String) = jdbc.update(
        """INSERT INTO distributed_product (source, source_key, name_ko, importer_or_maker, food_type, last_reported_on)
           VALUES ('manual', ?, ?, ?, ?, ?::date)""",
        "test-${seq++}", nameKo, importer, foodType, reportedOn,
    )

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

    companion object {
        private val ADMIN = "${ApiPaths.ADMIN}/products"
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
            registry.add("spring.autoconfigure.exclude") {
                "org.springframework.boot.autoconfigure.session.SessionAutoConfiguration"
            }
        }
    }
}
