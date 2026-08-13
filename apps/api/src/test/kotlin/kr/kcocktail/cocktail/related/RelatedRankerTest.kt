package kr.kcocktail.cocktail.related

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

/**
 * ISSUE-021 RED 1~7 · 11 · 15 — 배리에이션 순위 (`FR-COCKTAIL-024` · `R-C-3`).
 *
 * ## DB 없이 전수로 돈다
 *
 * 순위가 이 이슈의 **유일한 로직**이라 여기에 테스트를 집중한다.
 * SQL 통합 테스트로만 검증하면 순위가 틀렸을 때 **쿼리가 틀렸는지 규칙이 틀렸는지**
 * 구분이 안 되고, 컨테이너를 띄우느라 조합을 다 돌려 보지도 못한다.
 */
class RelatedRankerTest {

    // ── RED 1~5 : 순위 ────────────────────────────────────────────────────

    /**
     * RED 1·2·3·4 — 네 경우가 한 번에 드러난다.
     *
     * `style_primary` 가 앞서는 이유는 **만드는 방식이 닮은 것**이 배리에이션이기 때문이다.
     * 같은 기주라도 하이볼과 스피릿 포워드는 다른 음료다.
     */
    @Test
    fun `RED1-4 - 둘다 · 스타일 · 기주 순으로 정렬된다`() {
        val ranked = RelatedRanker.rank(
            NEGRONI,
            listOf(baseOnly("b"), styleOnly("s"), both("x")),
        )

        assertThat(ranked.map { it.cocktail.slug }).containsExactly("x", "s", "b")
        assertThat(ranked.map { it.matchedOn })
            .containsExactly(MatchedOn.BOTH, MatchedOn.STYLE, MatchedOn.BASE)
    }

    /**
     * RED 5 **결정** — 둘 다 불일치면 **제외**다.
     *
     * 후순위로 채우면 상세 하단이 "관련 없는 칵테일 8개" 가 된다.
     * 그건 추천이 아니라 잡음이고, 한 번 그렇게 보이면 사용자는 그 영역을 안 본다.
     */
    @Test
    fun `RED5 - 둘 다 불일치면 결과에 없다`() {
        val ranked = RelatedRanker.rank(NEGRONI, listOf(neither("n")))

        assertThat(ranked).isEmpty()
    }

    @Test
    fun `RED6 - 자기 자신은 제외된다`() {
        val ranked = RelatedRanker.rank(NEGRONI, listOf(NEGRONI, both("x")))

        assertThat(ranked.map { it.cocktail.slug }).containsExactly("x")
    }

    /**
     * RED 7 — **동점 정렬이 결정론적이어야 한다.**
     *
     * 없으면 같은 화면을 새로고침할 때마다 순서가 바뀌고, 테스트도 간헐적으로 깨진다.
     * 입력 순서를 뒤집어도 결과가 같은지로 확인한다 — 안정 정렬에 기대면
     * 입력이 달라질 때 무너진다.
     */
    @Test
    fun `RED7 - 동점 정렬이 입력 순서와 무관하다`() {
        val candidates = listOf(styleOnly("c"), styleOnly("a"), styleOnly("b"))

        val forward = RelatedRanker.rank(NEGRONI, candidates).map { it.cocktail.slug }
        val backward = RelatedRanker.rank(NEGRONI, candidates.reversed()).map { it.cocktail.slug }

        assertAll(
            { assertThat(forward).containsExactly("a", "b", "c") },
            { assertThat(backward).`as`("뒤집어 넣어도 같다").isEqualTo(forward) },
        )
    }

    // ── RED 11 : 상한 ────────────────────────────────────────────────────

    @Test
    fun `RED11 - 상한을 넘지 않는다`() {
        val many = (1..30).map { styleOnly("s%02d".format(it)) }

        val ranked = RelatedRanker.rank(NEGRONI, many)

        assertThat(ranked).hasSize(RelatedRanker.LIMIT)
        assertThat(ranked.first().cocktail.slug).`as`("상한은 정렬 뒤에 자른다").isEqualTo("s01")
    }

    /**
     * 상한이 순위를 이기지 않는다 — 스타일 일치가 8건을 넘어도, 둘 다 일치하는 것이
     * 있으면 그것이 먼저 들어간다. 자르기 전에 정렬이 끝나야 성립한다.
     */
    @Test
    fun `상한이 순위보다 먼저 적용되지 않는다`() {
        val candidates = (1..30).map { styleOnly("s%02d".format(it)) } + both("zzz")

        val ranked = RelatedRanker.rank(NEGRONI, candidates)

        assertThat(ranked.first().cocktail.slug).isEqualTo("zzz")
    }

    // ── RED 12 · 15 : 형태 ───────────────────────────────────────────────

    @Test
    fun `RED12 - 후보가 없으면 빈 목록이다`() {
        assertThat(RelatedRanker.rank(NEGRONI, emptyList())).isEmpty()
    }

    /** RED 15 — 사유가 없으면 사용자는 **왜 이게 여기 있는지** 모른다. */
    @Test
    fun `RED15 - 매칭 축이 결과에 담긴다`() {
        val ranked = RelatedRanker.rank(NEGRONI, listOf(styleOnly("s"), baseOnly("b")))

        assertThat(ranked.associate { it.cocktail.slug to it.matchedOn })
            .containsEntry("s", MatchedOn.STYLE)
            .containsEntry("b", MatchedOn.BASE)
    }

    /** 열거 순서와 `rank` 가 어긋나면 정렬이 조용히 뒤집힌다. */
    @Test
    fun `MatchedOn 의 rank 가 열거 순서와 같다`() {
        assertThat(MatchedOn.entries.map { it.rank }).isEqualTo(MatchedOn.entries.indices.toList())
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────

    private companion object {
        val NEGRONI = ref("negroni", style = "spirit-forward", base = "gin")

        fun ref(slug: String, style: String, base: String) =
            CocktailRef(slug, "이름", "Name", "요약", style, base)

        fun both(slug: String) = ref(slug, "spirit-forward", "gin")
        fun styleOnly(slug: String) = ref(slug, "spirit-forward", "vodka")
        fun baseOnly(slug: String) = ref(slug, "highball", "gin")
        fun neither(slug: String) = ref(slug, "highball", "rum")
    }
}
