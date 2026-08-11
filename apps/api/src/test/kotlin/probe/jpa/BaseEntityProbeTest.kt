package probe.jpa

import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import kr.kcocktail.KcocktailApplication
import kr.kcocktail.common.entity.BaseEntity
import kr.kcocktail.support.PostgresSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * ISSUE-002 RED 14·15 — [BaseEntity] 의 시각 컬럼은 **DB 가 채운다**.
 *
 * SPEC-06 §1.2 가 `updated_at` 을 "트리거로 갱신"이라고 못박았다.
 * `@PreUpdate` 로 하면 벌크 `UPDATE` 와 마이그레이션이 그것을 건너뛴다.
 *
 * ## 트랜잭션을 직접 연다
 *
 * `@DataJpaTest` 는 기본이 **롤백되는 한 트랜잭션**이다. 그 안에서는 커밋이 없어
 * `BEFORE UPDATE` 트리거의 결과를 다시 읽을 수 없다 — 트리거를 검증하려면 실제로 커밋해야 한다.
 * 주입되는 `EntityManager` 는 공유 프록시라 트랜잭션을 못 열기 때문에
 * [EntityManagerFactory] 에서 직접 만들어 쓴다.
 *
 * ## 프로브 테이블을 별도 스키마에 둔다
 *
 * `public` 에 만들면 [kr.kcocktail.architecture.SchemaLintTest] 가 그것까지 린트하고,
 * 두 테스트의 실행 순서에 결과가 끌려간다. 스키마를 갈라 서로를 모르게 한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// probe 패키지가 kr.kcocktail 밖이라 Spring 이 @SpringBootApplication 을 못 찾는다. 명시한다.
@ContextConfiguration(classes = [KcocktailApplication::class])
// @EntityScan 은 기본 스캔을 **대체한다.** 프로브만 적으면 도메인 엔티티가 관리 대상에서
// 빠지고, 그것을 쓰는 리포지토리가 "Not a managed type" 으로 죽는다.
//
// 클래스를 하나씩 적지 않는다 — 모듈이 생길 때마다 여기를 고쳐야 하고,
// 고치는 것을 잊으면 **그 모듈과 무관한 이 테스트가** 빨개진다 (ISSUE-005·008 에서 연달아 겪었다).
// 앱 루트를 통째로 잡고 프로브 패키지만 더한다.
@EntityScan(basePackages = ["kr.kcocktail", "probe.jpa"])
class BaseEntityProbeTest {

    @Autowired
    private lateinit var emf: EntityManagerFactory

    @Test
    fun `RED14 - 저장하면 created_at 과 updated_at 이 채워진다`() {
        val id = persist("저장")
        val saved = reload(id)

        assertThat(saved.id).`as`("identity 가 채워진다").isPositive()
        assertThat(saved.createdAt).`as`("DB DEFAULT now() 가 채운다").isNotEqualTo(Instant.EPOCH)
        assertThat(saved.updatedAt).isNotEqualTo(Instant.EPOCH)
        assertThat(saved.updatedAt)
            .`as`("갓 만든 행은 둘이 사실상 같다")
            .isCloseTo(saved.createdAt, within(2, ChronoUnit.SECONDS))
    }

    @Test
    fun `RED15 - 수정하면 updated_at 만 갱신된다`() {
        val id = persist("원본")
        val before = reload(id)

        // 트리거가 now() 를 쓴다. 같은 순간에 수정하면 값이 그대로다.
        Thread.sleep(20)
        inTransaction { em -> em.find(BaseEntityProbe::class.java, id).label = "수정" }

        val after = reload(id)
        assertThat(after.label).isEqualTo("수정")
        assertThat(after.createdAt).`as`("created_at 은 불변이다").isEqualTo(before.createdAt)
        assertThat(after.updatedAt).`as`("updated_at 은 트리거가 갱신한다").isAfter(before.updatedAt)
    }

