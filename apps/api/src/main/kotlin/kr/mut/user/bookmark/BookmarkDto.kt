package kr.mut.user.bookmark

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * 저장 요청 (SPEC-07 §2.5).
 *
 * **`targetSlug` 다.** 공개 식별자가 slug 이기 때문이다 (SPEC-07 §1.1) —
 * 내부 id 를 받으면 클라이언트가 그 값을 알아야 하고, 그러려면 어딘가에서 내보내야 한다.
 */
data class AddBookmarkRequest(
    @field:NotBlank
    @Schema(description = "cocktail · bar · article. Phase 1a 에 실재하는 것은 cocktail 뿐이다")
    val targetType: String = "",

    @field:NotBlank
    val targetSlug: String = "",

    @Schema(description = "생략하면 기본 컬렉션이다 (SPEC-06 §3.5)")
    val collectionId: Long? = null,
)

data class CreateCollectionRequest(
    @field:Size(max = 60)
    val name: String? = null,
)

/**
 * 내 북마크 한 줄.
 *
 * `id` 를 담는다 — `DELETE /me/bookmarks/{id}` 가 이것을 쓴다 (SPEC-07 §2.5).
 * **본인 리소스라 허용**되는 예외다 (RED 31): 남의 것은 애초에 목록에 없고,
 * 있어도 삭제가 404 다.
 */
data class BookmarkItem(
    val id: Long,
    val targetType: String,
    val targetSlug: String,
    val nameKo: String,
    val nameEn: String,

    /** `null` 이면 기본 컬렉션이다. 가짜 id 를 만들지 않는다. */
    val collectionId: Long?,
)

data class CollectionItem(
    val id: Long,
    val name: String,
    /** 공유 링크는 `/collections/{shareToken}` 이다. */
    val shareToken: String?,
    val bookmarkCount: Int,
)

/**
 * 공유 링크 응답 (`R-F5-2` · RED 22·23).
 *
 * **소유자도 내부 id 도 없다.** 컬렉션을 공유한 것이지 자기를 공개한 것이 아니고,
 * 링크를 받은 사람에게 필요한 것은 무엇이 담겼나 하나다.
 *
 * 표시명조차 넣지 않았다 — 이슈는 "표시명 정도" 를 허용했지만, 카카오톡으로 링크가
 * 굴러다니는 것을 전제하면(`FR-USER-005`) 이름이 붙어 다닐 이유가 없다.
 * 필요해지면 그때 넣는 편이, 넣어 두고 빼는 것보다 쉽다.
 */
data class SharedCollection(
    val name: String,
    val items: List<SharedItem>,
)

data class SharedItem(
    val targetType: String,
    val targetSlug: String,
    val nameKo: String,
    val nameEn: String,
)
