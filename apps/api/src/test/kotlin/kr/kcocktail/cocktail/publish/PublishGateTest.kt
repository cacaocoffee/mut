package kr.kcocktail.cocktail.publish

import kr.kcocktail.cocktail.api.IngredientSnapshot
import kr.kcocktail.cocktail.api.PublishCandidate
import kr.kcocktail.cocktail.api.PublishGate
import kr.kcocktail.cocktail.api.RecipeSnapshot
import kr.kcocktail.common.taxonomy.FlavorKey
import kr.kcocktail.common.taxonomy.StyleKey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

/**
 * ISSUE-013 RED 1~19 · 32~34 — 발행 게이트 6종 (SPEC-02 §2.3).
 *
 * ## 순수 함수라 DB 없이 전수로 돈다
 *
 * 발행 시점과 배치 검증(이슈 016)이 **같은 함수**를 본다. 두 벌 구현하면 반드시 어긋나고,
 * `NFR-D-02`("게이트를 우회한 `published` 0건")가 그 어긋남을 못 잡는다 —
 * 검사하는 쪽이 틀렸으니까.
 */
class PublishGateTest {

    // ── RED 1~2 : GATE-01 향과 맛 ─────────────────────────────────────────

    /**
     * `PRIN-P03` 의 핵심이다. 다른 사이트 설명을 옮기면 레시피 나열형 블로그와 구별되지 않고,
     * **그 순간 이 서비스의 존재 이유가 사라진다.**
     */
    @Test
    fun `RED1-2 - tasting_note 가 비면 발행이 막힌다`() {
        assertAll(
            listOf(null, "", "   ", "\t\n").map<String?, () -> Unit> { note ->
                {
                    assertThat(codes(candidate(tastingNote = note)))
                        .`as`("%s", note?.let { "'$it'" } ?: "null")
                        .contains("GATE-COCKTAIL-01")
                }
            },
        )

        assertThat(codes(candidate(tastingNote = "쌉싸름한 진 향에 토닉의 단맛이 얹힌다")))
            .doesNotContain("GATE-COCKTAIL-01")
    }

    // ── RED 3 : GATE-02 분류 3축 ──────────────────────────────────────────

    @Test
    fun `RED3 - 3축 불변식을 어기면 막힌다`() {
        assertAll(
            listOf<() -> Unit>(
                {
                    assertThat(codes(candidate(styles = emptySet())))
                        .`as`("스타일 없음")
                        .contains("GATE-COCKTAIL-02")
                },
                {
                    assertThat(codes(candidate(styles = setOf(StyleKey.HIGHBALL), primary = StyleKey.TIKI)))
                        .`as`("대표 스타일이 목록 밖")
                        .contains("GATE-COCKTAIL-02")
                },
                {
                    assertThat(codes(candidate(aroma = emptySet())))
                        .`as`("향 태그 0개")
                        .contains("GATE-COCKTAIL-02")
                },
                {
                    assertThat(
                        codes(
                            candidate(
                                aroma = setOf(
                                    FlavorKey.CITRUS, FlavorKey.SOUR,
                                    FlavorKey.HERBAL, FlavorKey.SMOKY,
                                ),
                            ),
                        ),
                    ).`as`("향 태그 4개").contains("GATE-COCKTAIL-02")
                },
            ),
        )
    }

    // ── RED 4~6 : GATE-03 표준 레시피 ─────────────────────────────────────

    @Test
    fun `RED4-6 - 표준 레시피가 부실하면 막힌다`() {
        assertAll(
            listOf<() -> Unit>(
                {
                    assertThat(codes(candidate(recipe = null)))
                        .`as`("표준 레시피 없음")
                        .contains("GATE-COCKTAIL-03")
                },
                {
                    assertThat(codes(candidate(recipe = RecipeSnapshot(emptyList(), stepCount = 3))))
                        .`as`("재료 0개")
                        .contains("GATE-COCKTAIL-03")
                },
                {
                    assertThat(codes(candidate(recipe = RecipeSnapshot(listOf(gin()), stepCount = 0))))
                        .`as`("스텝 0개")
                        .contains("GATE-COCKTAIL-03")
                },
            ),
        )
    }

