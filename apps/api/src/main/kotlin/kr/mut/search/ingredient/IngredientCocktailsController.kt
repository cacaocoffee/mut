package kr.mut.search.ingredient

import io.swagger.v3.oas.annotations.Operation
import kr.mut.cocktail.api.CocktailFacade
import kr.mut.common.web.ApiPaths
import kr.mut.common.web.error.ResourceNotFoundException
import kr.mut.common.web.page.PageQuery
import kr.mut.common.web.page.PageResponse
import kr.mut.common.web.page.SortableBy
import kr.mut.ingredient.api.IngredientFacade
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * `GET /ingredients/{slug}/cocktails` — 이 재료를 쓰는 칵테일 (`R-F1.3-1`, 이슈 023).
 *
 * ## 왜 `search` 모듈인가
 *
 * 이 엔드포인트는 **재료와 칵테일을 동시에 읽는다.** `ingredient` 에 두면
 * `ingredient → cocktail` 화살표가 생기는데, SPEC-05 §3 의 방향표에는
 * `COCKTAIL ──uses──▶ INGREDIENT` 만 있다 — 반대 방향이라 **순환**이 된다.
 *
 * 이슈 023 은 "1안: `CocktailFacade` 경유 — 경계 준수" 라고 적었지만 **그것으로는 안 된다.**
 * Facade 를 거치면 `repository` 직행은 막히지만 **모듈 간 화살표는 그대로 반대**다.
 * 경계 테스트(`RED6`·`RED7`)가 그것을 잡았다.
 *
 * 같은 이슈가 적어 둔 **2안**이 맞다 — 방향표의 `SEARCH ──reads──▶ COCKTAIL · INGREDIENT`
 * 가 정확히 이 모양이다. `search` 는 여러 도메인을 읽으라고 있는 모듈이다.
 *
 * ## 경로는 `/ingredients` 아래다
 *
 * 모듈과 URL 은 별개다. `search/list/CocktailListController` 도 `/cocktails` 를 잡는다 —
 * URL 은 사용자가 읽는 자원 구조이고, 모듈은 코드의 의존 방향이다.
 */
@RestController
@RequestMapping("${ApiPaths.BASE}/ingredients")
class IngredientCocktailsController(
    private val ingredients: IngredientFacade,
    private val cocktails: CocktailFacade,
) {

    @GetMapping("/{slug}/cocktails")
    @Operation(
        summary = "이 재료를 쓰는 칵테일",
        description = "표준 레시피 · 발행분만. 대체재로만 등장하는 것은 제외한다 (R-F1.3-1).",
    )
    fun cocktails(
        @PathVariable slug: String,
        @SortableBy("nameKo") page: PageQuery,
    ): PageResponse<IngredientCocktailItem> {
        // 없는 재료와 미승인 재료를 구분하지 않는다 — 둘 다 404 다.
        // 빈 목록으로 돌려주면 "없는 재료" 와 "쓰는 칵테일이 없는 재료" 도 섞인다.
        val ingredient = ingredients.findApprovedBySlug(slug) ?: throw ResourceNotFoundException()

        val items = cocktails
            .findPublishedByIngredient(ingredient.id, page.size, page.offset.toInt())
            .map {
                IngredientCocktailItem(
                    slug = it.cocktail.slug,
                    nameKo = it.cocktail.nameKo,
                    nameEn = it.cocktail.nameEn,
                    summary = it.cocktail.summary,
                    isOptional = it.isOptional,
                )
            }

        return PageResponse.of(
            items = items,
            query = page,
            totalElements = cocktails.countPublishedByIngredient(ingredient.id),
        )
    }
}

/** `R-F1.3-1` — 이 재료를 쓰는 칵테일 한 줄. */
data class IngredientCocktailItem(
    val slug: String,
    val nameKo: String,
    val nameEn: String,
    val summary: String,

    /**
     * 이 칵테일에서 **선택 재료인가** (이슈 023 RED 25).
     *
     * 선택 재료로 쓰인 칵테일도 목록에 넣되 표시한다 — 표시가 없으면 필수로 오해한다.
     * 한 레시피가 같은 재료를 두 줄에 쓰고 한 줄이라도 필수면 **필수**다.
     */
    val isOptional: Boolean,
)
