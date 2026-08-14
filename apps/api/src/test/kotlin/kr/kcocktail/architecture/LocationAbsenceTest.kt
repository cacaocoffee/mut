package kr.kcocktail.architecture

import kr.kcocktail.common.logging.SensitiveParams
import kr.kcocktail.support.PostgresSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.streams.asSequence

/**
 * ISSUE-033 — **유저의 좌표가 어디에도 남지 않는다**
 * (`PRIN-D04` · `FR-USER-006` · SPEC-08 §5.2 · `NFR-SEC-04` · SPEC-10 §10).
 *
 * ## 이 파일도 부재를 검증한다
 *
 * `PRIN-D04` 는 짧다:
 *
 * > **세션 내 사용만 한다. 유저의 좌표를 DB 에 쓰지 않는다.**
 *
 * SPEC-08 §5.2 가 그것을 풀어 적었다 — "그 요청에서만 쓰고 버린다.
 * **DB 컬럼도 로그도 남기지 않는다.**"
 *
 * ## 지금은 통과하기 쉽다. 값을 하는 시점은 1b 다
 *
 * Phase 1a 에는 "내 주변 바" 가 없다 (BAR 가 1b). 좌표를 받을 일 자체가 없다.
 * 그런데 `FR-USER-006` 은 P0 이고 `NFR-SEC-04` 가 **코드·로그 검사**를 측정 방법으로 못박았다.
 *
 * 1b 에서 누군가 `user.last_lat` 컬럼을 만들거나 좌표를 그대로 로그에 남기는 순간
 * 여기가 터진다. 그때 이 KDoc 을 읽고 왜 막혔는지 알 수 있어야 한다.
 *
 * **되돌리는 조건**: SPEC-00 `PRIN-D04` 개정 + ADR (SPEC-00 §4).
 * 테스트를 지우는 것이 아니라 원칙을 먼저 고친다.
 */
@Tag("boundary")
@SpringBootTest
class LocationAbsenceTest {

    @Autowired private lateinit var jdbc: JdbcTemplate

    /** 액추에이터가 같은 타입을 하나 더 올린다 (이슈 027 에서 겪었다). */
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private lateinit var handlerMapping: RequestMappingHandlerMapping

    // ── RED 1~6 : DB 컬럼 부재 (PRIN-D04 · SPEC-06 §3.5) ──────────────────

    /**
     * RED 1·4·5 — `information_schema` 전수 스캔.
     *
     * 테이블을 열거하지 않는다. 목록을 적으면 **새 테이블이 목록에서 빠진 채로 들어오고**,
     * 1b 의 "내 주변 바" 가 정확히 그 경우다.
     */
    @Test
    fun `RED1,4,5 - 좌표 컬럼이 어느 테이블에도 없다`() {
        val violations = allColumns()
            .filter { (_, column) -> looksLikeCoordinate(column) }
            .filterNot { (table, _) -> table in ALLOWED_TABLES }

        assertThat(violations)
            .`as`("PRIN-D04 — 유저의 좌표를 DB 에 쓰지 않는다 (SPEC-08 §5.2)")
            .isEmpty()
    }

    /**
     * RED 2·3 — **`bar` 는 예외다.**
     *
     * SPEC-06 §3.3 의 `bar.lat` · `lng` 는 **업소의 공개 위치**이지 개인의 위치가 아니다.
     * `PRIN-D04` 가 금지하는 것은 "**유저의** 좌표" 다 — 가게 주소를 지도에 찍는 것과
     * 사람이 어디 있었는지를 남기는 것은 다른 일이다.
     *
     * 이 구분이 주석에 남아 있어야 다음 사람이 규칙을 넓게도 좁게도 이해하지 않는다.
     */
    @Test
    fun `RED2,3 - bar 예외가 코드 상수이고 근거가 주석에 있다`() {
        val source = Files.readString(Path.of(SOURCE_PATH))

        assertAll(
            { assertThat(ALLOWED_TABLES).containsExactly("bar") },
            {
                assertThat(source)
                    .`as`("왜 예외인지 읽어서 알 수 있어야 한다")
                    .contains("업소의 공개 위치")
                    .contains("SPEC-06 §3.3")
            },
            {
                // Phase 1a 에는 bar 테이블이 없다. 예외가 **미리** 적혀 있는 것이 의도다 —
                // 1b 에서 테이블이 들어올 때 이 테스트가 빨개져 놀라지 않도록.
                assertThat(allColumns().map { it.first })
                    .`as`("1a 에는 bar 가 없다. 예외는 1b 대비다")
                    .doesNotContain("bar")
            },
        )
    }

