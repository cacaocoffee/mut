package kr.kcocktail.common.analytics

import com.fasterxml.jackson.databind.ObjectMapper
import kr.kcocktail.common.security.ratelimit.KeyBy
import kr.kcocktail.common.security.ratelimit.RateLimitPolicy
import kr.kcocktail.common.web.ApiPaths
import kr.kcocktail.common.web.idempotency.IdempotencyFilter
import kr.kcocktail.support.PostgresSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.util.UUID

/**
 * ISSUE-034 — 이벤트 수집 (SPEC-10 §7 · `PRIN-T07` · `NFR-R-04`).
 *
 * ## 이벤트는 소급이 안 된다
 *
 * SPEC-10 §1 이 이 이슈가 지금 있는 이유를 적었다 — 나중에 심으면 **그 기간의 데이터가
 * 영원히 없다.** 그래서 이 파일이 지키는 것은 "받는다" 가 아니라
 * **"어떤 경우에도 받은 것을 잃지 않는다"** 다.
 *
 * ## 요체는 부분 실패다
 *
 * 상세 화면 한 번에 이벤트가 대여섯 개 나간다. 그중 하나의 payload 가 이상하다고
 * 나머지를 버리면 지표에 구멍이 뚫리고, **그 구멍은 메울 수 없다.**
 */
