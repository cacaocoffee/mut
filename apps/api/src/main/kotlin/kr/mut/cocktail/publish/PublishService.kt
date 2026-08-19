package kr.mut.cocktail.publish

import kr.mut.cocktail.api.CocktailArchived
import kr.mut.cocktail.api.CocktailPublished
import kr.mut.cocktail.api.CocktailUnpublished
import kr.mut.cocktail.api.IngredientSnapshot
import kr.mut.cocktail.api.PublishCandidate
import kr.mut.cocktail.api.PublishGate
import kr.mut.cocktail.api.RecipeSnapshot
import kr.mut.cocktail.domain.Cocktail
import kr.mut.cocktail.domain.CocktailStatus
import kr.mut.cocktail.lifecycle.CocktailLifecycleService.Companion.ENTITY_TYPE
import kr.mut.cocktail.lifecycle.CocktailTransition
import kr.mut.cocktail.recipe.RecipeRepository
import kr.mut.cocktail.recipe.RecipeVersionType
import kr.mut.cocktail.repository.CocktailRepository
import kr.mut.common.audit.AuditRecorder
import kr.mut.common.revalidate.RevalidateHook
import kr.mut.common.revalidate.RevalidatePaths
import kr.mut.common.revalidate.RevalidateTarget
import kr.mut.common.web.error.ConflictException
import kr.mut.common.web.error.DomainViolationException
import kr.mut.common.web.error.ResourceNotFoundException
import kr.mut.ingredient.api.IngredientFacade
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
    private val revalidate: RevalidateHook,
    private val auditor: AuditRecorder,
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

        val from = cocktail.status
        cocktail.markPublished(clock.instant())
        audit(cocktail, from, CocktailStatus.PUBLISHED)

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

        revalidateAfterCommit(cocktail)
    }

    /**
     * 재생성 훅은 **커밋 뒤에** 부른다 (RED 17 · `NFR-R-03` · DECISIONS §1.7).
     *
     * 트랜잭션 안에서 부르면 커밋이 실패했을 때 **유령 재생성**이 나간다 —
     * 없던 일이 된 발행을 프론트가 그려 버린다.
     *
     * 실패를 삼키는 것은 훅 자신의 책임이다 ([RevalidateHook] 이 던지지 않는다).
     * 프론트가 늦게 갱신되는 것과 발행이 없던 일이 되는 것은 심각도가 다르다 —
     * 전자는 ISR 주기가 따라잡고, 후자는 에디터가 처음부터 다시 한다.
     * 색인 동기화(위의 이벤트)와 갈리는 지점이다.
     */
    private fun revalidateAfterCommit(cocktail: Cocktail) {
        val paths = RevalidatePaths.forCocktail(
            RevalidateTarget(
                slug = cocktail.slug,
                baseSpiritSlug = cocktail.baseSpirit.slug,
                stylePrimarySlug = cocktail.stylePrimary.slug,
                methodSlug = cocktail.method.slug,
            ),
        )

        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() {
                    // 훅 구현도 실패를 삼키지만(RevalidateHook 계약) 여기서 한 번 더 막는다.
                    // `NFR-R-03` 은 "발행은 훅 실패로 롤백되지 않는다" 를 **발행 쪽 보증**으로
                    // 요구한다 — 남의 클래스가 계약을 지킬 것이라는 믿음에 걸어 둘 수 없다.
                    // afterCommit 에서 던지면 커밋은 됐는데 호출자는 예외를 받는다.
                    runCatching { revalidate.revalidate(paths) }
                        .onFailure {
                            log.error("재생성 훅이 예외를 던졌다 — 발행은 유지한다 (NFR-R-03)", it)
                        }
                }
            },
        )
    }

    /** 회수. **게이트를 검사하지 않는다** (RED 24) — 내리는 데 조건을 걸 이유가 없다. */
    @Transactional
    fun unpublish(cocktailId: Long) {
        val cocktail = load(cocktailId)
        val from = cocktail.status
        requireTransition(from, CocktailStatus.DRAFT)

        cocktail.markDraft()
        audit(cocktail, from, CocktailStatus.DRAFT)

        // `archived → draft` 는 이미 색인에 없다. 내려야 하는 것은 `published → draft` 뿐이다.
        if (from == CocktailStatus.PUBLISHED) {
            events.publishEvent(CocktailUnpublished(cocktail.id, cocktail.slug))
            // RED 10 — 내릴 때도 재생성한다. 안 하면 없어진 페이지가 정적 파일로 남는다
            revalidateAfterCommit(cocktail)
        }
    }

    @Transactional
    fun archive(cocktailId: Long) {
        val cocktail = load(cocktailId)
        val from = cocktail.status
        requireTransition(from, CocktailStatus.ARCHIVED)

        cocktail.markArchived()
        audit(cocktail, from, CocktailStatus.ARCHIVED)

        events.publishEvent(CocktailArchived(cocktail.id, cocktail.slug))

        // 회수와 같은 이유다 — `archived` 도 공개 API 에서 404 라 (SPEC-07 §5)
        // 정적 파일이 남아 있으면 내린 것이 계속 보인다. RED 10 이 회수만 적었지만
        // 근거가 같아 함께 건다.
        revalidateAfterCommit(cocktail)
    }

    /** 배치 검증(이슈 016)이 쓴다. 저장하지 않고 판정만 한다. */
    @Transactional(readOnly = true)
    fun inspect(cocktailId: Long) = PublishGate.check(candidateOf(load(cocktailId)))

    /**
     * 전이는 **전부** 감사에 남는다 (`PRIN-T08` · SPEC-02 §8.1).
     *
     * 같은 트랜잭션이다 (`AuditRecorder` 가 `MANDATORY`). 전이가 롤백되면 기록도 없고,
     * **기록에 실패하면 전이도 실패한다** — 감사 없는 발행은 없다.
     */
    private fun audit(cocktail: Cocktail, from: CocktailStatus, to: CocktailStatus) {
        auditor.record(
            entityType = ENTITY_TYPE,
            entityId = cocktail.id,
            action = CocktailTransition.auditAction(from, to),
            before = mapOf("status" to from.slug),
            after = mapOf("status" to to.slug, "publishedAt" to cocktail.publishedAt?.toString()),
        )
    }

    private fun requireTransition(from: CocktailStatus, to: CocktailStatus) {
        if (!CocktailTransition.isAllowed(from, to)) {
            throw ConflictException(
                "허용되지 않는 상태 전이입니다: ${from.slug} → ${to.slug} " +
                    "(가능: ${CocktailTransition.allowedFrom(from).joinToString { it.slug }})",
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
