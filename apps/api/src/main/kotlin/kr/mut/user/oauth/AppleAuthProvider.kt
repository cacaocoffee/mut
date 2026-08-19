package kr.mut.user.oauth

import com.fasterxml.jackson.databind.JsonNode
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import kr.mut.user.domain.AuthProvider
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import java.security.KeyFactory
import java.security.interfaces.ECPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Date

/**
 * 애플 로그인 (`FR-USER-001` · SPEC-08 §4.2 · `PRIN-T06`).
 *
 * ## 셋 중 유일하게 모양이 다르다
 *
 * | | 카카오 · 네이버 | 애플 |
 * |---|---|---|
 * | `client_secret` | 고정 문자열 | **ES256 서명 JWT**, 최대 6개월 |
 * | 프로필 | userinfo 엔드포인트 | **`id_token` 클레임** |
 * | 이메일 | 동의하면 준다 | **비공개 릴레이** 또는 없음 |
 *
 * 이 차이가 전부 여기서 끝난다. `user` 도메인은 [SocialProfile] 셋만 본다.
 *
 * ## 서명을 검증하지 않으면 로그인이 아니다
 *
 * `id_token` 은 그냥 base64 라 **누구나 만들 수 있다.** 파싱만 하고 `sub` 를 믿으면
 * 아무 계정으로나 들어올 수 있다 — 애플 JWKS 로 서명·발급자·수신자를 확인한다.
 *
 * ## 이메일이 두 번 다시 안 온다
 *
 * 애플은 **최초 인가에서만** 이름과 이메일을 준다. 재로그인 때는 `id_token` 에
 * `email` 이 있을 수도, 없을 수도 있다. 그래서 이메일을 식별에 쓰지 않는 것이
 * 편의가 아니라 **필수**다 (SPEC-08 §4.2 — `(provider, provider_uid)` 로 판정).
 */
@Component
class AppleAuthProvider(
    private val properties: OAuthProperties,
    private val rest: RestClient,
) : SocialAuthProvider {

    override val provider = AuthProvider.APPLE

    /** JWKS 를 매 로그인마다 받지 않는다. 디코더가 키를 캐시한다. */
    private val decoder: JwtDecoder by lazy {
        NimbusJwtDecoder
            .withJwkSetUri(properties.apple.jwksUri.ifBlank { DEFAULT_JWKS })
            .jwsAlgorithm(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256)
            .build()
    }

    override fun authorizeUrl(state: String, codeChallenge: String): String {
        val client = properties.apple
        val base = client.authorizeUri.ifBlank { DEFAULT_AUTHORIZE }

        return base + "?" + mapOf(
            "response_type" to "code",
            "client_id" to client.clientId,
            "redirect_uri" to properties.redirectUri(provider),
            "state" to state,
            "code_challenge" to codeChallenge,
            "code_challenge_method" to Pkce.METHOD,
            "scope" to client.scopes.joinToString(" "),
            // scope 를 요청하면 애플이 콜백을 form_post 로 보낸다. 그건 이슈 밖이라
            // scope 를 비워 두는 것이 기본값이다 (SPEC-08 §5.1 — 이메일은 선택).
            "response_mode" to if (client.scopes.isEmpty()) "" else "form_post",
        ).toQuery()
    }

    override fun exchange(code: String, codeVerifier: String): SocialProfile {
        val client = properties.apple

        val token = rest.post()
            .uri(client.tokenUri.ifBlank { DEFAULT_TOKEN })
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                LinkedMultiValueMap<String, String>().apply {
                    add("grant_type", "authorization_code")
                    add("client_id", client.clientId)
                    add("client_secret", clientSecret())
                    add("redirect_uri", properties.redirectUri(provider))
                    add("code", code)
                    add("code_verifier", codeVerifier)
                },
            )
            .retrieve()
            .body(JsonNode::class.java)
            ?: throw OAuthExchangeException(provider, "토큰 응답이 비었다")

        val idToken = token.path("id_token").asText(null)
            ?: throw OAuthExchangeException(provider, "id_token 이 없다")

        val jwt = try {
            decoder.decode(idToken)
        } catch (e: JwtException) {
            // 원문을 메시지에 담지 않는다 — 토큰에 이메일이 들어 있다 (RED 38).
            throw OAuthExchangeException(provider, "id_token 검증 실패")
        }

        if (jwt.issuer?.toString() != ISSUER) throw OAuthExchangeException(provider, "issuer 불일치")
        if (client.clientId !in jwt.audience) throw OAuthExchangeException(provider, "audience 불일치")

        return SocialProfile(
            providerUid = jwt.subject ?: throw OAuthExchangeException(provider, "sub 가 없다"),
            // 애플은 이름을 id_token 에 담지 않는다. 최초 인가의 form_post 로만 오고,
            // 그것도 사용자가 감출 수 있다 — 표시 이름은 우리가 만든다 (RED 27).
            displayName = null,
            // 비공개 릴레이면 `@privaterelay.appleid.com` 이고, 아예 없을 수도 있다.
            email = jwt.getClaimAsString("email"),
        )
    }

    /**
     * `client_secret` 을 만든다 (애플 문서 — Generate and validate tokens).
     *
     * 매 요청 새로 만든다. 캐시하면 만료 관리가 하나 더 생기는데, 서명 한 번이
     * 네트워크 왕복보다 훨씬 싸다 — **관리할 상태를 늘리지 않는 편이 낫다.**
     */
    private fun clientSecret(): String {
        val client = properties.apple
        val now = Instant.now()

        val claims = JWTClaimsSet.Builder()
            .issuer(client.teamId)
            .subject(client.clientId)
            .audience(ISSUER)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(SECRET_TTL)))
            .build()

        val header = JWSHeader.Builder(JWSAlgorithm.ES256)
            .keyID(client.keyId)
            .type(JOSEObjectType.JWT)
            .build()

        return SignedJWT(header, claims)
            .apply { sign(ECDSASigner(privateKey())) }
            .serialize()
    }

    private fun privateKey(): ECPrivateKey {
        val pem = properties.apple.privateKeyPem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace(Regex("\\s"), "")

        if (pem.isBlank()) throw OAuthExchangeException(provider, "개인키가 설정되지 않았다")

        val spec = PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem))
        return KeyFactory.getInstance("EC").generatePrivate(spec) as ECPrivateKey
    }

    companion object {
        private const val ISSUER = "https://appleid.apple.com"
        private const val DEFAULT_AUTHORIZE = "$ISSUER/auth/authorize"
        private const val DEFAULT_TOKEN = "$ISSUER/auth/token"
        private const val DEFAULT_JWKS = "$ISSUER/auth/keys"

        /** 애플 상한은 6개월. 짧게 잡는다 — 이 값이 유출돼도 창이 좁다. */
        private val SECRET_TTL: Duration = Duration.ofMinutes(5)
    }
}
