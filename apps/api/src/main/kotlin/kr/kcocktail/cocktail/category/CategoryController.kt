package kr.kcocktail.cocktail.category

import kr.kcocktail.common.web.ApiPaths
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * `GET /api/v1/categories` — 3축 슬러그 (SPEC-07 §2.1).
 *
 * 공개 조회다. `ETag` 와 `Cache-Control` 은
 * [kr.kcocktail.common.web.cache.PublicEtagFilter] · [kr.kcocktail.common.web.cache.CacheControlFilter]
 * 가 `/api/v1` 하위 전체에 붙인다 (SPEC-07 §1.6) — 여기서 다시 붙이지 않는다.
 *
 * **`noindex` 를 붙이지 않는다.** 필터 결과(이슈 018)와 대비되는 지점이다 —
 * 카테고리 경로는 색인 대상이고(`NFR-S-02`), 그것이 이 엔드포인트가 존재하는 이유다.
 */
@RestController
class CategoryController(private val categories: CategoryService) {

    /**
     * @param include `all` 이면 enum 전체를 낸다. 그 외에는 **발행분이 있는 값만** —
     *   `generateStaticParams` 가 빈 카테고리 페이지를 만들면 안 된다 (DECISIONS §1.11).
     */
    @GetMapping(CATEGORIES)
    fun categories(
        @RequestParam(required = false) include: String?,
    ): CategoriesResponse = categories.categories(includeAll = include == INCLUDE_ALL)

    companion object {
        /** SPEC-07 §1.1 — `kebab-case` 복수형. */
        const val CATEGORIES = "${ApiPaths.BASE}/categories"

        const val INCLUDE_ALL = "all"
    }
}
