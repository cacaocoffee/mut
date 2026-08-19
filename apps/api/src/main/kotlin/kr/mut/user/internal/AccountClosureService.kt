package kr.mut.user.internal

import kr.mut.common.account.ClosureHook
import kr.mut.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 탈퇴 (SPEC-08 §5.3).
 *
 * | 데이터 | 처리 | 왜 |
 * |---|---|---|
 * | `user` 행 | 즉시 삭제 | |
 * | 북마크 · 컬렉션 · 내 술장 | CASCADE 삭제 | 본인 것이다 |
 * | `analytics_event.user_id` | **`NULL` 로 익명화** | 행은 남긴다 — 지우면 지표가 소급해 바뀐다 |
 * | `audit_log.actor_user_id` | **유지** | 누가 발행했는지는 기록이다. 지우면 감사가 성립하지 않는다 |
 *
 * 마지막 둘이 요점이다. **탈퇴는 "흔적을 전부 지운다"가 아니다** —
 * 개인을 식별할 수 없게 만들되, 일어난 일의 기록은 남긴다.
 *
 * ## 훅이 계약이다
 *
 * [ClosureHook] 은 `common` 에 있다 (이슈 034 에서 옮겼다). `user.internal` 에 두면
 * 구현하는 쪽이 `user` 를 참조하게 되고, `common.analytics` 가 구현하는 순간
 * `COMMON ──▶ USER` 라는 없는 화살표가 생긴다.
 *
 * `audit_log`(이슈 014)는 훅이 없다 — **아무것도 안 하는 것이 그쪽의 처리**다.
 * `analytics_event`(이슈 034)가 `user_id` 를 `NULL` 로 익명화한다.
 */
@Service
class AccountClosureService(
    private val users: UserRepository,
    private val hooks: List<ClosureHook>,
) {

    @Transactional
    fun close(userId: Long) {
        // 훅을 먼저 돌린다. user 행이 사라진 뒤에는 FK 로 연결된 것을 찾을 수 없다.
        hooks.forEach { it.onAccountClosing(userId) }
        users.deleteById(userId)
    }
}
