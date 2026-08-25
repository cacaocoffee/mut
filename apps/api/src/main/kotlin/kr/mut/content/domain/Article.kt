package kr.mut.content.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import kr.mut.common.entity.BaseEntity
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * 아티클 (SPEC-06 §3.6 개정 · ADR-0011 · V028).
 *
 * ## 코드에서 DB 로 왔다
 *
 * 143편은 `packages/domain` 코드였다가 이관됐다 (ADR-0010 → 0011). 어드민 편집이
 * 붙기 전까지는 코드가 정본이고 시드(R__seed_03)가 DB 를 채운다.
 *
 * ## `body` 는 블록 배열이다
 *
 * 문단 · 소제목 · 인용 · 사진 네 종류. 구조가 화면마다 바뀌지 않고 통째로 읽고 쓰므로
 * 조인이 아니라 JSONB 한 컬럼이다. 타입은 화면의 `ArticleBlock`(types.ts)과 쌍이라
 * 여기서는 느슨한 `List<Map>` 으로 받고, 검증은 화면과 API DTO 가 한다.
 *
 * ## 삭제가 아니라 상태 전이다 (`PRIN-D05`)
 *
 * 칵테일과 같다. 물리 삭제는 DB 가 막고(REVOKE DELETE), 삭제는 `archived` 로 내린다.
 */
@Entity
@Table(name = "article")
class Article(
    @Column(nullable = false, updatable = false)
    var slug: String,

    @Column(nullable = false)
    var category: String,

    @Column(nullable = false)
    var title: String,

    @Column(nullable = false)
    var dek: String,

    @Column(nullable = false)
    var hero: String,

    @Column(name = "source_url")
    var sourceUrl: String? = null,

    @Column(name = "is_sponsored", nullable = false)
    var isSponsored: Boolean = false,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    var body: List<Map<String, Any?>> = emptyList(),

    @Column(nullable = false)
    var status: String = ArticleStatus.DRAFT.slug,

    @Column(name = "published_at")
    var publishedAt: Instant? = null,
) : BaseEntity()
