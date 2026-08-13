package kr.kcocktail.user.bookmark

import kr.kcocktail.cocktail.api.CocktailFacade
import kr.kcocktail.cocktail.api.CocktailSummary
import kr.kcocktail.common.web.error.BadRequestException
import kr.kcocktail.common.web.error.ResourceNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 북마크 · 컬렉션 (ISSUE-031 · `FR-USER-004` · `R-F5-2`).
 *
 * ## 참조 무결성을 앱이 진다
 *
 * `bookmark.target_id` 에 FK 가 없다 (SPEC-06 §3.5 — 다형 참조라 걸 수 없다).
 * 그 대가를 여기서 치른다:
 *
 * | 시점 | 무엇 | RED |
 * |---|---|---|
 * | 저장 | 대상이 **발행돼 있는지** 확인 | 5 · 28 |
 * | 조회 | 사라졌거나 내려간 대상을 **걸러 냄** | 25 · 26 · 27 |
 *
 * **조회 시 거르되 행은 지우지 않는다** (RED 26). `archived` 는 되돌아올 수 있고,
 * 그때 저장해 둔 것이 살아 있어야 한다 — 잠깐 내려갔다고 남의 저장 목록을 지울 권한은 없다.
 *
 * ## 남의 것은 404 다
 *
 * SPEC-08 §2 의 `◐` — 자기 것만이다. 403 으로 답하면 **"그 id 는 존재한다"** 가 새어 나가고,
 * 순차 id 를 훑어 누가 무엇을 저장했는지 셀 수 있다 (`Action.OWN_BOOKMARK` 가 `HIDE` 인 이유).
 *
 * ## `cocktail` 모듈을 Facade 로만 본다
 *
 * `PRIN-T03`. 방향표에 `USER ──reads──▶ COCKTAIL` 을 추가하고 왔다 (2026-08-13 개정 · GAPS G-30) —
 * 이슈 023 이 Facade 만 거치면 되는 줄 알고 밟은 함정이 [G-28] 이다.
 * **Facade 를 거쳐도 모듈 화살표는 그대로다.**
 */
