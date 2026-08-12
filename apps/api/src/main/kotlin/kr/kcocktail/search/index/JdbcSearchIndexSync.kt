package kr.kcocktail.search.index

import kr.kcocktail.search.api.SearchDocumentDraft
import kr.kcocktail.search.api.SearchEntityType
import kr.kcocktail.search.api.SearchIndexSync
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.sql.PreparedStatement
import java.sql.Types

/**
 * `search_document` UPSERT (SPEC-06 §3.8).
 *
 * ## JPA 를 쓰지 않는다
 *
 * 색인 행은 **실체가 아니라 투영**이다. 엔티티로 만들면 복합 PK 에 `@IdClass` 가 붙고,
 * 멱등을 위해 매번 `find` 로 존재를 확인하게 된다 — 그 사이에 다른 트랜잭션이 넣으면
 * 유니크 위반이다. `ON CONFLICT` 는 그것을 DB 한 문장으로 끝낸다 (RED 17).
 *
 * ## 트랜잭션을 만들지 않는다
 *
 * `MANDATORY` 다. **호출부의 트랜잭션 안에서 돌아야** 색인 실패가 발행을 롤백한다
 * (DECISIONS §1.7 · RED 18). 여기서 새 트랜잭션을 열면 발행은 커밋되고 색인만 사라져,
 * "발행됐는데 검색에 안 나오는" 상태가 남는다 — 정확히 §1.7 이 막으려던 것이다.
 * 재생성 훅(`NFR-R-03`, 실패해도 발행 유지)과 갈리는 지점이다.
 */
@Component
class JdbcSearchIndexSync(private val jdbc: JdbcTemplate) : SearchIndexSync {

    /**
     * `(entity_type, entity_id)` 충돌 시 갱신한다. 몇 번을 불러도 한 행이다 (RED 17).
     *
     * `weight` 는 [SearchEntityType.defaultWeight] 에서 온다 — 산정식이 미정이라
     * 타입별 고정값이다 (G-13 · SPEC-06 §7 · DECISIONS §1.9).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    override fun index(draft: SearchDocumentDraft) {
        val aliases = SearchDocumentText.aliases(draft.nameKo, draft.nameEn, draft.aliases)
        val chosung = SearchDocumentText.chosung(draft.nameKo, draft.nameEn, aliases)

        jdbc.update(UPSERT) { ps: PreparedStatement ->
            ps.setString(1, draft.type.slug)
            ps.setLong(2, draft.entityId)
            ps.setString(3, draft.slug)
            ps.setString(4, draft.nameKo)
            ps.setObject(5, draft.nameEn, Types.VARCHAR)
            ps.setArray(6, ps.connection.createArrayOf("text", aliases.toTypedArray()))
            ps.setString(7, chosung)
            ps.setInt(8, draft.type.defaultWeight)
            // 9 = 새 행일 때, 10 = 기존 행일 때. null 이면 각각 false · 기존값이다.
            ps.setObject(9, draft.isPublished, Types.BOOLEAN)
            ps.setObject(10, draft.isPublished, Types.BOOLEAN)
        }
    }

    /**
     * 이름·별칭을 건드리지 않고 공개 여부만 바꾼다 (회수 · 보관 · 승인).
     *
     * **행을 지우지 않는다** (DECISIONS §1.9). 지우면 내려간 상태에서 고친 이름이 색인에
     * 반영되지 않아, 다시 올리는 순간 낡은 이름으로 검색된다. 공개 노출은 조회 쪽이
     * 이 플래그로 거른다 (이슈 024).
     *
     * 행이 없으면 0건 갱신으로 끝난다 — `draft → archived` 처럼 색인된 적 없는 전이가 있다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    override fun setPublished(type: SearchEntityType, entityId: Long, isPublished: Boolean) {
        jdbc.update(
            "UPDATE search_document SET is_published = ? WHERE entity_type = ? AND entity_id = ?",
            isPublished, type.slug, entityId,
        )
    }

    private companion object {
        val UPSERT = """
            INSERT INTO search_document
                (entity_type, entity_id, slug, name_ko, name_en, aliases, chosung, weight, is_published)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, coalesce(?, false))
            ON CONFLICT (entity_type, entity_id) DO UPDATE SET
                slug         = excluded.slug,
                name_ko      = excluded.name_ko,
                name_en      = excluded.name_en,
                aliases      = excluded.aliases,
                chosung      = excluded.chosung,
                weight       = excluded.weight,
                -- null 은 "건드리지 마라" 다. 이름만 바뀐 경우(CocktailRenamed) 발행 상태가
                -- 그대로여야 한다 — false 로 덮으면 발행된 칵테일이 검색에서 사라진다.
                is_published = coalesce(?, search_document.is_published)
        """.trimIndent()
    }
}
