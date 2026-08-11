package kr.kcocktail.cocktail.publish

import kr.kcocktail.cocktail.domain.CocktailStatus
import kr.kcocktail.cocktail.domain.CocktailStatus.ARCHIVED
import kr.kcocktail.cocktail.domain.CocktailStatus.DRAFT
import kr.kcocktail.cocktail.domain.CocktailStatus.PUBLISHED

/**
 * SPEC-02 §8.1 상태 전이 (DECISIONS §1.4 확정).
 *
 * ```
 * draft ⇄ published → archived → draft
 * ```
 *
 * | 전이 | 허용 | 왜 |
 * |---|---|---|
 * | `draft → published` | ✅ | 게이트 통과 시 |
 * | `published → draft` | ✅ | 되돌리기 (SPEC-02 §8.1) |
 * | `published → archived` | ✅ | 내리기 |
 * | `archived → draft` | ✅ | 다시 손보기 |
 * | `draft → archived` | ❌ | 도식에 없다 |
 * | **`archived → published`** | ❌ | `archived → draft → published` 만 |
 *
 * 마지막이 보수적인 선택이다 — 내렸던 것을 바로 올리면 **왜 내렸는지 다시 보지 않는다.**
 * draft 를 거치면 게이트를 다시 통과해야 한다.
 */
object PublishTransition {

    private val ALLOWED: Set<Pair<CocktailStatus, CocktailStatus>> = setOf(
        DRAFT to PUBLISHED,
        PUBLISHED to DRAFT,
        PUBLISHED to ARCHIVED,
        ARCHIVED to DRAFT,
    )

    fun isAllowed(from: CocktailStatus, to: CocktailStatus): Boolean = (from to to) in ALLOWED

    /** 게이트를 통과해야 하는 전이인가. 회수·보관에는 검사하지 않는다 (RED 24). */
    fun requiresGate(from: CocktailStatus, to: CocktailStatus): Boolean =
        from == DRAFT && to == PUBLISHED

    fun allowedFrom(from: CocktailStatus): Set<CocktailStatus> =
        ALLOWED.filter { it.first == from }.map { it.second }.toSet()
}
