package kr.kcocktail.ingredient.api

/**
 * 타 모듈이 재료를 보는 **유일한 창구** (`PRIN-T03` · SPEC-05 §3).
 *
 * 엔티티를 내보내지 않는다. 내보내면 영속성 컨텍스트가 모듈을 넘어가고,
 * 지연 로딩이 남의 트랜잭션에서 터진다. 경계 테스트(이슈 001)가 이것을 강제한다.
 *
 * 쓰는 쪽:
 * - 이슈 010 `recipe_ingredient` → [defaultCountsForStock]
 * - 이슈 013 `GATE-COCKTAIL-04` (마스터 참조) · `GATE-COCKTAIL-06` (대체재) → [findApproved] · [requiresSubstitute]
 */
interface IngredientFacade {

    /**
     * 승인된 재료만. 미승인은 `draft` 에는 쓸 수 있지만 발행 게이트에서 막힌다
     * (DECISIONS §1.1) — 그 판정을 이 메서드가 받쳐 준다.
     */
    fun findApproved(ids: Collection<Long>): List<IngredientView>

    /** 존재 여부와 무관하게 전부. 게이트가 "없는 재료"와 "미승인 재료"를 구분해야 한다. */
    fun findAll(ids: Collection<Long>): List<IngredientView>

    /** 역검색에서 셀지의 기본값 (`R-F2.2-5`). 레시피가 뒤집을 수 있다. */
    fun defaultCountsForStock(ingredientId: Long): Boolean

    /** `GATE-COCKTAIL-06` — 미유통이라 대체재 명시가 필요한가. */
    fun requiresSubstitute(ingredientId: Long): Boolean
}

/**
 * 모듈 밖으로 나가는 재료의 모습.
 *
 * `id` 가 있는 이유는 **모듈 간 참조 키**라서다. 공개 API 응답에는 `slug` 만 나간다
 * (SPEC-07 §1.1) — 그 변환은 이슈 023 의 `web` 계층이 한다.
 */
data class IngredientView(
    val id: Long,
    val slug: String,
    val nameKo: String,
    val nameEn: String,
    val categorySlug: String,
    val availabilitySlug: String,

    /**
     * 도수 자동 계산의 입력 (`FR-COCKTAIL-006`, 이슈 011).
     * 주스처럼 0 이거나 아직 안 채운 재료는 `null` 이다.
     */
    val abv: java.math.BigDecimal?,

    val isApproved: Boolean,
    val countsForStockByDefault: Boolean,
    val requiresSubstitute: Boolean,
    val substituteNote: String?,
    /** `INV-INGREDIENT-02` — 라벨을 붙여야 하는 브랜드가 하나라도 있는가. */
    val hasSponsoredBrand: Boolean,
)
