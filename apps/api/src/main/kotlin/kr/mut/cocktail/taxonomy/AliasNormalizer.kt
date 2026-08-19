package kr.mut.cocktail.taxonomy

/**
 * 별칭 정규화 (`FR-COCKTAIL-009` · `R-F2.1-3`).
 *
 * ## 여기서 하지 않는 것
 *
 * **띄어쓰기 제거와 초성 분해는 검색 색인(이슈 017)의 일이다.**
 * 여기서는 에디터가 입력한 문자열을 **그대로 보존**한다 —
 * `올드 패션드` 와 `올드패션드` 는 둘 다 별칭으로 남고, 매칭은 색인이 한다.
 *
 * 정규화를 여기서 하면 에디터가 입력한 것과 저장된 것이 달라지고,
 * 다시 열었을 때 자기가 쓴 것을 못 알아본다.
 */
object AliasNormalizer {

    /**
     * 앞뒤 공백을 떼고, 빈 것을 버리고, 중복을 없앤다.
     *
     * 중복 제거는 **입력 순서를 유지**한다 — 에디터가 중요한 순서대로 적었을 수 있다.
     */
    fun normalize(raw: List<String>): List<String> =
        raw.map(String::trim).filter(String::isNotEmpty).distinct()

    /**
     * 이름과 겹치는 별칭도 **저장은 허용한다** (DECISIONS §1).
     *
     * 검색은 이름도 보므로 색인 단계에서 중복을 없애면 된다 (이슈 017).
     * 여기서 막으면 에디터가 "왜 안 들어가지" 하고 헤맨다 — 저장은 관대하게,
     * 색인은 엄격하게.
     */
    fun withoutNames(aliases: List<String>, nameKo: String, nameEn: String): List<String> {
        val names = setOf(nameKo.trim(), nameEn.trim())
        return normalize(aliases).filterNot { it in names }
    }
}
