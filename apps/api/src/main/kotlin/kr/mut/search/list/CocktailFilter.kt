package kr.mut.search.list

import kr.mut.common.taxonomy.BaseSpirit
import kr.mut.common.taxonomy.FlavorKey
import kr.mut.common.taxonomy.Slugged
import kr.mut.common.taxonomy.StyleKey
import kr.mut.common.taxonomy.SweetLevel
import kr.mut.common.taxonomy.Technique
import kr.mut.common.web.error.BadRequestException

/**
 * `GET /cocktails` 의 필터 6축 (SPEC-07 §3.1 · `FR-SEARCH-001`).
 *
 * ## 향·맛만 AND 다
 *
 * ```
 * base=gin,vodka      진 **또는** 보드카
 * flavor=citrus,herbal 시트러스 **그리고** 허브   ← 이것만 다르다
 * ```
 *
 * 이 비대칭이 이 엔드포인트에서 가장 틀리기 쉬운 지점이라 [flavor] 의 타입 이름부터
 * 다르게 잡았다 — 셋 다 `Set` 이면 읽는 사람이 결합 규칙을 SQL 에서 역추적해야 한다.
 * SPEC-07 §3.1 이 이유를 적었다: "시트러스 **그리고** 허브" 를 원하지 "또는" 이 아니다.
 *
 * ## 축이 다르면 AND 로 묶인다
 *
 * `base=gin&style=sour` 는 진이면서 사워인 것이다. 축 안에서만 OR 다.
 */
data class CocktailFilter(
    /** OR */
    val base: Set<BaseSpirit> = emptySet(),
    /**
     * OR. **`style_primary` 가 아니라 보유 스타일 전체와 맞춘다** (DECISIONS §1.11) —
     * 카테고리 경로는 `style_primary` 지만 필터는 `styles` 다.
     */
    val style: Set<StyleKey> = emptySet(),
    /** OR */
    val method: Set<Technique> = emptySet(),
    /** **단일값.** 복수로 오면 400 이다 (DECISIONS §1.11). */
    val sweet: SweetLevel? = null,
    /** OR — 4구간 (ADR-0003). 연속값이 아니다 */
    val abv: Set<AbvBand> = emptySet(),
    /** **AND** ← 유일하게 다르다 (SPEC-07 §3.1) */
    val flavor: AllOf<FlavorKey> = AllOf.none(),
    /** 이름 검색. 초성·별칭까지 보는 정밀 검색은 이슈 024 다 */
    val q: String? = null,
) {
    val isEmpty: Boolean
        get() = base.isEmpty() && style.isEmpty() && method.isEmpty() &&
            sweet == null && abv.isEmpty() && flavor.values.isEmpty() && q == null
}

/**
 * "전부 가진 것" — 결합이 AND 인 축에만 쓴다.
 *
 * `Set` 을 쓰면 나머지 다섯 축과 구분이 안 되고, 그 순간 SQL 을 `IN` 으로 쓰는 실수가
 * 리뷰를 통과한다. 타입이 다르면 통과하지 못한다.
 */
@JvmInline
value class AllOf<T>(val values: Set<T>) {
    val size: Int get() = values.size

    companion object {
        fun <T> none() = AllOf<T>(emptySet())
    }
}

/**
 * 쿼리스트링 → [CocktailFilter].
 *
 * ## 모르는 값은 400 이다
 *
 * `base=whiskey`(오타)를 조용히 무시하면 사용자는 **필터가 걸린 줄 알고** 전체 목록을 본다.
 * 필터 결과가 링크로 공유되는 이상(`FR-SEARCH-005`) 조용한 무시는 잘못된 공유를 낳는다.
 */
object CocktailFilterParser {

    fun parse(
        base: String? = null,
        style: String? = null,
        method: String? = null,
        sweet: String? = null,
        abv: String? = null,
        flavor: String? = null,
        q: String? = null,
    ): CocktailFilter = CocktailFilter(
        base = slugs(base, "base").map { lookup(it, "base", BaseSpirit.entries) }.toSet(),
        style = slugs(style, "style").map { lookup(it, "style", StyleKey.entries) }.toSet(),
        method = slugs(method, "method").map { lookup(it, "method", Technique.entries) }.toSet(),
        sweet = single(sweet, "sweet")?.let { lookup(it, "sweet", SweetLevel.entries) },
        abv = slugs(abv, "abv").map(::abvBand).toSet(),
        flavor = AllOf(slugs(flavor, "flavor").map { lookup(it, "flavor", FlavorKey.entries) }.toSet()),
        q = query(q),
    )

    private fun slugs(raw: String?, axis: String): List<String> =
        raw?.split(',')?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()
            .also { if (raw != null && it.isEmpty()) throw BadRequestException("$axis 값이 비어 있습니다") }

    /** 당도는 단일값이다 — 복수로 오면 첫 값을 쓰지 않고 거절한다 (DECISIONS §1.11). */
    private fun single(raw: String?, axis: String): String? {
        val values = slugs(raw, axis)
        if (values.size > 1) {
            throw BadRequestException("$axis 는 하나만 지정할 수 있습니다: ${values.joinToString(",")}")
        }
        return values.firstOrNull()
    }

    private fun <T : Slugged> lookup(slug: String, axis: String, all: List<T>): T =
        all.firstOrNull { it.slug == slug }
            ?: throw BadRequestException(
                "알 수 없는 $axis 값입니다: $slug (가능: ${all.joinToString(", ") { it.slug }})",
            )

    private fun abvBand(slug: String): AbvBand =
        AbvBand.ofSlugOrNull(slug)
            ?: throw BadRequestException(
                "알 수 없는 abv 구간입니다: $slug (가능: ${AbvBand.slugs.joinToString(", ")})",
            )

    /** 빈 `q` 는 400 이다 (DECISIONS §1.9). 파라미터 자체가 없는 것과 다르다. */
    private fun query(raw: String?): String? {
        if (raw == null) return null
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) throw BadRequestException("q 가 비어 있습니다")
        return trimmed
    }
}
