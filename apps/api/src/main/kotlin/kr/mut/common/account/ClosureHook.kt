package kr.mut.common.account

/**
 * 탈퇴 시 각 모듈이 자기 데이터를 정리하는 지점 (SPEC-08 §5.3).
 *
 * `user` 모듈이 남의 테이블을 직접 건드리지 않는다 (`PRIN-T03`) —
 * 그렇게 하면 탈퇴 하나 때문에 모든 모듈이 서로를 참조하게 된다.
 *
 * ## 왜 `common` 에 있나 (2026-08-14, 이슈 034)
 *
 * 처음에는 `user.internal` 에 있었다. 그런데 구현하는 쪽이 **`user` 를 참조하게 되고**,
 * `common.analytics` 가 구현하려는 순간 `COMMON ──▶ USER` 라는 없는 화살표가 생긴다
 * (`ModuleBoundaryTest` RED 4 — "공용 커널은 아무도 참조하지 않는다").
 *
 * 계약을 `common` 에 두면 방향이 바로 선다: `user` 도 `common.analytics` 도
 * 공용 커널을 볼 뿐이다. **구현이 늘 때마다 화살표가 늘지 않는다** — 그게 이 자리의 값이다.
 */
interface ClosureHook {
    /** `user` 행이 삭제되기 **직전**에 불린다. 같은 트랜잭션이다. */
    fun onAccountClosing(userId: Long)
}
