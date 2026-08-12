package kr.kcocktail.search.facet

import kr.kcocktail.common.taxonomy.BaseSpirit
import kr.kcocktail.common.taxonomy.FlavorKey
import kr.kcocktail.common.taxonomy.Slugged
import kr.kcocktail.common.taxonomy.StyleKey
import kr.kcocktail.common.taxonomy.SweetLevel
import kr.kcocktail.common.taxonomy.Technique
import kr.kcocktail.search.list.AbvBand
import kr.kcocktail.search.list.CocktailFilter
import kr.kcocktail.search.list.CocktailListSql
import kr.kcocktail.search.list.Sql
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 패싯 카운트 조회 (ISSUE-019 · `FR-SEARCH-002`).
 *
 * ## 0 과 부재를 구분한다
 *
 * - **0**: 코퍼스에 그 값을 가진 발행분은 있는데 현재 필터에서 0건 → 키가 있고 값이 0.
 *   클라이언트가 **비활성 칩**으로 그린다 (`NFR-A-06` 은 개수도 읽어 주라고 했다).
 * - **부재**: 코퍼스에 아예 없는 값 → **키 자체를 넣지 않는다.**
 *   `floral` 처럼 항목이 0건이면 필터에 띄우지 않는다 (ADR-0002 §5).
 *
 * 둘을 합치면 "영영 없는 값" 과 "지금 조합에서만 없는 값" 이 같아 보인다 —
 * 앞은 고를 이유가 없고 뒤는 다른 필터를 풀면 살아난다.
 */
@Component
class FacetReader(private val jdbc: NamedParameterJdbcTemplate) {

    @Transactional(readOnly = true)
    fun counts(filter: CocktailFilter): FacetCounts {
        // 코퍼스에 존재하는 값 — 필터를 걸지 않은 전체 발행분 기준이다.
        // 이것이 "키를 넣을지" 를 정하고, 위의 필터 결과가 "값" 을 정한다.
        val empty = CocktailFilter()

        return FacetCounts(
            base = axis(BaseSpirit.entries, FacetSql.byColumn(BASE, filter.withoutBase()), FacetSql.byColumn(BASE, empty)),
            style = axis(StyleKey.entries, FacetSql.styles(filter.withoutStyle()), FacetSql.styles(empty)),
            method = axis(Technique.entries, FacetSql.byColumn(METHOD, filter.withoutMethod()), FacetSql.byColumn(METHOD, empty)),
            sweet = axis(SweetLevel.entries, FacetSql.byColumn(SWEET, filter.withoutSweet()), FacetSql.byColumn(SWEET, empty)),
            abv = axis(AbvBand.entries, FacetSql.abvBands(filter.withoutAbv()), FacetSql.abvBands(empty)),
            // 향·맛만 같은 축 선택을 유지한다 (SPEC-07 §3.2)
            flavor = axis(FlavorKey.entries, FacetSql.flavors(filter.keepFlavor()), FacetSql.flavors(empty)),
        )
    }

    /**
     * @param present 코퍼스에 존재하는 값 — 키 목록을 정한다
     * @param current 현재 필터에서의 카운트 — 값을 정한다. 없으면 0
     */
    private fun <T : Slugged> axis(
        all: List<T>,
        current: Sql,
        present: Sql,
    ): Map<String, Long> {
        val existing = query(present).keys
        val counted = query(current)

        // 열거 순서를 따른다 — 응답 키 순서가 흔들리면 클라이언트 스냅샷 테스트가 매번 깨진다
        return all.map { it.slug }
            .filter { it in existing }
            .associateWith { counted[it] ?: 0L }
    }

    private fun query(sql: Sql): Map<String, Long> =
        jdbc.query(sql.text, sql.params) { rs, _ ->
            rs.getString("value") to rs.getLong("n")
        }.filter { it.first != null }.toMap()

    private companion object {
        const val BASE = "base_spirit"
        const val METHOD = "method"
        const val SWEET = "sweetness"
    }
}

/**
 * SPEC-07 §3.2 응답. 축별 `슬러그 → 개수` 맵이다.
 *
 * 목록 API 와 **같은 쿼리스트링**을 받는다 (SPEC-05 §5 "UI 계약은 두 단계에서 동일하다").
 */
data class FacetCounts(
    val base: Map<String, Long>,
    val style: Map<String, Long>,
    val method: Map<String, Long>,
    val sweet: Map<String, Long>,
    val abv: Map<String, Long>,
    val flavor: Map<String, Long>,
)
