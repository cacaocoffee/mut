package kr.mut.common.revalidate

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

/**
 * ISSUE-015 RED 4~9 · 11 — 재생성 대상 경로 (SPEC-05 §4).
 *
 * DB 도 HTTP 도 없다. 경로 산출은 순수 함수라 전수로 돈다.
 */
class RevalidatePathsTest {

    private val negroni = RevalidateTarget(
        slug = "negroni",
        baseSpiritSlug = "gin",
        stylePrimarySlug = "spirit-forward",
        methodSlug = "build",
    )

    @Test
    fun `RED4-7 - 상세와 3축 카테고리 경로가 포함된다`() {
        val paths = RevalidatePaths.forCocktail(negroni)

        assertAll(
            { assertThat(paths).`as`("RED4 상세").contains("/cocktails/negroni") },
            { assertThat(paths).`as`("RED5 기주").contains("/cocktails/base/gin") },
            { assertThat(paths).`as`("RED6 스타일").contains("/cocktails/style/spirit-forward") },
            { assertThat(paths).`as`("RED7 메이킹").contains("/cocktails/method/build") },
        )
    }

    /**
     * RED 8 — 스타일은 여러 개를 가질 수 있지만 **경로는 `style_primary` 하나**다 (`R-C-2`).
     *
     * 함수가 애초에 대표 하나만 받는다. 목록을 받으면 "전부 만들까" 라는 선택지가 생기고,
     * 그 선택지가 있는 한 언젠가 전부 만든다.
     */
    @Test
    fun `RED8 - 스타일 경로는 대표 하나뿐이다`() {
        val paths = RevalidatePaths.forCocktail(negroni)

        assertThat(paths.filter { it.startsWith("/cocktails/style/") })
            .containsExactly("/cocktails/style/spirit-forward")
    }

    /**
     * RED 9 — **축 조합 경로 0건** (`R-C-2`).
     *
     * `/cocktails/base/gin/style/sour` 같은 것이 나오면 안 된다. 축 세그먼트가
     * 두 번 이상 들어간 경로가 있는지로 본다 — 조합을 만드는 코드가 생기면 여기서 걸린다.
     */
    @Test
    fun `RED9 - 축 조합 경로가 없다`() {
        val axes = listOf("base", "style", "method")

        RevalidatePaths.forCocktail(negroni).forEach { path ->
            val axisSegments = path.split("/").count { it in axes }
            assertThat(axisSegments)
                .`as`("%s — 축 세그먼트가 둘 이상이면 조합 경로다", path)
                .isLessThanOrEqualTo(1)
        }
    }

    /** RED 23 — 발행분 전체가 사이트맵에 들어가야 한다 (`NFR-S-04`). */
    @Test
    fun `RED23 - 사이트맵도 대상이다`() {
        assertThat(RevalidatePaths.forCocktail(negroni)).contains("/sitemap.xml")
    }

    /**
     * RED 11 — 중복 제거.
     *
     * 세 축의 슬러그가 겹칠 수 있다. 겹치면 같은 경로를 두 번 재생성하라고 보내는 셈이고,
     * 프론트가 그만큼 헛일을 한다.
     */
    @Test
    fun `RED11 - 경로가 중복되지 않는다`() {
        val collided = RevalidateTarget(
            slug = "x",
            baseSpiritSlug = "same",
            stylePrimarySlug = "same",
            methodSlug = "same",
        )

        val paths = RevalidatePaths.forCocktail(collided)

        assertThat(paths).doesNotHaveDuplicates()
    }

    @Test
    fun `경로는 전부 슬래시로 시작한다`() {
        assertThat(RevalidatePaths.forCocktail(negroni)).allSatisfy {
            assertThat(it).startsWith("/")
        }
    }
}