@SpringBootTest
@AutoConfigureMockMvc
class EventApiTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var json: ObjectMapper
    @Autowired private lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun clear() {
        jdbc.execute("TRUNCATE analytics_event CASCADE")
        jdbc.execute("TRUNCATE idempotency_key CASCADE")
    }

    // ── RED 1~8 : 수집 API (SPEC-10 §7) ───────────────────────────────────

    /**
     * RED 1·2·3·4 — 인증도 CSRF 도 없이 받고, **202 에 본문이 없다.**
     *
     * `200` 이 아닌 것이 의도다: 이 요청은 "받았다" 이지 "처리했다" 가 아니다.
     * 몇 건이 저장됐는지 알려 주지 않는다 — 알려 주면 클라이언트가 그걸 보고 재시도하고,
     * 그 재시도가 집계를 부풀린다.
     */
    @Test
    fun `RED1,2,3,4 - 인증·CSRF 없이 202 를 본문 없이 받는다`() {
        val result = send(listOf(event("cocktail_view")))

        assertAll(
            { assertThat(result.response.status).`as`("RED3 — 200 이 아니다").isEqualTo(202) },
            { assertThat(result.response.contentAsString).`as`("RED4").isBlank() },
            { assertThat(stored()).isEqualTo(1) },
        )
    }

    @Test
    fun `RED5 - 배치로 여러 이벤트를 받는다`() {
        send(listOf(event("cocktail_view"), event("search_miss"), event("finder_step")))

        assertThat(stored()).isEqualTo(3)
    }

    /**
     * RED 6·7 — 50건 상한. **절삭이 아니라 400 이다.**
     *
     * 조용히 자르면 클라이언트는 다 보냈다고 믿고, 잘린 이벤트는 영영 안 온다.
     * 개별 이벤트의 검증 실패(버린다)와 성격이 다르다 —
     * 저쪽은 데이터 문제고 이쪽은 **계약 위반**이다.
     */
    @Test
    fun `RED6,7 - 50건 초과는 400 이다`() {
        val exactly = send(List(EventCollector.MAX_BATCH) { event("cocktail_view") })
        val tooMany = send(List(EventCollector.MAX_BATCH + 1) { event("cocktail_view") })

        assertAll(
            { assertThat(exactly.response.status).`as`("50건은 된다").isEqualTo(202) },
            { assertThat(tooMany.response.status).`as`("51건은 400").isEqualTo(400) },
            { assertThat(stored()).`as`("초과분은 하나도 저장되지 않는다").isEqualTo(50) },
        )
    }

    /** RED 8 — 레이트 리밋이 **세션 기준**이다. IP 로 잡으면 공유 IP 뒤 사용자들이 서로를 막는다. */
    @Test
    fun `RED8 - 레이트 리밋 120rpm 이 세션 기준이다`() {
        assertAll(
            { assertThat(RateLimitPolicy.EVENTS.defaultLimit).isEqualTo(120) },
            { assertThat(RateLimitPolicy.EVENTS.keyBy).`as`("SPEC-08 §6 — IP 가 아니다").isEqualTo(KeyBy.SESSION) },
        )
    }

    // ── RED 9~13 : 멱등 (PRIN-T07) ────────────────────────────────────────

    /**
     * RED 9·10 — 키가 **필수**다.
     *
     * `IdempotencyFilter` 는 헤더가 없으면 그냥 통과시킨다 (`shouldNotFilter`).
     * 필터에 맡겨 두면 "없어도 되는 것" 으로 굳고, `PRIN-T07` 이 요구하는
     * "재시도가 집계를 부풀리지 않는다" 를 못 지킨다.
     */
    @Test
    fun `RED9,10 - Idempotency-Key 가 없으면 400 이다`() {
        val result = mvc.post(EVENTS) {
            // 헤더만 뺀다. 나머지는 정상 요청이라 400 의 원인이 키 부재임이 분명하다.
            contentType = MediaType.APPLICATION_JSON
            content = json.writeValueAsString(mapOf("events" to listOf(event("cocktail_view"))))
        }.andReturn()

        assertAll(
            { assertThat(result.response.status).isEqualTo(400) },
            { assertThat(stored()).isZero() },
        )
    }

    /**
     * RED 11·12·13 — **재시도가 집계를 부풀리지 않는다.**
     *
     * 이벤트 수집이 멱등을 가장 필요로 하는 자리다. 네트워크가 끊겨 클라이언트가 다시
     * 보내면, 그 한 번이 조회수 하나를 만든다.
     */
    @Test
    fun `RED11,12,13 - 같은 키 재요청은 중복 저장하지 않는다`() {
        val key = UUID.randomUUID().toString()
        val batch = listOf(event("cocktail_view"))

        val first = send(batch, key)
        val retry = send(batch, key)
        val other = send(batch, UUID.randomUUID().toString())

        assertAll(
            { assertThat(first.response.status).isEqualTo(202) },
            { assertThat(retry.response.status).`as`("RED12 — 재요청도 202").isEqualTo(202) },
            {
                assertThat(retry.response.getHeader(IdempotencyFilter.REPLAY_HEADER))
                    .`as`("재생된 것임을 알려 준다 — 없으면 디버깅에서 헤맨다")
                    .isEqualTo("true")
            },
            { assertThat(other.response.status).isEqualTo(202) },
            { assertThat(stored()).`as`("RED11·13 — 두 번이 아니라 두 건").isEqualTo(2) },
        )
    }

    // ── RED 14~18 : 실패 격리 (NFR-R-04) — 요체 ──────────────────────────

    /**
     * RED 14·15·18 — **하나가 잘못돼도 나머지는 저장된다.**
     *
     * 이 파일에서 가장 중요한 테스트다. 배치 전체를 롤백하면 데이터가 더 나빠진다 —
     * 잘못된 이벤트 하나 때문에 같은 페이지의 멀쩡한 계측까지 잃는다.
     */
    @Test
    fun `RED14,15,18 - 잘못된 이벤트만 버리고 나머지는 저장한다`() {
        val result = send(
            listOf(
                event("cocktail_view"),
                event("no_such_type"), // RED 18 — 알 수 없는 타입
                event("search_miss", sessionId = "not-a-uuid"), // RED 20
                event("finder_step", occurredAt = null), // RED 22
                event("bookmark_add"),
            ),
        )

        assertAll(
            { assertThat(result.response.status).`as`("전체가 실패하지 않는다").isEqualTo(202) },
            { assertThat(stored()).`as`("멀쩡한 둘은 남는다").isEqualTo(2) },
            {
                assertThat(typesStored())
                    .containsExactlyInAnyOrder("cocktail_view", "bookmark_add")
            },
        )
    }

    /**
     * RED 17 — **저장이 실패해도 202 다** (`NFR-R-04` 배포 차단).
     *
     * DB 가 죽었을 때 계측 때문에 사용자가 에러 화면을 보는 것이 최악이다.
     * 테이블을 잠깐 지워 그 상황을 만든다 — 목 대신 실제로 깨뜨려야
     * 예외가 어디서 새는지 알 수 있다.
     */
    @Test
    fun `RED17 - 저장이 실패해도 202 를 준다`() {
        jdbc.execute("ALTER TABLE analytics_event RENAME TO analytics_event_hidden")
        try {
            val result = send(listOf(event("cocktail_view")))

            assertThat(result.response.status)
                .`as`("NFR-R-04 — 수집 실패가 사용자 흐름을 막지 않는다")
                .isEqualTo(202)
        } finally {
            jdbc.execute("ALTER TABLE analytics_event_hidden RENAME TO analytics_event")
        }
    }

    // ── RED 19~25 : 공통 필드 (SPEC-10 §3) ────────────────────────────────

    @Test
    fun `RED19,21 - eventType 이 필수이고 userId 는 null 을 허용한다`() {
        send(listOf(event("cocktail_view", userId = null), event(null)))

        assertAll(
            { assertThat(stored()).`as`("타입 없는 것만 버려진다").isEqualTo(1) },
            { assertThat(count("SELECT count(*) FROM analytics_event WHERE user_id IS NULL")).isEqualTo(1) },
        )
    }

    /**
     * RED 23 — **쿼리스트링을 서버가 잘라 낸다** (SPEC-10 §3).
     *
     * 거부하지 않는 이유: 클라이언트가 실수로 붙였다고 이벤트를 잃을 이유가 없고,
     * 자르는 것으로 요구가 충족된다. 검색어·좌표가 섞여 들어오는 자리라 반드시 잘라야 한다.
     */
    @Test
    fun `RED23 - path 에서 쿼리스트링이 잘린다`() {
        send(listOf(event("cocktail_view", path = "/cocktails/negroni?q=%EB%84%A4%EA%B7%B8&lat=37.5")))

        val stored = jdbc.queryForObject(
            "SELECT path FROM analytics_event LIMIT 1", String::class.java,
        )

        assertAll(
            { assertThat(stored).isEqualTo("/cocktails/negroni") },
            { assertThat(stored).doesNotContain("lat").doesNotContain("q=") },
        )
    }

    /**
     * RED 24·25 — 5종 밖은 `unknown` 이고, **원본 referrer URL 은 저장되지 않는다.**
     *
     * 분류값으로 두는 이유가 SPEC-10 §3 에 있다 — 유기 검색 비중을 세는 데는 이걸로 충분하고,
     * **원본에는 개인정보가 섞일 수 있다** (사내 위키 주소, 초대 링크의 토큰).
     */
    @Test
    fun `RED24,25 - referrerType 5종만 저장되고 원본 URL 은 없다`() {
        send(
            listOf(
                event("cocktail_view", referrerType = "organic"),
                event("search_miss", referrerType = "https://www.google.com/search?q=secret"),
            ),
        )

        val stored = jdbc.queryForList("SELECT referrer_type FROM analytics_event", String::class.java)

        assertAll(
            { assertThat(stored).containsExactlyInAnyOrder("organic", "unknown") },
            {
                assertThat(columnsOf("analytics_event"))
                    .`as`("원본 URL 을 담을 컬럼 자체가 없다")
                    .noneMatch { it.contains("referrer_url") || it == "referrer" }
            },
        )
    }

    // ── RED 26~29 : 개인 식별 금지 (SPEC-10 §2·§10 · PRIN-D04) ────────────

    /** RED 26·27 — IP·User-Agent 컬럼이 **없다**. `LocationAbsenceTest` 도 전수로 훑는다. */
    @Test
    fun `RED26,27 - IP·User-Agent 컬럼이 없다`() {
        assertThat(columnsOf("analytics_event"))
            .`as`("SPEC-10 §10 — referrerType 으로 충분하다")
            .noneMatch { it.contains("ip") || it.contains("user_agent") || it.contains("agent") }
    }

    /**
     * RED 28 — **좌표가 오면 이벤트를 통째로 버린다.**
     *
     * 키만 떨어뜨려도 저장은 막을 수 있다. 그런데 그러면 클라이언트 버그가 조용히 산다 —
     * 좌표를 보내는 클라이언트는 다른 곳에서도 보내고 있을 가능성이 높다 (`PRIN-D04`).
     */
    @Test
    fun `RED28 - payload 에 좌표가 있으면 그 이벤트를 버린다`() {
        send(
            listOf(
                event("cocktail_view", payload = mapOf("cocktailSlug" to "negroni", "lat" to 37.5)),
                event("bookmark_add", payload = mapOf("targetSlug" to "martini")),
            ),
        )

        assertAll(
            { assertThat(stored()).isEqualTo(1) },
            { assertThat(typesStored()).containsExactly("bookmark_add") },
            {
                assertThat(payloadsOf())
                    .`as`("좌표가 어디에도 남지 않는다")
                    .noneMatch { it.contains("37.5") || it.contains("lat") }
            },
        )
    }

    /**
     * RED 29 — **알려진 키만 저장된다.**
     *
     * 임의 필드를 그대로 받으면 개인정보가 샌다. 이벤트를 버리지 않고 키만 떨어뜨리는 이유:
     * 좌표(RED 28)와 달리 **모르는 키는 대개 클라이언트가 앞서 나간 것**이라,
     * 그 이벤트의 나머지 값은 여전히 쓸모 있다.
     */
    @Test
    fun `RED29 - payload 는 알려진 필드만 받는다`() {
        send(
            listOf(
                event(
                    "search_miss",
                    payload = mapOf(
                        "query" to "네그로니",
                        "matchedCount" to 0,
                        "email" to "leak@example.com", // 알려지지 않은 키
                        "phone" to "010-0000-0000",
                    ),
                ),
            ),
        )

        val payload = payloadsOf().single()

        assertAll(
            { assertThat(payload).contains("네그로니") },
            { assertThat(payload).doesNotContain("leak@example.com").doesNotContain("010-") },
            { assertThat(payload).doesNotContain("email").doesNotContain("phone") },
        )
    }

    // ── RED 30~32 : 파티셔닝 대비 (SPEC-06 §3.8) ─────────────────────────

    /**
     * RED 30·31 — PK 에 `occurred_at` 이 들어 있다.
     *
     * Phase 1a 는 단일 테이블이다. 그래도 지금 넣는 이유: **나중에 쪼갤 때 PK 를 바꾸려면
     * 테이블을 다시 만들어야 하고, 그때는 행이 수백만이다.**
     */
    @Test
    fun `RED30,31 - PK 에 occurred_at 이 포함되고 단일 테이블이다`() {
        val pk = jdbc.queryForList(
            """
            SELECT a.attname FROM pg_index i
              JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey)
             WHERE i.indrelid = 'analytics_event'::regclass AND i.indisprimary
            """.trimIndent(),
            String::class.java,
        )

        val partitioned = jdbc.queryForObject(
            "SELECT count(*) FROM pg_partitioned_table WHERE partrelid = 'analytics_event'::regclass",
            Long::class.java,
        )!!

        assertAll(
            { assertThat(pk).containsExactlyInAnyOrder("id", "occurred_at") },
            { assertThat(partitioned).`as`("RED31 — 1a 는 단일 테이블").isZero() },
        )
    }

    /** RED 32 — 지표는 "기간 안의 이 이벤트" 로 읽는다 (SPEC-06 §5). */
    @Test
    fun `RED32 - event_type·occurred_at 인덱스가 있다`() {
        val indexes = jdbc.queryForList(
            "SELECT indexdef FROM pg_indexes WHERE tablename = 'analytics_event'",
            String::class.java,
        )

        assertThat(indexes)
            .anyMatch { it.contains("event_type") && it.contains("occurred_at") }
    }

    // ── RED 33~34 : Phase 1b 자리 (SPEC-10 §5) ───────────────────────────

    /**
     * RED 33·34 — 1b 값을 **지금** 정의한다.
     *
     * 컬럼도 열거도 나중에 늘릴 수 있다. 그런데 **그 사이에 쌓인 행에는 값이 영원히 없고**,
     * 열거를 늘리면 그것을 읽는 쪽(대시보드 필터)이 그때 깨진다.
     */
    @Test
    fun `RED33,34 - cross_nav 컬럼과 1b 이벤트 타입이 미리 있다`() {
        assertAll(
            {
                assertThat(columnsOf("analytics_event"))
                    .contains("from_type", "from_id", "to_type", "to_id")
            },
            {
                assertThat(EventType.entries.filter { !it.isPhase1a }.map { it.code })
                    .containsExactlyInAnyOrder("bar_view", "cross_nav", "partner_action")
            },
            {
                assertThat(EventType.entries.filter { it.isPhase1a }.map { it.code })
                    .`as`("SPEC-10 §4 의 1a 이벤트 7종")
                    .containsExactlyInAnyOrder(
                        "cocktail_view", "filter_apply", "search_miss",
                        "finder_step", "recipe_interact", "bookmark_add", "share_click",
                    )
            },
        )
    }

    /** 1b 타입으로 보내도 에러가 아니다 — 열거에 있으면 받는다. 심는 화면이 없을 뿐이다. */
    @ParameterizedTest
    @ValueSource(strings = ["bar_view", "cross_nav", "partner_action"])
    fun `RED34 - 1b 이벤트 타입도 받는다`(type: String) {
        assertThat(send(listOf(event(type))).response.status).isEqualTo(202)
        assertThat(stored()).isEqualTo(1)
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────

    private fun send(events: List<Map<String, Any?>>, key: String = UUID.randomUUID().toString()) =
        mvc.post(EVENTS) {
            header(IdempotencyFilter.HEADER, key)
            contentType = MediaType.APPLICATION_JSON
            content = json.writeValueAsString(mapOf("events" to events))
        }.andReturn()

    /**
     * 이벤트 하나. **맵으로 만든다** — `EventRequest` 로 만들면 `sessionId` 에 "not-a-uuid" 를
     * 넣는 것 같은 **잘못된 입력을 표현할 수 없다.** 클라이언트는 아무거나 보낼 수 있다.
     */
    private fun event(
        eventType: String?,
        sessionId: String? = UUID.randomUUID().toString(),
        userId: Long? = null,
        occurredAt: String? = Instant.now().toString(),
        path: String? = "/cocktails/negroni",
        referrerType: String? = "internal",
        payload: Map<String, Any?>? = null,
    ): Map<String, Any?> = buildMap {
        put("eventType", eventType)
        put("sessionId", sessionId)
        put("userId", userId)
        put("occurredAt", occurredAt)
        put("path", path)
        put("referrerType", referrerType)
        payload?.let { put("payload", it) }
    }

    private fun stored() = count("SELECT count(*) FROM analytics_event")

    private fun typesStored() =
        jdbc.queryForList("SELECT event_type FROM analytics_event", String::class.java)

    private fun payloadsOf() =
        jdbc.queryForList("SELECT payload::text FROM analytics_event", String::class.java)

    private fun columnsOf(table: String) = jdbc.queryForList(
        "SELECT column_name FROM information_schema.columns WHERE table_name = ?",
        String::class.java,
        table,
    )

    private fun count(sql: String) = jdbc.queryForObject(sql, Long::class.java)!!

    companion object {
        private val EVENTS = "${ApiPaths.BASE}/events"

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { PostgresSupport.container.jdbcUrl }
            registry.add("spring.datasource.username") { PostgresSupport.container.username }
            registry.add("spring.datasource.password") { PostgresSupport.container.password }
            registry.add("spring.flyway.enabled") { true }
            registry.add("spring.flyway.user") { PostgresSupport.container.username }
            registry.add("spring.flyway.password") { PostgresSupport.container.password }
            registry.add("kcocktail.verification.scheduled") { false }

            // 이 파일은 배치를 여러 번 보낸다. 120rpm 은 RED 8 이 정책으로 확인한다.
            registry.add("kcocktail.rate-limit.enabled") { false }
        }
    }
}
