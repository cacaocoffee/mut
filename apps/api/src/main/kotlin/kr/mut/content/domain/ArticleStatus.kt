package kr.mut.content.domain

/**
 * 아티클 상태 (ADR-0011). 칵테일과 같은 세 값·같은 전이지만, 발행에 게이트가 없다 —
 * 아티클은 존재하면 발행할 수 있다. 칵테일의 6종 발행 게이트 같은 조건이 없다.
 *
 * ```
 *    draft ──▶ published ──▶ archived
 *      │  ▲        │            │
 *      │  └────────┴────────────┘
 *      └──── 삭제(초안 버리기) ──▶ (archived)
 * ```
 */
enum class ArticleStatus(val slug: String) {
    DRAFT("draft"),
    PUBLISHED("published"),
    ARCHIVED("archived"),
    ;

    companion object {
        fun ofSlug(slug: String): ArticleStatus =
            entries.firstOrNull { it.slug == slug }
                ?: error("알 수 없는 아티클 상태: $slug")

        private val ALLOWED: Map<ArticleStatus, Set<ArticleStatus>> = mapOf(
            DRAFT to setOf(PUBLISHED, ARCHIVED),
            PUBLISHED to setOf(DRAFT, ARCHIVED),
            ARCHIVED to setOf(DRAFT),
        )

        fun isAllowed(from: ArticleStatus, to: ArticleStatus): Boolean = to in ALLOWED[from].orEmpty()
    }
}
