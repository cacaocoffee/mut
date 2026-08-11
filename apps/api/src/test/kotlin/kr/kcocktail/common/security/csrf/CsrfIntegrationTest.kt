package kr.kcocktail.common.security.csrf

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.Cookie
import kr.kcocktail.common.web.ApiPaths
import kr.kcocktail.support.PostgresSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * ISSUE-007 RED 1~9 — CSRF (SPEC-08 §4.3).
 *
 * 쿠키 인증이라 CSRF 방어가 필수다. `SameSite=Lax` 가 1차, 토큰이 2차다.
 *
 * ## 면제가 늘지 않는지 본다
 *
 * RED 8 이 요점이다 — `/events` 하나만 통과하고 **다른 비인증 POST 는 막힌다.**
 * 면제 목록이 조용히 늘면 방어가 무의미해지는데, 늘어난 목록은 리뷰에 잘 보이지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(CsrfProbes.PROFILE)
class CsrfIntegrationTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var json: ObjectMapper

    // ── RED 1~2 : 발급과 바인딩 ────────────────────────────────────────────

    @Test
    fun `RED1 - GET auth csrf 가 토큰을 발급한다`() {
        val issued = issueToken()

        assertThat(issued.token).isNotBlank()
        assertThat(issued.headerName).isEqualTo(SecurityConfig.CSRF_HEADER)
    }

    /**
     * 세션 바인딩 (SPEC-08 §4.3). 쿠키 방식이면 토큰이 브라우저에 있어 XSS 로 읽히면 그대로 뚫린다.
     */
    @Test
    fun `RED2 - 토큰이 세션에 바인딩된다`() {
        val mine = issueToken()
        val other = issueToken() // 세션 쿠키를 안 물려주면 별개 세션이다

        // 내 세션 + 내 토큰 → 통과
        assertThat(post(PROBE, token = mine).response.status).isEqualTo(200)

        // 내 세션 + 남의 토큰 → 거부
        assertThat(post(PROBE, token = mine.copy(token = other.token)).response.status)
            .`as`("다른 세션의 토큰은 통하지 않는다")
            .isEqualTo(403)
    }

    // ── RED 3~6 : 메서드별 요구 ───────────────────────────────────────────

    @Test
    fun `RED3 - POST 에 CSRF 토큰이 없으면 403`() {
        assertThat(mvc.post(PROBE).andReturn().response.status).isEqualTo(403)
    }

    @Test
    fun `RED4 - PATCH PUT DELETE 도 토큰을 요구한다`() {
        assertAll(
            listOf<() -> Unit>(
                { assertThat(mvc.patch(PROBE).andReturn().response.status).`as`("PATCH").isEqualTo(403) },
                { assertThat(mvc.put(PROBE).andReturn().response.status).`as`("PUT").isEqualTo(403) },
                { assertThat(mvc.delete(PROBE).andReturn().response.status).`as`("DELETE").isEqualTo(403) },
            ),
        )
    }

    /** 안전한 메서드까지 토큰을 요구하면 첫 조회조차 못 한다. */
    @Test
    fun `RED5 - GET 은 토큰을 요구하지 않는다`() {
        assertThat(mvc.get(PROBE).andReturn().response.status).isEqualTo(200)
    }

    @Test
    fun `RED6 - 잘못된 토큰은 403`() {
        val issued = issueToken()

        assertThat(post(PROBE, token = issued.copy(token = "완전히-틀린-값")).response.status)
            .isEqualTo(403)
    }

    // ── RED 7~9 : 면제 ────────────────────────────────────────────────────

    /**
     * SPEC-08 §4.3 의 유일한 예외. 인증이 필요 없고 부작용이 집계뿐이라
     * CSRF 대신 **레이트 리밋(120rpm, 세션 기준)이 방어**한다.
     */
    @Test
    fun `RED7 - POST events 는 CSRF 면제다`() {
        assertThat(mvc.post("${ApiPaths.BASE}/events") { jsonBody() }.andReturn().response.status)
            .`as`("토큰 없이도 통과한다")
            .isEqualTo(200)
    }

    /** **면제 목록이 최소인지** 확인한다. 이게 이 이슈에서 제일 중요한 테스트다. */
    @Test
    fun `RED8 - events 외의 비인증 POST 는 면제가 아니다`() {
        assertAll(
            listOf(PROBE, "${ApiPaths.BASE}/probe/csrf/public", "${ApiPaths.ADMIN}/probe")
                .map<String, () -> Unit> { path ->
                    {
                        assertThat(mvc.post(path) { jsonBody() }.andReturn().response.status)
                            .`as`("%s 는 면제가 아니다", path)
                            .isEqualTo(403)
                    }
                },
        )
    }

    /**
     * 설정 파일로 빼면 늘어난다. "이 엔드포인트만 잠깐"이 쌓이는 데 오래 걸리지 않는다.
     * **여기 한 줄을 추가하려면 커밋이 필요하고, 그 커밋에 이유를 적게 된다.**
     */
    @Test
    fun `RED9 - 면제 경로 목록이 코드 상수이고 하나뿐이다`() {
        assertThat(CsrfExemptions.PATHS)
            .`as`("SPEC-08 §4.3 의 유일한 예외")
            .containsExactly("${ApiPaths.BASE}/events")

        assertThat(CsrfExemptions.isExempt("${ApiPaths.BASE}/events")).isTrue()
        assertThat(CsrfExemptions.isExempt("${ApiPaths.BASE}/events/batch")).isTrue()
        assertThat(CsrfExemptions.isExempt("${ApiPaths.BASE}/eventsx")).isFalse()
        assertThat(CsrfExemptions.isExempt("${ApiPaths.ADMIN}/events")).isFalse()
    }

    // ── 헬퍼 ───────────────────────────────────────────────────────────────

    private data class Issued(val headerName: String, val token: String, val session: Cookie?)

    private fun issueToken(): Issued {
        val result = mvc.get("${ApiPaths.BASE}/auth/csrf").andReturn()
        val body = json.readValue(result.response.contentAsString, CsrfTokenResponse::class.java)
        return Issued(body.headerName, body.token, result.response.getCookie(COOKIE))
    }

    private fun post(path: String, token: Issued): MvcResult = mvc.post(path) {
        header(SecurityConfig.CSRF_HEADER, token.token)
        token.session?.let { cookie(it) }
        jsonBody()
    }.andReturn()

    private fun MockHttpServletRequestDsl.jsonBody() {
        contentType = MediaType.APPLICATION_JSON
        content = "{}"
    }

    companion object {
        val PROBE = "${ApiPaths.BASE}/probe/csrf"
        const val COOKIE = "KCSESSION"

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { PostgresSupport.container.jdbcUrl }
            registry.add("spring.datasource.username") { PostgresSupport.container.username }
            registry.add("spring.datasource.password") { PostgresSupport.container.password }
            registry.add("spring.flyway.enabled") { true }
            registry.add("spring.flyway.user") { PostgresSupport.container.username }
            registry.add("spring.flyway.password") { PostgresSupport.container.password }
            // 레이트 리밋이 CSRF 테스트를 방해하지 않게 한다 — 여기서 볼 것은 CSRF 다.
            registry.add("kcocktail.rate-limit.enabled") { false }
        }
    }
}

