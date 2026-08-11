package kr.kcocktail.cocktail

import kr.kcocktail.cocktail.api.CocktailSummary
import kr.kcocktail.cocktail.domain.CocktailInvariants
import kr.kcocktail.common.taxonomy.FlavorKey
import kr.kcocktail.common.taxonomy.StyleKey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

/**
 * ISSUE-009 RED 13~20 · 31~32 — 앱 강제 불변식 (SPEC-06 §4.3).
 *
 * ## DB 가 못 하는 것만 여기 있다
 *
 * `INV-COCKTAIL-02`·`04` 는 **자식 행의 개수**에 대한 규칙이다.
 * `CHECK` 는 자기 행만 보므로 다른 테이블의 행 수를 셀 수 없다.
 *
 * 순수 함수라 DB 없이 돈다 — 그래서 발행 게이트(013)와 배치 검증(016)이 재사용할 수 있다.
 */
class CocktailInvariantsTest {

    // ── RED 13~15 : styles 최소 1개 (INV-COCKTAIL-02) ─────────────────────

    @Test
    fun `RED13 - styles 가 비면 거부된다`() {
        val violations = check(styles = emptySet(), primary = StyleKey.HIGHBALL)

        assertThat(violations).singleElement().satisfies({
            assertThat(it.code).isEqualTo("INV-COCKTAIL-02")
            assertThat(it.field).isEqualTo("styles")
        })
    }

    /**
     * 비어 있으면 `03`(`style_primary ∈ styles`)도 자동으로 위반이지만 따로 알리지 않는다.
     * **원인은 하나인데 메시지가 둘이면 무엇을 고쳐야 할지 흐려진다.**
     */
    @Test
    fun `styles 가 비면 INV-COCKTAIL-03 을 중복으로 알리지 않는다`() {
        assertThat(check(styles = emptySet(), primary = StyleKey.TIKI).map { it.code })
            .containsExactly("INV-COCKTAIL-02")
    }

    @Test
    fun `RED14-15 - styles 가 1개거나 복수면 통과한다`() {
        assertThat(check(styles = setOf(StyleKey.HIGHBALL), primary = StyleKey.HIGHBALL)).isEmpty()
        assertThat(
            check(styles = setOf(StyleKey.HIGHBALL, StyleKey.SOUR), primary = StyleKey.SOUR),
        ).isEmpty()
    }

    /**
     * `INV-COCKTAIL-03` — DB 복합 FK 가 이미 막는다. **앱에서도 보는 이유**는
     * 에디터에게 어느 항목이 왜 막혔는지 알려 주기 위해서다 (`FR-ADMIN-003`).
     * FK 위반은 `violations` 를 만들지 못한다.
     */
    @Test
    fun `style_primary 가 styles 밖이면 INV-COCKTAIL-03 이다`() {
        val violations = check(styles = setOf(StyleKey.HIGHBALL), primary = StyleKey.TIKI)

        assertThat(violations).singleElement().satisfies({
            assertThat(it.code).isEqualTo("INV-COCKTAIL-03")
            assertThat(it.field).isEqualTo("stylePrimary")
            assertThat(it.message).contains("tiki")
        })
    }

    // ── RED 17~20 : 향 태그 1~3개 (INV-COCKTAIL-04) ───────────────────────

    @Test
    fun `RED17 - aroma_tags 가 0개면 거부된다`() {
        assertThat(check(aroma = emptySet()).map { it.code }).containsExactly("INV-COCKTAIL-04")
    }

    @Test
    fun `RED18-19 - 1개에서 3개까지 통과한다`() {
        val tags = listOf(FlavorKey.CITRUS, FlavorKey.SOUR, FlavorKey.HERBAL)

        assertAll(
            (1..3).map<Int, () -> Unit> { n ->
                {
                    assertThat(check(aroma = tags.take(n).toSet()))
                        .`as`("%d개", n)
                        .isEmpty()
                }
            },
        )
    }

