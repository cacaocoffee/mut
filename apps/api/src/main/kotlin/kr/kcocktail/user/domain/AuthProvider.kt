package kr.kcocktail.user.domain

/**
 * SPEC-06 §3.5 — 소셜 로그인 제공자 3종.
 *
 * **갱신 토큰을 저장하지 않는다** (DECISIONS §1). 로그인 이후의 세션은 우리 것이고,
 * 제공자 토큰을 들고 있으면 유출 시 남의 계정까지 열린다.
 */
enum class AuthProvider(val code: String) {
    KAKAO("kakao"),
    NAVER("naver"),

    /** 비공개 릴레이를 쓰면 이메일이 없다. `User.email` 이 NULL 허용인 이유다 (SPEC-08 §4.2). */
    APPLE("apple"),
    ;

    companion object {
        fun ofCode(code: String): AuthProvider =
            entries.firstOrNull { it.code == code } ?: error("알 수 없는 제공자: $code")
    }
}
