package kr.kcocktail.ingredient.internal

import kr.kcocktail.ingredient.api.IngredientFacade
import kr.kcocktail.ingredient.api.IngredientSaved
import kr.kcocktail.ingredient.api.IngredientView
import kr.kcocktail.ingredient.domain.Ingredient
import kr.kcocktail.ingredient.repository.IngredientRepository
import kr.kcocktail.common.web.error.ConflictException
import kr.kcocktail.common.web.error.ResourceNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 재료 마스터 (ISSUE-008).
 *
 * 조회 API 는 이슈 023, 승인 엔드포인트는 이슈 026 이다. 여기서는 **도메인 동작**까지다.
 */
@Service
class IngredientService(
    private val ingredients: IngredientRepository,
    private val events: ApplicationEventPublisher,
) : IngredientFacade {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 저장하고 [IngredientSaved] 를 발행한다.
     *
     * **이벤트는 저장 트랜잭션 안에서 발행된다.** 스프링의 `@TransactionalEventListener` 가
     * 커밋 후에 리스너를 부르므로, 롤백되면 색인 갱신도 일어나지 않는다 —
     * 저장에 실패했는데 검색에는 나오는 상태를 만들지 않는다.
     */
    @Transactional
    fun save(ingredient: Ingredient): Ingredient {
        ingredient.validate() // INV-INGREDIENT-01. DB CHECK 도 같은 조건을 건다

        val saved = ingredients.save(ingredient)
        events.publishEvent(saved.toSavedEvent())
        warnIfOverCap()
        return saved
    }

    /**
     * DECISIONS §1.1 — 승인은 `admin` 만. 권한 판정은 호출부(이슈 026)가
     * `PermissionMatrix.APPROVE_INGREDIENT` 로 한다.
     */
    @Transactional
    fun approve(id: Long): Ingredient {
        val ingredient = ingredients.findById(id).orElseThrow { ResourceNotFoundException() }

        // DECISIONS §1 — 재승인은 409 다. 멱등하게 넘기면 승인 이력이 흐려진다.
        if (ingredient.isApproved) throw ConflictException("이미 승인된 재료입니다")

        ingredient.approve()
        events.publishEvent(ingredient.toSavedEvent())
        warnIfOverCap()
        return ingredient
    }

    /**
     * 참조 중이면 거부한다 (RED 24).
     *
     * 미리 세어 보지 않고 **FK 가 막게 둔다** — 세어 보고 지우는 사이에 새 참조가 들어올 수 있다.
     */
    @Transactional
    fun delete(id: Long) {
        val ingredient = ingredients.findById(id).orElseThrow { ResourceNotFoundException() }
        try {
            ingredients.delete(ingredient)
            ingredients.flush() // flush 하지 않으면 FK 위반이 커밋 시점에 터져 잡을 수 없다
        } catch (e: DataIntegrityViolationException) {
            throw ConflictException("레시피가 참조 중인 재료는 삭제할 수 없습니다")
        }
    }

    /**
     * DECISIONS §1.2 — **경고지 차단이 아니다.**
     *
     * SPEC-02 §3 의 상한 근거가 "역검색 UX"이지 무결성이 아니다. 301번째 재료가
     * 데이터를 깨뜨리지는 않는다 — 다만 내 술장 화면이 감당하기 어려워진다.
     * 배치 리포트(이슈 016)가 이 로그를 집계한다.
     */
    private fun warnIfOverCap() {
        val approved = ingredients.countByIsApprovedTrue()
        if (approved > APPROVED_CAP) {
            log.warn(
                "승인된 재료가 상한을 넘었다: {}개 (권장 {}개, FR-INGREDIENT-001). " +
                    "차단하지 않으나 역검색 UX 를 검토한다",
                approved, APPROVED_CAP,
            )
        }
    }

    // ── IngredientFacade (PRIN-T03) ────────────────────────────────────────

    @Transactional(readOnly = true)
    override fun findApproved(ids: Collection<Long>): List<IngredientView> =
        if (ids.isEmpty()) emptyList()
        else ingredients.findByIdInAndIsApprovedTrue(ids).map { it.toView() }

    @Transactional(readOnly = true)
    override fun findAll(ids: Collection<Long>): List<IngredientView> =
        if (ids.isEmpty()) emptyList()
        else ingredients.findAllById(ids).map { it.toView() }

    @Transactional(readOnly = true)
    override fun defaultCountsForStock(ingredientId: Long): Boolean =
        load(ingredientId).defaultCountsForStock

    @Transactional(readOnly = true)
    override fun requiresSubstitute(ingredientId: Long): Boolean =
        load(ingredientId).requiresSubstitute

    private fun load(id: Long): Ingredient =
        ingredients.findById(id).orElseThrow { ResourceNotFoundException() }

    companion object {
        /** `FR-INGREDIENT-001` "국내 유통 기준 200~300개". 넘으면 경고한다. */
        const val APPROVED_CAP = 300L
    }
}

/** 색인에 필요한 것만. 엔티티를 담으면 리스너가 다른 트랜잭션에서 지연 로딩을 만난다. */
private fun Ingredient.toSavedEvent() = IngredientSaved(
    entityId = id,
    slug = slug,
    nameKo = nameKo,
    nameEn = nameEn,
    aliases = aliases.toList(),
    isApproved = isApproved,
)

private fun Ingredient.toView() = IngredientView(
    id = id,
    slug = slug,
    nameKo = nameKo,
    nameEn = nameEn,
    categorySlug = category.slug,
    availabilitySlug = domesticAvailability.slug,
    isApproved = isApproved,
    countsForStockByDefault = defaultCountsForStock,
    requiresSubstitute = requiresSubstitute,
    substituteNote = substituteNote,
    hasSponsoredBrand = brands.any { it.requiresAdLabel },
)
