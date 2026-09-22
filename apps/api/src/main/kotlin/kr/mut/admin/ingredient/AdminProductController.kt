package kr.mut.admin.ingredient

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import jakarta.servlet.http.HttpServletRequest
import kr.mut.admin.content.AdminActor
import kr.mut.common.security.authz.Action
import kr.mut.common.web.ApiPaths
import kr.mut.common.web.page.PageQuery
import kr.mut.common.web.page.SortableBy
import kr.mut.ingredient.api.DistributedProductPage
import kr.mut.ingredient.api.IngredientAdminFacade
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 유통 제품 목록 (#203 · G-41).
 *
 * 식약처 신고 4만 건을 훑는 화면의 뒷단이다. 재료의 브랜드 검색어를 적으려면 어떤 제품이
 * 들어와 있는지 먼저 봐야 한다. 구매 링크·가격은 계약에 없다 (`NFR-L-05`).
 */
@RestController
@RequestMapping("${ApiPaths.ADMIN}/products")
class AdminProductController(
    private val ingredients: IngredientAdminFacade,
    private val actor: AdminActor,
) {

    @GetMapping
    @Operation(
        summary = "유통 제품 목록",
        description = "editor 이상. q 는 제품명(한/영), importer 는 수입사, foodType 은 식품유형. 최근 신고순 고정.",
    )
    fun list(
        @Parameter(description = "제품명(한/영)의 일부") @RequestParam(required = false) q: String?,
        @Parameter(description = "수입사의 일부") @RequestParam(required = false) importer: String?,
        @Parameter(description = "식품유형 (위스키 · 리큐르 · 과실주 …)") @RequestParam(required = false) foodType: String?,
        @SortableBy page: PageQuery,
        http: HttpServletRequest,
    ): DistributedProductPage {
        actor.require(http, Action.WRITE_CONTENT)
        return ingredients.browseProducts(q, importer, foodType, page)
    }
}