@Service
class BookmarkService(
    private val bookmarks: BookmarkRepository,
    private val collections: BookmarkCollectionRepository,
    private val cocktails: CocktailFacade,
) {

    /**
     * 저장. **멱등이다** (RED 6).
     *
     * 유니크 제약에 걸리면 409 를 주는 대신 이미 있는 것을 돌려준다 — 저장 버튼은
     * 두 번 눌릴 수 있고(네트워크가 느리면 반드시 그렇다), 그때 에러를 보여 줄 이유가 없다.
     * 결과 상태가 같으므로 멱등이 사실에 맞는 응답이다.
     */
    @Transactional
    fun add(userId: Long, request: AddBookmarkRequest): BookmarkItem {
        val type = BookmarkTarget.find(request.targetType)
            ?: throw BadRequestException(
                "알 수 없는 targetType 입니다: ${request.targetType} " +
                    "(가능: ${BookmarkTarget.entries.joinToString(", ") { it.code }})",
            )

        // RED 5·8·28 — 발행된 대상만 저장한다. bar·article 은 Phase 1a 에 도메인이 없어
        // 여기서 404 로 끝난다. "아직 지원 안 함" 이 아니라 "그런 것이 없다" 가 맞다.
        val target = resolve(type, request.targetSlug) ?: throw ResourceNotFoundException()

        val collectionId = request.collectionId?.also { requireOwnCollection(userId, it) }

        val existing = bookmarks.findByUserIdAndTargetTypeCodeAndTargetId(userId, type.code, target.id)
        if (existing != null) {
            // 컬렉션만 옮긴다. 유니크 키에 컬렉션이 없는 것이 그 설계의 뒷면이다 —
            // 같은 것을 컬렉션마다 하나씩 담을 수 없고, 대신 옮길 수 있다.
            collectionId?.let { existing.collectionId = it }
            return existing.toItem(target)
        }

        val saved = bookmarks.save(Bookmark(userId, type, target.id, collectionId))
        return saved.toItem(target)
    }

    /** 목록. 사라진 대상은 빠진다 (RED 27). */
    @Transactional(readOnly = true)
    fun list(userId: Long, collectionId: Long?): List<BookmarkItem> {
        val rows = when {
            collectionId == null -> bookmarks.findByUserIdOrderByIdDesc(userId)
            collectionId == DEFAULT_COLLECTION ->
                bookmarks.findByUserIdAndCollectionIdIsNullOrderByIdDesc(userId)
            else -> {
                requireOwnCollection(userId, collectionId)
                bookmarks.findByUserIdAndCollectionIdOrderByIdDesc(userId, collectionId)
            }
        }
        return resolveAll(rows)
    }

    /** 삭제. **남의 것은 404** 다 (RED 10). */
    @Transactional
    fun remove(userId: Long, bookmarkId: Long) {
        val bookmark = bookmarks.findById(bookmarkId).orElseThrow { ResourceNotFoundException() }
        if (bookmark.userId != userId) throw ResourceNotFoundException()

        bookmarks.delete(bookmark)
    }

    @Transactional
    fun createCollection(userId: Long, request: CreateCollectionRequest): CollectionItem {
        val name = request.name?.trim()?.takeIf { it.isNotBlank() }
            ?: throw BadRequestException("컬렉션 이름이 필요합니다") // RED 17

        return collections.save(BookmarkCollection(userId, name)).toItem(count = 0)
    }

    @Transactional(readOnly = true)
    fun myCollections(userId: Long): List<CollectionItem> =
        collections.findByUserIdOrderByIdDesc(userId).map { collection ->
            collection.toItem(count = bookmarks.findByCollectionIdOrderByIdDesc(collection.id).size)
        }

    /**
     * 공유 링크 조회 (RED 19~25). **비로그인도 본다.**
     *
     * 소유자 정보를 담지 않는다 (RED 22·23) — 컬렉션을 공유한 것이지 자기를 공개한 것이 아니다.
     * 내부 id 도 없다. 링크를 받은 사람에게 필요한 것은 **무엇이 담겼나** 하나다.
     *
     * 미발행 항목은 빠진다 (RED 25) — 공유 링크가 발행 전 콘텐츠를 새는 통로가 되면 안 된다.
     */
    @Transactional(readOnly = true)
    fun shared(shareToken: String): SharedCollection {
        val collection = collections.findByShareToken(shareToken)
            ?: throw ResourceNotFoundException()

        val items = resolveAll(bookmarks.findByCollectionIdOrderByIdDesc(collection.id))

        return SharedCollection(
            name = collection.name,
            items = items.map { SharedItem(it.targetType, it.targetSlug, it.nameKo, it.nameEn) },
        )
    }

    // ── 대상 해석 ────────────────────────────────────────────────────────

    /** 저장 시점 검증. 발행된 것만 실재한다 (SPEC-07 §5). */
    private fun resolve(type: BookmarkTarget, slug: String): CocktailSummary? = when (type) {
        BookmarkTarget.COCKTAIL -> cocktails.findPublished(slug)
        // Phase 1a 에 도메인이 없다. 열거에는 있고 대상이 없는 상태다 (RED 8).
        BookmarkTarget.BAR, BookmarkTarget.ARTICLE -> null
    }

    /**
     * 조회 시점 해석. **타입별로 묶어 한 번에 읽는다** — 행마다 부르면 N+1 이다.
     *
     * 못 찾은 것은 빠진다 (RED 27). 저장할 때는 있었는데 지금 없다는 뜻이고,
     * 그 사이에 회수·보관됐거나 지워진 것이다.
     */
    private fun resolveAll(rows: List<Bookmark>): List<BookmarkItem> {
        val cocktailIds = rows.filter { it.targetType == BookmarkTarget.COCKTAIL }.map { it.targetId }
        val byId = if (cocktailIds.isEmpty()) emptyMap()
        else cocktails.findPublishedByIds(cocktailIds).associateBy { it.id }

        return rows.mapNotNull { row ->
            when (row.targetType) {
                BookmarkTarget.COCKTAIL -> byId[row.targetId]?.let { row.toItem(it) }
                else -> null
            }
        }
    }

    /** 남의 컬렉션에는 담을 수 없다 (RED 16). 존재 여부도 알려 주지 않는다. */
    private fun requireOwnCollection(userId: Long, collectionId: Long) {
        val collection = collections.findById(collectionId).orElseThrow { ResourceNotFoundException() }
        if (collection.userId != userId) throw ResourceNotFoundException()
    }

    private fun Bookmark.toItem(target: CocktailSummary) = BookmarkItem(
        id = id,
        targetType = targetType.code,
        targetSlug = target.slug,
        nameKo = target.nameKo,
        nameEn = target.nameEn,
        // `null` 을 그대로 내보낸다. "기본 컬렉션" 이라는 가짜 id 를 만들면
        // 클라이언트가 그 값으로 컬렉션을 조회하려 든다.
        collectionId = collectionId,
    )

    private fun BookmarkCollection.toItem(count: Int) = CollectionItem(
        id = id,
        name = name,
        shareToken = shareToken,
        bookmarkCount = count,
    )

    companion object {
        /**
         * 기본 컬렉션을 가리키는 조회용 값 (RED 14).
         *
         * 저장은 `collection_id IS NULL` 인데, 쿼리스트링으로 `null` 을 표현할 방법이 없다.
         * `0` 은 실제 id 로 쓰이지 않는다 — `GENERATED ALWAYS AS IDENTITY` 가 1부터 준다.
         */
        const val DEFAULT_COLLECTION = 0L
    }
}
