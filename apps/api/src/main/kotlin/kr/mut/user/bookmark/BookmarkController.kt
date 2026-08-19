package kr.mut.user.bookmark

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import kr.mut.common.security.session.AbsoluteExpiryFilter
import kr.mut.common.web.ApiPaths
import kr.mut.common.web.error.UnauthenticatedException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 내 북마크와 컬렉션 (ISSUE-031 · SPEC-07 §2.5 · SPEC-08 §2).
 *
 * ## 권한이 `◐` 다 — 자기 것만
 *
 * 소유 판정을 매 요청 세션에서 읽는다. `PermissionMatrix.OWN_BOOKMARK` 를 부르지 않는 이유는,
 * 그 액션의 스코프 판정(`Scope.Own`)이 **대상의 소유자를 이미 알고 있을 때** 쓰는 것이라
 * 조회 전에 한 번 더 읽어야 하기 때문이다. 서비스가 읽으면서 판정하는 편이 왕복이 적고,
 * 결과도 같다 — **남의 것은 404**.
 *
 * ## 로그인만 확인하고 역할은 보지 않는다
 *
 * SPEC-08 §2 에서 북마크는 **로그인한 전 역할**이 갖는다. `member` 도 `admin` 도 같다 —
 * 역할로 갈리는 것이 없으므로 매트릭스를 부르면 항상 같은 답이 나온다.
 */
@RestController
@RequestMapping("${ApiPaths.BASE}/me")
class BookmarkController(private val bookmarks: BookmarkService) {

    @GetMapping("/bookmarks")
    @Operation(
        summary = "내 북마크 목록",
        description = "사라지거나 내려간 대상은 빠진다 (다형 참조라 앱이 무결성을 진다).",
    )
    fun list(
        @Parameter(description = "생략하면 전체. 0 이면 기본 컬렉션(collection_id IS NULL)")
        @RequestParam(required = false) collectionId: Long?,
        http: HttpServletRequest,
    ): List<BookmarkItem> = bookmarks.list(currentUser(http), collectionId)

    /**
     * 저장. **멱등이다** (RED 6) — 두 번 눌러도 201 이 아니라 200 이고, 결과가 같다.
     *
     * 201 을 고집하면 "이미 저장됨" 을 409 로 답해야 하는데, 저장 버튼은 네트워크가 느리면
     * 반드시 두 번 눌린다. 사용자에게 아무 일도 안 일어난 것처럼 보이는 편이 맞다.
     */
    @PostMapping("/bookmarks")
    @Operation(summary = "북마크 추가", description = "targetSlug 로 받는다. 중복은 멱등이다.")
    fun add(
        @Valid @RequestBody request: AddBookmarkRequest,
        http: HttpServletRequest,
    ): BookmarkItem = bookmarks.add(currentUser(http), request)

    /** 남의 것은 **404** 다 — 403 이면 그 id 가 존재한다는 사실이 새어 나간다. */
    @DeleteMapping("/bookmarks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "북마크 삭제")
    fun remove(@PathVariable id: Long, http: HttpServletRequest) {
        bookmarks.remove(currentUser(http), id)
    }

    @GetMapping("/collections")
    @Operation(summary = "내 컬렉션 목록")
    fun collections(http: HttpServletRequest): List<CollectionItem> =
        bookmarks.myCollections(currentUser(http))

    @PostMapping("/collections")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "컬렉션 생성", description = "생성 시점에 공유 토큰이 함께 발급된다.")
    fun createCollection(
        @Valid @RequestBody request: CreateCollectionRequest,
        http: HttpServletRequest,
    ): CollectionItem = bookmarks.createCollection(currentUser(http), request)

    /** 비로그인은 401 이다 — 로그인하면 될 수도 있다는 사실이다 (SPEC-08 §2: 비로그인 `—`). */
    private fun currentUser(request: HttpServletRequest): Long =
        request.getSession(false)?.getAttribute(AbsoluteExpiryFilter.USER_ID) as? Long
            ?: throw UnauthenticatedException()
}

/**
 * 공유 링크 (SPEC-07 §2.5 — 권한 `—`).
 *
 * **비로그인도 본다.** 그것이 공유의 뜻이다. 대신 토큰이 추측 불가능해야 방어가 성립한다
 * (`BookmarkCollection.newShareToken`).
 *
 * `/me` 아래가 아니라 최상위인 이유: 남의 컬렉션이다. 경로가 `/me` 면 링크를 받은 사람이
 * 자기 것으로 오해한다.
 */
@RestController
@RequestMapping("${ApiPaths.BASE}/collections")
class SharedCollectionController(private val bookmarks: BookmarkService) {

    @GetMapping("/{shareToken}")
    @Operation(
        summary = "공유된 컬렉션",
        description = "비로그인 조회. 소유자 정보와 내부 id 를 담지 않고, 미발행 항목은 빠진다.",
    )
    fun shared(@PathVariable shareToken: String): SharedCollection = bookmarks.shared(shareToken)
}
