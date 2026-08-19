package kr.mut.user.oauth

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

/**
 * 카카오 · 네이버 · 애플을 흉내 내는 스텁 (ISSUE-030).
 *
 * ## 왜 어댑터를 가짜로 바꾸지 않는가
 *
 * `SocialAuthProvider` 구현을 테스트용으로 갈아 끼우면 RED 12~14("kakao 로그인이 동작한다")가
 * **스텁이 동작하는지**를 검증하게 된다. 카카오 응답의 `kakao_account.profile.nickname` 을
 * 실제로 읽는지, 네이버의 `response` 한 겹을 벗기는지는 확인되지 않는다 —
 * 그 파싱이 어댑터의 존재 이유인데.
 *
 * 그래서 **HTTP 층에서 흉내 낸다.** 어댑터 코드는 그대로 돌고, 제공자만 우리 것이다.
 * 제공자 URL 이 설정인 이유가 이것이다.
 *
 * ## JDK `HttpServer` 를 쓴다
 *
 * WireMock 같은 것을 들이지 않는다. 필요한 것이 엔드포인트 여섯 개라 의존을 늘릴 값이 없다.
 */
object SocialProviderStub {

    /** 마지막으로 받은 토큰 요청 폼. PKCE 검증자가 실제로 갔는지 보려고 남긴다. */
    val lastTokenForm = ConcurrentHashMap<String, Map<String, String>>()

    /** 프로필 응답을 테스트가 바꿔 끼운다 — 이메일 없는 경우 등. */
    val profiles = ConcurrentHashMap<String, String>()

    /** 애플 `id_token` 에 담을 클레임. */
    @Volatile var appleSub: String = "apple-sub-1"
    @Volatile var appleEmail: String? = "relay@privaterelay.appleid.com"
    @Volatile var appleAudience: String = "kr.mut.test"
    @Volatile var appleIssuer: String = "https://appleid.apple.com"

    /** `id_token` 서명 키. 애플과 같이 RS256 이다. */
    private val appleSigningKey: RSAKey =
        RSAKeyGenerator(2048).keyID("apple-test-key").generate()

    /** `client_secret` 서명에 쓸 EC 키. 애플 콘솔이 주는 `.p8` 자리다. */
    val clientSecretKeyPem: String by lazy {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(pair.private.encoded)
            .let { "-----BEGIN PRIVATE KEY-----\n$it\n-----END PRIVATE KEY-----" }
    }

    private val server: HttpServer by lazy {
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/kakao/token") { it.token("kakao") }
            createContext("/kakao/me") { it.json(profiles["kakao"] ?: KAKAO_PROFILE) }

            createContext("/naver/token") { it.token("naver") }
            createContext("/naver/me") { it.json(profiles["naver"] ?: NAVER_PROFILE) }

            createContext("/apple/token") { exchange ->
                exchange.readForm().also { lastTokenForm["apple"] = it }
                exchange.json("""{"id_token":"${appleIdToken()}","refresh_token":"apple-refresh"}""")
            }
            createContext("/apple/keys") {
                it.json(JWKSet(appleSigningKey).toPublicJWKSet().toString())
            }

            executor = null
            start()
        }
    }

    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}"

    /** 테스트 사이에 상태를 지운다 — 앞 테스트가 심은 프로필이 남으면 원인이 멀어진다. */
    fun reset() {
        lastTokenForm.clear()
        profiles.clear()
        appleSub = "apple-sub-1"
        appleEmail = "relay@privaterelay.appleid.com"
        appleAudience = "kr.mut.test"
        appleIssuer = "https://appleid.apple.com"
    }

    private fun appleIdToken(): String {
        val claims = JWTClaimsSet.Builder()
            .issuer(appleIssuer)
            .subject(appleSub)
            .audience(appleAudience)
            .issueTime(Date.from(Instant.now()))
            .expirationTime(Date.from(Instant.now().plusSeconds(600)))
            .apply { appleEmail?.let { claim("email", it) } }
            .build()

        return SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.RS256).keyID(appleSigningKey.keyID).build(),
            claims,
        ).apply { sign(RSASSASigner(appleSigningKey)) }.serialize()
    }

    private fun HttpExchange.token(provider: String) {
        lastTokenForm[provider] = readForm()
        json("""{"access_token":"stub-access-$provider","refresh_token":"stub-refresh-$provider"}""")
    }

    private fun HttpExchange.readForm(): Map<String, String> =
        requestBody.readBytes().toString(StandardCharsets.UTF_8)
            .split("&")
            .filter { it.contains("=") }
            .associate { pair ->
                val (k, v) = pair.split("=", limit = 2)
                URLDecoder.decode(k, StandardCharsets.UTF_8) to URLDecoder.decode(v, StandardCharsets.UTF_8)
            }

    private fun HttpExchange.json(body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        sendResponseHeaders(200, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    /** 카카오의 실제 모양 — 프로필이 `kakao_account` 아래 한 겹 더 있다. */
    const val KAKAO_PROFILE = """
        {
          "id": 1234567890,
          "properties": { "nickname": "구버전닉네임" },
          "kakao_account": {
            "profile": { "nickname": "카카오사용자" },
            "email": "kakao@example.com"
          }
        }
    """

    /** 네이버의 실제 모양 — `response` 한 겹과 `resultcode`. */
    const val NAVER_PROFILE = """
        {
          "resultcode": "00",
          "message": "success",
          "response": { "id": "naver-uid-1", "nickname": "네이버사용자", "email": "naver@example.com" }
        }
    """
}