    /** RED 6 — 금지 패턴이 코드 상수다. 늘릴 때 리뷰에 보인다. */
    @Test
    fun `RED6 - 금지 컬럼명 패턴이 코드 상수다`() {
        assertThat(FORBIDDEN_COLUMNS)
            .contains("lat", "lng", "longitude", "coord", "geo")
    }

    /** 규칙이 헛돌지 않는지. 합성 위반으로 확인한다 (이슈 027 과 같은 방식). */
    @Test
    fun `금지 목록이 실제 위반을 잡는다`() {
        val fake = listOf(
            "user" to "last_lat",
            "user" to "last_lng",
            "analytics_event" to "latitude",
            "session" to "geo_hash",
            "bookmark" to "coord_x",
        )

        assertAll(
            {
                assertThat(fake).allSatisfy { (_, column) ->
                    assertThat(looksLikeCoordinate(column))
                        .`as`("%s 를 못 잡는다", column)
                        .isTrue()
                }
            },
            {
                // 반대쪽. 규칙이 과하게 넓으면 정상 컬럼이 걸리고, **그때 사람은 목록을 깎는다.**
                //
                // `abv_calculated` 가 실제로 그랬다 — "calcuLATed" 안의 `lat` 이 걸려서
                // 처음 돌렸을 때 빨갛게 났다. 부분 문자열 매칭의 전형적 오탐이다.
                listOf(
                    "collection_id", "created_at", "slug", "aliases", "flavor_profile",
                    "abv_calculated", "category", "translation", "relation",
                ).forEach { column ->
                    assertThat(looksLikeCoordinate(column))
                        .`as`("정상 컬럼 %s 가 걸린다 — 규칙이 너무 넓다", column)
                        .isFalse()
                }
            },
        )
    }

    // ── RED 7~11 : 로그 마스킹 (SPEC-08 §5.2) ─────────────────────────────

    /**
     * RED 7·8·9·11 — 좌표 파라미터가 가려진다.
     *
     * **키는 남긴다.** 무엇을 받았는지는 알아야 디버깅이 되고, 위험한 것은 값이다.
     */
    @Test
    fun `RED7,8,9,11 - 좌표 파라미터가 마스킹된다`() {
        val masked = SensitiveParams.mask(
            mapOf("lat" to "37.5665", "lng" to "126.9780", "q" to "네그로니", "size" to "24"),
        )

        assertAll(
            { assertThat(masked["lat"]).isEqualTo(SensitiveParams.MASK) },
            { assertThat(masked["lng"]).isEqualTo(SensitiveParams.MASK) },
            { assertThat(masked["q"]).`as`("검색어는 좌표가 아니다").isEqualTo("네그로니") },
            { assertThat(masked["size"]).isEqualTo("24") },
            { assertThat(masked.keys).`as`("키는 남는다").contains("lat", "lng") },
            {
                assertThat(SensitiveParams.MASKED)
                    .`as`("RED11 목록이 코드 상수다")
                    .contains("lat", "lng", "latitude", "longitude")
            },
        )
    }

