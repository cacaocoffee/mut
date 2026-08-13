package kr.kcocktail.search.query

import kr.kcocktail.search.api.SearchEntityType
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 통합 검색 조회 (ISSUE-024 · `FR-SEARCH-006`·`007`·`008`).
 *
 * ## Postgres 만으로 간다
 *
 * SPEC-05 §6 — 별도 검색엔진은 코퍼스가 **수천 건을 넘을 때** 검토한다.
 * 지금은 `LIKE` + GIN 트라이그램으로 충분하고, 엔진을 들이면 색인 동기화 경로가
 * 하나 더 생겨 이슈 017 이 지킨 계약(색인 실패 = 발행 롤백)을 다시 설계해야 한다.
 *
 * ## 초성과 일반을 다른 컬럼에서 찾는다
 *
 * | 질의 | 보는 곳 | 인덱스 |
 * |---|---|---|
 * | 초성만 (`ㅁㄹㄱ`) | `chosung` | GIN 트라이그램 (`R-F2.1-4` · G-13) |
 * | 그 외 | `name_ko` · `name_en` · `aliases` | — |
 *
 * 섞인 입력(`마ㄹㄱ`)은 **일반 검색**이다 (DECISIONS §1.9). 초성으로 치다 만 것인지
 * 오타인지 알 수 없고, 초성 컬럼에서 찾으면 `마` 때문에 아무것도 안 걸린다.
 *
 * ## 띄어쓰기를 양쪽에서 지운다
 *
 * `올드 패션드` 와 `올드패션드` 가 같아야 한다 (`R-F2.1-3`). 색인은 별칭 표기를
 * **그대로** 보존하므로(`SearchDocumentText.aliases`) 비교 시점에 지운다.
 * 500종 규모라 함수 인덱스 없이 감당된다 — 수천 건이 되면 생성 컬럼으로 옮긴다.
 */
@Component
class SearchReader(private val jdbc: NamedParameterJdbcTemplate) {

    @Transactional(readOnly = true)
    fun search(query: SearchQuery, limitPerGroup: Int): SearchResponse {
        val rows = when {
            // 빈 패턴은 `LIKE '%%'` 라 **전 코퍼스가 걸린다.** 0건이어야 할 질의가
            // 전부를 반환하는 모습이라 결과가 그럴듯해 눈으로는 안 잡힌다.
            // 질의가 비는 경로는 이미 400 이지만, 정규화 결과가 비는 경우가 남는다.
            query.pattern.isEmpty() -> emptyList()
            query.isChosung -> byChosung(query, limitPerGroup)
            else -> byText(query, limitPerGroup)
        }

        // 빈 그룹도 자리를 만든다 (RED 18) — 클라이언트가 "바 0건" 을 그릴 수 있어야 하고,
        // 1b·2 에서 타입이 늘 때 렌더링이 깨지지 않는다 (RED 15).
        val grouped = SearchEntityType.entries.associate { type ->
            type.slug to SearchGroup(
                items = rows.filter { it.entityType == type.slug },
            )
        }

        return SearchResponse(
            query = query.raw,
            hadChosung = query.isChosung,
            matchedCount = rows.size,
            groups = grouped,
        )
    }

    /** 초성 — GIN 트라이그램을 탄다 (RED 12). */
    private fun byChosung(query: SearchQuery, limit: Int): List<SearchHit> = run(
        """
        SELECT entity_type, slug, name_ko, name_en, weight
          FROM search_document
         WHERE is_published = true
           AND chosung LIKE :chosung
         ORDER BY weight DESC, name_ko
         LIMIT :limit
        """.trimIndent(),
        MapSqlParameterSource()
            .addValue("chosung", "%${escape(query.chosung)}%")
            .addValue("limit", limit * SearchEntityType.entries.size),
    )

    /**
     * 일반 — 이름·별칭을 본다.
     *
     * 별칭은 배열이라 `EXISTS` 로 펼친다. `array_to_string` 으로 이어 붙이면
     * 별칭 경계를 넘는 우연한 매칭이 생긴다 — 색인이 `chosung` 을 공백으로 나눈 이유와 같다.
     */
    private fun byText(query: SearchQuery, limit: Int): List<SearchHit> = run(
        """
        SELECT entity_type, slug, name_ko, name_en, weight
          FROM search_document
         WHERE is_published = true
           AND (
                replace(name_ko, ' ', '') ILIKE :q
             OR replace(coalesce(name_en, ''), ' ', '') ILIKE :q
             OR EXISTS (
                  SELECT 1 FROM unnest(aliases) AS alias
                   WHERE replace(alias, ' ', '') ILIKE :q
                )
           )
         ORDER BY weight DESC, name_ko
         LIMIT :limit
        """.trimIndent(),
        MapSqlParameterSource()
            .addValue("q", "%${escape(query.normalized)}%")
            .addValue("limit", limit * SearchEntityType.entries.size),
    )

    /** 프리픽스 매칭 — 자동완성은 "앞에서부터" 다 (RED 31). */
    @Transactional(readOnly = true)
    fun suggest(query: SearchQuery, limit: Int): List<SearchHit> {
        val pattern = "${escape(if (query.isChosung) query.chosung else query.normalized)}%"
        val column = if (query.isChosung) "chosung" else "replace(name_ko, ' ', '')"

        return run(
            """
            SELECT entity_type, slug, name_ko, name_en, weight
              FROM search_document
             WHERE is_published = true AND $column ILIKE :q
             ORDER BY weight DESC, name_ko
             LIMIT :limit
            """.trimIndent(),
            MapSqlParameterSource().addValue("q", pattern).addValue("limit", limit),
        )
    }

    private fun run(sql: String, params: MapSqlParameterSource): List<SearchHit> =
        jdbc.query(sql, params) { rs, _ ->
            SearchHit(
                entityType = rs.getString("entity_type"),
                slug = rs.getString("slug"),
                nameKo = rs.getString("name_ko"),
                nameEn = rs.getString("name_en"),
                weight = rs.getInt("weight"),
            )
        }

    /**
     * `LIKE` 와일드카드를 막는다 (RED 25·26 · SPEC-08 §7).
     *
     * SQL 인젝션은 파라미터 바인딩이 막지만, **`%` 와 `_` 는 바인딩 안에서도 살아 있다** —
     * `q=%` 하나로 전 코퍼스를 긁어 가는 것을 막는 것은 이스케이프뿐이다.
     */
    private fun escape(raw: String): String =
        raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
