package kr.kcocktail.search.api

/**
 * 색인 동기화 (ISSUE-017, SPEC-06 §3.8 · SPEC-07 §3.4).
 *
 * ## `api` 에 있지만 타 모듈이 부르는 표면이 아니다
 *
 * 다른 모듈이 이것을 부르면 **순환이 생긴다** — `008 → 017 → 014 → 013 → 010 → 008`.
 * SPEC-05 §3 이 "부수효과는 도메인 이벤트로 발행하고 리스너가 처리한다"고 한 이유가 그것이다.
 * 각 도메인은 [kr.kcocktail.cocktail.api.CocktailPublished] 같은 **사건만 알리고**,
 * [kr.kcocktail.search.index.SearchIndexListener] 가 구독해 여기를 부른다.
 *
 * `api` 패키지에 두는 것은 **경계 테스트의 요구** 다 (`PRIN-T03` · ISSUE-001 RED 3):
 * 모듈 간에 보이는 타입은 `api` 여야 한다. 보이는 자리에 두되 **부르지 않는다** 는
 * 의존 역전을 이 주석이 근거로 남긴다. 호출부가 생기면 ISSUE-001 RED 7(순환)이 빨개진다.
 */
interface SearchIndexSync {

    /**
     * 색인을 밀어 넣는다. `(entity_type, entity_id)` 로 **UPSERT** 라 몇 번을 불러도 한 행이다.
     *
     * 호출부에 트랜잭션이 있어야 한다 — 동기화 실패는 원본 저장을 롤백시킨다 (DECISIONS §1.7).
     */
    fun index(draft: SearchDocumentDraft)

    /** 이름·별칭을 건드리지 않고 공개 여부만 바꾼다 (회수·보관). 행이 없으면 아무것도 하지 않는다. */
    fun setPublished(type: SearchEntityType, entityId: Long, isPublished: Boolean)
}

/**
 * 색인 대상 4종. **지금 다 정의한다** (RED 29).
 *
 * 나중에 늘리면 클라이언트의 타입별 그룹 렌더링(`R-F5-1`)이 깨진다. `bar` 는 Phase 1b,
 * `article` 은 Phase 2 라 아직 아무도 발행하지 않지만 값은 미리 있다.
 *
 * 선언 순서가 곧 **DECISIONS §1.9 의 그룹 순서** 다 — `cocktail → ingredient → bar → article`.
 */
enum class SearchEntityType(val slug: String, val defaultWeight: Int) {

    /**
     * `defaultWeight` 는 **잠정값이다** (G-13 · SPEC-06 §7 "가중치 산정식 미정").
     * DECISIONS §1.9 가 "`entity_type` 별 고정값으로 시작" 이라고 닫았다.
     * 산정식이 정해지면 이 상수가 아니라 산정 함수로 갈아탄다 (이슈 024).
     */
    COCKTAIL("cocktail", 100),
    INGREDIENT("ingredient", 50),
    BAR("bar", 80),
    ARTICLE("article", 30),
}

/**
 * 색인 한 행. **엔티티를 담지 않는다** — 리스너가 다른 트랜잭션에서 지연 로딩을 만난다.
 *
 * @param isPublished `null` 이면 **기존 값을 유지한다.** 이름만 바뀐 경우
 *   ([kr.kcocktail.cocktail.api.CocktailRenamed]) 발행 상태는 그대로여야 한다.
 *   새 행이면 `false` 로 들어간다.
 */
data class SearchDocumentDraft(
    val type: SearchEntityType,
    val entityId: Long,
    val slug: String,
    val nameKo: String,
    val nameEn: String?,
    val aliases: List<String>,
    val isPublished: Boolean?,
)
