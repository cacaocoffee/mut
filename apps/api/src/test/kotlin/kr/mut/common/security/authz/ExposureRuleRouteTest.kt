package kr.mut.common.security.authz

import kr.mut.support.PostgresSupport
import org.assertj.core.api.Assertions.assertThat
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

/**
 * ISSUE-006 RED 15 — 노출 규칙 엔드포인트가 **존재하지 않는다** (`PRIN-P02` · `FR-ADMIN-006`).
 *
 * > 부스팅 한도 · 홈 슬롯 비율은 `admin` 도 못 바꾼다. API 표면에도 DB 컬럼에도 존재하지 않는다.
 * > 바꾸려면 코드를 고치고 배포해야 한다. **그게 의도다** —
 * > 영업 압박이 들어오는 순간 "어드민에서 잠깐만"이 가능하면 반드시 그렇게 된다.
 *
 * ## 지금은 잡을 것이 없다
 *
 * 엔드포인트가 하나도 없어서 이 테스트는 공허하다. 그래도 지금 심는다 —
 * **규칙이 생기는 시점은 필요해진 시점이 아니라 압박이 들어온 시점**이고,
 * 그때 테스트를 새로 쓰자고 하면 쓰지 않는다.
 *
 * 부재 검증의 본체는 이슈 027(#29)이 이어받는다. 여기서는 라우트 표면만 본다.
 */
@SpringBootTest
class ExposureRuleRouteTest {

    /**
     * 액추에이터가 자기 매핑을 따로 등록해서 타입만으로는 빈이 둘이다.
     * 애플리케이션 라우트를 보려면 이름으로 집는다.
     */
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private lateinit var handlerMapping: RequestMappingHandlerMapping

    @Test
    fun `RED15 - 노출 규칙 변경 엔드포인트가 존재하지 않는다`() {
        // RequestMappingInfo.toString() 이 메서드·패턴을 다 담는다.
        // 패턴 조건 API 는 PathPattern 여부에 따라 갈려서 여기서는 문자열로 훑는다.
        val routes = handlerMapping.handlerMethods.keys.map { it.toString() }

        val suspicious = routes.filter { route ->
            FORBIDDEN_SEGMENTS.any { route.contains(it, ignoreCase = true) }
        }

        assertThat(suspicious)
            .`as`("PRIN-P02 — 노출 규칙은 API 표면에 없다. 코드를 고치고 배포해야 바뀐다")
            .isEmpty()
    }

    private companion object {
        /**
         * 노출 규칙을 조작하는 경로에 나올 법한 조각들.
         *
         * 넓게 잡는다 — 오탐이 나면 그 이름이 정말 적절한지 다시 보게 되고,
         * 그 대화가 이 규칙이 지켜지는 방식이다.
         */
        val FORBIDDEN_SEGMENTS = listOf(
            "boost", "slot-ratio", "exposure", "ranking-weight", "promote", "featured-slot",
        )

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { PostgresSupport.container.jdbcUrl }
            registry.add("spring.datasource.username") { PostgresSupport.container.username }
            registry.add("spring.datasource.password") { PostgresSupport.container.password }
            registry.add("spring.flyway.enabled") { true }
            registry.add("spring.flyway.user") { PostgresSupport.container.username }
            registry.add("spring.flyway.password") { PostgresSupport.container.password }
        }
    }
}
