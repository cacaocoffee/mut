package kr.kcocktail.common.web

/**
 * SPEC-07 §1.1 — 베이스는 `/api/v1`, 경로는 `kebab-case` **복수형**.
 *
 * > 경로는 복수형(`/cocktails`)이지만 **테이블은 단수형**(`cocktail`)이다 (SPEC-06 §1.1).
 * > 헷갈리기 쉬운 지점이라 양쪽 규칙을 각각의 테스트가 지킨다.
 */
object ApiPaths {
    const val BASE = "/api/v1"

    /** 어드민은 인증 뒤에 있고 **캐시하지 않는다** — 발행 전 데이터가 중간 캐시에 남으면 안 된다. */
    const val ADMIN = "$BASE/admin"

    /** 본인 리소스. 북마크·컬렉션·프로필 (SPEC-07 §2.5). */
    const val ME = "$BASE/me"

    /** 로그인 흐름. CSRF 토큰·인가·콜백 (SPEC-07 §2.5). */
    const val AUTH = "$BASE/auth"

    // 공개 API 인가. 어드민이 아닌 /api/v1 하위 전부다. 레이트 리밋 판정이 이걸 쓴다.
    // (KDoc 으로 쓰지 않는다 — Kotlin 블록 주석은 중첩돼서 본문의 슬래시+별표가 주석을 연다)
    fun isPublicApi(path: String) = path.startsWith("$BASE/") && !path.startsWith("$ADMIN/")

    // 공유 캐시에 올려도 되는가. isPublicApi 와 **다른 질문**이라 함수를 나눈다.
    //
    // 이슈 031 이 이걸 드러냈다. /me/bookmarks 가 isPublicApi 를 통과해
    // `Cache-Control: public, max-age=60` 과 ETag 를 달고 나갔다 —
    // 중간 캐시가 한 사람의 북마크를 다른 사람에게 줄 수 있다는 뜻이다.
    //
    // /auth 도 같은 이유로 뺀다. CSRF 토큰이 60초 캐시되면 토큰이 세션 바인딩인 의미가 없다.
    //
    // 인증이 필요한 경로를 열거하지 않고 **접두사 셋만 본다**. 열거하면 새 개인 경로가
    // 목록에서 빠진 채 들어오고, 그때 새는 것은 조용하다.
    fun isPubliclyCacheable(path: String) =
        isPublicApi(path) && !path.startsWith("$ME/") && path != ME &&
            !path.startsWith("$AUTH/") && path != AUTH
}
