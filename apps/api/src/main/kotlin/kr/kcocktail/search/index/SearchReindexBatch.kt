package kr.kcocktail.search.index

import kr.kcocktail.search.api.SearchDocumentDraft
import kr.kcocktail.search.api.SearchEntityType
import kr.kcocktail.search.api.SearchIndexSync
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet

/**
 * 검색 색인 전수 재작성 (ISSUE-053 · [G-34](../../../../../../../../docs/prd/GAPS.md)).
 *
 * ## 왜 필요한가
 *
 * 색인은 **발행 이벤트**를 듣고 채워진다 ([SearchIndexListener]). 그런데 코퍼스 41종은
 * SQL 시드로 들어왔다 — 애플리케이션을 거치지 않으니 이벤트가 없고 색인 행도 없었다.
 * 화면·API·색인 코드는 각각 맞는데 셋을 이어 보면 **통합 검색이 발행분 전부를 못 찾는** 상태였다.
 *
 * 시드만의 문제가 아니다. 색인은 투영이라 언제든 깨질 수 있고(마이그레이션 · 수동 SQL ·
 * 장애 중 유실) **되살릴 길이 하나는 있어야 한다.**
 *
 * ## 색인 규칙을 여기서 다시 쓰지 않는다
 *
 * [SearchIndexSync] 를 그대로 부른다. 배치가 자기 `INSERT` 를 갖는 순간 초성 분해와
 * 별칭 병합이 두 벌이 되고, 그때부터 **이벤트로 들어간 행과 배치로 들어간 행이 다르게
 * 검색된다.** 그 어긋남은 검색 결과로만 드러나서 아무도 못 찾는다.
 *
 * ## 읽기는 SQL 이다
 *
 * `cocktail` · `ingredient` 를 SQL 로 읽는다. 모듈 API 를 부르면 `search → cocktail` 의존이
 * 생겨 순환이 된다 (`SearchIndexSync` 주석 참조). 검색 모듈은 이미 읽기 모델을 SQL 로
 * 만든다 (`CocktailListSql`) — 같은 자리다.
 */
@Component
class SearchReindexBatch(
    private val jdbc: JdbcTemplate,
    private val sync: SearchIndexSync,
) {

    /**
     * 전부 다시 쓴다. **행을 지우지 않는다** — UPSERT 라 몇 번을 돌려도 결과가 같다.
     *
     * 발행 아닌 칵테일은 색인하지 않고, 색인된 적이 있으면 **내려 둔다**. 지우는 대신
     * 내리는 것은 DECISIONS §1.9 그대로다 — 내려간 동안 고친 이름이 반영돼야 다시 올릴 때
     * 낡은 이름으로 검색되지 않는다.
     */
    @Transactional
    fun run(): Result {
        var cocktails = 0
        var demoted = 0

        jdbc.query(COCKTAILS) { rs: ResultSet ->
            val published = rs.getString("status") == "published"
            if (published) {
                sync.index(rs.toDraft(SearchEntityType.COCKTAIL, isPublished = true))
                cocktails++
            } else {
                sync.setPublished(SearchEntityType.COCKTAIL, rs.getLong("id"), false)
                demoted++
            }
        }

        var ingredients = 0
        jdbc.query(INGREDIENTS) { rs: ResultSet ->
            // 미승인도 행을 만든다. 승인되는 순간 최신 이름으로 검색돼야 한다 (리스너와 같은 규칙)
            sync.index(rs.toDraft(SearchEntityType.INGREDIENT, rs.getBoolean("is_approved")))
            ingredients++
        }

        return Result(cocktails = cocktails, ingredients = ingredients, demoted = demoted)
    }

    private fun ResultSet.toDraft(type: SearchEntityType, isPublished: Boolean) = SearchDocumentDraft(
        type = type,
        entityId = getLong("id"),
        slug = getString("slug"),
        nameKo = getString("name_ko"),
        nameEn = getString("name_en"),
        aliases = aliases(),
        isPublished = isPublished,
    )

    /** `text[]` 컬럼. 비어 있으면 빈 목록이다 — `null` 을 그대로 넘기면 색인이 터진다. */
    private fun ResultSet.aliases(): List<String> {
        val array = getArray("aliases") ?: return emptyList()

        @Suppress("UNCHECKED_CAST")
        return (array.array as Array<String?>).filterNotNull()
    }

    /**
     * @param cocktails 공개로 올린 칵테일 수
     * @param ingredients 다시 쓴 재료 수 (미승인 포함 — 행은 만들되 내려 둔다)
     * @param demoted 발행이 아니라서 내린 칵테일 수. 색인된 적이 없으면 아무 일도 없다
     */
    data class Result(val cocktails: Int, val ingredients: Int, val demoted: Int)

    private companion object {
        /** 발행 여부까지 함께 읽는다 — 내리는 것도 재색인의 일이다. */
        const val COCKTAILS = "SELECT id, slug, name_ko, name_en, aliases, status FROM cocktail"

        const val INGREDIENTS = "SELECT id, slug, name_ko, name_en, aliases, is_approved FROM ingredient"
    }
}
