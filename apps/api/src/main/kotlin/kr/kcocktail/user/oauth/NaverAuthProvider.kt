package kr.kcocktail.user.oauth

import com.fasterxml.jackson.databind.JsonNode
import kr.kcocktail.user.domain.AuthProvider
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient

/**
 * 네이버 로그인 (`FR-USER-001` · `PRIN-T06`).
 *
 * ## 응답이 한 겹 더 싸여 있다
 *
 * 네이버는 프로필을 `{ "resultcode": "00", "message": "success", "response": { … } }` 로 준다.
 * 카카오와 모양이 다르다 — **그 차이가 여기서 끝나는 것이 `PRIN-T06` 의 전부**다.
 *
 * ## `id` 가 문자열이다
 *
 * 카카오는 숫자, 네이버는 문자열이다. `SocialProfile.providerUid` 를 `String` 으로 둔 이유이고,
 * `user.provider_uid` 가 `VARCHAR(120)` 인 이유이기도 하다 (SPEC-06 §3.5).
 */
@Component
class NaverAuthProvider(
    private val properties: OAuthProperties,
    private val rest: RestClient,
) : SocialAuthProvider {

    override val provider = AuthProvider.NAVER

    override fun authorizeUrl(state: String, codeChallenge: String): String {
        val client = properties.naver
        val base = client.authorizeUri.ifBlank { DEFAULT_AUTHORIZE }

        return base + "?" + mapOf(
            "response_type" to "code",
            "client_id" to client.clientId,
            "redirect_uri" to properties.redirectUri(provider),
            "state" to state,
            "code_challenge" to codeChallenge,
            "code_challenge_method" to Pkce.METHOD,
        ).toQuery()
    }

    override fun exchange(code: String, codeVerifier: String): SocialProfile {
        val client = properties.naver

        val token = rest.post()
            .uri(client.tokenUri.ifBlank { DEFAULT_TOKEN })
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                LinkedMultiValueMap<String, String>().apply {
                    add("grant_type", "authorization_code")
                    add("client_id", client.clientId)
                    add("client_secret", client.clientSecret)
                    add("redirect_uri", properties.redirectUri(provider))
                    add("code", code)
                    add("state", "-") // 네이버는 이 자리를 요구한다. 검증은 우리가 이미 했다
                    add("code_verifier", codeVerifier)
                },
            )
            .retrieve()
            .body(JsonNode::class.java)
            ?: throw OAuthExchangeException(provider, "토큰 응답이 비었다")

        val accessToken = token.path("access_token").asText(null)
            ?: throw OAuthExchangeException(provider, "access_token 이 없다")

        val body = rest.get()
            .uri(client.userInfoUri.ifBlank { DEFAULT_USERINFO })
            .header("Authorization", "Bearer $accessToken")
            .retrieve()
            .body(JsonNode::class.java)
            ?: throw OAuthExchangeException(provider, "프로필 응답이 비었다")

        // 네이버는 HTTP 200 에 실패를 담아 보낸다. resultcode 를 안 보면 빈 프로필로 진행한다.
        if (body.path("resultcode").asText("") !in setOf("", OK)) {
            throw OAuthExchangeException(provider, "프로필 조회 실패 (resultcode)")
        }

        val response = body.path("response")

        return SocialProfile(
            providerUid = response.path("id").asText(null)
                ?: throw OAuthExchangeException(provider, "id 가 없다"),
            displayName = response.path("nickname").asText(null)
                ?: response.path("name").asText(null),
            email = response.path("email").asText(null),
        )
    }

    companion object {
        private const val OK = "00"
        private const val DEFAULT_AUTHORIZE = "https://nid.naver.com/oauth2.0/authorize"
        private const val DEFAULT_TOKEN = "https://nid.naver.com/oauth2.0/token"
        private const val DEFAULT_USERINFO = "https://openapi.naver.com/v1/nid/me"
    }
}
