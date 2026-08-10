package kr.kcocktail.common.web.page

/**
 * SPEC-07 §1.5 페이지네이션 응답.
 *
 * ```json
 * { "items": [ … ], "page": { "number": 0, "size": 24, "totalElements": 137, "totalPages": 6 } }
 * ```
 *
 * Phase 1 규모(칵테일 500)에서는 offset 으로 충분하다. 커서는 수천 건을 넘을 때다.
 */
data class PageResponse<T>(
    val items: List<T>,
    val page: PageMeta,
) {
    companion object {
        fun <T> of(items: List<T>, query: PageQuery, totalElements: Long) = PageResponse(
            items = items,
            page = PageMeta(
                number = query.page,
                size = query.size,
                totalElements = totalElements,
                totalPages = if (query.size == 0) 0
                else ((totalElements + query.size - 1) / query.size).toInt(),
            ),
        )
    }
}

data class PageMeta(
    val number: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)
