package kr.mut.common.web.page

/**
 * `?page=0&size=24&sort=abv,asc` 를 해석한 결과 (SPEC-07 §1.5).
 *
 * 컨트롤러가 파라미터를 직접 읽지 않는다 — 상한과 허용목록을 이슈마다 다시 쓰면 반드시 어긋난다.
 */
data class PageQuery(
    val page: Int,
    val size: Int,
    val sort: List<SortOrder>,
) {
    val offset: Long get() = page.toLong() * size

    companion object {
        const val DEFAULT_PAGE = 0
        const val DEFAULT_SIZE = 24

        /**
         * 한 번에 가져갈 수 있는 최대치. **넘으면 400 이 아니라 절삭한다** —
         * 클라이언트가 큰 값을 넣는 것은 흔한 일이고, 그때 실패시키는 것보다
         * 서버가 감당 가능한 만큼 주는 편이 낫다. 무제한 조회만 막으면 된다.
         */
        const val MAX_SIZE = 100
    }
}

data class SortOrder(val property: String, val ascending: Boolean) {
    override fun toString() = "$property,${if (ascending) "asc" else "desc"}"
}

/**
 * 이 엔드포인트에서 정렬을 허용할 컬럼.
 *
 * **허용목록 밖이면 400 이다.** 인덱스 없는 컬럼으로 정렬을 받으면 풀스캔이 열린다 —
 * 클라이언트가 임의 컬럼을 넣을 수 있으면 그것이 곧 성능 구멍이다.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class SortableBy(vararg val value: String)
