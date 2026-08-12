package kr.kcocktail.search.index

import kr.kcocktail.cocktail.api.CocktailArchived
import kr.kcocktail.cocktail.api.CocktailPublished
import kr.kcocktail.cocktail.api.CocktailRenamed
import kr.kcocktail.cocktail.api.CocktailUnpublished
import kr.kcocktail.ingredient.api.IngredientSaved
import kr.kcocktail.search.api.SearchDocumentDraft
import kr.kcocktail.search.api.SearchEntityType
import kr.kcocktail.search.api.SearchIndexSync
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * 색인을 **구독한다** (ISSUE-017, SPEC-05 §3).
 *
 * ## 부르지 않고 듣는다
 *
 * `cocktail` · `ingredient` 는 `search` 를 모른다. 알면 순환이 생긴다
 * (`008 → 017 → 014 → 013 → 010 → 008`) — SPEC-05 §3 이 "부수효과는 도메인 이벤트로
 * 발행하고 리스너가 처리한다" 고 한 이유다. 이 클래스가 그 리스너다.
 *
 * ## `@TransactionalEventListener` 가 아니다
 *
 * 평범한 [EventListener] 라 **발행 트랜잭션 안에서** 불린다. 여기서 터지면 발행이 롤백된다
 * (DECISIONS §1.7 · RED 18). `AFTER_COMMIT` 으로 바꾸면 그 계약이 조용히 깨진다 —
 * 색인만 실패하고 발행은 남아, 발행됐는데 검색에 안 나오는 상태가 된다.
 * 재생성 훅(`NFR-R-03`)은 정반대로 커밋 뒤에 부른다. 둘을 헷갈리면 양쪽이 다 틀린다.
 *
 * ## Phase 1a 는 두 종류다
 *
 * `bar` 는 1b, `article` 은 Phase 2 라 아직 아무도 발행하지 않는다 (RED 28).
 * [SearchEntityType] 에 값은 미리 있다 — 나중에 늘리면 클라이언트의 타입별
 * 그룹 렌더링(`R-F5-1`)이 깨진다.
 */
@Component
class SearchIndexListener(private val sync: SearchIndexSync) {

    /** 발행 — 이름 · 별칭까지 새로 싣고 공개로 올린다. */
    @EventListener
    fun on(event: CocktailPublished) = sync.index(
        SearchDocumentDraft(
            type = SearchEntityType.COCKTAIL,
            entityId = event.entityId,
            slug = event.slug,
            nameKo = event.nameKo,
            nameEn = event.nameEn,
            aliases = event.aliases,
            isPublished = true,
        ),
    )

    /** 회수 — 행은 남기고 플래그만 내린다 (DECISIONS §1.9). */
    @EventListener
    fun on(event: CocktailUnpublished) =
        sync.setPublished(SearchEntityType.COCKTAIL, event.entityId, false)

    /** 보관 — 공개 API 에서 404 다 (SPEC-07 §5). 회수와 같이 내린다. */
    @EventListener
    fun on(event: CocktailArchived) =
        sync.setPublished(SearchEntityType.COCKTAIL, event.entityId, false)

    /**
     * 이름 · 별칭 변경 — **발행 상태를 건드리지 않는다.**
     *
     * 검색어가 이름과 별칭이라 (`R-F2.1-3`) 발행 상태가 그대로여도 색인은 바뀌어야 한다.
     * `isPublished = null` 이 "그대로 두라" 는 뜻이다 — `false` 로 보내면
     * 이름을 고친 것만으로 발행된 칵테일이 검색에서 사라진다.
     */
    @EventListener
    fun on(event: CocktailRenamed) = sync.index(
        SearchDocumentDraft(
            type = SearchEntityType.COCKTAIL,
            entityId = event.entityId,
            slug = event.slug,
            nameKo = event.nameKo,
            nameEn = event.nameEn,
            aliases = event.aliases,
            isPublished = null,
        ),
    )

    /**
     * 재료 저장 · 승인 — `is_approved` 가 곧 공개 여부다.
     *
     * [IngredientSaved] 의 "미승인 재료는 색인하지 않는다" 를 **행 삭제가 아니라 플래그**로
     * 실현한다. 승인 대기 중의 이름 변경을 따라가야 승인되는 순간 최신 이름으로 검색된다.
     * 공개 노출은 어느 쪽이든 막힌다 (이슈 024 가 `is_published` 로 거른다).
     */
    @EventListener
    fun on(event: IngredientSaved) = sync.index(
        SearchDocumentDraft(
            type = SearchEntityType.INGREDIENT,
            entityId = event.entityId,
            slug = event.slug,
            nameKo = event.nameKo,
            nameEn = event.nameEn,
            aliases = event.aliases,
            isPublished = event.isApproved,
        ),
    )
}
