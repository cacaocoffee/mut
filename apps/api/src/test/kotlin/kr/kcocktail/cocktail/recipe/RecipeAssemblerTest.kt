package kr.kcocktail.cocktail.recipe

import kr.kcocktail.ingredient.api.IngredientFacade
import kr.kcocktail.ingredient.api.IngredientView
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.math.BigDecimal

/**
 * ISSUE-010 RED 15 · 17~19 — 조립과 배수 판정. DB 없이 돈다.
 *
 * ## 기본값이 Facade 에서 온다
 *
 * `counts_for_stock` 은 재료 카테고리가 정한다 (`R-F2.2-5`). 그 값을 알려면 `ingredient` 를
 * 봐야 하는데 **엔티티가 직접 보면 모듈 의존이 도메인 계층에 박힌다** (`PRIN-T03`).
 * `RecipeAssembler` 가 `IngredientFacade` 만 쓴다.
 */
class RecipeAssemblerTest {

    // ── RED 17~19 : counts_for_stock ──────────────────────────────────────

    /** 이슈 008 의 `IngredientFacade.defaultCountsForStock` 이 여기서 처음 쓰인다. */
    @Test
    fun `RED17 - 값을 안 주면 재료 카테고리의 기본값을 쓴다`() {
        val assembler = RecipeAssembler(FakeFacade(defaults = mapOf(GIN to true)))

        val drafts = assembler.toDrafts(listOf(RecipeAssembler.Input(ingredientId = GIN)))

        assertThat(drafts.single().countsForStock).isTrue()
    }

    /** `PRIN-D01` 의 요체 — 없으면 **민트 잎 하나 없다고 모히토가 안 나온다.** */
    @Test
    fun `RED18 - garnish 재료는 기본이 false 다`() {
        val assembler = RecipeAssembler(FakeFacade(defaults = mapOf(MINT to false)))

        val drafts = assembler.toDrafts(listOf(RecipeAssembler.Input(ingredientId = MINT)))

        assertThat(drafts.single().countsForStock).isFalse()
    }

    /**
     * 가니시가 그 칵테일의 정체성인 경우가 있다 — 그때는 레시피가 뒤집는다.
     * `Input.countsForStock` 이 nullable 인 것이 요점이다: `false` 를 **명시한 것**과
     * "안 정했다"를 구분해야 기본값을 언제 쓸지 알 수 있다.
     */
    @Test
    fun `RED19 - 명시하면 기본값을 덮어쓴다`() {
        val assembler = RecipeAssembler(FakeFacade(defaults = mapOf(MINT to false, GIN to true)))

        val drafts = assembler.toDrafts(
            listOf(
                RecipeAssembler.Input(ingredientId = MINT, countsForStock = true),
                RecipeAssembler.Input(ingredientId = GIN, countsForStock = false),
            ),
        )

        assertThat(drafts.map { it.countsForStock })
            .`as`("명시한 값이 이긴다 — 양방향으로")
            .containsExactly(true, false)
    }

    /** 명시하지 않은 것만 Facade 를 부른다. 매번 부르면 재료 수만큼 왕복한다. */
    @Test
    fun `명시한 재료는 Facade 를 부르지 않는다`() {
        val facade = FakeFacade(defaults = mapOf(GIN to true))
        val assembler = RecipeAssembler(facade)

        assembler.toDrafts(listOf(RecipeAssembler.Input(ingredientId = GIN, countsForStock = false)))

        assertThat(facade.defaultCalls).isEmpty()
    }

    // ── RED 15 : 배수 계산 제외 (FR-COCKTAIL-019) ─────────────────────────

    /**
     * `amount_label` 이 있으면 배수 대상이 아니다. 잔 수를 2배로 해도
     * **"1조각"이 "2조각"이 되지 않는다.**
     *
     * 환산 자체는 FE(이슈 043)가 하지만 **판정은 서버가 준다** — 그래야 FE 와
     * 어드민 미리보기가 같은 규칙을 쓴다. 두 곳이 다르면 에디터가 미리보기에서 본 것과
     * 사용자가 보는 것이 달라진다.
     */
    @Test
    fun `RED15 - amount_label 이 있으면 배수에서 제외된다`() {
        assertAll(
            listOf<() -> Unit>(
                {
                    assertThat(RecipeIngredient.isScalable(BigDecimal("45"), null))
                        .`as`("45ml 는 배수 대상")
                        .isTrue()
                },
                {
                    assertThat(RecipeIngredient.isScalable(BigDecimal("1"), "1조각"))
                        .`as`("1조각은 배수 대상이 아니다")
                        .isFalse()
                },
                {
                    assertThat(RecipeIngredient.isScalable(null, null))
                        .`as`("수량이 없으면(top_up) 곱할 것이 없다")
                        .isFalse()
                },
                { assertThat(RecipeIngredient.isScalable(null, "가득")).isFalse() },
            ),
        )
    }

    /** `top_up` 은 수량이 필요 없다 — ml 로 고정하면 잔 크기에 종속된다. */
    @Test
    fun `단위마다 수량 필요 여부가 다르다`() {
        assertThat(MeasureUnit.TOP_UP.amountRequired).isFalse()
        assertThat(MeasureUnit.entries.filterNot { it.amountRequired }.map { it.slug })
            .containsExactly("top_up")
    }

    // ── 헬퍼 ───────────────────────────────────────────────────────────────

    /** 호출을 기록해 "명시하면 안 부른다"를 확인한다. */
    private class FakeFacade(private val defaults: Map<Long, Boolean>) : IngredientFacade {
        val defaultCalls = mutableListOf<Long>()

        override fun defaultCountsForStock(ingredientId: Long): Boolean {
            defaultCalls += ingredientId
            return defaults[ingredientId] ?: error("기본값을 모르는 재료: $ingredientId")
        }

        override fun findApproved(ids: Collection<Long>): List<IngredientView> = emptyList()
        override fun findAll(ids: Collection<Long>): List<IngredientView> = emptyList()
        override fun requiresSubstitute(ingredientId: Long): Boolean = false

        /** 이 테스트는 슬러그 조회를 쓰지 않는다 — 재료 사전(이슈 023)의 경로다. */
        override fun findApprovedBySlug(slug: String): kr.kcocktail.ingredient.api.IngredientView? = null
    }

    private companion object {
        const val GIN = 1L
        const val MINT = 2L
    }
}
