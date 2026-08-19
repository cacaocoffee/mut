package kr.mut.cocktail.category

import kr.mut.cocktail.domain.CocktailStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 3축 카테고리 조회 (ISSUE-022, `FR-COCKTAIL-029`).
 *
 * ## 왜 JPA 가 아닌가
 *
 * 축마다 `GROUP BY` 한 방이면 끝난다 (SPEC-06 §3.1 이 조인 테이블 대신 컬럼을 둔 이유가 그것이다).
 * 엔티티를 전부 실어 세는 것은 500종에서 이미 낭비고, 카테고리 응답은 `generateStaticParams`
 * 가 빌드마다 부른다.
 *
 * ## GREEN 범위
 *
 * **카테고리 페이지 렌더링(이슈 039)과 사이트맵 생성(039·044)은 프론트 몫이다.**
 * 여기서는 그 둘이 읽을 목록까지만 낸다.
 */
@Service
class CategoryService(private val jdbc: JdbcTemplate) {

    /**
     * @param includeAll `true` 면 enum 전체(건수 0 포함) — 필터 UI 가 쓸 목록이다.
     *   기본값 `false` 는 **발행분이 있는 값만** 낸다. `generateStaticParams` 가
     *   빈 카테고리 페이지를 만들면 안 되고, 목록만 있는 페이지는 색인 가치가 없다
     *   (DECISIONS §1.11 · `FR-COCKTAIL-031`).
     */
    @Transactional(readOnly = true)
    fun categories(includeAll: Boolean): CategoriesResponse {
        val intros = intros()
        return CategoriesResponse(
            base = items(CategoryAxis.BASE, intros, includeAll),
            style = items(CategoryAxis.STYLE, intros, includeAll),
            method = items(CategoryAxis.METHOD, intros, includeAll),
        )
    }

    private fun items(
        axis: CategoryAxis,
        intros: Map<Pair<String, String>, String?>,
        includeAll: Boolean,
    ): List<CategoryItem> {
        val counts = counts(axis)
        return axis.taxonomy
            .map { value ->
                CategoryItem(
                    slug = value.slug,
                    labelKo = value.labelKo,
                    count = counts[value.slug] ?: 0,
                    intro = intros[axis.slug to value.slug],
                )
            }
            // 건수 0 인 축값을 빼는 자리가 여기 하나뿐이다 (DECISIONS §1.11).
            .filter { includeAll || it.count > 0 }
    }

    /**
     * **발행분만 센다** — `draft` 는 URL 을 만들면 안 되고 `archived` 는 내린 것이다 (SPEC-07 §5).
     *
     * 컬럼 이름은 [CategoryAxis.countColumn] 이라 요청에서 오지 않는다.
     * 스타일이 `style_primary` 인 근거는 거기 적혀 있다 (`R-C-3` · DECISIONS §1.11).
     */
    private fun counts(axis: CategoryAxis): Map<String, Int> = jdbc.query(
        """
        SELECT ${axis.countColumn} AS slug, count(*) AS n
        FROM cocktail
        WHERE status = ?
        GROUP BY 1
        """.trimIndent(),
        { rs, _ -> rs.getString("slug") to rs.getInt("n") },
        CocktailStatus.PUBLISHED.slug,
    ).toMap()

    /** 문구가 없는 카테고리도 나온다 — D-1 이 `NFR-S-07` 을 경고로 확정했다 (DECISIONS §2). */
    private fun intros(): Map<Pair<String, String>, String?> = jdbc.query(
        "SELECT axis, slug, intro FROM category_intro",
    ) { rs, _ -> (rs.getString("axis") to rs.getString("slug")) to rs.getString("intro") }
        .toMap()
}