    /** RED 10 — 쿼리스트링에도 적용된다. 맵만 가리면 `getQueryString()` 경로로 샌다. */
    @Test
    fun `RED10 - 쿼리스트링이 통째로 와도 마스킹된다`() {
        val masked = SensitiveParams.maskQueryString("lat=37.5665&lng=126.9780&radius=500")

        assertAll(
            { assertThat(masked).doesNotContain("37.5665").doesNotContain("126.9780") },
            { assertThat(masked).`as`("좌표가 아닌 값은 남는다").contains("radius=500") },
            { assertThat(SensitiveParams.maskUri("/api/v1/bars", "lat=37.5&lng=127.0")) },
            {
                assertThat(SensitiveParams.maskUri("/api/v1/bars", "lat=37.5"))
                    .isEqualTo("/api/v1/bars?lat=${SensitiveParams.MASK}")
            },
            { assertThat(SensitiveParams.maskUri("/api/v1/cocktails", null)).isEqualTo("/api/v1/cocktails") },
        )
    }

    /**
     * RED 12·13 — **원문 쿼리스트링이 로그로 가는 경로가 없다.**
     *
     * 마스킹 함수가 있어도 아무도 안 부르면 소용없다. 정상 경로(접근 로그)와
     * 에러 경로(예외 핸들러) **양쪽**에 걸려야 하는데, 그것을 함수 존재로는 확인할 수 없다.
     *
     * 그래서 소스를 훑는다: `queryString` 을 읽는 곳이 전부 `SensitiveParams` 를 거치는가.
     */
    @Test
    fun `RED12,13 - queryString 을 마스킹 없이 로그에 넣는 곳이 없다`() {
        val offenders = productionSources()
            .filterNot { it.endsWith("SensitiveParams.kt") } // 마스킹 구현 자체
            .filter { path ->
                val text = Files.readString(path)
                text.contains("queryString") && !text.contains("SensitiveParams")
            }
            .map { it.fileName.toString() }
            .toList()

        assertAll(
            {
                assertThat(offenders)
                    .`as`("SPEC-08 §5.2 — 쿼리를 로그에 넣으려면 SensitiveParams 를 거친다")
                    .isEmpty()
            },
            {
                // 규칙이 헛돌지 않는지. 실제로 두 곳이 쓰고 있어야 한다 —
                // 아무도 안 쓰면 위 단언은 공짜로 통과한다.
                val users = productionSources()
                    .filter { Files.readString(it).contains("SensitiveParams.mask") }
                    .map { it.fileName.toString() }
                    .toList()

                assertThat(users)
                    .`as`("정상 경로와 에러 경로 양쪽에 걸려야 한다 — 한쪽만 하면 예외로 샌다")
                    .contains("RequestLoggingFilter.kt", "ApiExceptionHandler.kt")
            },
        )
    }

    // ── RED 14~16 : IP · User-Agent (SPEC-10 §2·§10) ──────────────────────

    /**
     * RED 14·15·16 — IP 와 User-Agent 를 **저장하지 않는다.**
     *
     * 레이트 리밋(SPEC-08 §6)이 IP 기준이라 **런타임에는 안다.** SPEC-10 §10 이 금지한 것은
     * "저장" 이다 — 메모리 버킷 키로 쓰는 것과 남기는 것은 다르다 (`RateLimitPolicy.KeyBy`).
     *
     * `analytics_event` 는 이슈 034 가 만든다. **없으면 없는 대로 통과**시키되,
     * 생기는 순간 이 단언이 그 테이블도 훑는다 — 전수 스캔이라 목록을 고칠 필요가 없다.
     */
    @Test
    fun `RED14,15,16 - IP 와 User-Agent 컬럼이 없다`() {
        val violations = allColumns().filter { (_, column) ->
            column == "ip" || column.contains("ip_address") || column.contains("remote_addr") ||
                column.contains("user_agent") || column.contains("useragent")
        }

        assertThat(violations)
            .`as`("SPEC-10 §10 — IP · User-Agent 원문을 저장하지 않는다 (referrerType 으로 충분하다)")
            .isEmpty()
    }

