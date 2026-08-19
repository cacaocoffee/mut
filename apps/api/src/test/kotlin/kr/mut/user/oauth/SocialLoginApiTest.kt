package kr.mut.user.oauth

import com.fasterxml.jackson.databind.ObjectMapper
import kr.mut.common.security.Role
import kr.mut.common.security.session.AbsoluteExpiryFilter
import kr.mut.common.security.session.SessionPolicy
import kr.mut.common.web.ApiPaths
import kr.mut.support.PostgresSupport
import kr.mut.user.domain.AuthProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.net.URI
import java.time.Instant

/**
 * ISSUE-030 — 소셜 로그인 3종 (`FR-USER-001` · SPEC-08 §4.2 · `PRIN-T06`).
 *
 * ## 제공자를 HTTP 층에서 흉내 낸다
 *
 * `SocialAuthProvider` 구현을 가짜로 갈아 끼우면 RED 12~14 가 **스텁이 도는지**를
 * 검증하게 된다 — 카카오 응답의 `kakao_account.profile.nickname` 을 실제로 읽는지,
 * 네이버의 `response` 한 겹을 벗기는지는 확인되지 않는다. 그 파싱이 어댑터의 존재 이유인데.
 *
 * [SocialProviderStub] 이 제공자 자리에 서고, 어댑터 코드는 그대로 돈다.
 *
 * ## 세션 저장소는 뺀다
 *
 * `spring-session-jdbc` 의 `SessionRepositoryFilter` 가 요청 세션을 자기 것으로 갈아끼워서,
 * `MockHttpSession` 에는 컨트롤러가 심은 속성이 하나도 안 남는다.
 *
 * 이 파일이 보는 것은 **세션에 무엇이 심기고 수명이 얼마인가**이지 그것이 어느 저장소에
 * 들어가느냐가 아니다. 저장·만료·강등 반영은 `SessionIntegrationTest` 가 실제 경로로 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SocialLoginApiTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var json: ObjectMapper
    @Autowired private lateinit var providers: List<SocialAuthProvider>

    @BeforeEach
    fun clear() {
        jdbc.execute("""TRUNCATE user_role, "user" CASCADE""")
        SocialProviderStub.reset()
    }

    // ── RED 1~4 : PKCE (SPEC-08 §4.2) ─────────────────────────────────────

    /**
     * RED 1 — 인가 URL 에 `code_challenge` 와 `S256` 이 있다.
     *
     * `plain` 은 챌린지가 곧 검증자라 아무것도 막지 못한다. [Pkce] 에 그 분기가 없다.
     */
    @ParameterizedTest
    @ValueSource(strings = ["kakao", "naver", "apple"])
    fun `RED1 - 인가 URL 이 PKCE 챌린지를 담는다`(provider: String) {
        val location = authorize(provider).location()

        assertAll(
            { assertThat(location).contains("code_challenge=") },
            { assertThat(location).contains("code_challenge_method=S256") },
            { assertThat(location).contains("state=") },
            { assertThat(location).doesNotContain("code_challenge_method=plain") },
        )
    }

    /**
     * RED 2·3 — 콜백이 **세션에 묶인 검증자**로 교환한다.
     *
     * 스텁이 받은 폼을 그대로 보관하므로, `code_verifier` 가 실제로 갔는지 확인할 수 있다.
     * 챌린지와의 관계도 본다 — 아무 값이나 보내면 제공자가 거부하니 의미가 없다.
     */
    @Test
    fun `RED2,3 - 콜백이 세션의 code_verifier 로 교환한다`() {
        val session = MockHttpSession()
        val challenge = authorize("kakao", session).location().param("code_challenge")

        callback("kakao", session, state = stateOf(session))

        val sent = SocialProviderStub.lastTokenForm["kakao"]!!

        assertAll(
            { assertThat(sent).containsKey("code_verifier") },
            {
                assertThat(Pkce.challengeOf(sent["code_verifier"]!!))
                    .`as`("보낸 검증자가 인가 때 쓴 챌린지와 짝이다")
                    .isEqualTo(challenge)
            },
        )
    }

    /**
     * RED 3 보강 — **다른 세션으로는 못 쓴다.**
     *
     * state 를 훔쳐도 검증자가 없으면 교환할 수 없다. 이것이 PKCE 가 인가 코드
     * 가로채기를 막는 방식이고, state 만으로는 못 하는 일이다.
     */
    @Test
    fun `RED3 - 다른 세션의 state 는 통하지 않는다`() {
        val mine = MockHttpSession()
        authorize("kakao", mine)
        val stolen = stateOf(mine)

        val attacker = MockHttpSession()
        val result = callback("kakao", attacker, state = stolen)

        assertAll(
            { assertThat(result.response.status).isEqualTo(400) },
            { assertThat(userCount()).`as`("계정이 만들어지지 않는다").isZero() },
        )
    }

    /**
     * RED 4 — **순수 Authorization Code 경로가 없다.**
     *
     * `SocialAuthProvider` 의 두 메서드가 모두 PKCE 값을 요구한다. 인터페이스에 그 자리가
     * 없으면 우회할 방법도 없다 (`PRIN-T05` · 이슈 025 와 같은 방식).
     */
    @Test
    fun `RED4 - PKCE 없는 흐름이 존재하지 않는다`() {
        val methods = SocialAuthProvider::class.java.declaredMethods.map { it.name to it.parameterCount }

        assertAll(
            { assertThat(methods).contains("authorizeUrl" to 2, "exchange" to 2) },
            {
                assertThat(methods.map { it.first })
                    .`as`("PKCE 를 안 받는 다른 진입점이 없다")
                    .containsExactlyInAnyOrder("authorizeUrl", "exchange", "getProvider")
            },
        )
    }

    // ── RED 5~11 : state ──────────────────────────────────────────────────

    @Test
    fun `RED5,6 - state 를 발급하고 콜백이 검증한다`() {
        val session = MockHttpSession()
        authorize("kakao", session)

        assertAll(
            { assertThat(stateOf(session)).isNotBlank() },
            { assertThat(callback("kakao", session, state = stateOf(session)).response.status).isEqualTo(302) },
        )
    }

    /**
     * RED 7·8 — **1회용이다.** 재사용은 거부하고 로그한다 (SPEC-08 §4.2).
     *
     * 남겨 두면 같은 코드로 두 번 교환할 수 있고, 그러면 `state` 는 CSRF 방어가 아니라
     * **재생 가능한 토큰**이 된다.
     */
    @Test
    fun `RED7,8 - state 는 1회용이다`() {
        val session = MockHttpSession()
        authorize("kakao", session)
        val state = stateOf(session)

        val first = callback("kakao", session, state = state)
        val second = callback("kakao", session, state = state)

        assertAll(
            { assertThat(first.response.status).isEqualTo(302) },
            { assertThat(second.response.status).`as`("두 번째는 거부").isEqualTo(400) },
            { assertThat(userCount()).`as`("계정이 둘 생기지 않는다").isEqualTo(1) },
        )
    }

    /**
     * RED 9·10 — 10분 만료.
     *
     * 시계를 돌리는 대신 세션에 심긴 발급 시각을 과거로 바꾼다 — `OAuthStateStore` 가
     * 그 값을 보고 판정하므로, 만료 판정 자체를 검증하는 데 충분하다.
     */
    @Test
    fun `RED9,10 - 10분 지난 state 는 거부된다`() {
        val session = MockHttpSession()
        authorize("kakao", session)
        val state = stateOf(session)

        expireState(session, state)

        assertAll(
            { assertThat(OAuthStateStore.TTL.toMinutes()).`as`("SPEC-08 §4.2").isEqualTo(10) },
            { assertThat(callback("kakao", session, state = state).response.status).isEqualTo(400) },
        )
    }

    @Test
    fun `RED11 - state 불일치는 거부된다`() {
        val session = MockHttpSession()
        authorize("kakao", session)

        assertAll(
            { assertThat(callback("kakao", session, state = "made-up").response.status).isEqualTo(400) },
            {
                // 카카오로 시작해 네이버 콜백으로 들어오는 경우.
                assertThat(callback("naver", session, state = stateOf(session)).response.status).isEqualTo(400)
            },
        )
    }

    // ── RED 12~16 : provider 3종 (FR-USER-001 · PRIN-T06) ─────────────────

    /**
     * RED 12·13·14 — 세 제공자가 각자의 응답 모양으로 동작한다.
     *
     * 카카오는 `kakao_account.profile.nickname`, 네이버는 `response` 한 겹,
     * 애플은 `id_token` 클레임이다. **그 차이가 어댑터 안에서 끝나는지**가 `PRIN-T06` 이다.
     */
    @Test
    fun `RED12 - kakao 로그인이 동작한다`() {
        loginWith("kakao")

        assertAll(
            { assertThat(providerUidOf("kakao")).isEqualTo("1234567890") },
            { assertThat(displayNameOf("kakao")).`as`("신 필드를 읽는다").isEqualTo("카카오사용자") },
            { assertThat(emailOf("kakao")).isEqualTo("kakao@example.com") },
        )
    }

    @Test
    fun `RED13 - naver 로그인이 동작한다`() {
        loginWith("naver")

        assertAll(
            { assertThat(providerUidOf("naver")).`as`("response 한 겹을 벗긴다").isEqualTo("naver-uid-1") },
            { assertThat(displayNameOf("naver")).isEqualTo("네이버사용자") },
        )
    }

    /** RED 14 — 애플은 `id_token` 서명을 JWKS 로 검증한 뒤에야 프로필이 나온다. */
    @Test
    fun `RED14 - apple 로그인이 동작한다`() {
        loginWith("apple")

        assertAll(
            { assertThat(providerUidOf("apple")).isEqualTo("apple-sub-1") },
            { assertThat(emailOf("apple")).isEqualTo("relay@privaterelay.appleid.com") },
        )
    }

    /**
     * RED 14 보강 — **서명을 검증하지 않으면 로그인이 아니다.**
     *
     * `id_token` 은 그냥 base64 라 누구나 만들 수 있다. 발급자·수신자가 틀리면 거부해야 한다 —
     * 통과시키면 아무 `sub` 로나 들어올 수 있다.
     */
    @Test
    fun `RED14 - id_token 의 발급자·수신자가 틀리면 거부한다`() {
        SocialProviderStub.appleAudience = "someone.else"
        val bad = attemptLogin("apple")

        SocialProviderStub.reset()
        SocialProviderStub.appleIssuer = "https://evil.example.com"
        val worse = attemptLogin("apple")

        assertAll(
            { assertThat(bad).`as`("audience 불일치").isNotEqualTo(302) },
            { assertThat(worse).`as`("issuer 불일치").isNotEqualTo(302) },
            { assertThat(userCount()).isZero() },
        )
    }

    /** RED 15 — 없는 제공자는 **404** 다. 잘못된 파라미터가 아니라 없는 경로다. */
    @ParameterizedTest
    @ValueSource(strings = ["google", "facebook", "email"])
    fun `RED15 - 그 외 provider 는 404 다`(provider: String) {
        assertThat(authorize(provider).response.status).isEqualTo(404)
    }

    /**
     * RED 16 — **도메인이 벤더를 모른다** (`PRIN-T06`).
     *
     * 어댑터는 셋 다 인터페이스 뒤에 있고, `user` 모듈의 나머지(도메인 · 리포지토리 · 로그인
     * 서비스)는 벤더 이름을 언급하지 않는다. `SocialProfile` 셋만 안다.
     */
    @Test
    fun `RED16 - 어댑터가 인터페이스 뒤에 있다`() {
        val vendorAware = listOf(
            kr.mut.user.domain.User::class,
            kr.mut.user.oauth.SocialLoginService::class,
        ).flatMap { it.java.declaredFields.map { f -> f.type.name } + it.java.declaredMethods.map { m -> m.returnType.name } }

        assertAll(
            {
                assertThat(providers.map { it.provider })
                    .containsExactlyInAnyOrder(AuthProvider.KAKAO, AuthProvider.NAVER, AuthProvider.APPLE)
            },
            {
                assertThat(vendorAware)
                    .`as`("도메인·로그인 서비스가 벤더 타입을 들고 있지 않다")
                    .noneMatch { it.contains("Kakao") || it.contains("Naver") || it.contains("Apple") }
            },
        )
    }

    // ── RED 17~20 : 동일인 판정 (SPEC-08 §4.2) ────────────────────────────

    @Test
    fun `RED17,18 - provider 와 uid 조합으로 식별하고 재로그인은 같은 계정이다`() {
        loginWith("kakao")
        val first = userIdOf("kakao")

        loginWith("kakao")

        assertAll(
            { assertThat(userCount()).isEqualTo(1) },
            { assertThat(userIdOf("kakao")).isEqualTo(first) },
        )
    }

    /**
     * RED 19 — **이메일이 같아도 병합하지 않는다.**
     *
     * 편의상 넣고 싶어지는 지점이고 SPEC-08 §4.2 가 명시적으로 금지했다.
     * 이메일은 제공자마다 다르고 바뀐다. 무엇보다 **계정 탈취 경로가 된다** —
     * 남의 이메일로 소셜 계정을 만들 수 있는 제공자가 하나라도 있으면 그것으로 들어갈 수 있다.
     */
    @Test
    fun `RED19 - 이메일이 같아도 provider 가 다르면 다른 계정이다`() {
        SocialProviderStub.profiles["kakao"] = kakaoProfile(id = "1", email = "same@example.com")
        SocialProviderStub.profiles["naver"] = naverProfile(id = "n1", email = "same@example.com")

        loginWith("kakao")
        loginWith("naver")

        assertAll(
            { assertThat(userCount()).`as`("병합하지 않는다").isEqualTo(2) },
            { assertThat(emailOf("kakao")).isEqualTo(emailOf("naver")) },
            { assertThat(userIdOf("kakao")).isNotEqualTo(userIdOf("naver")) },
        )
    }

    /** RED 20 — 이메일이 바뀌어도 같은 계정이다. 식별자는 `(provider, uid)` 다. */
    @Test
    fun `RED20 - 이메일 변경이 계정 식별에 영향을 주지 않는다`() {
        SocialProviderStub.profiles["kakao"] = kakaoProfile(id = "1", email = "before@example.com")
        loginWith("kakao")
        val id = userIdOf("kakao")

        SocialProviderStub.profiles["kakao"] = kakaoProfile(id = "1", email = "after@example.com")
        loginWith("kakao")

        assertAll(
            { assertThat(userCount()).isEqualTo(1) },
            { assertThat(userIdOf("kakao")).isEqualTo(id) },
            { assertThat(emailOf("kakao")).`as`("값은 따라간다").isEqualTo("after@example.com") },
        )
    }

    // ── RED 21~23 : 이메일 선택 (SPEC-08 §4.2 · §5.1) ─────────────────────

    /**
     * RED 21·22·23 — **이메일 없이 가입이 완료된다.**
     *
     * 애플 비공개 릴레이가 대표적이고, 카카오도 선택 동의라 거부할 수 있다.
     * 필수로 만들면 그 사람은 가입 자체를 못 한다.
     */
    @Test
    fun `RED21,22,23 - 이메일 없이 가입이 완료된다`() {
        SocialProviderStub.profiles["kakao"] = kakaoProfile(id = "1", email = null)
        SocialProviderStub.appleEmail = null

        val kakao = attemptLogin("kakao")
        val apple = attemptLogin("apple")

        assertAll(
            { assertThat(kakao).isEqualTo(302) },
            { assertThat(apple).isEqualTo(302) },
            { assertThat(userCount()).isEqualTo(2) },
            { assertThat(emailOf("kakao")).isNull() },
            { assertThat(emailOf("apple")).isNull() },
            {
                // 이메일이 null 인 계정으로도 세션이 정상 발급된다 (RED 22).
                assertThat(count("SELECT count(*) FROM user_role WHERE role = 'member'")).isEqualTo(2)
            },
        )
    }

    /** RED 23 보강 — 재로그인 때 이메일이 없어도 갖고 있던 값을 지우지 않는다. */
    @Test
    fun `RED23 - 재로그인에 이메일이 없어도 기존 값을 지우지 않는다`() {
        loginWith("apple")
        assertThat(emailOf("apple")).isNotNull()

        SocialProviderStub.appleEmail = null // 애플은 최초 인가에서만 준다
        loginWith("apple")

        assertThat(emailOf("apple"))
            .`as`("한 번 로그인할 때마다 연락처가 지워지면 안 된다")
            .isEqualTo("relay@privaterelay.appleid.com")
    }

    // ── RED 24~27 : 최초 로그인 ───────────────────────────────────────────

    @Test
    fun `RED24,25,26 - 최초 로그인에 계정과 member 역할이 생긴다`() {
        loginWith("kakao")

        assertAll(
            { assertThat(userCount()).`as`("RED24").isEqualTo(1) },
            {
                assertThat(jdbc.queryForList("SELECT role FROM user_role", String::class.java))
                    .`as`("RED25 기본 역할은 member")
                    .containsExactly(Role.MEMBER.code)
            },
            { assertThat(displayNameOf("kakao")).`as`("RED26 provider 에서 온다").isEqualTo("카카오사용자") },
        )
    }

    /**
     * RED 27 — 표시 이름이 없어도 **가입은 된다.**
     *
     * `display_name` 은 `NOT NULL` 이다 (SPEC-06 §3.5). 이름을 못 받았다는 이유로
     * 로그인을 막으면 그 사람은 서비스를 쓸 방법이 없다.
     *
     * `provider_uid` 를 그대로 쓰지 않는다 — 표시 이름은 화면에 나오는 값이다.
     */
    @Test
    fun `RED27 - 표시 이름이 없으면 기본값을 만든다`() {
        SocialProviderStub.profiles["kakao"] = """{ "id": 987654321, "kakao_account": {} }"""

        assertThat(attemptLogin("kakao")).isEqualTo(302)

        val name = displayNameOf("kakao")!!

        assertAll(
            { assertThat(name).startsWith(SocialLoginService.DEFAULT_NAME_PREFIX) },
            { assertThat(name).`as`("uid 를 통째로 노출하지 않는다").doesNotContain("987654321") },
        )
    }

    // ── RED 28~30 : 세션 (이슈 005 연계) ──────────────────────────────────

    /**
     * RED 28·29 — 콜백이 세션을 발급하고, `member` 는 **30일 rolling** 이다 (SPEC-08 §4.1).
     *
     * `httpOnly` 는 `SessionCookieConfig` 가 협상 대상이 아니라고 못박았다 (이슈 005) —
     * 여기서는 세션 자체가 서고 수명이 맞는지 본다.
     */
    @Test
    fun `RED28,29 - 콜백이 30일 rolling 세션을 발급한다`() {
        val session = MockHttpSession()
        authorize("kakao", session)
        callback("kakao", session, state = stateOf(session))

        assertAll(
            { assertThat(session.getAttribute(AbsoluteExpiryFilter.USER_ID)).isEqualTo(userIdOf("kakao")) },
            { assertThat(session.getAttribute(SessionPolicy.ISSUED_AT)).isInstanceOf(Instant::class.java) },
            {
                assertThat(session.maxInactiveInterval.toLong())
                    .`as`("member 는 30일")
                    .isEqualTo(SessionPolicy.lifetime(setOf(Role.MEMBER)).seconds)
            },
        )
    }

    @Test
    fun `RED30 - logout 이 세션을 무효화한다`() {
        val session = MockHttpSession()
        authorize("kakao", session)
        callback("kakao", session, state = stateOf(session))

        val result = mvc.post("$AUTH/logout") { with(csrf()); this.session = session }.andReturn()

        assertAll(
            { assertThat(result.response.status).isEqualTo(204) },
            { assertThat(session.isInvalid).isTrue() },
            {
                assertThat(mvc.post("$AUTH/logout") { with(csrf()) }.andReturn().response.status)
                    .`as`("비로그인 로그아웃은 401")
                    .isEqualTo(401)
            },
        )
    }

    // ── RED 31~34 : 수집 금지 (ADR-0004 · SPEC-08 §5.1) ───────────────────

    /**
     * RED 31·32·34 — **요청하지 않는 것이 결정이다.**
     *
     * 카카오는 `birthday` · `phone_number` 도 준다. 요청하면 받게 되고, 받으면
     * 저장하고 싶어진다. ADR-0004 가 성인 인증을 하지 않기로 한 이상 생년월일은 쓸 데가 없다.
     *
     * scope 는 설정이라 운영에서 바뀔 수 있다 — 그래서 **인가 URL 을 실제로 보고** 판정한다.
     */
    @ParameterizedTest
    @ValueSource(strings = ["kakao", "naver", "apple"])
    fun `RED31,32,34 - 금지된 scope 를 요청하지 않는다`(provider: String) {
        val location = authorize(provider).location().lowercase()

        assertThat(FORBIDDEN_SCOPES)
            .`as`("SPEC-08 §5.1 목록 밖을 요청한다: %s", location)
            .noneMatch { location.contains(it) }
    }

    /** RED 33 — 성인 인증 엔드포인트가 **없다** (ADR-0004 · SPEC-07 §2.5). */
    @ParameterizedTest
    @ValueSource(strings = ["/age-verify", "/adult", "/verify-age", "/birthdate"])
    fun `RED33 - 성인 인증 엔드포인트가 없다`(path: String) {
        assertThat(mvc.get("$AUTH$path").andReturn().response.status).isEqualTo(404)
    }

    /**
     * RED 34 — 저장된 컬럼이 SPEC-08 §5.1 목록 안이다.
     *
     * scope 를 안 받아도 컬럼이 생기면 언젠가 채워진다. `user` 테이블을 직접 본다.
     */
    @Test
    fun `RED34 - 수집한 항목이 SPEC-08 §5-1 목록 내다`() {
        val columns = jdbc.queryForList(
            "SELECT column_name FROM information_schema.columns WHERE table_name = 'user'",
            String::class.java,
        )

        assertAll(
            {
                assertThat(columns)
                    .containsExactlyInAnyOrder(
                        "id", "created_at", "updated_at",
                        "provider", "provider_uid", "display_name", "email",
                    )
            },
            {
                assertThat(columns)
                    .`as`("ADR-0004 · PRIN-D04 — 없는 것이 있는 것만큼 중요하다")
                    .noneMatch { it.contains("birth") || it.contains("phone") || it in setOf("lat", "lng") }
            },
        )
    }

    // ── RED 35~38 : 레이트 리밋 · 보안 ────────────────────────────────────

    /**
     * RED 35 — 콜백은 10rpm 이고 **장애 시에도 닫힌다** (SPEC-08 §6 · DECISIONS §1).
     *
     * 인증 경로라 무차별 시도를 막는 것이 목적이다. 카운터 저장소가 죽었다고 열어 주면
     * 하필 그때가 가장 시도하기 좋은 순간이 된다.
     *
     * 경로 판정도 함께 본다 — 정책이 있어도 라우트가 안 맞으면 아무것도 걸리지 않는다.
     */
    @Test
    fun `RED35 - callback 이 10rpm 으로 제한된다`() {
        val policy = kr.mut.common.security.ratelimit.RateLimitPolicy.AUTH_CALLBACK
        val request = org.springframework.mock.web.MockHttpServletRequest("GET", "$AUTH/kakao/callback")

        assertAll(
            { assertThat(policy.defaultLimit).`as`("SPEC-08 §6").isEqualTo(10) },
            { assertThat(policy.window.toMinutes()).isEqualTo(1) },
            {
                assertThat(policy.onStoreFailure)
                    .`as`("인증 경로는 장애 시에도 닫는다")
                    .isEqualTo(kr.mut.common.security.ratelimit.FailMode.CLOSED)
            },
            {
                assertThat(kr.mut.common.security.ratelimit.RateLimitRoutes.policyFor(request))
                    .`as`("콜백 경로가 이 정책에 걸린다")
                    .isEqualTo(policy)
            },
        )
    }

    /**
     * RED 36 — 오픈 리다이렉트를 막는다.
     *
     * `redirect_uri` 는 설정에서만 오고, `returnTo` 는 화이트리스트 밖이면 **조용히**
     * 기본값으로 간다. 거부하지 않는 이유: 로그인은 이미 성공했다.
     */
    @Test
    fun `RED36 - redirect 가 화이트리스트를 벗어나지 않는다`() {
        val session = MockHttpSession()
        authorize("kakao", session, returnTo = "https://evil.example.com/steal")
        val landing = callback("kakao", session, state = stateOf(session)).location()

        val boundary = MockHttpSession()
        authorize("kakao", boundary, returnTo = "$ALLOWED_RETURN.evil.example.com/steal")
        val tricky = callback("kakao", boundary, state = stateOf(boundary)).location()

        assertAll(
            { assertThat(landing).`as`("화이트리스트 밖은 기본값으로").isEqualTo(ALLOWED_RETURN) },
            { assertThat(tricky).`as`("접두사만 같은 도메인도 막는다").isEqualTo(ALLOWED_RETURN) },
            {
                // 제공자로 보내는 redirect_uri 는 요청이 정하지 못한다.
                assertThat(authorize("kakao").location())
                    .contains(URI.create(REDIRECT_BASE).host)
            },
        )
    }

    /**
     * RED 37 — **제공자 토큰을 저장하지 않는다** (DECISIONS §1).
     *
     * 스텁이 `refresh_token` 을 함께 준다. 우리 DB 어디에도 남지 않아야 한다 —
     * 갱신 토큰을 들고 있으면 우리 DB 가 유출될 때 **남의 계정까지 열린다.**
     */
    @Test
    fun `RED37 - provider 토큰이 저장되지 않는다`() {
        loginWith("kakao")

        val row = jdbc.queryForList("""SELECT * FROM "user"""").single()

        assertAll(
            {
                assertThat(row.values.filterNotNull().map { it.toString() })
                    .`as`("어느 컬럼에도 토큰이 없다")
                    .noneMatch { it.contains("stub-refresh") || it.contains("stub-access") }
            },
            {
                assertThat(row.keys)
                    .`as`("담을 자리 자체가 없다")
                    .noneMatch { it.contains("token") }
            },
        )
    }

    /**
     * RED 38 — 제공자 응답이 로그에 남지 않는다.
     *
     * 로그 캡처 대신 **예외 메시지**를 본다. 실패 경로에서 응답 본문을 메시지에 담으면
     * 그것이 그대로 `ERROR` 로그와 500 응답에 실린다 — 실제로 새는 경로가 거기다.
     */
    @Test
    fun `RED38 - 실패 메시지에 제공자 응답이 담기지 않는다`() {
        SocialProviderStub.profiles["kakao"] = """{ "kakao_account": { "email": "leak@example.com" } }"""

        val session = MockHttpSession()
        authorize("kakao", session)
        val body = callback("kakao", session, state = stateOf(session))
            .response.getContentAsString(Charsets.UTF_8)

        assertAll(
            { assertThat(body).doesNotContain("leak@example.com") },
            { assertThat(body).doesNotContain("kakao_account") },
        )
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────

    private fun authorize(
        provider: String,
        session: MockHttpSession = MockHttpSession(),
        returnTo: String? = null,
    ): MvcResult = mvc.get("$AUTH/$provider/authorize") {
        this.session = session
        returnTo?.let { param("returnTo", it) }
    }.andReturn()

    private fun callback(provider: String, session: MockHttpSession, state: String?) =
        mvc.get("$AUTH/$provider/callback") {
            this.session = session
            param("code", "stub-code")
            state?.let { param("state", it) }
        }.andReturn()

    /** 인가 → 콜백 한 번. 302 가 아니면 원인을 바로 보여 준다. */
    private fun loginWith(provider: String) {
        val status = attemptLogin(provider)
        check(status == 302) { "$provider 로그인이 실패했다 (status=$status)" }
    }

    private fun attemptLogin(provider: String): Int {
        val session = MockHttpSession()
        authorize(provider, session)
        return callback(provider, session, state = stateOf(session)).response.status
    }

    /**
     * 세션에 심긴 `state`. 속성 이름이 접두사 + state 라 거꾸로 뽑는다.
     *
     * 리다이렉트 URL 에서 뽑을 수도 있지만 세션에서 읽는 편이 낫다 —
     * **인가 URL 의 `state` 와 세션에 심긴 `state` 가 같은지**까지 함께 확인된다.
     * 둘이 어긋나면 콜백이 영영 통과하지 못하는데, URL 만 보면 그걸 못 잡는다.
     */
    private fun stateOf(session: MockHttpSession): String =
        session.attributeNames.toList()
            .filterIsInstance<String>()
            .singleOrNull { it.startsWith(STATE_PREFIX) }
            ?.removePrefix(STATE_PREFIX)
            ?: error(
                "세션에 state 가 없다. 심긴 속성: ${session.attributeNames.toList()}",
            )

    /** 발급 시각을 11분 전으로 바꾼다. `Pending` 이 private 이라 리플렉션으로 손댄다. */
    private fun expireState(session: MockHttpSession, state: String) {
        val key = STATE_PREFIX + state
        val pending = session.getAttribute(key)!!
        val field = pending.javaClass.getDeclaredField("issuedAt").apply { isAccessible = true }
        field.set(pending, Instant.now().minusSeconds(11 * 60))
    }

    private fun kakaoProfile(id: String, email: String?) = """
        { "id": $id, "kakao_account": { "profile": { "nickname": "카카오사용자" }
          ${if (email != null) ""","email":"$email"""" else ""} } }
    """

    private fun naverProfile(id: String, email: String?) = """
        { "resultcode": "00", "response": { "id": "$id", "nickname": "네이버사용자"
          ${if (email != null) ""","email":"$email"""" else ""} } }
    """

    private fun MvcResult.location(): String = response.getHeader("Location")
        ?: error("Location 헤더가 없다 (status=${response.status}): ${response.getContentAsString(Charsets.UTF_8)}")

    private fun String.param(name: String): String =
        substringAfter("$name=").substringBefore("&")

    private fun userCount() = count("""SELECT count(*) FROM "user"""")

    private fun count(sql: String) = jdbc.queryForObject(sql, Long::class.java)!!

    private fun userIdOf(provider: String) = one(provider, "id") as Long?

    private fun providerUidOf(provider: String) = one(provider, "provider_uid") as String?

    private fun displayNameOf(provider: String) = one(provider, "display_name") as String?

    private fun emailOf(provider: String) = one(provider, "email") as String?

    private fun one(provider: String, column: String): Any? = jdbc
        .queryForList("""SELECT $column FROM "user" WHERE provider = ?""", provider)
        .firstOrNull()?.get(column)

    companion object {
        private val AUTH = "${ApiPaths.BASE}/auth"
        private const val STATE_PREFIX = "mut.oauth.state."
        private const val REDIRECT_BASE = "https://api.mut.test"
        private const val ALLOWED_RETURN = "https://www.mut.test"

        /** SPEC-08 §5.1 이 수집하지 않기로 한 것들. ADR-0004 가 근거다. */
        private val FORBIDDEN_SCOPES = listOf(
            "birthday", "birthyear", "birthdate", "phone", "gender", "address", "shipping",
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
            registry.add("mut.verification.scheduled") { false }

            // 레이트 리밋을 끈다. 이 파일은 로그인 흐름을 여러 번 돌고, 콜백은 10rpm 이다 —
            // 켜 두면 뒤쪽 테스트가 429 로 죽고 원인이 멀어진다. 10rpm 자체는 RED 35 가 본다.
            registry.add("mut.rate-limit.enabled") { false }

            // 이슈 025 와 같은 이유 — `SessionRepositoryFilter` 가 요청 세션을 갈아끼운다.
            registry.add("spring.autoconfigure.exclude") {
                "org.springframework.boot.autoconfigure.session.SessionAutoConfiguration"
            }

            val stub = SocialProviderStub.baseUrl

            registry.add("mut.oauth.redirect-base") { REDIRECT_BASE }
            registry.add("mut.oauth.allowed-returns[0]") { ALLOWED_RETURN }

            registry.add("mut.oauth.kakao.client-id") { "kakao-test" }
            registry.add("mut.oauth.kakao.client-secret") { "kakao-secret" }
            registry.add("mut.oauth.kakao.authorize-uri") { "$stub/kakao/authorize" }
            registry.add("mut.oauth.kakao.token-uri") { "$stub/kakao/token" }
            registry.add("mut.oauth.kakao.user-info-uri") { "$stub/kakao/me" }
            registry.add("mut.oauth.kakao.scopes[0]") { "profile_nickname" }
            registry.add("mut.oauth.kakao.scopes[1]") { "account_email" }

            registry.add("mut.oauth.naver.client-id") { "naver-test" }
            registry.add("mut.oauth.naver.client-secret") { "naver-secret" }
            registry.add("mut.oauth.naver.authorize-uri") { "$stub/naver/authorize" }
            registry.add("mut.oauth.naver.token-uri") { "$stub/naver/token" }
            registry.add("mut.oauth.naver.user-info-uri") { "$stub/naver/me" }

            registry.add("mut.oauth.apple.client-id") { "kr.mut.test" }
            registry.add("mut.oauth.apple.team-id") { "TEAM123456" }
            registry.add("mut.oauth.apple.key-id") { "KEY1234567" }
            registry.add("mut.oauth.apple.private-key-pem") { SocialProviderStub.clientSecretKeyPem }
            registry.add("mut.oauth.apple.authorize-uri") { "$stub/apple/authorize" }
            registry.add("mut.oauth.apple.token-uri") { "$stub/apple/token" }
            registry.add("mut.oauth.apple.jwks-uri") { "$stub/apple/keys" }
        }
    }
}