    /**
     * `FR-COCKTAIL-008` 이 "4개째는 UI 가 막는다"고 했지만 **서버도 막는다** (`PRIN-T05`).
     * UI 만 막으면 API 를 직접 부르는 경로가 남는다.
     */
    @Test
    fun `RED20 - aroma_tags 가 4개면 거부된다`() {
        val four = setOf(FlavorKey.CITRUS, FlavorKey.SOUR, FlavorKey.HERBAL, FlavorKey.SMOKY)

        assertThat(check(aroma = four)).singleElement().satisfies({
            assertThat(it.code).isEqualTo("INV-COCKTAIL-04")
            assertThat(it.field).isEqualTo("aromaTags")
            assertThat(it.message).contains("최대 3개", "현재 4개")
        })
    }

    // ── 전부 모아서 돌려준다 (FR-ADMIN-003) ───────────────────────────────

    /** 첫 위반에서 멈추면 에디터가 저장·실패를 반복한다. */
    @Test
    fun `여러 축이 동시에 어긋나면 전부 돌려준다`() {
        val violations = CocktailInvariants.check(
            CocktailInvariants.Subject(
                slug = "broken",
                styles = setOf(StyleKey.HIGHBALL),
                stylePrimary = StyleKey.TIKI,   // 03 위반
                aromaTags = emptySet(),          // 04 위반
            ),
        )

        assertThat(violations.map { it.code })
            .containsExactlyInAnyOrder("INV-COCKTAIL-03", "INV-COCKTAIL-04")
    }

    /** 013·016 이 엔티티를 전부 로딩하지 않고도 검사할 수 있어야 한다. */
    @Test
    fun `엔티티가 아니라 값으로 검사한다`() {
        val subject = CocktailInvariants.Subject(
            slug = "gin-tonic",
            styles = setOf(StyleKey.HIGHBALL),
            stylePrimary = StyleKey.HIGHBALL,
            aromaTags = setOf(FlavorKey.CITRUS),
        )

        assertThat(CocktailInvariants.check(subject)).isEmpty()
    }

    // ── RED 31~32 : 공개 응답 규약 ────────────────────────────────────────

    /**
     * SPEC-07 §5 — 표시값 `abv` **하나**다.
     * 계산인지 수동인지는 내부 사정이라 밖으로 내보내지 않는다.
     */
    @Test
    fun `RED32 - 요약에 abv_calculated 와 abv_override 구분이 없다`() {
        val fields = CocktailSummary::class.java.declaredFields.map { it.name }

        assertThat(fields).contains("abv")
        assertThat(fields)
            .`as`("내부 사정이 밖으로 나가면 클라이언트가 그것에 의존하기 시작한다")
            .doesNotContain("abvCalculated", "abvOverride")
    }

    /**
     * RED 31 — 공개 **응답**에는 `slug` 만 나간다 (SPEC-07 §1.1).
     *
     * `CocktailSummary` 에 `id` 가 있는 것은 **모듈 간 참조 키**라서다 — 이슈 023·031 이
     * 이것으로 조인한다. 공개 응답으로의 변환은 `web` 계층(이슈 018·020)이 하고,
     * 그 계층이 `id` 를 떨어뜨린다.
     */
    @Test
    fun `RED31 - 모듈 간 뷰에는 slug 가 반드시 있다`() {
        val fields = CocktailSummary::class.java.declaredFields.map { it.name }

        assertThat(fields).contains("slug")
        assertThat(fields)
            .`as`("id 는 모듈 간 참조 키다. 공개 응답 변환은 web 계층의 몫")
            .contains("id")
    }

    // ── 헬퍼 ───────────────────────────────────────────────────────────────

    private fun check(
        styles: Set<StyleKey> = setOf(StyleKey.HIGHBALL),
        primary: StyleKey = StyleKey.HIGHBALL,
        aroma: Set<FlavorKey> = setOf(FlavorKey.CITRUS),
    ) = CocktailInvariants.check(
        CocktailInvariants.Subject(
            slug = "probe",
            styles = styles,
            stylePrimary = primary,
            aromaTags = aroma,
        ),
    )
}
