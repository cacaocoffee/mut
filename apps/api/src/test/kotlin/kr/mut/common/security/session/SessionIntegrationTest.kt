package kr.mut.common.security.session

import kr.mut.common.security.Role
import kr.mut.support.PostgresSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * ISSUE-005 RED 12~14 · 19~22 — 세션 쿠키와 무효화 (SPEC-08 §4.1).
 *
 * 쿠키 속성은 `DefaultCookieSerializer` 설정이 실제로 걸렸는지 **응답 헤더로** 봐야 안다.
 * 필터 등록이 빠지거나 순서가 틀리면 조용히 기본값으로 나간다.
 *
 * 시계를 주입해 8시간을 실제로 기다리지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(SessionProbes.PROFILE)
class SessionIntegrationTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var clock: MutableClock
    @Autowired private lateinit var roles: MutableRoleLookup
    @Autowired private lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun clearSessions() = jdbc.execute("DELETE FROM spring_session")

    // ── RED 12~14 : 쿠키 속성 ──────────────────────────────────────────────

    /** `NFR-SEC-01` — JS 가 못 읽어야 XSS 로 탈취되지 않는다. */
    @Test
    fun `RED12 - 세션 쿠키에 httpOnly 가 설정된다`() {
        assertThat(setCookie()).containsIgnoringCase("HttpOnly")
    }

    @Test
    fun `RED13 - 세션 쿠키에 Secure 가 설정된다`() {
        assertThat(setCookie()).containsIgnoringCase("Secure")
    }

    /** 크로스 사이트 POST 를 막되 일반 링크 이동은 살린다. */
    @Test
    fun `RED14 - 세션 쿠키에 SameSite Lax 가 설정된다`() {
        assertThat(setCookie()).contains("SameSite=Lax")
    }

    /** SPEC-08 §4.1 — 쿠키에 사용자 정보가 담기지 않는다. 서버 저장이다. */
    @Test
    fun `RED21 - 세션은 서버 저장이다`() {
        val cookie = setCookie()
        val value = cookie.substringAfter("=").substringBefore(";")

        assertThat(value)
            .`as`("쿠키 값은 세션 ID 뿐이다")
            .doesNotContain("editor", "admin", "member", "테스터")
        assertThat(value.length).isLessThan(120)
    }

    // ── RED 19~22 : 무효화 ─────────────────────────────────────────────────

    @Test
    fun `RED19 - 로그아웃시 세션이 즉시 무효화된다`() {
        val cookie = login(userId = 1, Role.MEMBER)
        assertThat(whoAmI(cookie)).isEqualTo("1")

        mvc.post("/probe/session/logout") { cookie(cookie); with(csrf()) }.andReturn()

        assertThat(whoAmI(cookie)).`as`("같은 쿠키로 더는 안 된다").isEmpty()
        assertThat(sessionCount()).isZero()
    }

    /** SPEC-08 §4.1 — 역할 변경 시 즉시 무효화. 다음 요청에 바로 걸려야 한다. */
    @Test
    fun `RED20 - 역할 변경시 세션이 즉시 무효화된다`() {
        val cookie = login(userId = 2, Role.MEMBER)
        assertThat(whoAmI(cookie)).isEqualTo("2")

        roles[2] = setOf(Role.EDITOR) // 승격도 변경이다

        assertThat(whoAmI(cookie))
            .`as`("역할이 바뀐 세션을 그대로 쓰게 두지 않는다")
            .isEmpty()
    }

    /**
     * SPEC-08 §3.3 — "세션에 캐시하지 않는다."
     *
     * 강등이 **다음 요청부터** 반영돼야 한다. 세션에 역할을 넣어 두고 그것으로 판정하면
     * 회수한 권한이 세션 수명만큼 살아 있다.
     */
    @Test
    fun `RED22 - 강등이 다음 요청부터 즉시 반영된다`() {
        val cookie = login(userId = 3, Role.ADMIN, Role.MEMBER)
        assertThat(whoAmI(cookie)).isEqualTo("3")

        roles[3] = emptySet() // 전부 회수 = 탈퇴하거나 차단됨

        assertThat(whoAmI(cookie))
            .`as`("회수한 권한이 세션 수명만큼 살아 있으면 안 된다")
            .isEmpty()
    }

    /** RED 16·17 의 조립 확인 — 정책이 필터에 실제로 걸렸는가. */
    @Test
    fun `editor 세션은 8시간이 지나면 죽는다`() {
        val cookie = login(userId = 4, Role.EDITOR)
        assertThat(storedMaxInactive())
            .`as`("8시간으로 줄어든다")
            .isEqualTo(Duration.ofHours(8).seconds.toInt())

        clock.advance(Duration.ofHours(7))
        assertThat(whoAmI(cookie)).`as`("7시간째는 살아 있다").isEqualTo("4")

        clock.advance(Duration.ofHours(2))
        assertThat(whoAmI(cookie)).`as`("9시간째는 죽는다 — 방금 활동했어도").isEmpty()
    }

    /** 일반 사용자는 rolling 이다. 같은 9시간이 지나도 살아 있어야 한다. */
    @Test
    fun `member 세션은 9시간이 지나도 산다`() {
        val cookie = login(userId = 5, Role.MEMBER)

        clock.advance(Duration.ofHours(9))

        assertThat(whoAmI(cookie)).isEqualTo("5")
        assertThat(storedMaxInactive()).isEqualTo(Duration.ofDays(30).seconds.toInt())
    }

    // ── 헬퍼 ───────────────────────────────────────────────────────────────

    private fun setCookie(): String =
        mvc.post("/probe/session/login") { param("userId", "99"); with(csrf()) }
            .andReturn().response.getHeader(HttpHeaders.SET_COOKIE)
            ?: error("Set-Cookie 가 없다 — 세션이 만들어지지 않았다")

    /**
     * 실제 클라이언트처럼 **쿠키로** 몬다.
     *
     * `MvcResult.request.getSession()` 을 보지 않는 이유는 Spring Session 의
     * `SessionRepositoryFilter` 가 요청을 감싸서 원본 요청에는 세션이 없기 때문이다 —
     * 그쪽을 보면 저장소를 거치지 않는 가짜 경로를 검증하게 된다.
     */
    private fun login(userId: Long, vararg granted: Role): Cookie {
        roles[userId] = granted.toSet()
        val response = mvc.post("/probe/session/login") { param("userId", "$userId"); with(csrf()) }
            .andReturn().response
        return response.getCookie(COOKIE_NAME)
            ?: error("세션 쿠키가 없다 — 세션이 만들어지지 않았다")
    }

    private fun whoAmI(cookie: Cookie): String =
        mvc.get("/probe/session/me") { cookie(cookie) }.andReturn().response.contentAsString

    private fun sessionCount(): Int =
        jdbc.queryForObject("SELECT count(*) FROM spring_session", Int::class.java) ?: 0

    /** 저장소에 실제로 반영됐는지 본다. 메모리 객체만 보면 영속 경로를 건너뛴다. */
    private fun storedMaxInactive(): Int = jdbc.queryForObject(
        "SELECT max_inactive_interval FROM spring_session ORDER BY creation_time DESC LIMIT 1",
        Int::class.java,
    ) ?: error("세션이 저장되지 않았다")


    companion object {
        const val COOKIE_NAME = "KCSESSION"

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { PostgresSupport.container.jdbcUrl }
            registry.add("spring.datasource.username") { PostgresSupport.container.username }
            registry.add("spring.datasource.password") { PostgresSupport.container.password }
            registry.add("spring.flyway.enabled") { true }
            registry.add("spring.flyway.user") { PostgresSupport.container.username }
            registry.add("spring.flyway.password") { PostgresSupport.container.password }
            // 운영 기본값이 true 다. 테스트에서도 켜 둬야 RED13 이 의미를 갖는다.
            registry.add("mut.session.secure") { true }
        }
    }
}

