package kr.mut.search.list

import kr.mut.common.web.page.PageQuery
import kr.mut.common.web.page.PageResponse
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet

/**
 * 목록 조회의 읽기 모델 (SPEC-05 §3 — `SEARCH ──reads──▶ COCKTAIL`).
 *
 * JPA 가 아니라 JDBC 인 이유는 셋이다.
 *
 * - 조건이 여섯 축이고 **결합 규칙이 축마다 다르다.** JPQL 로 짜면 조건 조립이 문자열 연결로 돌아온다
 * - 자식 축 두 개를 **스칼라 서브쿼리**로 가져와야 `LIMIT` 이 칵테일 수를 자른다 (N+1 도 없다)
 * - 타 모듈의 엔티티를 들이지 않는다 (`PRIN-T03`). 여기서 필요한 것은 응답 모양뿐이다
 */
@Component
class CocktailListReader(private val jdbc: NamedParameterJdbcTemplate) {

    @Transactional(readOnly = true)
    fun find(filter: CocktailFilter, page: PageQuery): PageResponse<CocktailListItem> {
        val total = count(filter)

        // 마지막 페이지를 넘겨 요청하면 문장을 날릴 이유가 없다.
        val items = if (page.offset >= total) emptyList()
        else CocktailListSql.select(filter, page).let { jdbc.query(it.text, it.params, ROW_MAPPER) }

        return PageResponse.of(items, page, total)
    }

    private fun count(filter: CocktailFilter): Long =
        CocktailListSql.count(filter).let { jdbc.queryForObject(it.text, it.params, Long::class.java) } ?: 0L

    private companion object {
        val ROW_MAPPER = RowMapper { rs, _ ->
            CocktailListItem(
                slug = rs.getString("slug"),
                nameKo = rs.getString("name_ko"),
                nameEn = rs.getString("name_en"),
                summary = rs.getString("summary"),
                baseSpirit = rs.getString("base_spirit"),
                stylePrimary = rs.getString("style_primary"),
                styles = rs.slugs("styles"),
                method = rs.getString("method"),
                sweetness = rs.getString("sweetness"),
                aromaTags = rs.slugs("aroma_tags"),
                // 표시값 하나. abv_calculated·abv_override 는 SELECT 목록에도 없다 (SPEC-07 §5).
                abv = rs.getBigDecimal("abv"),
                glassType = rs.getString("glass_type"),
                isClassic = rs.getBoolean("is_classic"),
            )
        }

        /** `array_agg` 는 자식 행이 없으면 `NULL` 이다 — 빈 배열로 편다. */
        @Suppress("UNCHECKED_CAST")
        fun ResultSet.slugs(column: String): List<String> =
            (getArray(column)?.array as? Array<String>)?.toList().orEmpty()
    }
}
