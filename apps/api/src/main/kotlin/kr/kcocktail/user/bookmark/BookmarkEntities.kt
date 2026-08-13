package kr.kcocktail.user.bookmark

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import kr.kcocktail.common.entity.BaseEntity
import java.security.SecureRandom
import java.util.Base64

/**
 * 저장한 것 하나 (SPEC-02 §7 · SPEC-06 §3.5 · `FR-USER-004`).
 *
 * ## 대상에 FK 가 없다
 *
 * 다형 참조라 걸 수 없다 — SPEC-06 §3.5 가 명시한 의도적 선택이다.
 * 타입별 테이블로 쪼개지 않은 이유는 **한 컬렉션이 세 종류를 섞어 담아야** 해서다 (`R-F5-2`).
 *
 * 대가는 dangling 참조이고, 무결성은 앱이 진다 ([BookmarkService]) —
 * 저장할 때 발행 여부를 보고, 조회할 때 사라진 것을 걸러 낸다.
 *
 * ## 사용자 참조를 id 로 든다
 *
 * `@ManyToOne` 으로 `User` 를 잡지 않는다. 지연 로딩이 `open-in-view: false` 와 만나
 * 컨트롤러에서 터지고(이슈 025 에서 겪었다), 북마크가 사용자에 대해 알아야 할 것은
 * **누구 것인가** 하나뿐이다.
 */
@Entity
@Table(name = "bookmark")
class Bookmark(
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: Long,

    @Column(name = "target_type", nullable = false, length = 12, updatable = false)
    private var targetTypeCode: String,

    @Column(name = "target_id", nullable = false, updatable = false)
    val targetId: Long,

    /** `null` 이면 기본 컬렉션이다 (SPEC-06 §3.5). */
    @Column(name = "collection_id")
    var collectionId: Long? = null,
) : BaseEntity() {

    var targetType: BookmarkTarget
        get() = BookmarkTarget.ofCode(targetTypeCode)
        set(value) { targetTypeCode = value.code }

    constructor(userId: Long, targetType: BookmarkTarget, targetId: Long, collectionId: Long? = null) :
        this(userId, targetType.code, targetId, collectionId)
}

/**
 * 저장 대상 3종 (SPEC-06 §3.5 CHECK).
 *
 * **Phase 1a 에 실재하는 것은 `COCKTAIL` 뿐이다.** 그래도 셋 다 정의한다 —
 * 나중에 열거를 늘리면 이 목록을 읽는 쪽(클라이언트 필터 · 컬렉션 화면)이 그때 깨진다.
 * `BAR` · `ARTICLE` 로 저장을 시도하면 대상이 없어 404 다 (RED 8).
 */
enum class BookmarkTarget(val code: String) {
    COCKTAIL("cocktail"),
    BAR("bar"),
    ARTICLE("article"),
    ;

    /** Phase 1a 에 대상 도메인이 있는가. 없으면 저장할 수 있는 것이 없다. */
    val isAvailable: Boolean get() = this == COCKTAIL

    companion object {
        fun find(code: String): BookmarkTarget? = entries.firstOrNull { it.code == code }

        fun ofCode(code: String): BookmarkTarget =
            find(code) ?: error("알 수 없는 저장 대상: $code")
    }
}

/**
 * 컬렉션 (`R-F5-2`).
 *
 * ## 공유 토큰이 순차가 아니다
 *
 * 순차 id 를 쓰면 1 부터 훑어 남의 컬렉션을 전부 읽을 수 있다. 공유 링크는
 * **아는 사람만 여는 것**이지 공개된 것이 아니다 (RED 20).
 */
@Entity
@Table(name = "bookmark_collection")
class BookmarkCollection(
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: Long,

    @Column(name = "name", nullable = false, length = 60)
    var name: String,
) : BaseEntity() {

    /**
     * `null` 이면 아직 공유하지 않았다.
     *
     * **생성 시점에 발급한다** (RED 18). 나중에 "공유하기" 를 누를 때 만들면 그 시점에
     * 쓰기가 한 번 더 필요한데, 토큰은 알기 전까지 아무 값도 아니라 미리 만들어도 새지 않는다.
     *
     * **해제·재발급을 제공하지 않는다** (RED 24) — SPEC 에 없다.
     */
    @Column(name = "share_token", length = 64, updatable = false)
    var shareToken: String? = newShareToken()
        protected set

    companion object {
        private val RANDOM = SecureRandom()

        /** 24바이트 → base64url 32자. 추측하려면 우주의 나이가 모자란다. */
        fun newShareToken(): String {
            val bytes = ByteArray(24)
            RANDOM.nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }
}