// ── 프로브 ─────────────────────────────────────────────────────────────────

/**
 * 세션 규약을 HTTP 로 태워 보기 위한 프로브다. 실제 로그인은 이슈 030(OAuth)이다.
 *
 * `@Profile` 로 가둔다 — 테스트 소스도 `kr.mut` 아래라 컴포넌트 스캔에 걸리고,
 * 그냥 두면 모든 웹 테스트에 이 매핑이 딸려 들어간다 (ISSUE-003 에서 겪었다).
 */
object SessionProbes {
    const val PROFILE = "session-probe"
}

/** 8시간을 실제로 기다리지 않는다. */
class MutableClock(private var now: Instant) : Clock() {
    override fun instant(): Instant = now
    override fun getZone() = ZoneOffset.UTC
    override fun withZone(zone: java.time.ZoneId): Clock = this
    fun advance(by: Duration) { now = now.plus(by) }
}

/** DB 없이 역할을 갈아 끼운다. 강등·승격을 그 자리에서 만들 수 있어야 한다. */
class MutableRoleLookup : SessionRoleLookup {
    private val map = mutableMapOf<Long, Set<Role>>()
    operator fun set(userId: Long, roles: Set<Role>) { map[userId] = roles }
    override fun rolesOf(userId: Long): Set<Role> = map[userId] ?: setOf(Role.MEMBER)
}

/** 테스트가 시계와 역할을 갈아 끼운다. `@Primary` 라 운영 빈을 밀어낸다. */
@Profile(SessionProbes.PROFILE)
@Configuration
class SessionProbeConfig {
    @Bean @Primary fun probeClock() = MutableClock(Instant.parse("2026-08-10T09:00:00Z"))
    @Bean @Primary fun probeRoles() = MutableRoleLookup()
}

@Profile(SessionProbes.PROFILE)
@RestController
class SessionProbeController(private val clock: MutableClock, private val roles: MutableRoleLookup) {

    @PostMapping("/probe/session/login")
    fun login(@RequestParam userId: Long, session: HttpSession): String {
        session.setAttribute(AbsoluteExpiryFilter.USER_ID, userId)
        session.setAttribute(SessionPolicy.ISSUED_AT, clock.instant())
        session.setAttribute(
            SessionPolicy.ISSUED_ROLES,
            roles.rolesOf(userId).map(Role::code).toSet(),
        )
        session.maxInactiveInterval = SessionPolicy.lifetime(roles.rolesOf(userId)).seconds.toInt()
        return "$userId"
    }

    /**
     * `HttpSession` 을 파라미터로 받으면 **Spring 이 없을 때 새로 만든다.**
     * 조회가 세션을 만들면 로그아웃 뒤에도 저장소에 행이 남아 "무효화됐다"를 확인할 수 없다.
     */
    @GetMapping("/probe/session/me")
    fun me(request: HttpServletRequest): String =
        request.getSession(false)?.getAttribute(AbsoluteExpiryFilter.USER_ID)?.toString() ?: ""

    @PostMapping("/probe/session/logout")
    fun logout(session: HttpSession): String {
        session.invalidate()
        return "ok"
    }
}
