package kr.kcocktail.cocktail.publish

import kr.kcocktail.cocktail.api.CocktailPublished
import kr.kcocktail.cocktail.api.IngredientSnapshot
import kr.kcocktail.cocktail.api.PublishCandidate
import kr.kcocktail.cocktail.api.PublishGate
import kr.kcocktail.cocktail.api.RecipeSnapshot
import kr.kcocktail.cocktail.domain.Cocktail
import kr.kcocktail.cocktail.domain.CocktailStatus
import kr.kcocktail.cocktail.recipe.RecipeRepository
import kr.kcocktail.cocktail.recipe.RecipeVersionType
import kr.kcocktail.cocktail.repository.CocktailRepository
import kr.kcocktail.common.web.error.ConflictException
import kr.kcocktail.common.web.error.DomainViolationException
import kr.kcocktail.common.web.error.ResourceNotFoundException
import kr.kcocktail.ingredient.api.IngredientFacade
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Clock

/**
 * 발행 (ISSUE-013, SPEC-07 §3.4).
 *
 * ## 우회 경로가 없다
 *
 * `status` 를 직접 바꾸는 공개 메서드를 두지 않는다 (`NFR-D-02` · RED 29·30).
 * 어드민이 `PATCH` 로 상태를 넘겨도 그것을 받는 자리가 없어야 한다 —
 * 발행은 **전용 엔드포인트만**이다 (이슈 025).
 *
 * ## 게이트는 트랜잭션 안에서
 *
 * `PRIN-T05` — 프론트 검증은 UX 용 중복이다. 없어도 데이터가 깨지지 않아야 한다.
 */
@Service
class PublishService(
    private val cocktails: CocktailRepository,
    private val recipes: RecipeRepository,
    private val ingredients: IngredientFacade,
    private val events: ApplicationEventPublisher,
    private val regeneration: RegenerationHook,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * `draft → published`.
     *
     * @throws ConflictException 이미 발행됐거나 허용되지 않는 전이
     * @throws DomainViolationException 게이트 실패 — `violations` 를 **전부** 담는다
     */
    @Transactional
    fun publish(cocktailId: Long) {
        val cocktail = load(cocktailId)

        // 이미 발행됐으면 409 (SPEC-07 §3.4). 게이트를 돌리기 전에 본다 —
        // 통과할 수도 있는 상태라 violations 를 돌려주면 "고치면 되나" 싶어진다.
        if (cocktail.status == CocktailStatus.PUBLISHED) {
            throw ConflictException("이미 발행된 칵테일입니다")
        }
        requireTransition(cocktail.status, CocktailStatus.PUBLISHED)

        val violations = PublishGate.check(candidateOf(cocktail))
        if (violations.isNotEmpty()) throw DomainViolationException(violations)

        cocktail.markPublished(clock.instant())

        // 색인 동기화 실패는 발행을 롤백한다 (DECISIONS §1.7) — 재생성 훅과 다르다.
        // 검색 정확성에 직결되므로 같은 트랜잭션에서 발행한다.
        events.publishEvent(
            CocktailPublished(
                entityId = cocktail.id,
                slug = cocktail.slug,
                nameKo = cocktail.nameKo,
                nameEn = cocktail.nameEn,
                aliases = cocktail.aliases.toList(),
            ),
        )

        regenerateAfterCommit(cocktail.slug)
    }

    /**
     * 재생성 훅은 **커밋 뒤에, 실패를 삼키고** 부른다 (`NFR-R-03` · DECISIONS §1.7).
     *
     * 프론트가 늦게 갱신되는 것과 발행이 없던 일이 되는 것은 심각도가 다르다 —
     * 전자는 다음 재생성이 따라잡고, 후자는 에디터가 처음부터 다시 한다.
     * 색인 동기화(위의 이벤트)와 갈리는 지점이다.
     */
    private fun regenerateAfterCommit(slug: String) {
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() {
                    runCatching { regeneration.onPublished(slug) }
                        .onFailure { log.error("재생성 훅 실패 — 발행은 유지한다 (NFR-R-03): {}", slug, it) }
                }
            },
        )
    }

    /** 회수. **게이트를 검사하지 않는다** (RED 24) — 내리는 데 조건을 걸 이유가 없다. */
    @Transactional
    fun unpublish(cocktailId: Long) {
        val cocktail = load(cocktailId)
        requireTransition(cocktail.status, CocktailStatus.DRAFT)
        cocktail.markDraft()
    }

    @Transactional
    fun archive(cocktailId: Long) {
        val cocktail = load(cocktailId)
        requireTransition(cocktail.status, CocktailStatus.ARCHIVED)
        cocktail.markArchived()
    }

    /** 배치 검증(이슈 016)이 쓴다. 저장하지 않고 판정만 한다. */
    @Transactional(readOnly = true)
    fun inspect(cocktailId: Long) = PublishGate.check(candidateOf(load(cocktailId)))

    private fun requireTransition(from: CocktailStatus, to: CocktailStatus) {
        if (!PublishTransition.isAllowed(from, to)) {
            throw ConflictException(
                "허용되지 않는 상태 전이입니다: ${from.slug} → ${to.slug} " +
                    "(가능: ${PublishTransition.allowedFrom(from).joinToString { it.slug }})",
            )
        }
    }

    /** DB 에서 게이트가 볼 스냅샷을 조립한다. 재료는 `IngredientFacade` 로만 본다 (`PRIN-T03`). */
    private fun candidateOf(cocktail: Cocktail): PublishCandidate {
        val standard = recipes.findByCocktailIdAndVersionTypeSlug(
            cocktail.id,
            RecipeVersionType.STANDARD.slug,
        )

        val rows = standard?.ingredients.orEmpty()
        val views = ingredients.findAll(rows.map { it.ingredientId }.distinct()).associateBy { it.id }

        val snapshots = rows.map { row ->
            val view = views[row.ingredientId]
            IngredientSnapshot(
                id = row.ingredientId,
                slug = view?.slug ?: "id:${row.ingredientId}",
                isApproved = view?.isApproved ?: false,
                requiresSubstitute = view?.requiresSubstitute ?: false,
                hasRecipeSubstitute =
                    row.substituteIngredientId != null || !row.substituteNote.isNullOrBlank(),
            )
        }

        return PublishCandidate(
            slug = cocktail.slug,
            tastingNote = cocktail.tastingNote,
            isClassic = cocktail.isClassic,
            story = cocktail.story,
            styles = cocktail.styles,
            stylePrimary = cocktail.stylePrimary,
            aromaTags = cocktail.aromaTags,
            standardRecipe = standard?.let {
                RecipeSnapshot(ingredients = snapshots, stepCount = it.steps.size)
            },
            ingredients = snapshots,
        )
    }

    private fun load(id: Long): Cocktail =
        cocktails.findById(id).orElseThrow { ResourceNotFoundException() }
}
