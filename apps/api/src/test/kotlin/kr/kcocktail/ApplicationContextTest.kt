package kr.kcocktail

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * ISSUE-000 RED 2 — 애플리케이션 컨텍스트 로드.
 *
 * DB 없이 뜬다. 실제 스키마·Testcontainers 는 PostgresContainerTest 가 본다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextTest {

    @Test
    fun `컨텍스트가_로드된다`() {
        // 컨텍스트 로드 실패 시 이 테스트가 예외로 떨어진다
    }
}
