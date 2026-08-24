package kr.mut.admin.ingredient

import com.fasterxml.jackson.databind.ObjectMapper
import kr.mut.common.security.session.AbsoluteExpiryFilter
import kr.mut.common.security.session.SessionPolicy
import kr.mut.common.web.ApiPaths
import kr.mut.support.PostgresSupport
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
 * ISSUE-026 — 재료 승인 워크플로 (`FR-ADMIN-007` · `FR-INGREDIENT-001` · SPEC-08 §2).
 *
 * ## 이 파일이 지키는 것은 문장 하나다
 *
 * > **만드는 사람과 통과시키는 사람이 다르다.**
 *
 * `editor` 가 만들고 `admin` 이 승인한다. 같으면 승인제가 형식만 남고,
 * 마스터 오염을 막는다는 `PRIN-D01` 의 전제가 자기 검토로 무너진다 (SPEC-08 §2.2).
 *
 * RED 5·6 이 그 문장이고 나머지는 그 주변을 고정한다.
 *
 * ## `FR-ADMIN-007` 과 어긋나는 것을 알고 쓴다
 *
 * `FR-ADMIN-007` 은 "에디터 승인 단계"라고 적었다. SPEC-07 §1.3 이 스코프를 SPEC-08 로
 * **명시적으로 위임**했고 그 표가 `admin` 이라 SPEC-08 이 이긴다 (GAPS G-29 · DECISIONS §1.1).
 * 조용히 맞추지 않고 등재했다 — 문구가 틀렸다면 SPEC-03 을 고쳐야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminIngredientApiTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var json: ObjectMapper
    @Autowired private lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun clear() {
        jdbc.execute("TRUNCATE cocktail, ingredient, audit_log, search_document CASCADE")
        jdbc.execute("""TRUNCATE user_role, "user" CASCADE""")
    }

    // ── RED 1~4 : 생성 (FR-INGREDIENT-001) ────────────────────────────────

    /** RED 1·3·4 — 생성은 `editor` 이상. `member` 는 403 이다 (`WRITE_CONTENT` 는 `FORBID`). */
    @ParameterizedTest
    @CsvSource("editor, 201", "admin, 201", "member, 403", "partner_owner, 403")
    fun `RED1,3,4 - 생성은 editor 이상만 된다`(role: String, expected: Int) {
        assertThat(create(session(role)).response.status).isEqualTo(expected)
    }

    /**
     * RED 2 — **생성 시점은 항상 승인 대기다.**
     *
     * `isApproved` 는 `CreateIngredientRequest` 에 **없다**. 서비스에서 걸러 내는 것보다 강하다 —
     * 타입에 없으면 우회할 방법 자체가 없다 (`PRIN-T05`, 이슈 025 와 같은 방식).
     */
    @Test
    fun `RED2 - 생성하면 승인 대기다`() {
        val created = create(session("editor"))
        val id = idOf(created)

        assertAll(
            { assertThat(bodyOf(created)["isApproved"].asBoolean()).isFalse() },
            { assertThat(approvedFlag(id)).`as`("DB 도 false").isFalse() },
            {
                // 요청에 넣어도 무시된다 — 애초에 바인딩될 필드가 없다.
                val sneaky = create(session("editor"), extra = """"isApproved":true""")
                assertThat(approvedFlag(idOf(sneaky))).`as`("요청으로 우회 불가").isFalse()
            },
        )
    }

    // ── RED 5~8 : 승인 — 이 이슈의 요점 (SPEC-08 §2) ──────────────────────

    /**
     * RED 5·6 — **`admin` 만 승인한다. `editor` 는 403 이다.**
     *
     * `editor` 가 자기가 만든 재료를 스스로 통과시킬 수 있으면 승인 단계가 아무것도 막지 못한다.
     * 403 이지 404 가 아닌 이유: `APPROVE_INGREDIENT` 는 `FORBID` 다 — 엔드포인트는 문서에 있고
     * 재료의 존재를 숨길 이유도 없다 (`draft` 와 다르다).
     */
    @ParameterizedTest
    @CsvSource("admin, 200", "editor, 403", "member, 403", "partner_owner, 403")
    fun `RED5,6 - 승인은 admin 만 된다`(role: String, expected: Int) {
        val id = idOf(create(session("editor")))

        assertThat(approve(id, session(role)).response.status).isEqualTo(expected)
    }

    /** RED 6 보강 — 거부된 승인은 상태를 남기지 않는다. */
    @Test
    fun `RED6 - editor 가 승인을 시도해도 상태가 그대로다`() {
        val id = idOf(create(session("editor")))

        approve(id, session("editor"))

        assertThat(approvedFlag(id)).isFalse()
    }

    @Test
    fun `RED7 - 승인하면 is_approved 가 true 가 된다`() {
        val id = idOf(create(session("editor")))

        val result = approve(id, session("admin"))

        assertAll(
            { assertThat(bodyOf(result)["isApproved"].asBoolean()).isTrue() },
            { assertThat(approvedFlag(id)).isTrue() },
        )
    }

    /**
     * RED 8 — **재승인은 409 다** (DECISIONS §1.11).
     *
     * 멱등 200 으로 넘기면 "언제 누가 통과시켰나"가 흐려진다. 승인은 되풀이할 수 있는
     * 조회가 아니라 **한 번 일어나는 판단**이다.
     */
    @Test
    fun `RED8 - 재승인은 409 다`() {
        val id = idOf(create(session("editor")))
        approve(id, session("admin"))

        assertThat(approve(id, session("admin")).response.status).isEqualTo(409)
    }

    /**
     * RED 9 — **승인이 감사에 남는다** (DECISIONS §1.3).
     *
     * `PRIN-T08` 이 열거한 4종에 재료 승인은 없다. 그래도 남기는 이유는
     * 마스터 오염이 `PRIN-D01` 의 전제를 무너뜨리기 때문이다 —
     * **누가 통과시켰는지** 없으면 오염을 되짚을 수 없다.
     */
    @Test
    fun `RED9 - 승인이 감사에 기록된다`() {
        val id = idOf(create(session("editor")))
        approve(id, session("admin"))

        assertAll(
            {
                assertThat(
                    count(
                        "SELECT count(*) FROM audit_log " +
                            "WHERE entity_type = 'ingredient' AND entity_id = $id AND action = 'approve'",
                    ),
                ).isEqualTo(1)
            },
            {
                assertThat(
                    count(
                        "SELECT count(*) FROM audit_log " +
                            "WHERE entity_id = $id AND actor_user_id IS NOT NULL",
                    ),
                ).`as`("누가 통과시켰는지 남는다").isEqualTo(1)
            },
            {
                // 거부된 승인은 트랜잭션이 서지 않으므로 기록도 없다.
                val other = idOf(create(session("editor")))
                approve(other, session("editor"))
                assertThat(count("SELECT count(*) FROM audit_log WHERE entity_id = $other")).isZero()
            },
        )
    }

    /**
     * RED 10 — **승인 취소를 제공하지 않는다.**
     *
     * SPEC 에 없고, 이미 발행된 레시피가 참조 중일 수 있다. 취소하면 그 레시피들이
     * 게이트를 어긴 채 `published` 로 남는다 (`NFR-D-02` 위반). 없는 것이 결정이라
     * 경로가 없다는 사실 자체를 고정한다.
     */
    @Test
    fun `RED10 - 승인 취소 경로가 없다`() {
        val id = idOf(create(session("editor")))
        approve(id, session("admin"))

        val revoke = mvc.post("$ADMIN/$id/unapprove") { with(csrf()); session = session("admin")!! }.andReturn()

        assertAll(
            { assertThat(revoke.response.status).`as`("그런 엔드포인트가 없다").isEqualTo(404) },
            { assertThat(approvedFlag(id)).isTrue() },
        )
    }

    // ── RED 11 : 승인 대기 큐 ─────────────────────────────────────────────

    /**
     * RED 11 — 대기 큐는 `editor` 도 본다.
     *
     * 자기가 올린 것이 어디까지 갔는지 알아야 기다릴지 다른 걸 할지 정한다.
     * **통과시키는 것만** `admin` 이다 — 보는 것까지 막을 이유가 없다.
     */
    @Test
    fun `RED11 - 미승인 재료 목록을 조회한다`() {
        val pendingId = idOf(create(session("editor")))
        val approvedId = idOf(create(session("editor")))
        approve(approvedId, session("admin"))

        val body = json.readTree(
            mvc.get("$ADMIN/pending") { session = session("editor")!! }
                .andReturn().response.getContentAsString(Charsets.UTF_8),
        )
        val ids = body.map { it["id"].asLong() }

        assertAll(
            { assertThat(ids).contains(pendingId) },
            { assertThat(ids).`as`("승인된 것은 큐에 없다").doesNotContain(approvedId) },
            {
                assertThat(mvc.get("$ADMIN/pending") { session = session("member")!! }.andReturn().response.status)
                    .isEqualTo(403)
            },
        )
    }

    /**
     * 검색어 없이 전체 목록을 본다 — 어드민 진입 판정(웹 미들웨어)이 이 모양으로 두드린다.
     *
     * 회귀 고정: `:q` 가 null 이면 Postgres 가 파라미터 타입을 bytea 로 추론해서
     * `lower(bytea)` 오류로 500 이 났다. 기존 테스트가 전부 검색어를 넣고 불러서
     * 실서버(2026-08-24)에서야 드러났다 — cast 로 고쳤고 이 테스트가 지킨다.
     */
    @Test
    fun `검색어 없이도 목록이 나온다`() {
        val editor = session("editor")
        create(editor)

        val res = mvc.get(ADMIN) { session = editor!! }.andReturn().response
        assertAll(
            { assertThat(res.status).isEqualTo(200) },
            { assertThat(json.readTree(res.getContentAsString(Charsets.UTF_8)).size()).isGreaterThan(0) },
        )
    }

    // ── RED 12~13 : 공개 노출 (이슈 008 RED 16 · 023 RED 2·17) ────────────

    /**
     * RED 12·13 — **미승인은 공개에 없고, 상세는 404 다.**
     *
     * 403 이면 "그 슬러그는 존재한다"가 새어 나간다. 승인 전 재료가 사전에 보이면
     * 승인제가 방어선 노릇을 못 한다 (`PRIN-D01`).
     */
    @Test
    fun `RED12,13 - 미승인 재료는 공개 API 에 없다`() {
        val created = create(session("editor"))
        val slug = bodyOf(created)["slug"].asText()

        val listBefore = mvc.get("${ApiPaths.BASE}/ingredients").andReturn().response
            .getContentAsString(Charsets.UTF_8)
        val detailBefore = mvc.get("${ApiPaths.BASE}/ingredients/$slug").andReturn().response.status

        approve(idOf(created), session("admin"))

        val listAfter = mvc.get("${ApiPaths.BASE}/ingredients").andReturn().response
            .getContentAsString(Charsets.UTF_8)
        val detailAfter = mvc.get("${ApiPaths.BASE}/ingredients/$slug").andReturn().response.status

        assertAll(
            { assertThat(listBefore).`as`("RED12 목록에 없다").doesNotContain(slug) },
            { assertThat(detailBefore).`as`("RED13 상세는 404").isEqualTo(404) },
            { assertThat(listAfter).`as`("승인하면 나온다").contains(slug) },
            { assertThat(detailAfter).isEqualTo(200) },
        )
    }

    // ── RED 14~16 : 사용 제약 (DECISIONS §1.1) ────────────────────────────

    /**
     * RED 14~16 — **`draft` 에는 쓸 수 있고 발행에서 막힌다.**
     *
     * 이 조합이 DECISIONS §1.1 의 전부다. 승인을 기다리면 에디터 작업이 끊기고,
     * 발행에서 막으면 마스터 오염도 없다.
     *
     * 차단은 이슈 013 의 `GATE-COCKTAIL-04` 가 이미 한다 — 여기서 다시 구현하지 않고
     * **그 게이트가 실제로 이 경로를 막는지** 확인한다. 두 벌이면 반드시 어긋난다.
     */
    @Test
    fun `RED14,15,16 - 미승인 재료는 draft 에 쓰이고 발행에서 막힌다`() {
        val ingredientId = idOf(create(session("editor")))
        val cocktailId = draftWithIngredient(ingredientId)

        val blocked = mvc.post("${ApiPaths.ADMIN}/cocktails/$cocktailId/publish") {
            with(csrf()); session = session("editor")!!
        }.andReturn()
        val body = blocked.response.getContentAsString(Charsets.UTF_8)

        // 승인하면 같은 칵테일이 발행된다 — 막은 것이 승인 여부였다는 증거다.
        approve(ingredientId, session("admin"))
        val allowed = mvc.post("${ApiPaths.ADMIN}/cocktails/$cocktailId/publish") {
            with(csrf()); session = session("editor")!!
        }.andReturn()

        assertAll(
            {
                assertThat(count("SELECT count(*) FROM recipe_ingredient WHERE ingredient_id = $ingredientId"))
                    .`as`("RED14 draft 레시피에는 들어간다").isEqualTo(1)
            },
            { assertThat(blocked.response.status).`as`("RED15 발행 차단").isEqualTo(422) },
            { assertThat(body).`as`("RED16 사유가 violations 에 담긴다").contains("GATE-COCKTAIL-04") },
            { assertThat(allowed.response.status).`as`("승인 후에는 발행된다").isEqualTo(200) },
        )
    }

    // ── RED 17~20 : 상한 (FR-INGREDIENT-001 · DECISIONS §1.2) ─────────────

    /**
     * RED 17~20 — **상한은 경고지 차단이 아니다.**
     *
     * SPEC-02 §3 의 근거가 "무한정 늘리면 역검색 UX 가 무너진다"이지 무결성이 아니다.
     * 301번째 재료가 데이터를 깨뜨리지는 않는다.
     *
     * 이 테스트는 상한을 [CAP] 로 낮춰 돌린다. 300건을 실제로 승인하면 느리고,
     * 확인할 것은 개수가 아니라 **넘었을 때 무슨 일이 일어나는가**다.
     * 낮출 수 있다는 사실 자체가 RED 20("설정이다")의 증거이기도 하다 —
     * 상수였다면 이 오버라이드가 아무 효과도 없다.
     */
    @Test
    fun `RED17,18,19,20 - 상한 초과는 경고이고 승인을 막지 않는다`() {
        val admin = session("admin")

        val under = capacity()
        repeat(CAP.toInt()) { approve(idOf(create(session("editor"))), admin) }
        val atCap = capacity()

        val last = idOf(create(session("editor")))
        val overflowing = approve(last, admin)
        val over = capacity()

        assertAll(
            { assertThat(under["approved"].asLong()).`as`("RED17 개수를 조회한다").isZero() },
            { assertThat(under["cap"].asLong()).`as`("RED20 상한이 설정에서 온다").isEqualTo(CAP) },
            { assertThat(atCap["warning"].asBoolean()).`as`("같으면 아직 경고가 아니다").isFalse() },
            { assertThat(overflowing.response.status).`as`("RED19 경고가 승인을 막지 않는다").isEqualTo(200) },
            { assertThat(approvedFlag(last)).`as`("RED19 실제로 승인된다").isTrue() },
            { assertThat(over["warning"].asBoolean()).`as`("RED18 넘으면 경고").isTrue() },
            { assertThat(over["approved"].asLong()).isEqualTo(CAP + 1) },
        )
    }

    // ── RED 21~22 : 규약 ──────────────────────────────────────────────────

    /**
     * RED 21 — **어드민 경로는 `id` 를 쓴다.**
     *
     * 공개 경로는 `slug` 다 (SPEC-07 §1.1 · 이슈 023). 승인 전 재료의 슬러그는 아직 고칠 수
     * 있고, 고치는 순간 열려 있던 편집 화면의 URL 이 죽는다.
     */
    @Test
    fun `RED21 - 어드민 경로는 id 를 쓴다`() {
        val created = create(session("editor"))
        val id = idOf(created)
        val slug = bodyOf(created)["slug"].asText()

        assertAll(
            {
                assertThat(mvc.get("$ADMIN/$id") { session = session("editor")!! }.andReturn().response.status)
                    .isEqualTo(200)
            },
            {
                // slug 로는 잡히지 않는다 — `{id}` 가 Long 이라 타입 변환에서 걸린다.
                assertThat(mvc.get("$ADMIN/$slug") { session = session("editor")!! }.andReturn().response.status)
                    .`as`("slug 로는 안 된다").isNotEqualTo(200)
            },
        )
    }

    /** RED 22 — 어드민 응답은 캐시하지 않는다. 승인 전 데이터가 중간 캐시에 남으면 안 된다. */
    @Test
    fun `RED22 - 어드민 응답에 캐시 헤더가 없다`() {
        val response = create(session("editor")).response

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

    /**
     * 파라미터 이름이 `login` 인 이유는 이슈 025 와 같다 — `session` 으로 두면 DSL 람다 안에서
     * `MockHttpServletRequestDsl.session` 프로퍼티가 이름을 가져가 세션이 영영 안 붙는다.
     */
    private fun create(login: MockHttpSession?, extra: String = ""): MvcResult {
        val n = seq++
        val fields = buildList {
            add(""""slug":"admin-ing-$n"""")
            add(""""nameKo":"테스트재료$n"""")
            add(""""nameEn":"Test Ingredient $n"""")
            add(""""category":"spirit"""")
            add(""""domesticAvailability":"common"""")
            if (extra.isNotBlank()) add(extra)
        }
        return mvc.post(ADMIN) {
            with(csrf())
            login?.let { this.session = it }
            contentType = MediaType.APPLICATION_JSON
            content = fields.joinToString(",", "{", "}")
        }.andReturn()
    }

    private fun approve(id: Long, login: MockHttpSession?) =
        mvc.post("$ADMIN/$id/approve") { with(csrf()); login?.let { this.session = it } }.andReturn()

    private fun capacity() = json.readTree(
        mvc.get("$ADMIN/capacity") { session = session("admin")!! }
            .andReturn().response.getContentAsString(Charsets.UTF_8),
    )

    /** 게이트가 재료 승인 하나만 걸리도록 나머지는 전부 채운 `draft`. */
    private fun draftWithIngredient(ingredientId: Long): Long {
        val n = seq++
        val created = mvc.post("${ApiPaths.ADMIN}/cocktails") {
            with(csrf())
            session = session("editor")!!
            contentType = MediaType.APPLICATION_JSON
            content = json.writeValueAsString(
                mapOf(
                    "slug" to "gate-check-$n",
                    "nameKo" to "게이트", "nameEn" to "Gate", "summary" to "요약",
                    "baseSpirit" to "gin", "stylePrimary" to "highball", "method" to "build",
                    "sweetness" to "dry", "glassType" to "하이볼 글라스",
                    "styles" to listOf("highball"), "aromaTags" to listOf("citrus"),
                    "tastingNote" to "쌉싸름한 향에 단맛이 얹힌다", "isClassic" to false,
                ),
            )
        }.andReturn()
        val cocktailId = idOf(created)

        val recipeId = jdbc.queryForObject(
            "INSERT INTO recipe (cocktail_id, version_type) VALUES ($cocktailId, 'standard') RETURNING id",
            Long::class.java,
        )!!
        jdbc.execute(
            "INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit) " +
                "VALUES ($recipeId, $ingredientId, 1, 45, 'ml')",
        )
        jdbc.execute("INSERT INTO recipe_step (recipe_id, step_no, text) VALUES ($recipeId, 1, '얼음을 채운다')")
        return cocktailId
    }

    /** 역할을 가진 세션. `null` 이면 비로그인이다. */
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

    private fun bodyOf(result: MvcResult) =
        json.readTree(result.response.getContentAsString(Charsets.UTF_8))

    private fun idOf(result: MvcResult): Long {
        val body = result.response.getContentAsString(Charsets.UTF_8)
        val node = json.readTree(body)["id"]
            ?: error("생성 응답에 id 가 없다 (status=${result.response.status}): $body")
        return node.asLong()
    }

    private fun approvedFlag(id: Long) =
        jdbc.queryForObject("SELECT is_approved FROM ingredient WHERE id = $id", Boolean::class.java)!!

    private fun count(sql: String) = jdbc.queryForObject(sql, Long::class.java)!!

    companion object {
        private val ADMIN = "${ApiPaths.ADMIN}/ingredients"

        /** 300건을 실제로 승인하지 않는다. 확인할 것은 개수가 아니라 넘었을 때의 동작이다. */
        private const val CAP = 3L

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { PostgresSupport.container.jdbcUrl }
            registry.add("spring.datasource.username") { PostgresSupport.container.username }
            registry.add("spring.datasource.password") { PostgresSupport.container.password }
            registry.add("spring.flyway.enabled") { true }
            registry.add("spring.flyway.user") { PostgresSupport.container.username }
            registry.add("spring.flyway.password") { PostgresSupport.container.password }
            registry.add("mut.verification.scheduled") { false }

            // RED 20 — 상한이 설정이라는 증거. 상수였다면 이 줄이 아무 효과도 없다.
            registry.add("mut.ingredient.approved-cap") { CAP }

            // `spring-session-jdbc` 의 `SessionRepositoryFilter` 가 요청 세션을 갈아끼워
            // `MockHttpSession` 에 심은 속성이 컨트롤러까지 가지 않는다 — 전부 401 이 된다.
            // 원인이 권한처럼 보이지만 세션 저장소 문제다 (이슈 025 에서 반나절 걸렸다).
            // `spring.session.store-type=none` 은 Boot 3 에 없는 값이라 조용히 무시된다.
            registry.add("spring.autoconfigure.exclude") {
                "org.springframework.boot.autoconfigure.session.SessionAutoConfiguration"
            }
        }
    }
}
