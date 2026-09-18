package kr.mut.ingredient

import kr.mut.support.PostgresSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.SQLException

/**
 * #194 — 국내 유통 술 테이블 (GAPS G-41 · PRIN-P05).
 *
 * 식약처 신고 건의 사본이다. DB 가 막는 것은 둘 — 출처 안에서 같은 건이 두 번 들어오지 않는 것,
 * 출처가 셋 중 하나인 것. 변환 규칙(주류 유형만 남기기·중복 제거)은 스크립트 쪽 테스트가 본다
 * (`scripts/distributed-product.test.ts`).
 */
class DistributedProductSchemaTest {

    @Test
    fun `RED1 - (source, source_key) 가 유일하다`() {
        insert(source = "mfds_import", key = "dup-1")

        assertThatThrownBy { insert(source = "mfds_import", key = "dup-1") }
            .isInstanceOf(SQLException::class.java)
            .hasMessageContaining("uq_distributed_product__source_key")
    }

    @Test
    fun `RED1 - 출처가 다르면 같은 키를 쓸 수 있다`() {
        insert(source = "mfds_import", key = "same-key")
        insert(source = "mfds_domestic", key = "same-key")
        insert(source = "manual", key = "same-key")
    }

    @Test
    fun `RED2 - source 는 mfds_import · mfds_domestic · manual 셋뿐이다`() {
        assertThatThrownBy { insert(source = "customs", key = "bogus") }
            .isInstanceOf(SQLException::class.java)
            .hasMessageContaining("ck_distributed_product__source")
    }

    @Test
    fun `RED4 - 시드가 쓰는 ON CONFLICT DO NOTHING 은 두 번 돌려도 행이 늘지 않는다`() {
        val sql = """
            INSERT INTO distributed_product (source, source_key, name_ko, food_type)
            VALUES ('mfds_domestic', 'twice', '화요 41', '일반증류주')
            ON CONFLICT (source, source_key) DO NOTHING
        """.trimIndent()
        exec(sql)
        exec(sql)

        assertThat(one("SELECT count(*)::text FROM distributed_product WHERE source_key = 'twice'"))
            .isEqualTo("1")
    }

    @Test
    fun `food_type 은 NOT NULL 이다 - 유형 없는 건은 주류인지 알 수 없다`() {
        assertThatThrownBy {
            exec("INSERT INTO distributed_product (source, source_key, name_ko) VALUES ('manual', 'no-type', '무엇')")
        }.isInstanceOf(SQLException::class.java)
    }

    /** 출처의 사본이라 물리 삭제를 막지 않는다 — 시드가 통째로 다시 채운다. */
    @Test
    fun `앱 롤이 지울 수 있다 - 보호 테이블이 아니다`() {
        insert(source = "mfds_import", key = "deletable")
        PostgresSupport.appConnection().use { c ->
            c.createStatement().use { st ->
                st.execute("DELETE FROM distributed_product WHERE source_key = 'deletable'")
            }
        }
        assertThat(one("SELECT count(*)::text FROM distributed_product WHERE source_key = 'deletable'"))
            .isEqualTo("0")
    }

    // ── 헬퍼 ───────────────────────────────────────────────────────────────

    private fun insert(source: String, key: String): Long = conn().use { c ->
        c.prepareStatement(
            """INSERT INTO distributed_product (source, source_key, name_ko, food_type)
               VALUES (?, ?, ?, ?) RETURNING id""",
        ).use { st ->
            st.setString(1, source)
            st.setString(2, key)
            st.setString(3, "테스트 제품")
            st.setString(4, "위스키")
            st.executeQuery().use { rs -> rs.next(); rs.getLong(1) }
        }
    }

    private fun conn(): Connection = PostgresSupport.migrateConnection()

    private fun exec(sql: String) = conn().use { it.createStatement().use { st -> st.execute(sql) } }

    private fun one(sql: String): String? = conn().use { c ->
        c.createStatement().use { st ->
            st.executeQuery(sql).use { rs -> if (rs.next()) rs.getString(1) else null }
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
