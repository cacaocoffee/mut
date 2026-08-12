package kr.kcocktail.common.revalidate

/**
 * 프론트에 on-demand 재생성을 요청한다 (SPEC-07 §4).
 *
 * **방향이 반대인 유일한 호출이다** — 백엔드가 프론트를 부른다.
 *
 * ## fire-and-forget
 *
 * `FR-COCKTAIL-016` 이 "에디터가 반영을 기다리지 않는다"고 했다.
 * 그래서 이 메서드는 **즉시 돌아온다.** 결과를 돌려주지 않는 것도 의도다 —
 * 반환값이 있으면 호출부가 그것을 보고 분기하고, 분기하는 순간 발행이 훅에 매인다.
 *
 * ## 실패는 삼킨다 (`NFR-R-03`)
 *
 * 던지지 않는다. SPEC-07 §4 가 "실패해도 발행 트랜잭션을 되돌리지 않는다.
 * ISR 주기가 결국 따라잡는다" 고 했다. 재시도 큐를 만들지 않는 이유도 같다 —
 * 자동으로 복구되는 실패에 복잡도를 쓰지 않는다.
 */
interface RevalidateHook {

    /** @param paths 다시 만들 정적 경로. [RevalidatePaths] 가 만든다. */
    fun revalidate(paths: List<String>)
}
