package kr.mut.user.oauth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * PKCE (RFC 7636) — SPEC-08 §4.2.
 *
 * ## 왜 서버가 하는데도 PKCE 인가
 *
 * PKCE 는 원래 공개 클라이언트(앱·SPA)를 위한 것이고, 우리는 클라이언트 시크릿을 쥔
 * 서버다. 그래도 붙이는 이유가 둘이다.
 *
 * 하나, **인가 코드 가로채기**를 막는다. 코드는 리다이렉트로 브라우저를 거쳐 오고,
 * 그 경로에는 우리가 통제하지 못하는 것들이 있다 — 확장 프로그램, 프록시, 로그.
 * 코드만으로는 교환이 안 되게 만들면 그 구간이 값을 잃는다.
 *
 * 둘, **스펙이 그렇게 정했다** (SPEC-08 §4.2 "Authorization Code + PKCE").
 * 나중에 앱이 붙을 때 흐름을 두 벌로 만들지 않아도 된다.
 *
 * `S256` 만 쓴다. `plain` 은 챌린지가 곧 검증자라 아무것도 막지 못한다 —
 * 지원하지 않는 것이 아니라 **존재하지 않는다** (RED 4).
 */
object Pkce {

    const val METHOD = "S256"

    private val random = SecureRandom()

    /** RFC 7636 §4.1 — 43~128자. 32바이트 엔트로피면 base64url 로 43자다. */
    fun newVerifier(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return ENCODER.encodeToString(bytes)
    }

    /** `S256` — `BASE64URL(SHA256(verifier))`. 패딩 없이. */
    fun challengeOf(verifier: String): String =
        ENCODER.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
        )

    /** `state` 도 같은 엔트로피로 만든다 — 추측 가능한 state 는 CSRF 방어가 아니다. */
    fun newState(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return ENCODER.encodeToString(bytes)
    }

    private val ENCODER: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
}