    // ── RED 7 : GATE-04 마스터 참조 ───────────────────────────────────────

    /** FK 가 존재는 보장하지만 게이트가 **승인 여부**를 재확인한다 (DECISIONS §1.1). */
    @Test
    fun `RED7 - 미승인 재료가 있으면 막힌다`() {
        val pending = gin(approved = false)

        assertThat(codes(candidate(ingredients = listOf(pending))))
            .contains("GATE-COCKTAIL-04")
        assertThat(violations(candidate(ingredients = listOf(pending))).first { it.code == "GATE-COCKTAIL-04" }.message)
            .`as`("어느 재료인지 알려 준다")
            .contains("gin")
    }

    // ── RED 8~9 : GATE-05 클래식 이야기 ───────────────────────────────────

    @Test
    fun `RED8-9 - 클래식이면 story 가 필수다`() {
        assertThat(codes(candidate(isClassic = true, story = null)))
            .contains("GATE-COCKTAIL-05")
        assertThat(codes(candidate(isClassic = true, story = "1806년 처음 기록됐다")))
            .doesNotContain("GATE-COCKTAIL-05")
        assertThat(codes(candidate(isClassic = false, story = null)))
            .`as`("클래식이 아니면 없어도 된다")
            .doesNotContain("GATE-COCKTAIL-05")
    }

    // ── RED 10~13 : GATE-06 대체재 ────────────────────────────────────────

    @Test
    fun `RED10-12 - 미유통 재료에 대체 안내가 없으면 막힌다`() {
        val importOnly = gin(requiresSubstitute = true, hasSubstitute = false)

        assertThat(codes(candidate(ingredients = listOf(importOnly))))
            .contains("GATE-COCKTAIL-06")

        assertThat(codes(candidate(ingredients = listOf(gin()))))
            .`as`("common·specialty 만 있으면 불필요")
            .doesNotContain("GATE-COCKTAIL-06")
    }

    /**
     * RED 13 — **대체 재료 지정 또는 안내 문구, 둘 중 하나면 충족**한다.
     *
     * 둘 다 "대신 이렇게 하세요"를 전하고, 자가제조처럼 대체 재료가 없는 경우도 있다.
     */
    @Test
    fun `RED13 - 대체재는 지정이나 안내 둘 중 하나면 된다`() {
        val satisfied = gin(requiresSubstitute = true, hasSubstitute = true)

        assertThat(codes(candidate(ingredients = listOf(satisfied))))
            .doesNotContain("GATE-COCKTAIL-06")
    }

    // ── RED 14~19 : 전부 반환 ─────────────────────────────────────────────

    @Test
    fun `RED14 - 6종을 전부 통과하면 발행 가능하다`() {
        assertThat(PublishGate.check(candidate())).isEmpty()
        assertThat(PublishGate.canPublish(candidate())).isTrue()
    }

    /** `FR-ADMIN-003` — 첫 실패에서 멈추면 에디터가 저장·실패를 반복한다. */
    @Test
    fun `RED15 - 둘이 동시에 실패하면 violations 가 2건이다`() {
        val broken = candidate(tastingNote = null, isClassic = true, story = null)

        assertThat(codes(broken))
            .containsExactly("GATE-COCKTAIL-01", "GATE-COCKTAIL-05")
    }

    @Test
    fun `RED16 - 6종이 전부 실패하면 violations 가 6건이다`() {
        val broken = PublishCandidate(
            slug = "broken",
            tastingNote = null,                                    // 01
            isClassic = true,
            story = null,                                          // 05
            styles = emptySet(),                                   // 02
            stylePrimary = StyleKey.HIGHBALL,
            aromaTags = emptySet(),
            standardRecipe = null,                                 // 03
            ingredients = listOf(
                gin(approved = false, requiresSubstitute = true),  // 04 · 06
            ),
        )

        assertThat(codes(broken)).containsExactly(
            "GATE-COCKTAIL-01", "GATE-COCKTAIL-02", "GATE-COCKTAIL-03",
            "GATE-COCKTAIL-04", "GATE-COCKTAIL-05", "GATE-COCKTAIL-06",
        )
    }