    // ── RED 17~19 : 세션 내 사용 (FR-USER-006 · SPEC-08 §5.2) ─────────────

    /**
     * RED 17 — Phase 1a 에 좌표를 받는 엔드포인트가 없다.
     *
     * 현재 상태 확인이다. 1b 에서 생길 때 이 테스트가 빨개지는 것이 **정상**이고,
     * 그때 RED 18·19(요청 스코프만 쓴다)를 함께 지키게 하는 것이 목적이다.
     */
    @Test
    fun `RED17 - 좌표를 받는 엔드포인트가 Phase 1a 에 없다`() {
        val takers = handlerMapping.handlerMethods.map { (_, handler) ->
            handler to handler.methodParameters.mapNotNull { parameter ->
                parameter.getParameterAnnotation(RequestParam::class.java)?.let { annotation ->
                    annotation.name.ifBlank { annotation.value }.ifBlank { parameter.parameterName }
                }
            }
        }.filter { (_, names) -> names.any { SensitiveParams.isMasked(it) } }

        assertThat(takers.map { it.first.method.name })
            .`as`("1b 에서 생기면 여기가 빨개진다 — 그때 RED 18·19 를 함께 지킨다")
            .isEmpty()
    }

    /**
     * RED 19 — **세션에도 저장하지 않는다.**
     *
     * "세션 내 사용" 이라는 말이 세션 저장을 뜻하는지가 판단 지점이었다.
     * **요청 스코프만**이다 (SPEC-08 §5.2 — "그 요청에서만 쓰고 버린다").
     *
     * 세션은 `spring-session-jdbc` 라 **DB 에 직렬화돼 들어간다** — 세션에 넣는 것이 곧
     * DB 에 쓰는 것이고, 그러면 `PRIN-D04` 위반이 세션 저장소를 통해 우회된다.
     */
    @Test
    fun `RED19 - 좌표를 세션 속성에 넣는 코드가 없다`() {
        val offenders = productionSources()
            .filter { path ->
                val text = Files.readString(path)
                if (!text.contains("setAttribute")) return@filter false
                // `setAttribute("lat", …)` 같은 모양.
                SensitiveParams.MASKED.any { name ->
                    text.contains("setAttribute(\"$name\"") || text.contains("\"$name\" to")
                }
            }
            .map { it.fileName.toString() }
            .toList()

        assertThat(offenders)
            .`as`("세션은 DB 에 직렬화된다 — 넣는 순간 PRIN-D04 위반이다")
            .isEmpty()
    }

    // ── RED 20~21 : 이벤트 (SPEC-10) ──────────────────────────────────────

