package kr.kcocktail.search.index

import java.text.Normalizer

/**
 * 초성 분해 (`FR-SEARCH-007` · `R-F2.1-4`).
 *
 * ## 순수 함수다
 *
 * DB 도 스프링도 모른다. 한글 초성 추출은 경계가 많아 (복합 초성 · 받침 · 정규화 ·
 * 서로게이트) **전수로 고정할 수 있을 때 고정한다** — 컨테이너를 띄우는 테스트로 밀면
 * 경우의 수를 늘리는 값이 비싸져 결국 대표 사례 몇 개만 남는다.
 *
 * ## 저장 시점에 부른다
 *
 * 조회 때 계산하면 `chosung` 이 표현식이 되어 GIN 인덱스가 붙지 않는다 (SPEC-05 §6 · G-13).
 * 색인 컬럼에 넣는 것은 [SearchDocumentText] 가 조립한다.
 */
object Chosung {

    /** `가` U+AC00 ~ `힣` U+D7A3 — 현대 한글 음절 블록. 자모(U+3131~)는 여기 없다. */
    private const val SYLLABLE_FIRST = 0xAC00
    private const val SYLLABLE_LAST = 0xD7A3

    /** 음절 = 초성 × 21(중성) × 28(종성). 한 초성이 588 음절을 차지한다. */
    private const val SYLLABLES_PER_INITIAL = 21 * 28

    /** 유니코드가 정한 순서 그대로다. 순서를 바꾸면 산술이 어긋난다. */
    private val INITIALS = charArrayOf(
        'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
        'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ',
    )

    /**
     * 한글 음절은 초성으로, 나머지는 그대로. 공백은 버린다.
     *
     * | 입력 | 출력 | 왜 |
     * |---|---|---|
     * | `마르가리타` | `ㅁㄹㄱㄹㅌ` | `R-F2.1-4` |
     * | `올드 패션드` | `ㅇㄷㅍㅅㄷ` | 초성으로 칠 때 띄어쓰기를 맞추는 사용자는 없다 |
     * | `잭다니엘No.7` | `ㅈㄷㄴㅇNo.7` | 버리면 `No.7` 과 `No.9` 의 색인이 같아진다 |
     * | `Negroni` | (빈 문자열) | DECISIONS §1.9 |
     *
     * **한글이 하나도 없으면 빈 문자열이다** (DECISIONS §1.9). 원문을 남기면 같은 이름이
     * `chosung` 과 `name_en` 두 컬럼에서 걸려 가중치 산정(G-13)이 그만큼 왜곡된다.
     *
     * NFD 로 들어온 문자열(macOS 붙여넣기 · 파일명)을 NFC 로 모은다. 정규화하지 않으면
     * 눈에 똑같은 두 문자열이 다른 색인을 갖는다 — 자모가 풀려 있어 음절 블록에 안 들어온다.
     */
    fun of(text: String): String {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFC)
        val out = StringBuilder(normalized.length)
        var sawHangul = false

        var i = 0
        while (i < normalized.length) {
            // 코드포인트 단위로 읽는다. char 로 훑으면 이모지의 서로게이트 쌍이 반 토막 나
            // 색인에 깨진 문자가 들어가고 그 행은 영영 안 잡힌다.
            val codePoint = normalized.codePointAt(i)
            i += Character.charCount(codePoint)

            when {
                Character.isWhitespace(codePoint) -> Unit
                codePoint in SYLLABLE_FIRST..SYLLABLE_LAST -> {
                    sawHangul = true
                    out.append(INITIALS[(codePoint - SYLLABLE_FIRST) / SYLLABLES_PER_INITIAL])
                }
                else -> out.appendCodePoint(codePoint)
            }
        }

        return if (sawHangul) out.toString() else ""
    }
}

/**
 * 색인 컬럼에 들어갈 문자열을 조립한다 (DECISIONS §1.9).
 *
 * `search` 안에 두는 이유: `cocktail.taxonomy.AliasNormalizer` 가 같은 규칙을 갖고 있지만
 * `api` 밖이라 모듈 경계(`PRIN-T03` · ISSUE-001 RED 3)가 참조를 막는다. 쓰임도 다르다 —
 * 저장 쪽은 **에디터가 친 그대로** 남기고(다시 열었을 때 알아보게), 색인 쪽은 여기서 걷어낸다.
 * "저장은 관대하게, 색인은 엄격하게" 의 색인 절반이 이 파일이다.
 */
internal object SearchDocumentText {

    /**
     * 별칭 정리 — 앞뒤 공백 · 빈 값 · 이름과 겹치는 것 · 중복을 걷어낸다 (RED 21).
     *
     * **입력 순서를 지킨다.** 에디터가 중요한 순서대로 적었을 수 있다.
     *
     * 대소문자를 무시하고 비교한다 — `Old Fashioned` 와 `old fashioned` 는 검색에서 같은 값이라
     * 둘 다 남겨 봐야 `aliases` 만 길어진다. 저장되는 값은 **에디터가 친 표기 그대로**다.
     *
     * 띄어쓰기는 **무시하지 않는다**. `올드 패션드` 는 `올드패션드` 와 다른 별칭으로 남아야 한다
     * (DECISIONS §1.9 "띄어쓰기 변형은 정규화 매칭 + 에디터 별칭 **양쪽**").
     */
    fun aliases(nameKo: String, nameEn: String?, raw: List<String>): List<String> {
        val names = setOfNotNull(nameKo.trim().lowercase(), nameEn?.trim()?.lowercase())

        return raw.asSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() }
            .filterNot { it.lowercase() in names }
            .distinctBy { it.lowercase() }
            .toList()
    }

    /**
     * `chosung` 컬럼 — **이름과 별칭을 각각 분해해 공백으로 잇는다** (DECISIONS §1.9).
     *
     * 이어 붙이지 않고 나눠 두는 이유: 붙이면 `올드패션드` + `올패` 가 `ㅇㄷㅍㅅㄷㅇㅍ` 가 되어
     * 경계를 넘는 우연한 부분 문자열(`ㅅㄷㅇ`)이 걸린다. 공백이 그 우연을 끊는다.
     *
     * 중복은 한 번만 담는다 — `올드 패션드` 는 이름과 같은 초성이라 두 번 들어갈 이유가 없다.
     * 초성이 빈 값(영문 이름)은 아예 빠진다.
     */
    fun chosung(nameKo: String, nameEn: String?, aliases: List<String>): String =
        (listOf(nameKo) + listOfNotNull(nameEn) + aliases)
            .map(Chosung::of)
            .filter(String::isNotEmpty)
            .distinct()
            .joinToString(" ")
}
