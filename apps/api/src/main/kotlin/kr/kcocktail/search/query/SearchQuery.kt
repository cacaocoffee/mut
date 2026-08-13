package kr.kcocktail.search.query

import kr.kcocktail.common.web.error.BadRequestException
import kr.kcocktail.search.index.Chosung

/**
 * 검색어 해석 (ISSUE-024 · `FR-SEARCH-006`·`007`).
 *
 * ## 초성 검색인지 아닌지를 여기서 정한다
 *
 * 판정이 한 곳에 있어야 `search_miss` 가 뜻을 갖는다 (SPEC-10 §4.3) —
 * **초성 검색이 0건이면 콘텐츠가 없는 게 아니라 초성 색인이 고장난 것일 수 있다.**
 * 두 원인을 구분하려면 "이 질의가 초성이었나" 가 응답에 실려야 한다.
 */
data class SearchQuery(
    val raw: String,

    /** 입력이 **초성으로만** 이뤄졌는가. 섞이면 일반 검색이다 (DECISIONS §1.9). */
    val isChosung: Boolean,
) {
    companion object {
        /** 과도한 입력 방어 (RED 29). 이름이 120자라 그보다 길 이유가 없다. */
        const val MAX_LENGTH = 60

        private val CHOSUNG_ONLY = Regex("^[ㄱ-ㅎ]+$")

        /**
         * @throws BadRequestException 빈 질의 (RED 28 · DECISIONS §1.9) 또는 상한 초과
         *
         * 빈 `q` 를 빈 결과로 돌려주지 않는 이유: 클라이언트가 파라미터를 빠뜨린 것과
         * 정말 아무것도 없는 것을 구분하지 못하면, 검색창이 비어 있을 때
         * **전체 목록을 검색 결과로 그리는** 화면이 나온다.
         */
        fun of(raw: String?): SearchQuery {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty()) throw BadRequestException("q 가 비어 있습니다")
            if (trimmed.length > MAX_LENGTH) {
                throw BadRequestException("q 는 ${MAX_LENGTH}자를 넘을 수 없습니다")
            }

            // 공백을 지우고 판정한다 — `ㅁ ㄹ ㄱ` 처럼 띄어 친 입력도 초성이다.
            val compact = trimmed.replace(" ", "")
            return SearchQuery(raw = trimmed, isChosung = CHOSUNG_ONLY.matches(compact))
        }
    }

    /**
     * 색인과 맞출 문자열.
     *
     * 초성 질의는 그대로 쓰고(색인의 `chosung` 컬럼과 맞춘다), 일반 질의는
     * **띄어쓰기를 지운다** — `올드 패션드` 와 `올드패션드` 가 같아야 한다 (`R-F2.1-3`).
     * 색인 쪽도 같은 규칙으로 저장돼 있다 (`SearchDocumentText`).
     */
    val normalized: String get() = if (isChosung) raw.replace(" ", "") else raw.replace(" ", "")

    /**
     * 초성 질의를 색인과 맞출 형태로.
     *
     * **`Chosung.of()` 를 다시 돌리지 않는다.** 그 함수는 **음절**(`마`)을 초성으로 바꾸는데,
     * 초성 질의는 이미 **자모**(`ㅁ`)라 음절이 하나도 없다 — 그러면 "한글이 없으면 빈 문자열"
     * 규칙에 걸려 `""` 가 나오고, `LIKE '%%'` 가 되어 **전 코퍼스가 걸린다.**
     *
     * 0건이어야 할 질의가 전부를 반환하는 것이 이 실수의 모습이라, 결과가 그럴듯해서
     * 눈으로는 안 잡힌다.
     */
    val chosung: String get() = raw.replace(" ", "")

    /** 실제로 `LIKE` 에 들어갈 문자열. 비면 조회 자체를 하지 않는다 (전체 매칭 방지). */
    val pattern: String get() = if (isChosung) chosung else normalized
}
