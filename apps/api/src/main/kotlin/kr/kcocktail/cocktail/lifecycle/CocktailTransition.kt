package kr.kcocktail.cocktail.lifecycle

import kr.kcocktail.common.audit.AuditAction
import kr.kcocktail.cocktail.domain.CocktailStatus
import kr.kcocktail.cocktail.domain.CocktailStatus.ARCHIVED
import kr.kcocktail.cocktail.domain.CocktailStatus.DRAFT
import kr.kcocktail.cocktail.domain.CocktailStatus.PUBLISHED

/**
 * SPEC-02 §8.1 상태 전이 (DECISIONS §1.4 확정).
 *
 * ```
 *    draft ──발행 게이트 통과──▶ published ──▶ archived
 *      ▲                            │            │
 *      └────────────────────────────┴────────────┘
 * ```
 *
 * | 전이 | 허용 | 왜 |
 * |---|---|---|
 * | `draft → published` | ✅ | 게이트 6종 통과 시 (이슈 013) |
 * | `published → draft` | ✅ | 되돌리기 (SPEC-02 §8.1) |
 * | `published → archived` | ✅ | 내리기 |
 * | `archived → draft` | ✅ | 다시 손보기 |
 * | `draft → archived` | ❌ | 도식에 없다 |
 * | **`archived → published`** | ❌ | `archived → draft → published` 만 |
 *
 * 마지막이 보수적인 선택이다 — 내렸던 것을 바로 올리면 **왜 내렸는지 다시 보지 않는다.**
 * draft 를 거치면 게이트를 다시 통과해야 한다.
 *
 * ## 이 표가 한 벌인 이유
 *
 * 발행(이슈 013)과 회수·보관(이슈 014)이 같은 표를 본다. 두 벌로 두면
 * 한쪽만 고쳐지고, 그때 `archived → published` 같은 구멍이 조용히 열린다.
 */
object CocktailTransition {

    private val ALLOWED: Map<CocktailStatus, Set<CocktailStatus>> = mapOf(
        DRAFT to setOf(PUBLISHED),
        PUBLISHED to setOf(DRAFT, ARCHIVED),
        ARCHIVED to setOf(DRAFT),
    )

    fun isAllowed(from: CocktailStatus, to: CocktailStatus): Boolean =
        to in allowedFrom(from)

    fun allowedFrom(from: CocktailStatus): Set<CocktailStatus> =
        ALLOWED[from].orEmpty()

    /** 게이트를 통과해야 하는 전이인가. 회수·보관에는 검사하지 않는다 (이슈 013 RED 24). */
    fun requiresGate(from: CocktailStatus, to: CocktailStatus): Boolean =
        from == DRAFT && to == PUBLISHED

    /**
     * 전이가 남길 감사 행위 (`PRIN-T08` — 전이는 **전부** 기록한다).
     *
     * `→ draft` 가 둘로 갈린다. `published → draft` 는 내린 것(`unpublish`),
     * `archived → draft` 는 다시 꺼낸 것(`restore`)이라 사후 조사에서 뜻이 다르다.
     */
    fun auditAction(from: CocktailStatus, to: CocktailStatus): AuditAction =
        when (from to to) {
            DRAFT to PUBLISHED -> AuditAction.PUBLISH
            PUBLISHED to DRAFT -> AuditAction.UNPUBLISH
            PUBLISHED to ARCHIVED -> AuditAction.ARCHIVE
            ARCHIVED to DRAFT -> AuditAction.RESTORE
            else -> error("허용되지 않는 전이에는 감사 행위가 없다: ${from.slug} → ${to.slug}")
        }
}
