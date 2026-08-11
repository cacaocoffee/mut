package kr.kcocktail.cocktail.api

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
}

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
