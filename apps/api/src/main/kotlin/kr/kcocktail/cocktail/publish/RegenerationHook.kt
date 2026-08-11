package kr.kcocktail.cocktail.publish

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 발행이 **커밋된 뒤** 프론트 재생성을 요청한다 (SPEC-07 §3.4 부수효과 ③).
 *
 * ## 왜 커밋 뒤인가 — 색인 동기화와 다르다
 *
 * DECISIONS §1.7 이 둘을 갈랐다.
 *
 * | 부수효과 | 실패하면 | 왜 |
 * |---|---|---|
 * | 색인 동기화 (이슈 017) | **발행 롤백** | 발행됐는데 검색에 안 나오면 검색이 틀린 답을 준다 |
 * | 재생성 훅 (이슈 015) | **발행 유지** (`NFR-R-03`) | 프론트가 늦게 갱신될 뿐, 데이터는 맞다 |
 *
 * 그래서 색인은 `@EventListener` 로 트랜잭션 안에서 받고, 재생성은 여기서 커밋 뒤에 부른다.
 *
 * 실제 구현은 **이슈 015** 가 넣는다. 지금은 [NoOpRegenerationHook] 이 자리를 지킨다 —
 * 계약을 지금 고정해 두지 않으면 015 가 들어올 때 발행 경로를 다시 열어야 한다.
 */
interface RegenerationHook {

    /** @param slug 재생성할 칵테일. 발행 시점에 확정된다 (이슈 014 `slug` 불변). */
    fun onPublished(slug: String)
}

/** 이슈 015 가 실제 구현으로 교체한다. 그때까지 발행은 훅 없이도 완결된다. */
@Component
class NoOpRegenerationHook : RegenerationHook {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun onPublished(slug: String) {
        log.debug("재생성 훅 미구현 — 건너뛴다 (이슈 015): {}", slug)
    }
}
