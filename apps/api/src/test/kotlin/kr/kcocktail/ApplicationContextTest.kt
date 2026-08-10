package kr.kcocktail

import kr.kcocktail.support.PostgresSupport
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/**
 * ISSUE-000 RED 2 — 애플리케이션 컨텍스트 로드.
 *
 * ## DB 를 붙인다
 *
 * 스캐폴딩 때는 "DB 없이 뜬다"였다. **이슈 003 부터 그 전제가 깨졌다** —
 * 멱등 키 저장소(SPEC-07 §1.7)가 `JdbcTemplate` 을 요구한다.
 *
 * 그 의존을 조건부로 만들어 회피할 수도 있었지만 하지 않았다.
 * 애플리케이션이 실제로 DB 없이는 못 뜨는데 테스트만 뜨게 하면,
 * **이 테스트가 확인하는 것과 운영에서 뜨는 것이 달라진다.**
 * 컨텍스트 로드 테스트의 값어치는 그 둘이 같다는 데 있다.
 */
@SpringBootTest
class ApplicationContextTest {

    @Test
    fun `컨텍스트가_로드된다`() {
        // 컨텍스트 로드 실패 시 이 테스트가 예외로 떨어진다
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun datasource(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { PostgresSupport.container.jdbcUrl }
            registry.add("spring.datasource.username") { PostgresSupport.container.username }
            registry.add("spring.datasource.password") { PostgresSupport.container.password }
            registry.add("spring.flyway.enabled") { true }
            registry.add("spring.flyway.user") { PostgresSupport.container.username }
            registry.add("spring.flyway.password") { PostgresSupport.container.password }
        }
    }
}
