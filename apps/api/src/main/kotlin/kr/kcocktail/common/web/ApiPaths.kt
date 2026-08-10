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

    // 공개 조회인가. 어드민이 아닌 /api/v1 하위 전부다.
    // (KDoc 으로 쓰지 않는다 — Kotlin 블록 주석은 중첩돼서 본문의 슬래시+별표가 주석을 연다)
    fun isPublicApi(path: String) = path.startsWith("$BASE/") && !path.startsWith("$ADMIN/")
}
