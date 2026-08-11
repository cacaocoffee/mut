package kr.kcocktail.common.security.sanitize

/**
 * 에디터 본문의 저장 시점 정화 (SPEC-08 §7).
 *
 * ## 왜 저장 시점인가
 *
 * 렌더링 시점에만 처리하면 **출력 경로마다 놓친다** — SSG 빌드 · 어드민 미리보기 ·
 * OG 태그 생성 · 검색 색인. 경로는 이슈가 늘수록 늘어나고, 새 경로를 만드는 사람은
 * 정화가 필요하다는 것을 모른다.
 *
 * **저장된 값 자체가 안전해야 한다.** 그러면 출력 경로가 몇 개든 상관없다.
 *
 * ## 마크다운 원문을 다룬다
 *
 * 저장하는 것이 마크다운 원문이므로 원문에서 위험 패턴을 제거한다.
 * HTML 로 변환한 뒤 정화하면 저장할 것이 HTML 이 되어 버리고,
 * 그러면 에디터가 다시 열었을 때 자기가 쓴 것과 다른 것을 보게 된다.
 *
 * 렌더링은 **원시 HTML 을 통과시키지 않는 파서**를 쓴다 — 두 겹이다.
 */
object MarkdownSanitizer {

    /**
     * @return 정화된 마크다운. 정상 문법은 그대로 남는다.
     */
    fun sanitize(raw: String): String = raw
        .removeHtmlTags()
        .removeDangerousUrls()
        .trim()

    /**
     * HTML 태그를 통째로 지운다.
     *
     * 마크다운에 인라인 HTML 을 허용하지 않는다 — 허용하면 어떤 태그가 안전한지의 목록을
     * 유지해야 하고, 그 목록은 반드시 뒤처진다. 우리 본문에 `<div>` 가 필요한 이유가 없다.
     *
     * `<script>` 는 여는 태그만 지우면 내용이 본문으로 남아 이상해지므로 **블록째** 지운다.
     */
    private fun String.removeHtmlTags(): String = this
        .replace(SCRIPT_BLOCK, "")
        .replace(STYLE_BLOCK, "")
        .replace(HTML_TAG, "")

    /**
     * `javascript:` · `data:` · `vbscript:` 링크를 무해하게 만든다.
     *
     * 마크다운 링크 문법 `[텍스트](javascript:...)` 은 태그가 아니라 파서가 만든다.
     * 태그를 지워도 남으므로 따로 본다.
     */
    private fun String.removeDangerousUrls(): String =
        DANGEROUS_URL.replace(this) { match -> "${match.groupValues[1]}(#)" }

    private val SCRIPT_BLOCK = Regex("""<script\b[^>]*>[\s\S]*?</script\s*>""", RegexOption.IGNORE_CASE)
    private val STYLE_BLOCK = Regex("""<style\b[^>]*>[\s\S]*?</style\s*>""", RegexOption.IGNORE_CASE)

    /** 여는·닫는·자기닫는 태그 전부. 속성 안의 `>` 는 우리 본문에 나올 일이 없다. */
    private val HTML_TAG = Regex("""</?[a-zA-Z][^>]*>""")

    /** `[텍스트](javascript:alert(1))` → `[텍스트](#)` */
    private val DANGEROUS_URL = Regex(
        """(\[[^\]]*\])\(\s*(?:javascript|data|vbscript)\s*:[^)]*\)""",
        RegexOption.IGNORE_CASE,
    )
}
