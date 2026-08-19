package kr.mut.ingredient.internal

import kr.mut.ingredient.api.AdminIngredientResponse
import kr.mut.ingredient.api.CreateIngredientRequest
import kr.mut.ingredient.api.IngredientAdminFacade
import kr.mut.ingredient.api.IngredientCapacity
import kr.mut.ingredient.api.IngredientProperties
import kr.mut.ingredient.domain.DomesticAvailability
import kr.mut.ingredient.domain.Ingredient
import kr.mut.ingredient.domain.IngredientCategory
import kr.mut.ingredient.repository.IngredientRepository
import kr.mut.common.web.error.BadRequestException
import kr.mut.common.web.error.ConflictException
import kr.mut.common.web.error.ResourceNotFoundException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [IngredientAdminFacade] 구현 (ISSUE-026).
 *
 * 저장·승인의 도메인 동작은 [IngredientService] 가 이미 갖고 있다 (이슈 008).
 * 여기서는 **DTO 경계**만 맡는다 — 재승인 409 나 상한 경고를 다시 구현하면
 * 어드민과 도메인이 다른 규칙을 갖게 된다.
 *
 * DTO 로 바꿔서 돌려주는 이유: 엔티티를 트랜잭션 밖으로 내보내면 `brands` 같은
 * 지연 컬렉션이 `open-in-view: false` 와 만나 터진다 (이슈 025 에서 겪었다).
 */
@Service
class IngredientAdminService(
    private val ingredients: IngredientRepository,
    private val service: IngredientService,
    private val properties: IngredientProperties,
) : IngredientAdminFacade {

    /**
     * 항상 **승인 대기**로 만든다. `isApproved` 를 요청에서 받지 않는 것이 의도다 —
     * **타입에 없으면 우회할 수 없다** (이슈 025 와 같은 방식).
     */
    @Transactional
    override fun create(request: CreateIngredientRequest): AdminIngredientResponse {
        if (ingredients.existsBySlug(request.slug)) {
            throw ConflictException("이미 쓰이는 슬러그입니다: ${request.slug}")
        }

        val ingredient = Ingredient(
            slug = request.slug,
            nameKo = request.nameKo,
            nameEn = request.nameEn,
            categorySlug = category(request.category).slug,
            availabilitySlug = availability(request.domesticAvailability).slug,
            aliases = request.aliases.toTypedArray(),
            abv = request.abv,
            description = request.description,
            substituteNote = request.substituteNote,
            priceBand = request.priceBand,
        )

        return try {
            service.save(ingredient).toAdminResponse()
        } catch (e: DataIntegrityViolationException) {
            // INV-INGREDIENT-01 — 미유통이면 대체 안내가 필수다 (DB CHECK 가 막는다)
            throw ConflictException("도메인 제약을 어겼습니다: ${e.mostSpecificCause.message}")
        }
    }

    /** 재승인 409 · 감사 기록은 [IngredientService.approve] 가 이미 한다 (DECISIONS §1.3). */
    @Transactional
    override fun approve(id: Long): AdminIngredientResponse = service.approve(id).toAdminResponse()

    @Transactional(readOnly = true)
    override fun find(id: Long): AdminIngredientResponse =
        ingredients.findById(id).orElseThrow { ResourceNotFoundException() }.toAdminResponse()

    @Transactional(readOnly = true)
    override fun pending(): List<AdminIngredientResponse> =
        ingredients.findByIsApprovedFalseOrderByNameKo().map { it.toAdminResponse() }

    /** 이름·영문명·슬러그를 한 번에 본다. **미승인도 준다** — 레시피 편집이 쓴다 (이슈 051). */
    @Transactional(readOnly = true)
    override fun search(query: String?, limit: Int): List<AdminIngredientResponse> =
        ingredients
            .searchForAdmin(query?.trim()?.takeIf { it.isNotEmpty() }, PageRequest.of(0, limit))
            .map { it.toAdminResponse() }

    @Transactional(readOnly = true)
    override fun capacity(): IngredientCapacity {
        val approved = ingredients.countByIsApprovedTrue()
        return IngredientCapacity(
            approved = approved,
            cap = properties.approvedCap,
            // 넘어도 승인은 된다. 상한 근거가 UX 라 데이터를 막을 이유가 없다
            warning = approved > properties.approvedCap,
        )
    }

    private fun category(slug: String) = IngredientCategory.entries.firstOrNull { it.slug == slug }
        ?: throw BadRequestException(
            "알 수 없는 category 입니다: $slug " +
                "(가능: ${IngredientCategory.entries.joinToString(", ") { it.slug }})",
        )

    private fun availability(slug: String) =
        DomesticAvailability.entries.firstOrNull { it.slug == slug }
            ?: throw BadRequestException(
                "알 수 없는 domesticAvailability 입니다: $slug " +
                    "(가능: ${DomesticAvailability.entries.joinToString(", ") { it.slug }})",
            )
}

/** **트랜잭션 안에서만 부른다** — `brands` 가 지연 컬렉션이다. */
private fun Ingredient.toAdminResponse() = AdminIngredientResponse(
    id = id,
    slug = slug,
    nameKo = nameKo,
    nameEn = nameEn,
    category = category.slug,
    domesticAvailability = domesticAvailability.slug,
    isApproved = isApproved,
    aliases = aliases.toList(),
    abv = abv,
    description = description,
    substituteNote = substituteNote,
    priceBand = priceBand,
)
