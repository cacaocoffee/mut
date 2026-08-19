package kr.mut.admin.verification

import kr.mut.cocktail.api.PublishInspectionFacade
import kr.mut.common.web.error.Violation
import kr.mut.ingredient.api.IngredientInspectionFacade
import kr.mut.ingredient.api.IngredientView
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * `npm run check` 의 서버판 (SPEC-06 §4.3, `NFR-D-01`).
 *
 * > 앱 강제 항목은 배치 검증으로 이중 확인한다. 일 1회 전수 스캔해 위반 건을
 * > 관리자 태스크로 올린다.
 *
 * ## 규칙을 여기서 쓰지 않는다
 *
 * 게이트도 불변식도 각 모듈의 창구에 물어본다. 배치가 자기 규칙을 구현하면
 * `NFR-D-02`("게이트를 우회한 published 0건")를 **검출할 수 없다** —
 * 두 규칙이 어긋났을 때 어느 쪽이 맞는지 알 방법이 없기 때문이다.
 * 검사하는 쪽이 틀렸을 가능성을 없애는 것이 이 설계의 요점이다.
 *
 * ## 자동 회수하지 않는다
 *
 * `NFR-D-02` 는 "즉시 회수" 라고 적었지만 **태스크와 알림까지만** 한다.
 * 자동으로 내리면 에디터가 쓰던 것이 예고 없이 사라지고, 배치가 오판했을 때
 * 되돌릴 사람이 그 사실조차 모른다. 사람이 보고 내린다 (GAPS G-27).
 */
@Component
open class InvariantVerificationBatch(
    private val cocktails: PublishInspectionFacade,
    private val ingredients: IngredientInspectionFacade,
    private val slugWatch: SlugChangeWatch,
    private val store: VerificationTaskStore,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** `open` 인 이유는 CLI 러너 테스트가 위반 있는/없는 결과를 흉내 내기 때문이다 (RED 27·28). */
    @Transactional
    open fun run(): VerificationRun {
        val startedAt = System.nanoTime()

        val publishedIds = cocktails.publishedIds()
        val ingredientViews = ingredients.findAllForVerification()

        val found = buildList {
            publishedIds.forEach { id ->
                addAll(cocktailTasks(id))
            }
            addAll(ingredientViews.flatMap { ingredientTasks(it) })
            addAll(slugWatch.detect())
        }

        val opened = store.openAll(found)
        val resolved = store.resolveMissing(found, TaskType.entries.toSet())

        val run = VerificationRun(
            scannedCocktails = publishedIds.size,
            scannedIngredients = ingredientViews.size,
            opened = opened.opened,
            reopened = opened.reopened,
            resolved = resolved,
            violations = found,
        )

        // RED 25 — 실행 이력. `batch_run` 테이블은 만들지 않는다 (DECISIONS D-2).
        log.info(
            "불변식 검증 완료 ({}ms) — 칵테일 {}건 · 재료 {}건 스캔, 위반 {}건 " +
                "(신규 {} · 재발 {} · 해소 {})",
            (System.nanoTime() - startedAt) / 1_000_000,
            run.scannedCocktails,
            run.scannedIngredients,
            found.size,
            run.opened,
            run.reopened,
            run.resolved,
        )

        return run
    }

    /**
     * 발행분 하나. **첫 위반에서 멈추지 않는다** (RED 4) —
     * 한 칵테일이 여러 규칙을 동시에 어길 수 있고, 하나씩 알려 주면 사람이 여러 번 온다.
     */
    private fun cocktailTasks(cocktailId: Long): List<VerificationTask> {
        val identity = cocktails.identify(cocktailId)

        // GATE-* 가 발행분에 남아 있다는 것은 데이터가 아니라 경로가 뚫렸다는 뜻이다
        val gate = cocktails.inspectGate(cocktailId).map {
            task(TaskType.GATE_BYPASS, cocktailId, it, identity?.slug)
        }
        val invariants = cocktails.inspectInvariants(cocktailId).map {
            task(TaskType.INVARIANT_VIOLATION, cocktailId, it, identity?.slug)
        }

        return gate + invariants
    }

    /**
     * `INV-INGREDIENT-01` (SPEC-06 §4.3).
     *
     * 마스터는 발행분만 보지 않는다 — 오염되면 그것을 쓰는 **모든** 레시피가 같이 틀린다
     * (`PRIN-D01`).
     *
     * ## 이 검사는 지금 아무것도 못 찾는다. 그래도 둔다
     *
     * SPEC-06 §4.3 은 `INV-INGREDIENT-01` 을 "앱 강제" 로 분류했지만,
     * 이슈 008 이 **DB `CHECK` 로도** 걸었다 (`ck_ingredient__substitute`, GAPS G-24).
     * 그래서 이 상태는 SQL 로도 만들 수 없다 — 이 배치가 위반을 발견하는 날은
     * **그 CHECK 가 사라진 날**이다. 배치의 존재 이유가 "검사하는 쪽이 틀렸을 가능성" 이라
     * 그 날을 위해 남긴다.
     *
     * ## `INV-INGREDIENT-02` 는 여기서 못 본다
     *
     * "브랜드 광고성 표기" 는 **데이터가 아니라 렌더링** 규칙이다 (G-24 —
     * "판정이 데이터가 아니라 편집 판단이라 CHECK 로 옮길 수 없다. `is_sponsored` 가
     * `NOT NULL` 인 것까지가 DB 가 할 수 있는 전부다"). 데이터만 보는 배치가 판정할 수 있는
     * 것이 남아 있지 않다 — SPEC-06 §4.3 도 렌더링 강제(`INV-CONTENT-02`)를 **Phase 2** 로 뒀다.
     * 억지 조건을 넣으면 **거짓 태스크**가 쌓이고, 그러면 사람이 큐를 안 믿는다.
     */
    private fun ingredientTasks(view: IngredientView): List<VerificationTask> = buildList {
        if (view.requiresSubstitute && view.substituteNote.isNullOrBlank()) {
            add(
                VerificationTask(
                    taskType = TaskType.INVARIANT_VIOLATION,
                    entityType = INGREDIENT,
                    entityId = view.id,
                    code = "INV-INGREDIENT-01",
                    detail = mapOf(
                        "slug" to view.slug,
                        "availability" to view.availabilitySlug,
                        "why" to "국내 미유통인데 대체 안내가 없다",
                    ),
                ),
            )
        }
    }

    private fun task(
        type: TaskType,
        cocktailId: Long,
        violation: Violation,
        slug: String?,
    ) = VerificationTask(
        taskType = type,
        entityType = COCKTAIL,
        entityId = cocktailId,
        code = violation.code,
        detail = mapOf(
            "slug" to slug,
            "field" to violation.field,
            "message" to violation.message,
        ),
    )

    companion object {
        const val COCKTAIL = "cocktail"
        const val INGREDIENT = "ingredient"
    }
}