    /**
     * 정본이 DB 라는 것의 실질 — **JPA 콜백이 안 도는 경로에서도** 갱신된다.
     * `@PreUpdate` 로 구현했다면 이 테스트가 빨개진다.
     */
    @Test
    fun `벌크 UPDATE 도 트리거가 잡는다`() {
        val id = persist("벌크")
        val before = reload(id)

        Thread.sleep(20)
        inTransaction { em ->
            em.createNativeQuery("UPDATE $SCHEMA.$TABLE SET label = :v WHERE id = :id")
                .setParameter("v", "벌크수정")
                .setParameter("id", id)
                .executeUpdate()
        }

        assertThat(reload(id).updatedAt).isAfter(before.updatedAt)
    }

    /** 애플리케이션이 `updated_at` 을 직접 쓰려 해도 트리거가 덮는다. */
    @Test
    fun `애플리케이션이 updated_at 을 지정해도 트리거가 이긴다`() {
        val id = persist("덮어쓰기")
        val past = Instant.parse("2000-01-01T00:00:00Z")

        inTransaction { em ->
            em.createNativeQuery("UPDATE $SCHEMA.$TABLE SET updated_at = :t WHERE id = :id")
                .setParameter("t", past)
                .setParameter("id", id)
                .executeUpdate()
        }

        assertThat(reload(id).updatedAt)
            .`as`("트리거가 NEW.updated_at 을 now() 로 덮는다")
            .isAfter(past)
    }

    // ── 헬퍼 ───────────────────────────────────────────────────────────────

    private fun persist(label: String): Long {
        val probe = BaseEntityProbe(label)
        inTransaction { em -> em.persist(probe) }
        return probe.id
    }

    private fun reload(id: Long): BaseEntityProbe = withEm { it.find(BaseEntityProbe::class.java, id) }

    private fun inTransaction(block: (EntityManager) -> Unit) = withEm { em ->
        em.transaction.begin()
        try {
            block(em)
            em.transaction.commit()
        } catch (e: Throwable) {
            if (em.transaction.isActive) em.transaction.rollback()
            throw e
        }
    }

    private fun <T> withEm(block: (EntityManager) -> T): T =
        emf.createEntityManager().use(block)

    private fun <T> EntityManager.use(block: (EntityManager) -> T): T =
        try {
            block(this)
        } finally {
            close()
        }

    companion object {
        const val SCHEMA = BaseEntityProbe.SCHEMA
        const val TABLE = BaseEntityProbe.TABLE

        @JvmStatic
        @DynamicPropertySource
        fun datasource(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { PostgresSupport.container.jdbcUrl }
            registry.add("spring.datasource.username") { PostgresSupport.container.username }
            registry.add("spring.datasource.password") { PostgresSupport.container.password }
            registry.add("spring.flyway.enabled") { false } // @BeforeAll 에서 이미 돌렸다
            registry.add("spring.jpa.hibernate.ddl-auto") { "none" }
        }

        @JvmStatic
        @BeforeAll
        fun createProbeTable() {
            // @DynamicPropertySource 보다 먼저 돌 수 있다. 트리거를 걸려면 set_updated_at() 이
            // 이미 있어야 하므로 여기서도 마이그레이션을 보장한다 (두 번 불러도 한 번만 돈다).
            PostgresSupport.flyway

            PostgresSupport.migrateConnection().use { conn ->
                conn.createStatement().use {
                    it.execute(
                        """
                        DROP SCHEMA IF EXISTS $SCHEMA CASCADE;
                        CREATE SCHEMA $SCHEMA;
                        CREATE TABLE $SCHEMA.$TABLE (
                            id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                            created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
                            updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
                            label       TEXT NOT NULL
                        );
                        CREATE TRIGGER ${TABLE}_set_updated_at BEFORE UPDATE ON $SCHEMA.$TABLE
                            FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();
                        """.trimIndent(),
                    )
                }
            }
        }
    }
}
