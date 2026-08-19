package kr.mut.user.oauth

import kr.mut.user.domain.AuthProvider
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 소셜 로그인 설정 (SPEC-08 §4.2 · §5.1).
 *
 * ```yaml
 * mut:
 *   oauth:
 *     redirect-base: https://api.example.kr
 *     allowed-returns: [ "https://www.example.kr" ]
 *     kakao:
 *       client-id: ${KAKAO_CLIENT_ID:}
 *       client-secret: ${KAKAO_CLIENT_SECRET:}
 * ```
 *
 * ## 엔드포인트 URL 도 설정이다
 *
 * 상수로 박으면 **테스트가 제공자를 실제로 부르게 된다.** 그러면 CI 가 카카오 장애에
 * 따라 빨개지고, 어댑터의 파싱 코드는 정작 검증되지 않는다.
 * 기본값은 실제 주소라 운영에서는 설정 없이 동작한다.
 *
 * ## 시크릿에 기본값이 없다
 *
 * 빠뜨린 채로 기동하면 로그인 시도 때 제공자가 401 을 주고, 사용자에게는
 * "로그인 실패" 로만 보인다. 원인이 멀다 — [isConfigured] 로 미리 거른다.
 */
@ConfigurationProperties(prefix = "mut.oauth")
data class OAuthProperties(

    /**
     * 콜백 URL 의 앞부분. 제공자 콘솔에 등록한 값과 **글자 단위로 같아야** 한다.
     *
     * 제공자가 `redirect_uri` 완전 일치를 요구하므로, 이 값이 오픈 리다이렉트의
     * 1차 방어선이다 (RED 36) — 우리가 보낸 주소로만 코드가 돌아온다.
     */
    val redirectBase: String = "http://localhost:8080",

    /**
     * 로그인 후 되돌려 보낼 수 있는 프론트 주소 **화이트리스트** (RED 36).
     *
     * `?returnTo=` 를 그대로 믿고 리다이렉트하면 오픈 리다이렉트다 —
     * 피싱 사이트가 우리 도메인 링크로 사람을 넘긴다.
     * 목록에 없으면 [defaultReturn] 으로 간다. **거부가 아니라 무시**다:
     * 로그인은 이미 성공했는데 그것 때문에 에러를 보여 줄 이유가 없다.
     */
    val allowedReturns: List<String> = emptyList(),

    val kakao: Client = Client(),
    val naver: Client = Client(),
    val apple: AppleClient = AppleClient(),
) {

    val defaultReturn: String get() = allowedReturns.firstOrNull() ?: "/"

    fun clientOf(provider: AuthProvider): ClientConfig = when (provider) {
        AuthProvider.KAKAO -> kakao
        AuthProvider.NAVER -> naver
        AuthProvider.APPLE -> apple
    }

    fun redirectUri(provider: AuthProvider): String =
        "${redirectBase.trimEnd('/')}/api/v1/auth/${provider.code}/callback"

    /**
     * `returnTo` 가 화이트리스트에 있는가.
     *
     * 접두사 비교를 하되 **경계를 본다** — `https://evil.com` 이 `https://evil.com.attacker.kr`
     * 을 통과시키면 안 된다. 같거나, 뒤에 `/` 가 붙은 것만 인정한다.
     */
    fun sanitizeReturn(raw: String?): String {
        val candidate = raw?.takeIf { it.isNotBlank() } ?: return defaultReturn
        val ok = allowedReturns.any { candidate == it || candidate.startsWith("${it.trimEnd('/')}/") }
        return if (ok) candidate else defaultReturn
    }

    /**
     * 세 제공자가 공통으로 갖는 것.
     *
     * 상속이 아니라 인터페이스로 나눈 이유: `@ConfigurationProperties` 의 생성자 바인딩은
     * 클래스 계층에서 잘 깨진다 — 부모 생성자로 값을 넘기는 형태를 못 읽는다.
     * 어차피 애플은 `client_secret` 부터 성격이 달라 "공통 필드를 물려받는" 관계도 아니다.
     */
    interface ClientConfig {
        val clientId: String
        val authorizeUri: String
        val tokenUri: String

        /**
         * OAuth scope. **SPEC-08 §5.1 목록 안이어야 한다** (RED 31~34).
         *
         * `birthday` · `phone_number` 를 넣지 않는다 — **요청하면 받게 되고,
         * 받으면 저장하고 싶어진다.** ADR-0004 가 성인 인증을 하지 않기로 한 이상
         * 생년월일은 쓸 데가 없다.
         */
        val scopes: List<String>

        val isConfigured: Boolean get() = clientId.isNotBlank()
    }

    /** 카카오 · 네이버. 고정 `client_secret` 과 userinfo 엔드포인트를 쓴다. */
    data class Client(
        override val clientId: String = "",
        val clientSecret: String = "",

        /** 비우면 제공자 기본값. 테스트가 스텁 서버를 가리키게 하려고 열어 둔다. */
        override val authorizeUri: String = "",
        override val tokenUri: String = "",
        val userInfoUri: String = "",

        override val scopes: List<String> = emptyList(),
    ) : ClientConfig

    /**
     * 애플만 다르다 — `client_secret` 이 고정 문자열이 아니라 **ES256 으로 서명한 JWT** 이고,
     * 프로필을 userinfo 가 아니라 `id_token` 클레임에서 얻는다.
     */
    data class AppleClient(
        override val clientId: String = "",
        override val authorizeUri: String = "",
        override val tokenUri: String = "",
        override val scopes: List<String> = emptyList(),

        val teamId: String = "",
        val keyId: String = "",

        /** PKCS#8 PEM. 환경변수로 넣는다 — 저장소에 들어가면 그 순간 폐기 대상이다. */
        val privateKeyPem: String = "",

        /** `id_token` 서명 검증용 JWKS. 검증을 건너뛰면 누구나 로그인할 수 있다. */
        val jwksUri: String = "",
    ) : ClientConfig
}