    @Test
    fun `RED17-18 - 각 violation 에 코드와 field 가 있다`() {
        violations(candidate(tastingNote = null, isClassic = true, story = null)).forEach {
            assertThat(it.code).matches("^GATE-COCKTAIL-\\d{2}$")
            assertThat(it.field).isNotBlank()
            assertThat(it.message).isNotBlank()
        }
    }

    /** RED 19 — **게이트 번호순**이다. 순서가 흔들리면 테스트가 불안정해진다. */
    @Test
    fun `RED19 - violations 순서가 결정론적이다`() {
        val broken = PublishCandidate(
            slug = "order",
            tastingNote = null,
            isClassic = true,
            story = null,
            styles = emptySet(),
            stylePrimary = StyleKey.HIGHBALL,
            aromaTags = emptySet(),
            standardRecipe = null,
            ingredients = listOf(gin(approved = false, requiresSubstitute = true)),
        )

        repeat(5) {
            assertThat(codes(broken)).containsExactly(
                "GATE-COCKTAIL-01", "GATE-COCKTAIL-02", "GATE-COCKTAIL-03",
                "GATE-COCKTAIL-04", "GATE-COCKTAIL-05", "GATE-COCKTAIL-06",
            )
        }
    }

    // ── RED 32~34 : 재사용 계약 ───────────────────────────────────────────

    /** DB 없이 부를 수 있어야 배치 검증(016)이 전수로 돌 수 있다. */
    @Test
    fun `RED32 - PublishGate 가 순수 함수다`() {
        assertThat(PublishGate.check(candidate())).isEmpty()
        assertThat(PublishGate.check(candidate())).isEqualTo(PublishGate.check(candidate()))
    }

    /**
     * RED 33·34 — 시그니처를 고정한다. 이슈 016 이 이 모양에 의존하고,
     * `cocktail.api` 에 있어야 `admin` 모듈이 경계 위반 없이 호출한다 (`PRIN-T03`).
     */
    @Test
    fun `RED33-34 - 배치 검증이 쓸 시그니처가 api 에 공개돼 있다`() {
        assertThat(PublishGate::class.java.packageName).isEqualTo("kr.kcocktail.cocktail.api")
        assertThat(PublishCandidate::class.java.packageName).isEqualTo("kr.kcocktail.cocktail.api")

        assertThat(PublishGate::class.java.methods.map { it.name })
            .contains("check", "canPublish")
    }

    // ── 헬퍼 ───────────────────────────────────────────────────────────────

    private fun codes(c: PublishCandidate) = violations(c).map { it.code }

    private fun violations(c: PublishCandidate) = PublishGate.check(c)

    private fun gin(
        approved: Boolean = true,
        requiresSubstitute: Boolean = false,
        hasSubstitute: Boolean = false,
    ) = IngredientSnapshot(
        id = 1,
        slug = "gin",
        isApproved = approved,
        requiresSubstitute = requiresSubstitute,
        hasRecipeSubstitute = hasSubstitute,
    )

    /** 기본은 **전부 통과하는** 후보다. 테스트마다 하나씩 망가뜨린다. */
    private fun candidate(
        tastingNote: String? = "쌉싸름한 진 향에 토닉의 단맛이 얹힌다",
        isClassic: Boolean = false,
        story: String? = null,
        styles: Set<StyleKey> = setOf(StyleKey.HIGHBALL),
        primary: StyleKey = StyleKey.HIGHBALL,
        aroma: Set<FlavorKey> = setOf(FlavorKey.CITRUS),
        ingredients: List<IngredientSnapshot> = listOf(gin()),
        recipe: RecipeSnapshot? = RecipeSnapshot(listOf(gin()), stepCount = 3),
    ) = PublishCandidate(
        slug = "gin-tonic",
        tastingNote = tastingNote,
        isClassic = isClassic,
        story = story,
        styles = styles,
        stylePrimary = primary,
        aromaTags = aroma,
        standardRecipe = recipe,
        ingredients = ingredients,
    )
}