// ── 프로브 ─────────────────────────────────────────────────────────────────

object CsrfProbes {
    const val PROFILE = "csrf-probe"
}

/** CSRF 규약을 태워 보기 위한 프로브. `@Profile` 로 가둔다 (ISSUE-003 에서 겪은 누출 방지). */
@Profile(CsrfProbes.PROFILE)
@RestController
@RequestMapping(ApiPaths.BASE)
class CsrfProbeController {

    @GetMapping("/probe/csrf")
    fun read() = mapOf("ok" to true)

    @PostMapping("/probe/csrf")
    fun create() = mapOf("ok" to true)

    @PutMapping("/probe/csrf")
    fun replace() = mapOf("ok" to true)

    @PatchMapping("/probe/csrf")
    fun update() = mapOf("ok" to true)

    @DeleteMapping("/probe/csrf")
    fun remove() = mapOf("ok" to true)

    /** 인증이 필요 없는 공개 POST. **그래도 면제가 아니다** (RED 8). */
    @PostMapping("/probe/csrf/public")
    fun publicWrite() = mapOf("ok" to true)

    /** 이벤트 수집 자리. 실물은 이슈 034(#36)다 — 여기서는 면제 등록만 확인한다. */
    @PostMapping("/events")
    fun events() = mapOf("accepted" to true)
}

@Profile(CsrfProbes.PROFILE)
@RestController
@RequestMapping(ApiPaths.ADMIN)
class CsrfAdminProbeController {
    @PostMapping("/probe")
    fun write() = mapOf("ok" to true)
}
