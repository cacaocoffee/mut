package kr.kcocktail.user.oauth

import com.fasterxml.jackson.databind.JsonNode
import kr.kcocktail.user.domain.AuthProvider
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 카카오 로그인 (`FR-USER-001` · `PRIN-T06`).
 *
 * ## scope 가 셋뿐이다
 *
 * `profile_nickname` · `account_email` 만 요청한다 (설정 기본값).
 * 카카오는 `birthday` · `phone_number` 도 준다 — **요청하지 않는 것이 결정이다**
 * (ADR-0004 · SPEC-08 §5.1). 요청하면 받게 되고, 받으면 저장하고 싶어진다.
 *
 * ## 이메일이 없을 수 있다
 *
 * `account_email` 은 **선택 동의** 항목이라 사용자가 거부하면 안 온다.
 * 이메일을 필수로 만들면 그 사람은 가입 자체를 못 한다 (RED 21~23).
 */
@Component
class KakaoAuthProvider(
    private val properties: OAuthProperties,
    private val rest: RestClient,
) : SocialAuthProvider {

    override val provider = AuthProvider.KAKAO

    override fun authorizeUrl(state: String, codeChallenge: String): String {
        val client = properties.kakao
        val base = client.authorizeUri.ifBlank { DEFAULT_AUTHORIZE }

        return base + "?" + mapOf(
            "response_type" to "code",
            "client_id" to client.clientId,
            "redirect_uri" to properties.redirectUri(provider),
            "state" to state,
            "code_challenge" to codeChallenge,
            "code_challenge_method" to Pkce.METHOD,
            "scope" to client.scopes.joinToString(" "),
        ).toQuery()
    }

    override fun exchange(code: String, codeVerifier: String): SocialProfile {
        val client = properties.kakao

        val token = rest.post()
            .uri(client.tokenUri.ifBlank { DEFAULT_TOKEN })
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                LinkedMultiValueMap<String, String>().apply {
                    add("grant_type", "authorization_code")
                    add("client_id", client.clientId)
                    client.clientSecret.takeIf { it.isNotBlank() }?.let { add("client_secret", it) }
                    add("redirect_uri", properties.redirectUri(provider))
                    add("code", code)
                    add("code_verifier", codeVerifier) // PKCE — 없으면 카카오가 거부한다
                },
            )
            .retrieve()
            .body(JsonNode::class.java)
            ?: throw OAuthExchangeException(provider, "토큰 응답이 비었다")

        val accessToken = token.path("access_token").asText(null)
            ?: throw OAuthExchangeException(provider, "access_token 이 없다")

        val me = rest.get()
            .uri(client.userInfoUri.ifBlank { DEFAULT_USERINFO })
            .header("Authorization", "Bearer $accessToken")
            .retrieve()
            .body(JsonNode::class.java)
            ?: throw OAuthExchangeException(provider, "프로필 응답이 비었다")

        // 카카오의 두 자리 — `properties.nickname`(구) 과 `kakao_account.profile.nickname`(신).
        // 이 분기가 어댑터 밖으로 나가면 카카오가 필드를 옮기는 날 도메인을 고치게 된다.
        val account = me.path("kakao_account")
        val nickname = account.path("profile").path("nickname").asText(null)
            ?: me.path("properties").path("nickname").asText(null)

        return SocialProfile(
            providerUid = me.path("id").asText(null)
                ?: throw OAuthExchangeException(provider, "id 가 없다"),
            displayName = nickname,
            // 동의를 안 했으면 `email` 자체가 없거나 `email_needs_agreement` 가 true 다.
            email = account.path("email").asText(null),
        )
    }

    companion object {
        private const val DEFAULT_AUTHORIZE = "https://kauth.kakao.com/oauth/authorize"
        private const val DEFAULT_TOKEN = "https://kauth.kakao.com/oauth/token"
        private const val DEFAULT_USERINFO = "https://kapi.kakao.com/v2/user/me"
    }
}

/** 인가 코드 교환 실패. 제공자 응답 본문을 메시지에 담지 않는다 (RED 38 — 개인정보). */
class OAuthExchangeException(provider: AuthProvider, reason: String) :
    RuntimeException("${provider.code} 로그인에 실패했습니다 ($reason)")

internal fun Map<String, String>.toQuery(): String =
    filterValues { it.isNotBlank() }
        .map { (k, v) -> "$k=" + URLEncoder.encode(v, StandardCharsets.UTF_8) }
        .joinToString("&")
