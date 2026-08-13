package kr.kcocktail.admin.task

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kr.kcocktail.admin.verification.TaskType
import kr.kcocktail.common.security.session.AbsoluteExpiryFilter
import kr.kcocktail.common.security.session.SessionPolicy
import kr.kcocktail.common.web.ApiPaths
import kr.kcocktail.support.PostgresSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Instant

/**
 * ISSUE-028 — 검증 태스크 큐 (`FR-ADMIN-004` · SPEC-06 §4.3 · SPEC-07 §2.7).
 *
 * ## 이 큐가 없으면 `NFR-D-01` 이 성립하지 않는다
 *
 * 배치(이슈 016)가 일 1회 위반을 쌓는다. **찾기만 하고 처리 창구가 없으면
 * "위반 0건"은 목표가 아니라 희망이다.**
 *
 * 태스크를 만드는 것은 이 이슈가 아니다 — 여기서는 **읽고 닫는 것**까지다.
 * 픽스처를 SQL 로 심는 이유가 그것이다. 배치를 돌려 만들면 배치의 판정까지
 * 이 파일이 검증하게 되고, 그건 이슈 016 의 테스트가 이미 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminTaskApiTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var json: ObjectMapper
    @Autowired private lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun clear() {
        jdbc.execute("TRUNCATE verification_task CASCADE")
        jdbc.execute("""TRUNCATE user_role, "user" CASCADE""")
    }

    // ── RED 1~4 : 권한 (SPEC-08 §2) ───────────────────────────────────────

    /**
     * RED 1·2·3 — 조회는 `editor` 이상.
     *
     * 재료 승인(이슈 026)과 달리 **조회와 해소가 같은 권한**이다. 그쪽은 마스터를
     * 오염시킬 수 있어 권한을 나눴고, 여기서 닫는 것은 이미 일어난 위반의 처리 표시라
     * 중립성과 무관하다.
     */
    @ParameterizedTest
    @CsvSource("editor, 200", "admin, 200", "member, 403", "partner_owner, 403")
    fun `RED1,2,3 - 조회는 editor 이상만 된다`(role: String, expected: Int) {
        assertThat(list(session(role)).response.status).isEqualTo(expected)
    }

    /** RED 4 — 미인증은 401 이다. 로그인하면 될 수도 있다는 사실을 알려 준다. */
    @Test
    fun `RED4 - 비로그인은 401 이다`() {
        assertThat(list(null).response.status).isEqualTo(401)
    }

    // ── RED 5~10 : 조회 ───────────────────────────────────────────────────

    /**
     * RED 5·6 — **기본이 `open` 이다.**
     *
     * 큐를 여는 이유가 "지금 처리할 것"을 보기 위해서인데 닫힌 것까지 섞여 나오면
     * 첫 화면부터 걸러 내야 한다.
     */
    @Test
    fun `RED5,6 - 기본은 open 이고 status 필터가 동작한다`() {
        val open = insertTask(code = "INV-COCKTAIL-02")
        val closed = insertTask(code = "INV-COCKTAIL-03", status = "resolved")
        val ignored = insertTask(code = "INV-COCKTAIL-04", status = "dismissed")

        assertAll(
            { assertThat(idsOf(list(session("editor")))).containsExactly(open) },
            { assertThat(idsOf(list(session("editor"), "status" to "resolved"))).containsExactly(closed) },
            { assertThat(idsOf(list(session("editor"), "status" to "dismissed"))).containsExactly(ignored) },
            {
                assertThat(list(session("editor"), "status" to "unknown").response.status)
                    .`as`("모르는 status 는 400 이다 — 빈 결과면 오타를 없는 것으로 읽는다")
                    .isEqualTo(400)
            },
        )
    }

    @Test
    fun `RED7,8 - taskType 과 entityType 으로 거른다`() {
        val invariant = insertTask(code = "INV-COCKTAIL-02", entityType = "cocktail")
        val bypass = insertTask(
            taskType = TaskType.GATE_BYPASS, code = "GATE-COCKTAIL-01", entityType = "cocktail",
        )
        val ingredient = insertTask(code = "INV-INGREDIENT-01", entityType = "ingredient")

        assertAll(
            {
                assertThat(idsOf(list(session("editor"), "taskType" to "gate_bypass")))
                    .containsExactly(bypass)
            },
            {
                assertThat(idsOf(list(session("editor"), "entityType" to "ingredient")))
                    .containsExactly(ingredient)
            },
            {
                assertThat(idsOf(list(session("editor"), "taskType" to "invariant_violation")))
                    .containsExactlyInAnyOrder(invariant, ingredient)
            },
        )
    }

    /**
     * RED 9·10 — 페이징과 **최근 탐지순** 기본 정렬.
     *
     * `(status, detected_at DESC)` 인덱스가 정확히 이 순서다 (V016). 다른 컬럼으로
     * 정렬을 열어 주면 인덱스 없는 정렬이 열린다.
     */
    @Test
    fun `RED9,10 - 페이징되고 최근 탐지순이 기본이다`() {
        val old = insertTask(code = "INV-A", detectedAt = Instant.now().minusSeconds(3_600))
        val mid = insertTask(code = "INV-B", detectedAt = Instant.now().minusSeconds(600))
        val recent = insertTask(code = "INV-C", detectedAt = Instant.now())

        val first = bodyOf(list(session("editor"), "size" to "2"))

        assertAll(
            { assertThat(idsOf(list(session("editor")))).containsExactly(recent, mid, old) },
            { assertThat(first["items"].map { it["id"].asLong() }).containsExactly(recent, mid) },
            { assertThat(first["page"]["totalElements"].asLong()).isEqualTo(3) },
            { assertThat(first["page"]["totalPages"].asInt()).isEqualTo(2) },
            {
                assertThat(list(session("editor"), "sort" to "code,asc").response.status)
                    .`as`("허용목록 밖 정렬은 400 — 인덱스 없는 정렬이 곧 성능 구멍이다")
                    .isEqualTo(400)
            },
        )
    }

    // ── RED 11~15 : 태스크 내용 (이슈 016 연계) ───────────────────────────

    /**
     * RED 11~15 — 큐의 한 줄이 **고치러 갈 수 있을 만큼** 담아야 한다.
     *
     * RED 15 의 `adminPath` 를 서버가 만드는 것이 요점이다. 프론트가 `entityType` →
     * 경로 매핑을 따로 들면, 엔티티 종류가 늘 때 서버는 태스크를 만들고 프론트는
     * 링크를 못 만드는 상태가 생긴다 — **큐에는 보이는데 갈 수가 없다.**
     */
    @Test
    fun `RED11,12,13,14,15 - 태스크가 코드·대상·상세·이동경로를 담는다`() {
        val id = insertTask(
            code = "INV-COCKTAIL-02",
            entityType = "cocktail",
            entityId = 42,
            detail = """{"field":"stylePrimary","reason":"styles 에 없다"}""",
        )

        val item = bodyOf(list(session("editor")))["items"].single { it["id"].asLong() == id }

        assertAll(
            { assertThat(item["taskType"].asText()).`as`("RED11").isEqualTo("invariant_violation") },
            { assertThat(item["code"].asText()).`as`("RED12").isEqualTo("INV-COCKTAIL-02") },
            { assertThat(item["entityType"].asText()).`as`("RED13").isEqualTo("cocktail") },
            { assertThat(item["entityId"].asLong()).`as`("RED13").isEqualTo(42) },
            { assertThat(item["detail"]["field"].asText()).`as`("RED14").isEqualTo("stylePrimary") },
            {
                assertThat(item["adminPath"].asText())
                    .`as`("RED15 — 서버가 만든다")
                    .isEqualTo("${ApiPaths.ADMIN}/cocktails/42")
            },
        )
    }

    /**
     * RED 15 보강 — **모르는 종류는 `null` 이다.**
     *
     * 짐작해서 죽은 링크를 주는 것보다, 링크가 없다는 사실이 드러나는 편이 낫다.
     * Phase 1b 의 `bar` 가 그 자리다.
     */
    @Test
    fun `RED15 - 모르는 엔티티 종류는 이동 경로가 없다`() {
        insertTask(code = "INV-BAR-01", entityType = "bar", entityId = 7)

        val item = bodyOf(list(session("editor")))["items"].single()

        assertThat(item["adminPath"].isNull).isTrue()
    }

    // ── RED 16~20 : 해소 (SPEC-07 §2.7) ───────────────────────────────────

    @Test
    fun `RED16,17 - resolved 로 처리하고 누가 언제 닫았는지 남는다`() {
        val id = insertTask(code = "INV-COCKTAIL-02")
        val editor = session("editor")

        val result = resolve(id, editor)
        val item = json.readTree(result.response.getContentAsString(Charsets.UTF_8))

        assertAll(
            { assertThat(result.response.status).isEqualTo(200) },
            { assertThat(item["status"].asText()).`as`("RED16").isEqualTo("resolved") },
            { assertThat(item["resolvedAt"].isNull).`as`("RED17").isFalse() },
            { assertThat(item["resolvedBy"].asLong()).`as`("RED17").isEqualTo(userIdOf(editor)) },
        )
    }

    /**
     * RED 18 — **무시는 제공하되 사유가 필수다.**
     *
     * 무시를 아예 막으면 오탐 하나가 큐를 영원히 더럽히고, 사유 없이 열어 두면 큐가
     * 조용히 비워진다. 남길 것을 요구하는 쪽이 둘 다 피한다.
     */
    @Test
    fun `RED18 - dismissed 는 사유가 필수다`() {
        val withReason = insertTask(code = "INV-A")
        val without = insertTask(code = "INV-B")
        val blank = insertTask(code = "INV-C")
        val editor = session("editor")

        val ok = resolve(withReason, editor, """{"dismiss":true,"reason":"시드 데이터라 의도된 위반이다"}""")

        assertAll(
            { assertThat(ok.response.status).isEqualTo(200) },
            {
                val item = json.readTree(ok.response.getContentAsString(Charsets.UTF_8))
                assertThat(item["status"].asText()).isEqualTo("dismissed")
                assertThat(item["resolution"].asText()).isEqualTo("시드 데이터라 의도된 위반이다")
            },
            {
                assertThat(resolve(without, editor, """{"dismiss":true}""").response.status)
                    .`as`("사유 없는 무시는 400").isEqualTo(400)
            },
            {
                assertThat(resolve(blank, editor, """{"dismiss":true,"reason":"   "}""").response.status)
                    .`as`("공백만 있는 사유는 없는 것으로 친다").isEqualTo(400)
            },
            { assertThat(statusOf(without)).`as`("거부된 요청은 상태를 바꾸지 않는다").isEqualTo("open") },
        )
    }

    /** RED 19 — 재해소는 409 다. 멱등하게 넘기면 "누가 언제 닫았나"가 나중 사람 것으로 덮인다. */
    @Test
    fun `RED19 - 이미 해소된 태스크의 재해소는 409 다`() {
        val id = insertTask(code = "INV-COCKTAIL-02")
        resolve(id, session("editor"))

        assertThat(resolve(id, session("admin")).response.status).isEqualTo(409)
    }

    /**
     * RED 20 — **해소를 감사에 남기지 않는다** (DECISIONS §1.3).
     *
     * `PRIN-T08` 의 4종에 없고, **태스크 테이블 자체가 이력**이다 —
     * `resolved_at` · `resolved_by` · `resolution` 이 감사 로그가 담을 것과 같은 내용이다.
     * 두 벌로 남기면 둘이 어긋날 때 어느 쪽이 맞는지 알 수 없다.
     */
    @Test
    fun `RED20 - 해소는 감사에 남지 않는다`() {
        val before = count("SELECT count(*) FROM audit_log")
        val id = insertTask(code = "INV-COCKTAIL-02")

        resolve(id, session("editor"))

        assertAll(
            { assertThat(count("SELECT count(*) FROM audit_log")).isEqualTo(before) },
            {
                assertThat(count("SELECT count(*) FROM verification_task WHERE id = $id AND resolved_by IS NOT NULL"))
                    .`as`("이력은 태스크 행이 갖는다").isEqualTo(1)
            },
        )
    }

    // ── RED 21~23 : 자동 해소 (이슈 016 연계) ─────────────────────────────

    /**
     * RED 21·22 — 배치가 닫은 태스크는 **`resolvedBy` 가 비어 있다.**
     *
     * 사람이 닫은 것과 시스템이 닫은 것을 구별할 수 있어야 한다 — "고쳐서 닫힌 것"과
     * "누가 확인하고 닫은 것"은 다른 사건이다. 별도 시스템 계정을 만들지 않는 이유는
     * 감사 주체와 같다 (`AuditRecorder`) — **거짓 주체를 지어내지 않는다.**
     *
     * 닫는 행위 자체는 이슈 016 의 `VerificationTaskStore.resolveMissing` 이 한다.
     * 여기서는 **그 결과가 큐에 어떻게 보이는지**만 본다.
     */
    @Test
    fun `RED21,22 - 자동 해소된 태스크는 resolvedBy 가 비어 있다`() {
        val auto = insertTask(code = "INV-A", status = "resolved") // 배치가 닫은 모양
        val manual = insertTask(code = "INV-B")
        resolve(manual, session("editor"))

        val closed = bodyOf(list(session("editor"), "status" to "resolved"))["items"]
            .associateBy { it["id"].asLong() }

        assertAll(
            { assertThat(closed[auto]!!["resolvedBy"].isNull).`as`("RED22 시스템이 닫았다").isTrue() },
            { assertThat(closed[manual]!!["resolvedBy"].isNull).`as`("사람이 닫았다").isFalse() },
        )
    }

    /**
     * RED 23 — **수동 해소 후에도 위반이 남아 있으면 다시 열린다.**
     *
     * 배치의 멱등(`PRIN-T07`)이 그렇게 동작한다 — 같은 위반이 또 걸리면 새 줄을 만들지 않고
     * 그 줄을 다시 연다. 사람이 "고쳤다"고 표시했는데 안 고쳐졌다면 그건 새 사건이 아니라
     * **같은 사건의 재발**이고, 이력을 나누면 그 사실이 흐려진다.
     *
     * 재오픈되면 지난번 무시 사유도 지워진다 — 열린 태스크에 해소 사유가 붙어 있으면
     * 큐를 읽는 사람이 "처리된 것"으로 오해한다.
     */
    @Test
    fun `RED23 - 수동 해소 후 위반이 남아 있으면 재오픈된다`() {
        val id = insertTask(code = "INV-COCKTAIL-02", entityId = 42)
        resolve(id, session("editor"), """{"dismiss":true,"reason":"확인했다"}""")

        // 배치가 같은 위반을 다시 본 상황 — V016 의 유니크가 새 줄을 막고 이 줄을 다시 연다.
        reopenViaBatchUpsert(entityId = 42, code = "INV-COCKTAIL-02")

        val item = bodyOf(list(session("editor")))["items"].single()

        assertAll(
            { assertThat(item["id"].asLong()).`as`("새 줄이 생기지 않는다").isEqualTo(id) },
            { assertThat(item["status"].asText()).isEqualTo("open") },
            { assertThat(item["resolvedBy"].isNull).isTrue() },
            { assertThat(item["resolution"].isNull).`as`("지난번 사유는 더 이상 유효하지 않다").isTrue() },
            { assertThat(count("SELECT count(*) FROM verification_task")).isEqualTo(1) },
        )
    }

    // ── RED 24~25 : Phase 1b 확장 자리 ────────────────────────────────────

    /**
     * RED 24·25 — **1b 값을 지금 정의한다.**
     *
     * `FR-ADMIN-004` 가 `hours_verified_at` 만료와 인스타 폐업 신호를 명시적으로 예로 들었다.
     * 생성자는 BAR 도메인이라 1b 지만, 나중에 열거를 늘리면 이 목록을 읽는 쪽
     * (큐 필터 · 어드민 UI)이 **그때** 깨진다.
     *
     * 조회는 지금도 되고 결과가 빈 것뿐이다 — 에러가 아니다.
     */
    @ParameterizedTest
    @CsvSource("hours_expired", "instagram_signal")
    fun `RED24,25 - 1b 태스크 종류로 조회해도 에러가 아니다`(slug: String) {
        insertTask(code = "INV-COCKTAIL-02")

        val result = list(session("editor"), "taskType" to slug)

        assertAll(
            { assertThat(TaskType.findBySlug(slug)).`as`("RED24 열거에 있다").isNotNull() },
            { assertThat(result.response.status).`as`("RED25 에러가 아니다").isEqualTo(200) },
            { assertThat(bodyOf(result)["items"]).`as`("빈 결과다").isEmpty() },
        )
    }

    // ── RED 26~27 : 규약 ──────────────────────────────────────────────────

    /** RED 26 — 어드민 경로는 `id` 를 쓴다. */
    @Test
    fun `RED26 - 어드민 경로는 id 를 쓴다`() {
        val id = insertTask(code = "INV-COCKTAIL-02")

        assertAll(
            {
                assertThat(mvc.get("$ADMIN/$id") { session = session("editor")!! }.andReturn().response.status)
                    .isEqualTo(200)
            },
            {
                assertThat(mvc.get("$ADMIN/INV-COCKTAIL-02") { session = session("editor")!! }
                    .andReturn().response.status)
                    .`as`("code 로는 안 된다").isNotEqualTo(200)
            },
        )
    }

    /** RED 27 — 어드민 응답은 캐시하지 않는다. 처리 전 위반이 중간 캐시에 남으면 안 된다. */
    @Test
    fun `RED27 - 캐시 헤더가 없다`() {
        insertTask(code = "INV-COCKTAIL-02")
        val response = list(session("editor")).response

        assertAll(
            { assertThat(response.getHeader(HttpHeaders.ETAG)).isNull() },
            {
                assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL).orEmpty())
                    .doesNotContain("public", "max-age=60")
            },
        )
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────

    private var seq = 0

    private fun list(login: MockHttpSession?, vararg params: Pair<String, String>) =
        mvc.get(ADMIN) {
            login?.let { this.session = it }
            params.forEach { (name, value) -> param(name, value) }
        }.andReturn()

    private fun resolve(id: Long, login: MockHttpSession?, body: String? = null) =
        mvc.post("$ADMIN/$id/resolve") {
            with(csrf())
            login?.let { this.session = it }
            body?.let { contentType = MediaType.APPLICATION_JSON; content = it }
        }.andReturn()

    /**
     * 배치가 만들어 둔 것과 같은 모양의 행. **배치를 돌리지 않는다** —
     * 돌리면 이 파일이 배치의 판정까지 검증하게 되고, 그건 이슈 016 의 테스트가 이미 한다.
     */
    private fun insertTask(
        code: String,
        taskType: TaskType = TaskType.INVARIANT_VIOLATION,
        entityType: String = "cocktail",
        entityId: Long = (++seq).toLong(),
        status: String = "open",
        detail: String = """{"reason":"테스트"}""",
        detectedAt: Instant = Instant.now(),
    ): Long = jdbc.queryForObject(
        """
        INSERT INTO verification_task
               (task_type, entity_type, entity_id, code, detail, status, detected_at, resolved_at)
        VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?)
        RETURNING id
        """.trimIndent(),
        Long::class.java,
        taskType.slug, entityType, entityId, code, detail, status,
        java.sql.Timestamp.from(detectedAt),
        if (status == "open") null else java.sql.Timestamp.from(Instant.now()),
    )!!

    /** V016 의 upsert 와 같은 SQL. 배치가 같은 위반을 다시 본 상황을 만든다. */
    private fun reopenViaBatchUpsert(entityId: Long, code: String) {
        jdbc.update(
            """
            INSERT INTO verification_task (task_type, entity_type, entity_id, code, detail, status)
            VALUES ('invariant_violation', 'cocktail', ?, ?, '{}'::jsonb, 'open')
            ON CONFLICT (task_type, entity_type, entity_id, code) DO UPDATE
                SET detail = EXCLUDED.detail,
                    status = 'open',
                    detected_at = now(),
                    resolved_at = NULL,
                    resolved_by = NULL,
                    resolution = NULL
            """.trimIndent(),
            entityId, code,
        )
    }

    private fun session(role: String): MockHttpSession? {
        val userId = jdbc.queryForObject(
            """INSERT INTO "user" (provider, provider_uid, display_name)
               VALUES ('kakao', 'uid-${seq++}-${System.nanoTime()}', '테스터') RETURNING id""",
            Long::class.java,
        )!!
        jdbc.update("INSERT INTO user_role (user_id, role) VALUES (?, ?)", userId, role)

        return MockHttpSession().apply {
            setAttribute(AbsoluteExpiryFilter.USER_ID, userId)
            setAttribute(SessionPolicy.ISSUED_AT, Instant.now())
            setAttribute(SessionPolicy.ISSUED_ROLES, setOf(role))
        }
    }

    private fun userIdOf(session: MockHttpSession?) =
        session!!.getAttribute(AbsoluteExpiryFilter.USER_ID) as Long

    private fun bodyOf(result: MvcResult): JsonNode =
        json.readTree(result.response.getContentAsString(Charsets.UTF_8))

    private fun idsOf(result: MvcResult) = bodyOf(result)["items"].map { it["id"].asLong() }

    private fun statusOf(id: Long) =
        jdbc.queryForObject("SELECT status FROM verification_task WHERE id = $id", String::class.java)

    private fun count(sql: String) = jdbc.queryForObject(sql, Long::class.java)!!

    companion object {
        private val ADMIN = "${ApiPaths.ADMIN}/tasks"

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

            // 이슈 025 와 같은 이유 — `spring-session-jdbc` 의 `SessionRepositoryFilter` 가
            // 요청 세션을 갈아끼워 `MockHttpSession` 속성이 컨트롤러까지 가지 않는다.
            registry.add("spring.autoconfigure.exclude") {
                "org.springframework.boot.autoconfigure.session.SessionAutoConfiguration"
            }
        }
    }
}
