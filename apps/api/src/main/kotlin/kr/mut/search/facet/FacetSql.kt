package kr.mut.search.facet

import kr.mut.search.list.AllOf
import kr.mut.search.list.CocktailFilter
import kr.mut.search.list.CocktailListSql
import kr.mut.search.list.Sql

/**
 * 패싯 카운트 SQL (ISSUE-019 · SPEC-07 §3.2).
 *
 * ## 축마다 계산이 다르다 — 이 파일의 요체
 *
 * | 축 | 계산 |
 * |---|---|
 * | 기주 · 스타일 · 메이킹 · 당도 · 도수 | **같은 축의 현재 선택을 무시**하고 그 값만 골랐을 때의 수 |
 * | 향·맛 | **현재 선택에 이 태그를 더했을 때**의 수 |
 *
 * 비대칭인 이유: 앞의 다섯은 OR 라 같은 축을 하나 더 고르면 결과가 **늘어난다** —
 * 자기 선택을 반영한 카운트를 보여 주면 "보드카 0" 처럼 읽혀 다시 고를 수 없게 된다.
 * 향·맛은 AND 라 더할수록 **줄어들고**, 조합 불가능한 태그가 즉시 0 으로 떨어져야 한다
 * (`FR-SEARCH-009`).
 *
 * ## 축당 쿼리 하나다
 *
 * 값마다 세면 42 번이 나간다 (기주 10 + 스타일 9 + 메이킹 5 + 당도 4 + 도수 4 + 향 10).
 * `GROUP BY` 로 접어 축당 한 번만 돈다 — SPEC-06 §1.4 가 조인 테이블을 택한 이유가
 * "패싯 카운트가 `GROUP BY` 한 방으로 끝난다" 였다.
 *
 * ## 조건은 목록과 같은 곳에서 온다
 *
 * [CocktailListSql.where] 를 그대로 쓴다. 두 벌로 쓰면 카운트와 실제 결과가 어긋나고,
 * 그것이 `FR-SEARCH-002` 가 막으려는 상황이다 (RED 14 가 일치를 강제한다).
 */
object FacetSql {

    /** 같은 축 선택을 지운 필터로 `GROUP BY`. 컬럼이 곧 축이다. */
    fun byColumn(column: String, filter: CocktailFilter): Sql {
        val where = CocktailListSql.where(filter)
        return Sql(
            """
            SELECT c.$column AS value, count(*) AS n
              FROM cocktail c
             WHERE ${where.clause}
             GROUP BY c.$column
            """.trimIndent(),
            where.params,
        )
    }

    /**
     * 스타일은 자식 테이블이라 조인해서 센다.
     *
     * `style_primary` 가 아니라 **보유 스타일 전체**다 (DECISIONS §1.11) — 필터가 그렇게 맞추므로
     * 카운트도 같아야 한다. 카테고리 경로만 `style_primary` 를 쓴다.
     */
    fun styles(filter: CocktailFilter): Sql {
        val where = CocktailListSql.where(filter)
        return Sql(
            """
            SELECT s.style AS value, count(*) AS n
              FROM cocktail c
              JOIN cocktail_style s ON s.cocktail_id = c.id
             WHERE ${where.clause}
             GROUP BY s.style
            """.trimIndent(),
            where.params,
        )
    }

    /**
     * 향·맛 — **현재 선택을 유지한 채** 매칭된 것들을 태그별로 접는다.
     *
     * "현재 필터 + 태그 X" 의 개수는 곧 "현재 필터에 맞는 것 중 X 를 가진 것" 의 개수다.
     * 그래서 태그마다 다시 세지 않고 한 번의 `GROUP BY` 로 전부 나온다.
     *
     * 이미 고른 태그도 결과에 나온다 — 클라이언트가 그 값을 **해제 가능한 상태**로
     * 표시해야 하므로 빼지 않는다.
     */
    fun flavors(filter: CocktailFilter): Sql {
        val where = CocktailListSql.where(filter)
        return Sql(
            """
            SELECT a.aroma_tag AS value, count(*) AS n
              FROM cocktail c
              JOIN cocktail_aroma_tag a ON a.cocktail_id = c.id
             WHERE ${where.clause}
             GROUP BY a.aroma_tag
            """.trimIndent(),
            where.params,
        )
    }

    /**
     * 도수는 구간이라 컬럼이 아니다 (`ADR-0003`). 구간 정의는 `AbvBand` 한 곳에만 있고
     * 여기서는 그 술어를 `CASE` 로 옮긴다 — 정의를 두 벌로 두면 필터와 카운트가 갈린다.
     */
    fun abvBands(filter: CocktailFilter): Sql {
        val where = CocktailListSql.where(filter)
        val cases = kr.mut.search.list.AbvBand.entries.joinToString("\n                   ") {
            "WHEN ${it.sqlPredicate} THEN '${it.slug}'"
        }
        return Sql(
            """
            SELECT CASE
                   $cases
                   END AS value, count(*) AS n
              FROM cocktail c
             WHERE ${where.clause}
             GROUP BY 1
            """.trimIndent(),
            where.params,
        )
    }
}

/** 같은 축의 선택만 지운 사본. 나머지 축은 그대로 반영된다 (RED 8). */
internal fun CocktailFilter.withoutBase() = copy(base = emptySet())

internal fun CocktailFilter.withoutStyle() = copy(style = emptySet())

internal fun CocktailFilter.withoutMethod() = copy(method = emptySet())

internal fun CocktailFilter.withoutSweet() = copy(sweet = null)

internal fun CocktailFilter.withoutAbv() = copy(abv = emptySet())

/** 향·맛은 지우지 않는다 — AND 축이라 현재 선택을 유지한 채 더한다 (RED 6·7). */
internal fun CocktailFilter.keepFlavor() = this

@Suppress("unused")
internal fun CocktailFilter.plusFlavor(tag: kr.mut.common.taxonomy.FlavorKey) =
    copy(flavor = AllOf(flavor.values + tag))
