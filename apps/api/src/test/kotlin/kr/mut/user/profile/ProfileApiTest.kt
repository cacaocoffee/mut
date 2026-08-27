package kr.mut.user.profile

import com.fasterxml.jackson.databind.ObjectMapper
import kr.mut.common.security.session.AbsoluteExpiryFilter
import kr.mut.common.security.session.SessionPolicy
import kr.mut.common.web.ApiPaths
import kr.mut.support.PostgresSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
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
 * `GET /me/profile` (SPEC-07 §2.5).
 *
 * 화면 내비가 "로그인했는가 · 누구인가" 를 이 하나로 가른다. 계약을 고정한다:
 * 로그인은 표시명·역할을 주고, 비로그인은 401 이다(북마크와 같은 규약).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProfileApiTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var json: ObjectMapper
    @Autowired private lateinit var jdbc: JdbcTemplate

    private var seq = 0

    @BeforeEach
    fun clear() {
        jdbc.execute("""TRUNCATE user_role, "user" CASCADE""")
    }

    @Test
    fun `로그인하면 표시명과 역할을 준다`() {
        val me = login(displayName = "칵테일러", roles = listOf("member", "editor"))

        val result = mvc.get("${ApiPaths.BASE}/me/profile") { session = me }.andReturn()
        val body = json.readTree(result.response.contentAsByteArray)

        assertAll(
            { assertThat(result.response.status).isEqualTo(200) },
            { assertThat(body["displayName"].asText()).isEqualTo("칵테일러") },
            // 정렬해서 준다 — 화면이 순서에 기대지 않게.
            { assertThat(body["roles"].map { it.asText() }).containsExactly("editor", "member") },
        )
    }

    @Test
    fun `비로그인은 401 이다`() {
        val result = mvc.get("${ApiPaths.BASE}/me/profile").andReturn()

        assertThat(result.response.status).isEqualTo(401)
    }

    @Test
    fun `세션은 있는데 사용자가 없으면 401 이다`() {
        // 세션에 남은 user_id 의 사용자가 사라진 경우(삭제 등). 있는 척하지 않는다.
        val ghost = MockHttpSession().apply {
            setAttribute(AbsoluteExpiryFilter.USER_ID, 999_999L)
            setAttribute(SessionPolicy.ISSUED_AT, Instant.now())
            setAttribute(SessionPolicy.ISSUED_ROLES, setOf("member"))
        }

        val result = mvc.get("${ApiPaths.BASE}/me/profile") { session = ghost }.andReturn()

        assertThat(result.response.status).isEqualTo(401)
    }

    private fun login(displayName: String, roles: List<String>): MockHttpSession {
        val userId = jdbc.queryForObject(
            """INSERT INTO "user" (provider, provider_uid, display_name)
               VALUES ('kakao', 'uid-${seq++}-${System.nanoTime()}', ?) RETURNING id""",
            Long::class.java,
            displayName,
        )!!
        roles.forEach { jdbc.update("INSERT INTO user_role (user_id, role) VALUES (?, ?)", userId, it) }

        return MockHttpSession().apply {
            setAttribute(AbsoluteExpiryFilter.USER_ID, userId)
            setAttribute(SessionPolicy.ISSUED_AT, Instant.now())
            setAttribute(SessionPolicy.ISSUED_ROLES, roles.toSet())
        }
    }

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
            registry.add("mut.verification.scheduled") { false }

            // 이슈 025 — SessionRepositoryFilter 가 요청 세션을 갈아끼우면 MockHttpSession 의
            // 세션 속성(USER_ID 등)을 컨트롤러가 못 읽는다. 세션 오토컨피그를 뺀다.
            registry.add("spring.autoconfigure.exclude") {
                "org.springframework.boot.autoconfigure.session.SessionAutoConfiguration"
            }
        }
    }
}
