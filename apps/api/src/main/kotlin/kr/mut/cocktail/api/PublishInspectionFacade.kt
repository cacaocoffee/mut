package kr.mut.cocktail.api

import kr.mut.common.web.error.Violation

/**
 * 배치 검증(이슈 016)이 칵테일을 보는 창구 (`PRIN-T03` · SPEC-05 §3).
 *
 * ## 왜 판정을 여기서 돌려주나
 *
 * `admin` 이 후보를 받아 스스로 게이트를 돌리게 하면, **후보를 조립하는 코드가 두 벌**이 된다.
 * 발행 경로(이슈 013)와 배치가 다른 스냅샷을 보면 `NFR-D-02`("게이트를 우회한 published")를
 * 검출할 수 없다 — 검사하는 쪽이 다른 것을 보고 있으니까.
 *
 * 그래서 조립도 판정도 `cocktail` 안에서 하고, 밖으로는 **결과만** 나간다.
 * 규칙의 정본은 [PublishGate] 하나다.
 */
interface PublishInspectionFacade {

    /** 발행분 전체. `NFR-D-01` 이 "발행분"이라고 못박았다 — `draft` 는 대상이 아니다. */
    fun publishedIds(): List<Long>

    /**
     * 발행 게이트 6종 (`GATE-COCKTAIL-*`). 저장하지 않고 판정만 한다.
     *
     * 발행분에 위반이 남아 있다는 것은 **게이트를 통과하지 않고 published 가 됐다**는
     * 뜻이다 (`NFR-D-02`) — 데이터가 아니라 경로가 뚫린 것이다.
     */
    fun inspectGate(cocktailId: Long): List<Violation>

    /**
     * 앱 강제 불변식 (`INV-COCKTAIL-*`, SPEC-06 §4.3).
     *
     * 게이트와 나누는 이유: 이쪽은 발행 여부와 무관한 **데이터 자체의 규칙**이고,
     * 코드가 달라 태스크 큐에서 대응이 갈린다.
     */
    fun inspectInvariants(cocktailId: Long): List<Violation>

    /** 보고에 실을 최소 식별자. 태스크 큐가 사람에게 보여 줄 것들이다. */
    fun identify(cocktailId: Long): CocktailIdentity?
}

data class CocktailIdentity(
    val id: Long,
    val slug: String,
    val nameKo: String,
)
