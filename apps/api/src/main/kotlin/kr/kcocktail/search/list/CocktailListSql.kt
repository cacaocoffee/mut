package kr.kcocktail.search.list

import kr.kcocktail.common.web.page.PageQuery
import kr.kcocktail.common.web.page.SortOrder
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource

/** 실행할 문장과 바인딩. 테스트가 **프로덕션과 같은 문장**을 EXPLAIN 하려고 밖으로 낸다 (RED 32). */
data class Sql(val text: String, val params: MapSqlParameterSource)

/**
 * `GET /cocktails` 의 SQL (SPEC-07 §3.1).
 *
 * ## 왜 SEARCH 모듈이 직접 읽나
 *
 * SPEC-05 §3 의 의존 방향표가 `SEARCH ──reads──▶ COCKTAIL · BAR · INGREDIENT · CONTENT` 이고,
 * "양방향으로 보이지만 **조회는 SEARCH 나 조회 전용 서비스가 담당해 순환을 끊는다**" 고 했다.
 * 여섯 축의 조합 조건을 파사드 시그니처로 옮기면 결국 `CocktailFacade` 가 필터 DSL 을
 * 되받게 되고, 그때는 `cocktail` 모듈이 검색 요구사항을 따라다니게 된다.
 *
 * 그래서 **읽기 전용 조회 모델**을 이쪽에 둔다. `cocktail` 의 리포지토리·엔티티는 참조하지 않는다
 * (`PRIN-T03` — 모듈 경계 테스트가 감시한다). 쓰기는 여전히 `cocktail` 모듈만 한다.
 *
 * ## 인덱스를 탈 수 있는 모양으로 쓴다 (SPEC-06 §5)
 *
 * 컬럼을 함수로 감싸지 않는다. `abv` 가 생성 컬럼이라 `COALESCE` 없이 그대로 걸 수 있고
 * (V009 §36), 그래야 `(status, abv)` 인덱스가 붙는다. `(status, base_spirit)` 도 같다.
 *
 * ## 결합 규칙은 축마다 다르다
 *
 * | 축 | SQL |
 * |---|---|
 * | base · method | `IN (…)` — 같은 컬럼의 OR |
 * | style | `EXISTS (…)` — **보유 스타일 전체**와 맞춘다 (DECISIONS §1.11) |
 * | abv | 구간 술어의 OR (ADR-0003) |
 * | sweet | `=` — 단일값 |
 * | **flavor** | **`count(*) = 태그 개수`** — AND (SPEC-07 §3.1) |
 */
object CocktailListSql {

    /**
     * 목록 한 페이지.
     *
     * 자식 축(스타일·향)을 조인이 아니라 스칼라 서브쿼리로 가져온다 —
     * 조인하면 한 칵테일이 여러 행이 되어 `LIMIT` 이 **칵테일 수가 아니라 행 수**를 자른다.
     */
    fun select(filter: CocktailFilter, page: PageQuery): Sql {
        val where = where(filter)
        val text = """
            SELECT c.slug, c.name_ko, c.name_en, c.summary,
                   c.base_spirit, c.style_primary, c.method, c.sweetness,
                   c.abv, c.glass_type, c.is_classic,
                   (SELECT array_agg(s.style ORDER BY s.style)
                      FROM cocktail_style s WHERE s.cocktail_id = c.id) AS styles,
                   (SELECT array_agg(a.aroma_tag ORDER BY a.aroma_tag)
                      FROM cocktail_aroma_tag a WHERE a.cocktail_id = c.id) AS aroma_tags
              FROM cocktail c
             WHERE ${where.clause}
             ORDER BY ${orderBy(page.sort)}
             LIMIT ${page.size} OFFSET ${page.offset}
        """.trimIndent()

        return Sql(text, where.params)
    }

    /** 같은 조건의 전체 건수. 페이지 메타(`totalElements`)가 이것으로 만들어진다. */
    fun count(filter: CocktailFilter): Sql {
        val where = where(filter)
        return Sql("SELECT count(*) FROM cocktail c WHERE ${where.clause}", where.params)
    }

