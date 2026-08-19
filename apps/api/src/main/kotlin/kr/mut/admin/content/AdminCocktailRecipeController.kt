package kr.mut.admin.content

import io.swagger.v3.oas.annotations.Operation
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import kr.mut.cocktail.api.AdminRecipeResponse
import kr.mut.cocktail.api.RecipeAdminFacade
import kr.mut.cocktail.api.SaveRecipeRequest
import kr.mut.common.security.authz.Action
import kr.mut.common.web.ApiPaths
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 어드민 표준 레시피 (ISSUE-051 · `NFR-O-01` · [G-38](../../../../../../../../docs/prd/GAPS.md)).
 *
 * ## 이 엔드포인트가 없어서 `NFR-O-01` 이 닫히지 않았다
 *
 * 발행 게이트가 표준 레시피를 요구하는데(`GATE-COCKTAIL-03`) 어드민에서 레시피를 쓸 길이
 * 없었다. 엔티티와 조립기는 이슈 010·011 이 이미 만들어 뒀고 **테스트만 그것을 부르고
 * 있었다.** 운영 경로를 여기서 연다.
 *
 * ## `PUT` 인 이유
 *
 * 통째로 덮는다. 줄 단위 `PATCH` 를 열면 순서를 다시 매기는 코드가 서버와 화면 두 벌이
 * 되고, 재료를 지웠을 때 `position` 에 구멍이 남는다.
 *
 * ## 권한은 칵테일 편집과 같다
 *
 * 레시피는 칵테일의 일부다 (`PRIN-D03` 의 애그리게이트). `editor` 가 쓴다 —
 * 재료 **마스터** 승인만 `admin` 이고(SPEC-08 §2), 레시피에 그 재료를 쓰는 것은 편집이다.
 */
@RestController
@RequestMapping("${ApiPaths.ADMIN}/cocktails/{id}/recipe")
class AdminCocktailRecipeController(
    private val recipes: RecipeAdminFacade,
    private val actor: AdminActor,
) {

    /** 아직 안 쓴 레시피는 `exists: false` 로 답한다. 404 는 **없는 칵테일**의 몫이다. */
    @GetMapping
    @Operation(
        summary = "표준 레시피 조회",
        description = "아직 없으면 exists=false 인 빈 레시피. draft 도 본다.",
    )
    fun find(@PathVariable id: Long, http: HttpServletRequest): AdminRecipeResponse {
        actor.require(http, Action.VIEW_DRAFT)
        return recipes.find(id)
    }

    @PutMapping
    @Operation(
        summary = "표준 레시피 저장",
        description = "통째로 덮는다. 재료 순서와 스텝 번호는 1부터 다시 매겨진다. " +
            "저장 직후 abv_calculated 를 다시 채운다. 게이트는 검사하지 않는다 — 발행이 본다.",
    )
    fun save(
        @PathVariable id: Long,
        @Valid @RequestBody request: SaveRecipeRequest,
        http: HttpServletRequest,
    ): AdminRecipeResponse {
        actor.require(http, Action.WRITE_CONTENT)
        return recipes.save(id, request)
    }
}
