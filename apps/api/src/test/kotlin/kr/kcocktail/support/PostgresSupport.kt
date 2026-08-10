package kr.kcocktail.support

import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.DriverManager

/**
 * PG16 컨테이너 하나를 테스트 전체가 공유한다 (ISSUE-002).
 *
 * 테스트 클래스마다 컨테이너를 띄우면 이슈가 쌓일수록 CI 가 감당하지 못한다.
 * JVM 이 죽을 때 Ryuk 또는 컨테이너 종료 훅이 정리한다 — `stop()` 을 부르지 않는다.
 *
 * ## 역할 두 개로 붙는다
 *
 * SPEC-06 §4.1 의 물리 삭제 금지는 **DB 가 막아야** 의미가 있다 (`PRIN-T05`).
 * 슈퍼유저로 붙어 테스트하면 `REVOKE DELETE` 가 있든 없든 전부 통과해 버린다 —
 * 규칙이 살아 있는지 아무것도 증명하지 못한다.
 *
 * | 커넥션 | 계정 | 쓰임 |
 * |---|---|---|
 * | [migrateConnection] | 컨테이너 슈퍼유저 | Flyway · 픽스처 DDL |
 * | [appConnection] | `app_test` (`kcocktail_app` 멤버) | 런타임이 실제로 갖는 권한 |
 *
 * `kcocktail_app` 은 `NOLOGIN` 이라 직접 붙지 못한다 — 운영과 같은 모양으로,
 * 로그인 계정을 그 역할의 멤버로 넣어 권한을 상속받게 한다.
 */
object PostgresSupport {

    private const val APP_USER = "app_test"
    private const val APP_PASSWORD = "app_test"

    val container: PostgreSQLContainer<*> =
        PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("kcocktail")
            .withUsername("postgres")
            .withPassword("postgres")
            .also { it.start() }

    /** Flyway 를 한 번만 돌린다. 여러 테스트가 불러도 안전하다. */
    val flyway: Flyway by lazy {
        Flyway.configure()
            .dataSource(container.jdbcUrl, container.username, container.password)
            .locations("classpath:db/migration")
            .load()
            .also { it.migrate() }
            .also { grantAppLogin() }
    }

    /** 슈퍼유저. 마이그레이션과 픽스처 DDL 전용이다. */
    fun migrateConnection(): Connection =
        DriverManager.getConnection(container.jdbcUrl, container.username, container.password)

    /** 런타임 권한 그대로. 권한 테스트는 반드시 이쪽으로 한다. */
    fun appConnection(): Connection =
        DriverManager.getConnection(container.jdbcUrl, APP_USER, APP_PASSWORD)

    /**
     * `kcocktail_app` 에 붙을 로그인 계정을 만든다.
     *
     * 마이그레이션이 하지 않는다 — 계정과 비밀번호는 환경마다 다르고,
     * 자격증명을 DDL 이력에 남기지 않는다.
     */
    private fun grantAppLogin() {
        migrateConnection().use { conn ->
            conn.createStatement().use { st ->
                st.execute(
                    """
                    DO ${'$'}${'$'}
                    BEGIN
                        IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '$APP_USER') THEN
                            CREATE ROLE $APP_USER LOGIN PASSWORD '$APP_PASSWORD';
                        END IF;
                    END
                    ${'$'}${'$'};
                    """.trimIndent(),
                )
                st.execute("GRANT kcocktail_app TO $APP_USER")
            }
        }
    }
}
