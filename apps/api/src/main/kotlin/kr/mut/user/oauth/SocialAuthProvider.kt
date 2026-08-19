package kr.mut.user.oauth

import kr.mut.user.domain.AuthProvider

/**
 * 소셜 로그인 어댑터 (ISSUE-030 · `PRIN-T06` · SPEC-05 §7).
 *
 * ## 벤더는 바뀐다
 *
 * `PRIN-T06` 이 외부 연동을 인터페이스 뒤에 두라고 한 이유가 이것이다.
 * 카카오 응답 JSON 이 `kakao_account.profile.nickname` 인지 `properties.nickname` 인지는
 * **어댑터 안에서 끝나야 한다.** 그 모양이 `user` 도메인에 새면, 카카오가 필드를 옮기는 날
 * 도메인 코드를 고치게 된다.
 *
 * 도메인이 아는 것은 [SocialProfile] 셋뿐이다 — 식별자 · 표시 이름 · 이메일(없을 수 있다).
 *
 * ## PKCE 가 선택이 아니다
 *
 * 두 메서드가 모두 PKCE 값을 받는다. **순수 Authorization Code 경로를 만들지 않는다** (RED 4) —
 * 인터페이스에 그 자리가 없으면 우회할 방법도 없다 (`PRIN-T05` · 이슈 025 와 같은 방식).
 */
interface SocialAuthProvider {

    val provider: AuthProvider

    /**
     * 사용자를 보낼 인가 URL.
     *
     * @param state 1회용, 10분 (SPEC-08 §4.2). CSRF 방어이자 콜백 대조 키다
     * @param codeChallenge PKCE `S256` 챌린지
     */
    fun authorizeUrl(state: String, codeChallenge: String): String

    /**
     * 인가 코드를 프로필로 바꾼다.
     *
     * **제공자 토큰을 돌려주지 않는다** (RED 37 · DECISIONS §1). 로그인 이후의 세션은 우리 것이고,
     * 갱신 토큰을 들고 있으면 우리 DB 가 유출될 때 **남의 계정까지 열린다.**
     * 반환 타입에 그 자리가 없는 것이 그 결정의 구현이다.
     */
    fun exchange(code: String, codeVerifier: String): SocialProfile
}

/**
 * 도메인이 아는 전부.
 *
 * @param providerUid 제공자 안에서의 식별자. `(provider, providerUid)` 가 동일인 판정이다
 * @param displayName 제공자가 주지 않을 수 있다. 없으면 [SocialLoginService] 가 기본값을 만든다
 * @param email **없을 수 있다.** 애플 비공개 릴레이가 그렇고, 카카오도 동의를 안 받으면 안 준다
 */
data class SocialProfile(
    val providerUid: String,
    val displayName: String?,
    val email: String?,
)
