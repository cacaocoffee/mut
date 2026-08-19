package kr.mut

import kr.mut.support.PostgresSupport
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ISSUE-000 RED 3 — Testcontainers PostgreSQL 16 기동.
 *
 * PRIN-T01 이 확정한 DB 버전이 실제로 뜨는지 본다.
 * 스키마·Flyway 는 이슈 002 가 올린다.
 */
@Testcontainers
class PostgresContainerTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer(PostgresSupport.IMAGE) // PRIN-T01
    }

    @Test
    fun `PostgreSQL_16_컨테이너가_뜨고_연결된다`() {
        assertTrue(postgres.isRunning, "컨테이너가 실행 중이 아니다")

        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
            .use { conn ->
                conn.createStatement().executeQuery("SELECT version()").use { rs ->
                    assertTrue(rs.next())
                    val version = rs.getString(1)
                    assertTrue("PostgreSQL 16" in version, "PostgreSQL 16 이 아니다: $version")
                }
            }
    }

    @Test
    fun `pg_trgm_확장을_설치할_수_있다`() {
        // SPEC-06 §5 — 초성 검색에 필요. 실제 설치는 이슈 002 의 V001 이 한다
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
            .use { conn ->
                conn.createStatement().execute("CREATE EXTENSION IF NOT EXISTS pg_trgm")
                conn.createStatement()
                    .executeQuery("SELECT count(*) FROM pg_extension WHERE extname='pg_trgm'")
                    .use { rs ->
                        rs.next()
                        assertEquals(1, rs.getInt(1), "pg_trgm 확장이 설치되지 않았다")
                    }
            }
    }
}
