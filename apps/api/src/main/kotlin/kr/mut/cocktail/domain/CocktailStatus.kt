package kr.mut.cocktail.domain

/**
 * 발행 상태 (SPEC-06 §3.1).
 *
 * `PRIN-D05` — **삭제가 아니라 상태 전이다.** `cocktail` 은 `REVOKE DELETE` 대상이라
 * 물리 삭제 자체가 불가능하다.
 *
 * 상태 전이 규칙과 감사 로그는 이슈 014 다. 여기서는 값만 세운다.
 */
enum class CocktailStatus(val slug: String) {
    DRAFT("draft"),
    PUBLISHED("published"),

    /** 내린 것. 공개 API 에서는 `draft` 와 마찬가지로 **404** 다 (SPEC-07 §5). */
    ARCHIVED("archived"),
    ;

    /** 공개 API 에 보이는가. `PermissionMatrix` 의 `VIEW_PUBLISHED` 와 짝이다. */
    val isPublic: Boolean get() = this == PUBLISHED

    companion object {
        fun ofSlug(slug: String): CocktailStatus =
            entries.firstOrNull { it.slug == slug } ?: error("알 수 없는 상태: $slug")
    }
}
