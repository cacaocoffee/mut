package kr.mut.cocktail.api

/**
 * 타 모듈이 칵테일을 보는 **유일한 창구** (`PRIN-T03` · SPEC-05 §3).
 *
 * ## 왜 이 이슈가 만드는가
 *
 * 이슈 023(재료 사전의 "이 재료를 쓰는 칵테일")과 031(북마크 대상 조회)이
 * `cocktail` 테이블을 직접 조인할 수 없다. 조회 계약이 먼저 있어야 두 이슈가 막히지 않는다.
 *
 * ## 소유가 파일 단위다
 *
 * `cocktail/api/` 를 통째로 소유하지 않는다 — `PublishGate.kt` 는 이슈 013 이 만든다.
 * 디렉터리째 잡으면 두 세션이 충돌한다 (CONVENTIONS §4).
 */
interface CocktailFacade {

    /** 공개 조회. `draft`·`archived` 는 없는 것으로 취급한다 (SPEC-07 §5). */
    fun findPublished(slug: String): CocktailSummary?

    fun findPublishedByIds(ids: Collection<Long>): List<CocktailSummary>

    /** 존재 여부와 무관하게. 어드민·게이트가 상태를 구분해야 한다. */
    fun findAny(slug: String): CocktailSummary?

    /**
     * **이 재료를 쓰는 칵테일** (`R-F1.3-1`, 이슈 023).
     *
     * `ingredient` 가 `cocktail` 테이블을 직접 조인하면 경계 위반이다 (`PRIN-T03`).
     * 조인은 여기서 하고 결과만 넘긴다.
     *
     * 범위는 이슈 023 의 확정분이다.
     * - **표준 레시피만** — `bar_signature`(Phase 1b)에만 쓰인 재료는 세지 않는다
     * - **발행분만** — `draft` 는 없는 것이다 (SPEC-07 §5)
     * - **대체재로만 등장하면 제외** — "이 재료를 쓰는" 이 아니다
     * - 선택 재료(`is_optional`)는 **포함**한다
     *
     * `recipe_ingredient(ingredient_id)` 인덱스를 탄다 (SPEC-06 §5).
     */
    fun findPublishedByIngredient(
        ingredientId: Long,
        limit: Int,
        offset: Int,
    ): List<CocktailIngredientUsage>

    /** 위 조건의 총 건수. 페이징이 총계를 알아야 한다. */
    fun countPublishedByIngredient(ingredientId: Long): Long
}

/**
 * 어떤 재료가 **그 칵테일에서 어떻게 쓰이는가** (이슈 023 RED 25).
 *
 * `isOptional` 을 칵테일과 함께 내보내는 이유: 선택 재료로 쓰인 칵테일도 사전에 올리되
 * **표시해야** 한다. 표시가 없으면 필수로 오해한다.
 *
 * 한 레시피가 같은 재료를 두 줄에 쓸 수 있다 (예: 진 30ml 필수 + 15ml 선택).
 * 그때는 **한 줄이라도 필수면 필수**다 — 덜 넣어도 되는 것과 없어도 되는 것은 다르다.
 */
data class CocktailIngredientUsage(
    val cocktail: CocktailSummary,
    val isOptional: Boolean,
)

/**
 * 모듈 밖으로 나가는 칵테일의 모습.
 *
 * `abv` 가 **하나뿐**이다 — 계산인지 수동인지는 내부 사정이라 밖으로 내보내지 않는다
 * (SPEC-07 §5). `id` 는 모듈 간 참조 키이고, 공개 응답의 `slug` 변환은
 * `web` 계층(이슈 018·020)이 한다.
 */
data class CocktailSummary(
    val id: Long,
    val slug: String,
    val nameKo: String,
    val nameEn: String,
    val summary: String,
    val baseSpiritSlug: String,
    val stylePrimarySlug: String,
    val styleSlugs: Set<String>,
    val methodSlug: String,
    val sweetnessSlug: String,
    val aromaTagSlugs: Set<String>,
    val abv: java.math.BigDecimal?,
    val statusSlug: String,
)