    /**
     * `WHERE` 절만 따로 낸다 — 패싯 카운트(이슈 019)가 **같은 조건**을 축별로 다시 세야 한다.
     * 두 벌로 쓰면 목록과 카운트가 어긋나고, 그건 `FR-SEARCH-002` 가 막으려는 상황이다.
     */
    fun where(filter: CocktailFilter): Where {
        // 발행분만 (SPEC-07 §2.1·§5). 파라미터가 아니라 상수다 — 열 수 있는 문을 만들지 않는다.
        val clauses = mutableListOf("c.status = 'published'")
        val params = MapSqlParameterSource()

        if (filter.base.isNotEmpty()) {
            clauses += "c.base_spirit IN (:base)"
            params.addValue("base", filter.base.map { it.slug })
        }

        // style_primary 가 아니다 — 보유 스타일 전체를 본다 (DECISIONS §1.11).
        if (filter.style.isNotEmpty()) {
            clauses += "EXISTS (SELECT 1 FROM cocktail_style fs " +
                "WHERE fs.cocktail_id = c.id AND fs.style IN (:style))"
            params.addValue("style", filter.style.map { it.slug })
        }

        if (filter.method.isNotEmpty()) {
            clauses += "c.method IN (:method)"
            params.addValue("method", filter.method.map { it.slug })
        }

        filter.sweet?.let {
            clauses += "c.sweetness = :sweet"
            params.addValue("sweet", it.slug)
        }

        // 4구간의 OR. 구간 정의는 AbvBand 한 곳에만 있다 (ADR-0003).
        if (filter.abv.isNotEmpty()) {
            clauses += filter.abv.sortedBy { it.ordinal }
                .joinToString(" OR ", prefix = "(", postfix = ")") { it.sqlPredicate }
        }

        // ── 여기만 AND 다 (SPEC-07 §3.1) ──────────────────────────────────
        // 태그 개수만큼 매칭돼야 한다. IN 으로 쓰면 조용히 OR 가 되고, 결과가 그럴듯해서 안 걸린다.
        if (filter.flavor.values.isNotEmpty()) {
            clauses += "(SELECT count(*) FROM cocktail_aroma_tag ft " +
                "WHERE ft.cocktail_id = c.id AND ft.aroma_tag IN (:flavor)) = :flavorCount"
            params.addValue("flavor", filter.flavor.values.map { it.slug })
            params.addValue("flavorCount", filter.flavor.size.toLong())
        }

        // 이름만 본다. 초성·별칭·가중치는 검색 색인의 일이다 (이슈 017·024).
        filter.q?.let {
            clauses += "(c.name_ko ILIKE :q OR c.name_en ILIKE :q)"
            params.addValue("q", "%${escapeLike(it)}%")
        }

        return Where(clauses.joinToString("\n   AND "), params)
    }

    data class Where(val clause: String, val params: MapSqlParameterSource)

    /**
     * 허용목록 밖의 정렬은 [kr.kcocktail.common.web.page.PageQueryArgumentResolver] 가 이미 400 으로 막았다
     * (`@SortableBy`). 여기서는 이름을 컬럼으로 옮기기만 한다 — 그래도 `else` 를 열어 두지 않는다.
     *
     * `c.id` 를 항상 마지막에 붙인다. 동점이 있으면 페이지마다 순서가 흔들려
     * **같은 항목이 두 페이지에 나오거나 아예 빠진다.**
     */
    private fun orderBy(sort: List<SortOrder>): String {
        val columns = sort.map { order ->
            val column = when (order.property) {
                "name" -> "c.name_ko"
                "abv" -> "c.abv"
                else -> error("정렬 허용목록 밖이다: ${order.property}")
            }
            "$column ${if (order.ascending) "ASC" else "DESC"}"
        }
        return (columns.ifEmpty { listOf("c.name_ko ASC") } + "c.id ASC").joinToString(", ")
    }

    /** `%`·`_` 는 사용자가 친 글자다. 패턴 문자로 새면 `%` 하나에 전체가 걸린다. */
    private fun escapeLike(raw: String): String =
        raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
