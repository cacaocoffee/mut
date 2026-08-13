package kr.kcocktail.user.bookmark

import org.springframework.data.jpa.repository.JpaRepository

/**
 * `PRIN-T03` — 모듈 밖에서 참조하지 않는다.
 *
 * `user/bookmark` 안에 리포지토리를 두는 이유: 이 이슈의 `owns` 가 이 디렉터리 하나다.
 * `user/repository` 에 두면 이슈 005 의 소유 경로와 겹쳐 두 세션이 충돌한다 (CONVENTIONS §4).
 */
interface BookmarkRepository : JpaRepository<Bookmark, Long> {

    fun findByUserIdOrderByIdDesc(userId: Long): List<Bookmark>

    fun findByUserIdAndCollectionIdOrderByIdDesc(userId: Long, collectionId: Long): List<Bookmark>

    /** 기본 컬렉션 — `collection_id IS NULL` (SPEC-06 §3.5). */
    fun findByUserIdAndCollectionIdIsNullOrderByIdDesc(userId: Long): List<Bookmark>

    /** 멱등 저장이 이것으로 판정한다 (RED 6). 유니크 제약과 같은 키다. */
    fun findByUserIdAndTargetTypeCodeAndTargetId(
        userId: Long,
        targetTypeCode: String,
        targetId: Long,
    ): Bookmark?

    fun findByCollectionIdOrderByIdDesc(collectionId: Long): List<Bookmark>
}

interface BookmarkCollectionRepository : JpaRepository<BookmarkCollection, Long> {

    fun findByUserIdOrderByIdDesc(userId: Long): List<BookmarkCollection>

    fun findByShareToken(shareToken: String): BookmarkCollection?
}
