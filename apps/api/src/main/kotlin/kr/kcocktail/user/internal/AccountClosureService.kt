package kr.kcocktail.user.internal

import kr.kcocktail.user.repository.UserRepository
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
 * ## 지금은 절반만 한다
 *
 * `analytics_event`(이슈 034)와 `audit_log`(이슈 014)가 아직 없다.
 * [ClosureHook] 이 그 계약이고, 해당 이슈가 구현을 끼운다.
 * 훅이 없으면 그 이슈가 탈퇴 처리를 잊고, 잊었다는 사실도 드러나지 않는다.
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

/**
 * 탈퇴 시 각 모듈이 자기 데이터를 정리하는 지점.
 *
 * `user` 모듈이 남의 테이블을 직접 건드리지 않는다 (`PRIN-T03`) —
 * 그렇게 하면 탈퇴 하나 때문에 모든 모듈이 서로를 참조하게 된다.
 */
interface ClosureHook {
    /** `user` 행이 삭제되기 **직전**에 불린다. 같은 트랜잭션이다. */
    fun onAccountClosing(userId: Long)
}