    /**
     * RED 20·21 — 이벤트 payload 에 좌표가 들어가지 않는다.
     *
     * ## 이 테스트는 한 번 뒤집혔다 (2026-08-14, 이슈 034)
     *
     * 이슈 033 을 쓸 때는 `analytics_event` 가 없어서 **"이벤트 테이블이 없다"** 를 확인했다.
     * 이슈 034 가 그 테이블을 만들면서 빨갛게 됐고, **그게 이 테스트의 목적이었다** —
     * 이벤트 저장소가 생기는 순간 좌표 정책을 다시 보게 만드는 것.
     *
     * 이제 실재하는 계약을 확인한다:
     * - 컬럼에 좌표가 없다 (RED 1 의 전수 스캔이 이 테이블도 훑는다)
     * - **payload 가 알려진 키만 받는다** — `EventType.allowedPayloadKeys` 밖은 저장되지 않고,
     *   좌표가 섞여 오면 그 이벤트를 통째로 버린다 (`EventCollector` · `EventApiTest` RED 28)
     *
     * 여기서는 **열거에 좌표를 담을 자리가 있는지**만 본다. 런타임 동작은 `EventApiTest` 가 본다.
     */
    @Test
    fun `RED20,21 - 이벤트 payload 스키마에 좌표 자리가 없다`() {
        val leaky = kr.kcocktail.common.analytics.EventType.entries
            .flatMap { type -> type.allowedPayloadKeys.map { type.code to it } }
            .filter { (_, key) -> SensitiveParams.isMasked(key) }

        assertAll(
            {
                assertThat(leaky)
                    .`as`("payload 스키마가 좌표를 허용하면 EventCollector 가 그것을 저장한다")
                    .isEmpty()
            },
            {
                // 규칙이 헛돌지 않는지 — 열거를 실제로 읽었는가.
                assertThat(kr.kcocktail.common.analytics.EventType.entries).isNotEmpty()
            },
            {
                // 저장소가 생겼다. RED 1 이 이 테이블도 훑고 있다.
                assertThat(allColumns().map { it.first })
                    .`as`("이슈 034 가 만들었다 — 전수 스캔 대상이다")
                    .contains("analytics_event")
            },
        )
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────

    private fun allColumns(): List<Pair<String, String>> =
        jdbc.queryForList(
            """
            SELECT table_name, column_name
              FROM information_schema.columns
             WHERE table_schema = 'public'
               AND table_name <> 'flyway_schema_history'
            """.trimIndent(),
        ).map { it["table_name"] as String to (it["column_name"] as String).lowercase() }

    private fun productionSources(): Sequence<Path> =
        Files.walk(Path.of("src/main/kotlin")).asSequence().filter { it.extension == "kt" }

    companion object {
        private const val SOURCE_PATH =
            "src/test/kotlin/kr/kcocktail/architecture/LocationAbsenceTest.kt"

        /**
         * **금지 컬럼명 조각** (RED 6).
         *
         * `PRIN-D04` 가 금지하는 것은 **유저의 좌표**다. 조각으로 두는 이유는
         * `last_lat` · `home_lng` · `geo_hash` 를 다 적을 수 없어서다 —
         * 넓게 잡고 예외를 [ALLOWED_TABLES] 에 근거와 함께 적는 편이, 좁게 잡고 새는 것보다 낫다.
         *
         * `location` 을 넣지 않았다. 컬럼 이름으로 너무 흔해서
         * (`origin_place` 류가 걸릴 자리) 규칙이 헛돌기 시작한다.
         */
        private val FORBIDDEN_COLUMNS = listOf(
            "lat", "lng", "longitude", "coord", "geo",
        )

        /**
         * **토큰 경계로 본다.** 부분 문자열이 아니다.
         *
         * `abv_calculated` 의 "calcu**lat**ed" 가 `lat` 에 걸려서 처음 돌렸을 때 빨갛게 났다.
         * 그런 오탐이 남으면 다음 사람은 규칙을 고치는 대신 **목록에서 `lat` 을 지운다** —
         * 이슈 027 에서 같은 것을 겪고 적어 둔 그대로다.
         *
         * `_` 로 자른 조각이 금지어로 **시작**하면 잡는다:
         * `last_lat` ✓ · `latitude` ✓ · `geo_hash` ✓ · `coord_x` ✓
         * `calculated` ✗ · `collection` ✗ · `category` ✗
         */
        internal fun looksLikeCoordinate(column: String): Boolean =
            column.split('_').any { token -> FORBIDDEN_COLUMNS.any { token.startsWith(it) } }

        /**
         * 금지 조각에 걸리지만 **성격이 다른** 테이블.
         *
         * `bar` 하나다. SPEC-06 §3.3 의 `bar.lat` · `lng` 는 **업소의 공개 위치**이지
         * 개인의 위치가 아니다 — 가게 주소를 지도에 찍는 것과 사람이 어디 있었는지를
         * 남기는 것은 다른 일이다.
         *
         * Phase 1b 에 테이블이 들어올 때를 대비해 **미리** 적어 둔다.
         */
        private val ALLOWED_TABLES = setOf("bar")

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
        }
    }
}
