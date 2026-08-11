package kr.kcocktail.ingredient

import kr.kcocktail.common.web.error.DomainViolationException
import kr.kcocktail.ingredient.domain.DomesticAvailability
import kr.kcocktail.ingredient.domain.Ingredient
import kr.kcocktail.ingredient.domain.IngredientCategory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

/**
 * ISSUE-008 — 재료 도메인 규칙. DB 없이 돈다.
 *
 * `INV-INGREDIENT-01` 은 DB CHECK 에도 있다 (`IngredientSchemaTest`).
 * **앱에서도 막는 이유**는 에디터에게 어느 항목이 왜 막혔는지 알려 주기 위해서다 —
 * DB 제약 위반은 `violations` 를 만들지 못한다 (`FR-ADMIN-003`).
 */
class IngredientDomainTest {

    // ── RED 2~4 : counts_for_stock 기본값 (R-F2.2-5) ──────────────────────

    /**
     * `PRIN-D01` 의 요체다. 이게 없으면 **민트 잎 하나 없다고 모히토가 안 나온다.**
     */
    @Test
    fun `RED2 - garnish 는 counts_for_stock 기본값이 false 다`() {
        assertThat(IngredientCategory.GARNISH.defaultCountsForStock)
            .`as`("가니시는 없어도 만들 수 있다")
            .isFalse()
    }

    @Test
    fun `RED3 - 그 외 카테고리는 기본값이 true 다`() {
        assertAll(
            (IngredientCategory.entries - IngredientCategory.GARNISH)
                .map<IngredientCategory, () -> Unit> { category ->
                    {
                        assertThat(category.defaultCountsForStock)
                            .`as`("%s", category.slug)
                            .isTrue()
                    }
                },
        )
    }

    /** 이슈 010 의 `recipe_ingredient` 가 이 함수를 호출한다. */
    @Test
    fun `RED4 - 기본값 결정 함수를 제공한다`() {
        assertThat(IngredientCategory.defaultCountsForStock(IngredientCategory.GARNISH)).isFalse()
        assertThat(IngredientCategory.defaultCountsForStock(IngredientCategory.SPIRIT)).isTrue()

        assertThat(ingredient(category = IngredientCategory.GARNISH).defaultCountsForStock).isFalse()
        assertThat(ingredient(category = IngredientCategory.MIXER).defaultCountsForStock).isTrue()
    }

    @Test
    fun `카테고리 7종이 스펙과 일치한다`() {
        assertThat(IngredientCategory.entries.map { it.slug })
            .containsExactly("spirit", "liqueur", "bitters", "syrup", "juice", "garnish", "mixer")
    }

    // ── RED 7~10 : INV-INGREDIENT-01 (앱 쪽) ──────────────────────────────

    @Test
    fun `RED7-8 - 미유통이면 대체재 안내가 없으면 거부된다`() {
        assertAll(
            listOf(DomesticAvailability.IMPORT_ONLY, DomesticAvailability.UNAVAILABLE)
                .map<DomesticAvailability, () -> Unit> { availability ->
                    {
                        assertThatThrownBy { ingredient(availability = availability, note = null) }
                            .`as`("%s", availability.slug)
                            .isInstanceOf(DomainViolationException::class.java)
                    }
                },
        )
    }

    /** `FR-ADMIN-003` — 어느 항목이 왜 막혔는지 알려 준다. 코드로 분기할 수 있어야 한다. */
    @Test
    fun `거부 시 INV-INGREDIENT-01 코드와 필드가 나온다`() {
        val thrown = runCatching {
            ingredient(availability = DomesticAvailability.IMPORT_ONLY, note = null)
        }.exceptionOrNull() as DomainViolationException

        assertThat(thrown.violations).singleElement().satisfies({
            assertThat(it.code).isEqualTo("INV-INGREDIENT-01")
            assertThat(it.field).isEqualTo("substituteNote")
            assertThat(it.message).isNotBlank()
        })
    }

    @Test
    fun `RED9 - common 과 specialty 는 안내가 없어도 된다`() {
        ingredient(availability = DomesticAvailability.COMMON, note = null)
        ingredient(availability = DomesticAvailability.SPECIALTY, note = null)
    }

    /** 코틀린 `isNullOrBlank()` 와 DB 의 `~ '\S'` 가 같은 것을 공백으로 쳐야 한다. */
    @Test
    fun `RED10 - 공백만 있는 안내는 없는 것으로 친다`() {
        assertAll(
            listOf("", " ", "   ", "\t", "\n", " \t\n ").map<String, () -> Unit> { blank ->
                {
                    assertThatThrownBy {
                        ingredient(availability = DomesticAvailability.UNAVAILABLE, note = blank)
                    }.`as`("%s (길이 %d)", blank.replace("\n", "\\n").replace("\t", "\\t"), blank.length)
                        .isInstanceOf(DomainViolationException::class.java)
                }
            },
        )
    }

    @Test
    fun `needsSubstitute 가 DB CHECK 와 같은 조건이다`() {
        assertThat(DomesticAvailability.entries.filter { it.needsSubstitute }.map { it.slug })
            .`as`("ck_ingredient__substitute 의 NOT IN 목록과 일치해야 한다")
            .containsExactly("import_only", "unavailable")
    }

    // ── RED 14 : 광고 라벨 ────────────────────────────────────────────────

    /** 표현은 FE 가 하지만 **판단은 서버가 내린다** — 클라이언트에 맡기면 안 붙이는 것이 생긴다. */
    @Test
    fun `RED14 - is_sponsored 면 라벨 표기 플래그가 켜진다`() {
        val target = ingredient()
        target.addBrand(name = "탱커레이")
        target.addBrand(name = "협찬 브랜드", isSponsored = true)

        assertThat(target.brands.map { it.requiresAdLabel }).containsExactly(false, true)
    }

    @Test
    fun `RED12 - 브랜드 광고성 기본값은 false 다`() {
        val target = ingredient()
        target.addBrand(name = "봄베이 사파이어")

        assertThat(target.brands.single().isSponsored).isFalse()
    }

    // ── RED 15 : 승인제 ───────────────────────────────────────────────────

    @Test
    fun `RED15 - 신규 재료는 미승인이다`() {
        assertThat(ingredient().isApproved)
            .`as`("DECISIONS §1.1 — 에디터가 요청하고 admin 이 승인한다")
            .isFalse()
    }

    @Test
    fun `승인하면 켜진다`() {
        val target = ingredient()
        target.approve()
        assertThat(target.isApproved).isTrue()
    }

    // ── 헬퍼 ───────────────────────────────────────────────────────────────

    private fun ingredient(
        slug: String = "gin",
        category: IngredientCategory = IngredientCategory.SPIRIT,
        availability: DomesticAvailability = DomesticAvailability.COMMON,
        note: String? = null,
    ) = Ingredient(
        slug = slug,
        nameKo = "진",
        nameEn = "gin",
        categorySlug = category.slug,
        availabilitySlug = availability.slug,
        substituteNote = note,
    )
}
