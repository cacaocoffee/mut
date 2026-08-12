package kr.kcocktail.search

import kr.kcocktail.search.api.SearchEntityType
import kr.kcocktail.support.PostgresSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.sql.Connection
import java.sql.SQLException

/**
 * ISSUE-017 RED 16 · 24~26 · 29 — `search_document` 스키마 (SPEC-06 §3.8 · §5).
 *
 * ## 인덱스를 눈으로 확인하지 않는다
 *
 * "GIN 인덱스를 만들었다" 와 "초성 프리픽스가 그 인덱스를 탄다" 는 다른 얘기다.
 * 연산자 클래스를 잘못 붙이면 인덱스는 존재하지만 계획에 안 잡히고, 그 사실은
 * 코퍼스가 커진 뒤에야 느린 쿼리로 드러난다. RED 26 이 `EXPLAIN` 으로 못박는다.
 */
class SearchDocumentSchemaTest {

    // ── RED 16 : 복합 PK ──────────────────────────────────────────────────

    /**
     * `(entity_type, entity_id)` 다. 대리키 `id` 를 붙이면 같은 엔티티가 두 번 색인돼
     * 검색 결과에 중복이 뜬다 — UPSERT 의 충돌 대상이 곧 이 PK 다 (RED 17).
     */
    @Test
    fun `RED16 - PK 가 entity_type 과 entity_id 복합이다`() {
        assertThat(
            rows(
                """
                SELECT a.attname
                FROM pg_index i
                JOIN pg_class c ON c.oid = i.indrelid
                JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum = ANY (i.indkey)
                WHERE c.relname = 'search_document' AND i.indisprimary
                ORDER BY a.attname
                """,
            ),
        ).containsExactly("entity_id", "entity_type")
    }

    // ── RED 29 : entity_type 4종 ──────────────────────────────────────────

    /** 나중에 늘리면 클라이언트의 타입별 그룹 렌더링이 깨진다 (`R-F5-1`). 지금 다 정의한다. */
    @Test
    fun `RED29 - entity_type 4종이 미리 정의돼 있다`() {
        assertAll(
            listOf("cocktail", "bar", "ingredient", "article")
                .map<String, () -> Unit> { type -> { insert(type = type, entityId = 9_000L) } },
        )

        assertThatThrownBy { insert(type = "video", entityId = 9_001L) }
            .isInstanceOf(SQLException::class.java)
            .hasMessageContaining("ck_search_document__entity_type")

        assertThat(SearchEntityType.entries.map { it.slug })
            .`as`("DECISIONS §1.9 그룹 순서 — cocktail → ingredient → bar → article")
            .containsExactly("cocktail", "ingredient", "bar", "article")
    }

    // ── RED 24~25 : 인덱스 (SPEC-06 §5) ───────────────────────────────────

    @Test
    fun `RED24 - chosung 에 GIN pg_trgm 인덱스가 있다`() {
        assertThat(
            rows(
                """
                SELECT indexdef FROM pg_indexes
                WHERE tablename = 'search_document'
                  AND indexdef ILIKE '%using gin%' AND indexdef ILIKE '%gin_trgm_ops%'
                """,
            ),
        ).`as`("초성 프리픽스는 B-tree 로 안 된다 (G-13)")
            .anySatisfy { assertThat(it).contains("chosung") }
    }

    @Test
    fun `RED25 - aliases 에 GIN 인덱스가 있다`() {
        assertThat(
            rows(
                """
                SELECT indexdef FROM pg_indexes
                WHERE tablename = 'search_document' AND indexdef ILIKE '%using gin%'
                """,
            ),
        ).anySatisfy { assertThat(it).contains("aliases") }
    }

    // ── RED 26 : 계획을 확인한다 ──────────────────────────────────────────

    /**
     * `enable_seqscan = off` 로 **인덱스를 쓸 수 있는지**를 묻는다.
     *
     * 행이 몇 개뿐인 테스트 DB 에서는 플래너가 언제나 순차 스캔을 고른다 — 그 상태로
     * 계획을 단언하면 인덱스가 잘못 붙어도 초록이거나, 반대로 옳아도 빨갛다.
     * 끄고 물으면 "연산자 클래스가 이 술어를 지원하는가" 만 남는다. 지원하지 않으면
     * 플래너는 비용을 무시하고도 순차 스캔으로 돌아온다.
     */
    @Test
    fun `RED26 - 초성 프리픽스 매칭이 인덱스를 탄다`() {
        conn().use { c ->
            c.createStatement().use { st ->
                st.execute("SET enable_seqscan = off")
                st.execute("ANALYZE search_document")

                val plan = st.executeQuery(
                    "EXPLAIN SELECT slug FROM search_document WHERE chosung LIKE '%ㅁㄹㄱ%'",
                ).use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }.joinToString("\n")

                assertThat(plan)
                    .`as`("계획:\n%s", plan)
                    .contains("ix_search_document__chosung")
                    .doesNotContain("Seq Scan")
            }
        }
    }

    // ── 헬퍼 ───────────────────────────────────────────────────────────────

    private fun insert(type: String, entityId: Long): Unit = conn().use { c ->
        c.prepareStatement(
            """INSERT INTO search_document (entity_type, entity_id, slug, name_ko)
               VALUES (?, ?, ?, ?)""",
        ).use { st ->
            st.setString(1, type)
            st.setLong(2, entityId)
            st.setString(3, "$type-$entityId")
            st.setString(4, "테스트")
            st.executeUpdate()
        }
    }

    private fun conn(): Connection = PostgresSupport.migrateConnection()

    private fun rows(sql: String): List<String> = conn().use { c ->
        c.createStatement().use { st ->
            st.executeQuery(sql.trimIndent()).use { rs ->
                buildList { while (rs.next()) add(rs.getString(1)) }
            }
        }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun migrate() {
            PostgresSupport.flyway
        }
    }
}
