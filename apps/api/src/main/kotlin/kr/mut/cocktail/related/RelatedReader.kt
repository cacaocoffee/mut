package kr.mut.cocktail.related

import kr.mut.common.web.error.ResourceNotFoundException
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 배리에이션 조회 (ISSUE-021 · `FR-COCKTAIL-024`).
 *
 * ## 후보를 좁혀서 가져오고 순위는 코드가 매긴다
 *
 * `WHERE` 로 "스타일이 같거나 기주가 같은 것" 까지만 좁힌다 —
 * `(status, style_primary)` · `(status, base_spirit)` 인덱스를 타는 조건이다 (SPEC-06 §5).
 * 순위는 [RelatedRanker] 가 매긴다. `ORDER BY` 표현식에 넣으면 읽는 사람이
 * SQL 에서 순위 규칙을 역추적해야 하고, 그때 "둘 다 일치가 최상위인가" 가 다시 열린다.
 *
 * 상한(8건)을 SQL 이 아니라 코드에서 자르는 이유도 같다 — 자르기 전에 순위가 정해져야 한다.
 * 후보가 500종을 넘지 않으므로 전부 가져와 정렬해도 싸다.
 *
 * ## N+1 이 없다
 *
 * 한 번의 조회로 끝난다 (RED 19). 상세 화면이 부르는 경로라 종수만큼 곱해지면
 * SSG 빌드가 그대로 느려진다.
 */
@Component
class RelatedReader(private val jdbc: NamedParameterJdbcTemplate) {

    @Transactional(readOnly = true)
    fun related(slug: String): List<Related> {
        val target = findRef(slug) ?: throw ResourceNotFoundException()

        val candidates = jdbc.query(
            """
            SELECT slug, name_ko, name_en, summary, style_primary, base_spirit
              FROM cocktail
             WHERE status = 'published'
               AND slug <> :slug
               AND (style_primary = :style OR base_spirit = :base)
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("slug", slug)
                .addValue("style", target.stylePrimary)
                .addValue("base", target.baseSpirit),
            ROW_MAPPER,
        )

        return RelatedRanker.rank(target, candidates)
    }

    /**
     * 대상은 **발행분만** 본다 — `draft` 상세가 404 인데(SPEC-07 §5)
     * 배리에이션만 200 이면 존재가 새어 나간다.
     */
    private fun findRef(slug: String): CocktailRef? = jdbc.query(
        """
        SELECT slug, name_ko, name_en, summary, style_primary, base_spirit
          FROM cocktail
         WHERE slug = :slug AND status = 'published'
        """.trimIndent(),
        MapSqlParameterSource().addValue("slug", slug),
        ROW_MAPPER,
    ).firstOrNull()

    private companion object {
        val ROW_MAPPER = org.springframework.jdbc.core.RowMapper { rs, _ ->
            CocktailRef(
                slug = rs.getString("slug"),
                nameKo = rs.getString("name_ko"),
                nameEn = rs.getString("name_en"),
                summary = rs.getString("summary"),
                stylePrimary = rs.getString("style_primary"),
                baseSpirit = rs.getString("base_spirit"),
            )
        }
    }
}
