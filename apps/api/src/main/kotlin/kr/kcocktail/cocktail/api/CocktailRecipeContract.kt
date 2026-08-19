package kr.kcocktail.cocktail.api

import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal

/**
 * 어드민이 **표준 레시피**를 다루는 창구 (ISSUE-051 · `NFR-O-01` · [G-38]).
 *
 * ## 왜 이제야 생겼나
 *
 * 이슈 025 는 칵테일의 스칼라 필드만 열었다. 그런데 발행 게이트는 표준 레시피를 요구한다
 * (`GATE-COCKTAIL-03`·`04`·`06`) — **레시피를 쓸 길이 없으니 어드민만으로는 영영 발행할 수
 * 없었다.** `NFR-O-01` ("에디터가 개발자 없이 신규 1건 발행")이 닫히지 않던 이유다.
 *
 * ## 통째로 갈아 끼운다
 *
 * 줄 단위로 고치지 않고 [SaveRecipeRequest] 하나로 덮는다. 순서가 데이터의 일부라서다 —
 * 재료 `position` 과 스텝 `step_no` 는 1부터 연속이어야 하고([Recipe.setIngredients]),
 * 줄마다 `PATCH` 를 열면 순서를 다시 매기는 코드가 서버와 화면 두 벌이 된다.
 *
 * ## 게이트를 여기서 검사하지 않는다
 *
 * 재료가 비어 있어도 저장된다. **저장은 저장이고 판정은 발행이다** (`PRIN-T05`) —
 * 쓰다 만 초안을 못 저장하게 하면 에디터는 메모장에 쓰게 되고, 그때 게이트는 아무것도
 * 지키지 못한다. 발행 시점에 `PublishGate` 가 전부 본다.
 */
interface RecipeAdminFacade {

    /**
     * 표준 레시피. **아직 없으면 빈 것을 돌려준다** ([AdminRecipeResponse.exists] 가 `false`).
     *
     * 404 로 답하지 않는 이유: `draft` 에 레시피가 없는 것은 정상 상태다. 편집 화면이
     * 404 를 "없는 칵테일" 과 구분하지 못하면 새로 쓰기 시작할 수가 없다.
     */
    fun find(cocktailId: Long): AdminRecipeResponse

    /** 표준 레시피를 만들거나 덮는다. 저장 직후 `abv_calculated` 를 다시 채운다 (이슈 011). */
    fun save(cocktailId: Long, request: SaveRecipeRequest): AdminRecipeResponse
}

/**
 * 저장 요청.
 *
 * `versionType` 이 **없다.** 이 창구는 표준 레시피만 다룬다 — `bar_signature` 는 파트너가
 * 쓰는 것이고 Phase 1b 다 (`PRIN-D03`). 필드를 두면 어드민에서 바 버전을 만들 수 있게
 * 되고, 그러면 `INV-COCKTAIL-07`(표준 정확히 1개)의 판정 주체가 흐려진다.
 */
data class SaveRecipeRequest(
    /** DECISIONS §1.2 — 잔 수는 1~8. 화면의 배수 환산이 그 범위를 쓴다 (`FR-COCKTAIL-019`). */
    @field:Min(1) @field:Max(8) val servingCount: Short = 1,

    val note: String? = null,

    @field:Valid val ingredients: List<RecipeIngredientInput> = emptyList(),
    @field:Valid val steps: List<RecipeStepInput> = emptyList(),
)

/**
 * 재료 한 줄.
 *
 * `position` 이 없다 — **배열 순서가 곧 순서**다. 번호를 받으면 중복·구멍을 서버가 다시
 * 정리해야 하고, 화면이 보낸 번호와 저장된 번호가 달라진다.
 */
data class RecipeIngredientInput(
    val ingredientId: Long,

    /** `top_up`(채운다)은 수량이 없다 — 잔 크기에 종속되면 잔 수 환산이 틀어진다. */
    val amount: BigDecimal? = null,
    val unit: String? = null,

    /** `1조각` 처럼 **배수 계산에서 빼는** 표기 (`FR-COCKTAIL-019`). */
    val amountLabel: String? = null,

    val role: String? = null,
    val isOptional: Boolean = false,
    val substituteIngredientId: Long? = null,
    val substituteNote: String? = null,

    /**
     * `null` 이면 재료 카테고리의 기본값을 쓴다 (`R-F2.2-5`).
     * **`false` 를 명시한 것과 구분해야** 언제 기본값을 쓸지 알 수 있다.
     */
    val countsForStock: Boolean? = null,
)

data class RecipeStepInput(
    @field:NotBlank val text: String,
    /** 기법 사전으로 가는 참조. 없어도 된다. */
    val techniqueRef: String? = null,
)

/**
 * 응답. 재료 이름과 승인 여부를 **함께** 싣는다.
 *
 * 편집 화면이 `ingredientId` 만 받으면 줄마다 이름을 다시 물어야 한다. 승인 여부는
 * 게이트가 막을 것을 미리 보여 주려는 것이다 — 미승인 재료로도 저장은 되지만
 * 발행은 안 된다 (DECISIONS §1.1 · `GATE-COCKTAIL-04`).
 */
data class AdminRecipeResponse(
    val cocktailId: Long,

    /** `false` 면 아직 쓰지 않은 것이다. 없는 칵테일은 404 로 따로 답한다. */
    val exists: Boolean,

    val servingCount: Short,
    val note: String?,
    val ingredients: List<AdminRecipeIngredient>,
    val steps: List<AdminRecipeStep>,

    /** 저장 직후 다시 계산된 값 (이슈 011). 수동 오버라이드는 칵테일 쪽에 있다. */
    val abvCalculated: BigDecimal?,
)

data class AdminRecipeIngredient(
    val position: Short,
    val ingredientId: Long,
    val ingredientSlug: String?,
    val nameKo: String?,
    val isApproved: Boolean,
    val amount: BigDecimal?,
    val unit: String?,
    val amountLabel: String?,
    val role: String?,
    val isOptional: Boolean,
    val substituteIngredientId: Long?,
    val substituteNote: String?,
    val countsForStock: Boolean,
)

data class AdminRecipeStep(
    val stepNo: Short,
    val text: String,
    val techniqueRef: String?,
)
