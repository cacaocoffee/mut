package kr.kcocktail.admin.audit

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kr.kcocktail.common.audit.AuditAction
import kr.kcocktail.common.security.session.AbsoluteExpiryFilter
import kr.kcocktail.common.security.session.SessionPolicy
import kr.kcocktail.common.web.ApiPaths
import kr.kcocktail.support.PostgresSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import java.sql.Timestamp
import java.time.Instant

/**
 * ISSUE-029 — 감사 로그 조회 (`FR-ADMIN-005` · `PRIN-T08` · SPEC-08 §2.2).
 *
 * ## 이 파일이 지키는 문장 하나
 *
 * > **감시받는 사람이 감시 기록을 보면 안 된다.**
 *
 * `editor` 는 403 이다 (RED 2). SPEC-08 §2.2 가 권한 분리를 중립성 장치로 규정했고,
 * 무엇이 기록되는지 아는 사람은 기록되지 않는 방법도 알게 된다.
 *
 * ## `NFR-O-05` 는 "재구성 가능" 이다
 *
 * RED 17 이 그 인수 시나리오다 — 발행 → 회수 → 재발행을 실제로 돌리고, 그 이력만으로
 * 무슨 일이 있었는지 되짚을 수 있는지 본다. 나머지는 그 길에 놓인 필터들이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminAuditLogApiTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var json: ObjectMapper
    @Autowired private lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun clear() {
        jdbc.execute("TRUNCATE cocktail, ingredient, audit_log, search_document CASCADE")
        jdbc.execute("""TRUNCATE user_role, "user" CASCADE""")
    }

    // ── RED 1~5 : 권한 (SPEC-08 §2 · §2.2) ────────────────────────────────

    /**
     * RED 1~4 — **`admin` 만이다. `editor` 도 403.**
     *
     * 이 파일에서 가장 중요한 한 줄이다. `editor` 를 허용하는 순간 SPEC-08 §2.2 의
     * 중립성 장치가 무너진다 — 감사받는 사람이 자기 기록을 들여다볼 수 있게 된다.
     *
     * 403 이지 404 가 아닌 이유: `VIEW_AUDIT_LOG` 는 `FORBID` 다. 엔드포인트는 문서에 있고,
     * 감사 로그가 존재한다는 사실 자체는 비밀이 아니다 — 오히려 알려져야 억지력이 생긴다.
     */
    @ParameterizedTest
    @CsvSource("admin, 200", "editor, 403", "member, 403", "partner_owner, 403")
    fun `RED1,2,3,4 - 감사 조회는 admin 만 된다`(role: String, expected: Int) {
        assertThat(list(session(role)).response.status).isEqualTo(expected)
    }

    @Test
    fun `RED5 - 비로그인은 401 이다`() {
        assertThat(list(null).response.status).isEqualTo(401)
    }

    // ── RED 6~13 : 조회 ───────────────────────────────────────────────────

    @Test
    fun `RED6,7,8,9 - entityType·entityId·action·actor 로 거른다`() {
        val alice = userId("앨리스")
        val bob = userId("밥")

        val target = insertLog("cocktail", 1, AuditAction.PUBLISH, actor = alice)
        insertLog("cocktail", 2, AuditAction.PUBLISH, actor = bob)
        insertLog("cocktail", 1, AuditAction.UNPUBLISH, actor = bob)
        insertLog("ingredient", 1, AuditAction.APPROVE, actor = alice)

        assertAll(
            { assertThat(idsOf(list(admin(), "entityType" to "ingredient"))).hasSize(1) },
            { assertThat(idsOf(list(admin(), "entityId" to "1"))).hasSize(3) },
            { assertThat(idsOf(list(admin(), "action" to "unpublish"))).hasSize(1) },
            { assertThat(idsOf(list(admin(), "actorUserId" to "$alice"))).hasSize(2) },
            {
                assertThat(list(admin(), "action" to "nonsense").response.status)
                    .`as`("모르는 action 은 400 — 빈 결과면 오타가 '문제 없음' 이 된다")
                    .isEqualTo(400)
            },
            { assertThat(idsOf(list(admin()))).contains(target) },
        )
    }

    /**
     * RED 10·11 — 기간 필터와 **AND 결합**.
     *
     * OR 를 섞으면 "이 칵테일의 발행 이력"을 물었는데 남의 행이 끼어든다 —
     * 재구성(`NFR-O-05`)이 그 순간 무너진다.
     */
    @Test
    fun `RED10,11 - 기간 필터가 동작하고 조건이 AND 로 묶인다`() {
        val alice = userId("앨리스")
        val old = Instant.now().minusSeconds(7 * 24 * 3600)
        val now = Instant.now()

        val ancient = insertLog("cocktail", 1, AuditAction.PUBLISH, actor = alice, at = old)
        val recent = insertLog("cocktail", 1, AuditAction.UNPUBLISH, actor = alice, at = now)
        insertLog("cocktail", 2, AuditAction.PUBLISH, actor = alice, at = now)

        val since = now.minusSeconds(3600).toString()

        assertAll(
            { assertThat(idsOf(list(admin(), "from" to since))).containsExactlyInAnyOrder(recent, ids[2]) },
            { assertThat(idsOf(list(admin(), "to" to since))).containsExactly(ancient) },
            {
                assertThat(idsOf(list(admin(), "entityId" to "1", "from" to since)))
                    .`as`("둘 다 만족하는 것만")
                    .containsExactly(recent)
            },
        )
    }

    /**
     * RED 12·13 — 최신순 기본 정렬과 페이징.
     *
     * 조사는 항상 "가장 최근에 무슨 일이 있었나"에서 시작한다.
     */
    @Test
    fun `RED12,13 - 최신순이 기본이고 페이징된다`() {
        val actor = userId("앨리스")
        val first = insertLog("cocktail", 1, AuditAction.PUBLISH, actor, at = Instant.now().minusSeconds(300))
        val second = insertLog("cocktail", 1, AuditAction.UNPUBLISH, actor, at = Instant.now().minusSeconds(200))
        val third = insertLog("cocktail", 1, AuditAction.PUBLISH, actor, at = Instant.now().minusSeconds(100))

        val page = bodyOf(list(admin(), "size" to "2"))

        assertAll(
            { assertThat(idsOf(list(admin()))).containsExactly(third, second, first) },
            { assertThat(page["items"].map { it["id"].asLong() }).containsExactly(third, second) },
            { assertThat(page["page"]["totalElements"].asLong()).isEqualTo(3) },
            {
                assertThat(list(admin(), "sort" to "action,asc").response.status)
                    .`as`("허용목록 밖 정렬은 400")
                    .isEqualTo(400)
            },
        )
    }

    /**
     * RED 14 — **엔티티별 이력 조회가 인덱스를 탄다.**
     *
     * SPEC-06 §5 의 `(entity_type, entity_id, created_at)` 이 그 경로다.
     * 이것이 `NFR-O-05` 의 주 질의라 인덱스가 붙어 있고, 나머지 필터는 안 붙는다 —
     * 스펙에 없는 인덱스를 임의로 늘리지 않는다는 판단이 이슈에 적혀 있다.
     */
    @Test
    fun `RED14 - 엔티티 이력 조회가 인덱스를 탄다`() {
        // 행이 적으면 플래너가 인덱스를 **안 쓰는 게 옳다** — 페이지 하나면 훑는 편이 싸다.
        // 그러면 이 테스트는 인덱스가 있든 없든 빨갛고, 아무것도 증명하지 못한다.
        // 술어가 선택적이어야 하므로 엔티티를 넓게 흩뿌린다 (이슈 023 에서 같은 함정을 밟았다).
        val actor = userId("앨리스")
        jdbc.update(
            """
            INSERT INTO audit_log (entity_type, entity_id, action, actor_user_id, created_at)
            SELECT CASE WHEN i % 2 = 0 THEN 'cocktail' ELSE 'ingredient' END,
                   i % 500,
                   'publish',
                   ?,
                   now() - (i || ' seconds')::interval
              FROM generate_series(1, 5000) AS i
            """.trimIndent(),
            actor,
        )
        jdbc.execute("ANALYZE audit_log")

        val plan = jdbc.queryForList(
            """
            EXPLAIN SELECT id FROM audit_log
             WHERE entity_type = 'cocktail' AND entity_id = 3
             ORDER BY created_at DESC
            """.trimIndent(),
        ).joinToString("\n") { it.values.first().toString() }

        assertThat(plan)
            .`as`("SPEC-06 §5 의 인덱스가 안 쓰이면 이력 조회가 풀스캔이다:\n%s", plan)
            .contains("ix_audit_log__entity_at")
    }

    // ── RED 15~20 : 재구성 (NFR-O-05) ─────────────────────────────────────

    /**
     * RED 15·16·17 — **인수 시나리오다.**
     *
     * 로그를 심지 않고 **실제 API 로 발행 → 회수 → 재발행**을 돌린다. 이 파일이 심은
     * 픽스처만 검증하면 "감사가 실제로 남는가"는 확인되지 않는다 — 남기는 쪽(이슈 013·014)과
     * 읽는 쪽(여기)이 같은 모양을 쓰는지가 `NFR-O-05` 의 전부다.
     */
    @Test
    fun `RED15,16,17 - 발행·회수·재발행을 이력만으로 재구성한다`() {
        val editor = session("editor")
        val id = publishableCocktail(editor)

        transition(id, "publish", editor)
        transition(id, "unpublish", editor)
        transition(id, "publish", editor)

        val history = bodyOf(list(admin(), "entityType" to "cocktail", "entityId" to "$id"))["items"]
        val actions = history.map { it["action"].asText() }

        assertAll(
            { assertThat(actions).`as`("RED15 시간순(최신 먼저)").containsExactly("publish", "unpublish", "publish") },
            {
                assertThat(history.map { it["at"].asText() })
                    .`as`("RED15 시각이 내림차순")
                    .isSortedAccordingTo(reverseOrder())
            },
            {
                val unpublish = history.single { it["action"].asText() == "unpublish" }
                assertThat(unpublish["before"]["status"].asText()).`as`("RED16").isEqualTo("published")
                assertThat(unpublish["after"]["status"].asText()).`as`("RED16").isEqualTo("draft")
            },
            {
                assertThat(history.map { it["actor"]["userId"].asLong() })
                    .`as`("RED17·18 누가 했는지 전부 남는다")
                    .containsOnly(userIdOf(editor))
            },
        )
    }

    /**
     * RED 19·20 — **탈퇴해도 이력은 남고, 그 상태가 구분된다.**
     *
     * `audit_log.actor_user_id` 에 FK 가 없다 (이슈 014) — SPEC-08 §5.3 이 "탈퇴해도 유지" 와
     * "user 행 즉시 삭제" 를 둘 다 요구해서다. 그 결과 **id 는 남는데 조인이 안 되는 행**이 생긴다.
     *
     * 조회 쿼리가 `LEFT JOIN` 인 이유가 이것이다. `INNER` 로 묶으면 그 행이 조회에서 사라진다 —
     * **이력이 남아 있는데 안 보이는 것은 없는 것보다 나쁘다.** 발행 이력은 법적 근거다.
     */
    @Test
    fun `RED19,20 - 탈퇴한 행위자도 남고 표시가 구분된다`() {
        val staying = userId("남은사람")
        val leaving = userId("떠난사람")
        insertLog("cocktail", 1, AuditAction.PUBLISH, actor = staying)
        insertLog("cocktail", 2, AuditAction.PUBLISH, actor = leaving)
        insertLog("cocktail", 3, AuditAction.PUBLISH, actor = null) // 배치·마이그레이션

        jdbc.update("""DELETE FROM "user" WHERE id = ?""", leaving)

        val items = bodyOf(list(admin()))["items"].associateBy { it["entityId"].asLong() }

        assertAll(
            { assertThat(items).`as`("RED19 탈퇴해도 행이 사라지지 않는다").hasSize(3) },
            { assertThat(items[2]!!["actor"]["userId"].asLong()).`as`("RED19 id 는 남는다").isEqualTo(leaving) },
            { assertThat(items[2]!!["actor"]["displayName"].isNull).isTrue() },
            { assertThat(items[2]!!["actor"]["withdrawn"].asBoolean()).`as`("RED20").isTrue() },
            { assertThat(items[1]!!["actor"]["displayName"].asText()).isEqualTo("남은사람") },
            { assertThat(items[1]!!["actor"]["withdrawn"].asBoolean()).isFalse() },
            {
                assertThat(items[3]!!["actor"].isNull)
                    .`as`("사람이 없는 행위는 actor 자체가 없다 — 거짓 주체를 지어내지 않는다")
                    .isTrue()
            },
        )
    }

    // ── RED 21~23 : 감사 대상 (PRIN-T08) ──────────────────────────────────

    /**
     * RED 22·23 — **Phase 1b·2 값으로 걸러도 에러가 아니다.**
     *
     * `tier_change` · `rank_change` · `verify` 는 `PRIN-T08` 이 열거한 4종 중 아직 안 온 것들이다.
     * 지금 정의해 두는 이유는 이슈 014 와 같다 — 나중에 열거를 늘리면 이 목록을 읽는 쪽이
     * 그때 깨진다. 결과가 빈 것과 필터가 없는 것은 다르다.
     */
    @ParameterizedTest
    @ValueSource(strings = ["tier_change", "rank_change", "verify"])
    fun `RED22,23 - Phase 1b·2 action 으로 조회해도 빈 결과다`(slug: String) {
        insertLog("cocktail", 1, AuditAction.PUBLISH, actor = userId("앨리스"))

        val result = list(admin(), "action" to slug)

        assertAll(
            { assertThat(AuditAction.entries.map { it.slug }).contains(slug) },
            { assertThat(result.response.status).isEqualTo(200) },
            { assertThat(bodyOf(result)["items"]).isEmpty() },
        )
    }

    // ── RED 24~25 : slug 감시 (NFR-D-04) ──────────────────────────────────

    /**
     * RED 24·25 — **`NFR-D-04` 의 "0건" 측정이 이 쿼리 하나로 된다.**
     *
     * 발행 후 슬러그는 못 바꾼다 (`INV-COCKTAIL-05`). 그런데도 거부된 시도를 남기는 이유가
     * 여기 있다 — 기록이 없으면 "발견 시 즉시 조사"할 것이 없다.
     *
     * 정상 상태는 `totalElements = 0` 이고, 0 이 아닌 순간이 조사 시작점이다.
     */
    @Test
    fun `RED24,25 - slug 변경 시도를 조회하고 0건을 확인한다`() {
        val clean = bodyOf(list(admin(), "action" to "slug_change_attempt"))

        assertThat(clean["page"]["totalElements"].asLong())
            .`as`("NFR-D-04 — 정상은 0건이다")
            .isZero()

        // 이슈 014 의 `RejectedAttemptRecorder` 가 남기는 모양.
        insertLog("cocktail", 7, AuditAction.SLUG_CHANGE_ATTEMPT, actor = userId("앨리스"))

        val dirty = bodyOf(list(admin(), "action" to "slug_change_attempt"))

        assertAll(
            { assertThat(dirty["page"]["totalElements"].asLong()).`as`("0 이 아니면 조사한다").isEqualTo(1) },
            { assertThat(dirty["items"].single()["entityId"].asLong()).isEqualTo(7) },
        )
    }

    // ── RED 26~28 : 불변 (PRIN-T08) ───────────────────────────────────────

    /**
     * RED 26·27·28 — **고칠 수 있는 이력은 이력이 아니다.**
     *
     * API 부재만으로는 부족하다. 누군가 나중에 엔드포인트를 만들 수 있으니
     * **DB 권한까지 확인**한다 (이슈 014 가 `REVOKE UPDATE, DELETE` 했다).
     * 두 층이 다 막혀 있어야 `PRIN-T08` 의 "다툼의 근거"가 성립한다.
     */
    @Test
    fun `RED26,27,28 - 감사 로그를 고치거나 지울 수 없다`() {
        val id = insertLog("cocktail", 1, AuditAction.PUBLISH, actor = userId("앨리스"))
        val admin = admin()

        assertAll(
            {
                assertThat(mvc.patch("$ADMIN/$id") { with(csrf()); session = admin!! }.andReturn().response.status)
                    .`as`("RED26 수정 경로가 없다").isNotIn(200, 204)
            },
            {
                assertThat(mvc.delete("$ADMIN/$id") { with(csrf()); session = admin!! }.andReturn().response.status)
                    .`as`("RED27 삭제 경로가 없다").isNotIn(200, 204)
            },
            {
                // **앱 커넥션으로 친다.** `jdbc` 는 컨테이너 슈퍼유저라 REVOKE 가 있든 없든
                // 전부 통과해 버린다 — 규칙이 살아 있는지 아무것도 증명하지 못한다
                // (`PostgresSupport` 가 이 함정을 KDoc 에 적어 뒀다).
                //
                // `app_test` 로그인 계정은 `PostgresSupport.flyway` 를 건드릴 때 만들어진다.
                // 이 파일은 스프링 Flyway 로 마이그레이션하므로 그 지점을 지나지 않는다 —
                // 여기서 한 번 건드린다 (lazy 라 두 번 돌지 않는다).
                PostgresSupport.flyway
                PostgresSupport.appConnection().use { conn ->
                    assertAll(
                        {
                            assertThatThrownBy {
                                conn.createStatement().use { it.execute("UPDATE audit_log SET action = 'archive'") }
                            }.`as`("RED28 DB 가 UPDATE 를 거부한다").hasMessageContaining("permission denied")
                        },
                        {
                            assertThatThrownBy {
                                conn.createStatement().use { it.execute("DELETE FROM audit_log") }
                            }.`as`("RED28 DB 가 DELETE 를 거부한다").hasMessageContaining("permission denied")
                        },
                        {
                            conn.createStatement().use { st ->
                                st.executeQuery("SELECT count(*) FROM audit_log WHERE id = $id").use { rs ->
                                    rs.next()
                                    assertThat(rs.getInt(1)).`as`("읽기는 된다 — 조회 API 가 이 권한으로 돈다").isEqualTo(1)
                                }
                            }
                        },
                    )
                }
            },
        )
    }

    // ── RED 29~31 : 개인정보와 규약 ───────────────────────────────────────

    /**
     * RED 29 — **행위자 표시명까지다.**
     *
     * SPEC-08 §5.4 의 정신이 "필요한 것만"이다. 감사에 필요한 것은 "누가"를 사람이
     * 알아볼 수 있는 정도이고, 그건 표시명이면 충분하다 — 이메일·제공자 UID 는
     * 조사에 쓸모가 없으면서 유출되면 손해만 크다.
     */
    @Test
    fun `RED29 - 응답에 불필요한 개인정보가 없다`() {
        val actor = userId("앨리스")
        jdbc.update("""UPDATE "user" SET email = 'alice@example.com' WHERE id = ?""", actor)
        insertLog("cocktail", 1, AuditAction.PUBLISH, actor = actor)

        val raw = list(admin()).response.getContentAsString(Charsets.UTF_8)

        assertAll(
            { assertThat(raw).`as`("이메일이 새어 나간다").doesNotContain("alice@example.com") },
            { assertThat(raw).doesNotContain("providerUid").doesNotContain("provider_uid") },
            { assertThat(raw).`as`("표시명은 있어야 누가 했는지 안다").contains("앨리스") },
        )
    }

    /** RED 30 — 감사 응답을 캐시하지 않는다. 중간 캐시에 남을 성격의 데이터가 아니다. */
    @Test
    fun `RED30 - 캐시 헤더가 없다`() {
        insertLog("cocktail", 1, AuditAction.PUBLISH, actor = userId("앨리스"))
        val response = list(admin()).response

        assertAll(
            { assertThat(response.getHeader(HttpHeaders.ETAG)).isNull() },
            {
                assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL).orEmpty())
                    .doesNotContain("public", "max-age=60")
            },
        )
    }

    /** RED 31 — 어드민 경로는 `id` 를 쓴다. 감사 필터의 `entityId` 도 `slug` 가 아니다. */
    @Test
    fun `RED31 - 어드민 경로라 id 를 쓴다`() {
        insertLog("cocktail", 1, AuditAction.PUBLISH, actor = userId("앨리스"))

        assertAll(
            { assertThat(idsOf(list(admin(), "entityId" to "1"))).hasSize(1) },
            {
                assertThat(list(admin(), "entityId" to "some-slug").response.status)
                    .`as`("slug 는 안 받는다").isEqualTo(400)
            },
        )
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────

    private var seq = 0
    private val ids = mutableListOf<Long>()

    private fun list(login: MockHttpSession?, vararg params: Pair<String, String>) =
        mvc.get(ADMIN) {
            login?.let { this.session = it }
            params.forEach { (name, value) -> param(name, value) }
        }.andReturn()

    private fun admin() = session("admin")

    private fun insertLog(
        entityType: String,
        entityId: Long,
        action: AuditAction,
        actor: Long?,
        at: Instant = Instant.now(),
        before: String? = null,
        after: String? = null,
    ): Long = jdbc.queryForObject(
        """
        INSERT INTO audit_log (entity_type, entity_id, action, actor_user_id, before, after, created_at)
        VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?) RETURNING id
        """.trimIndent(),
        Long::class.java,
        entityType, entityId, action.slug, actor, before, after, Timestamp.from(at),
    )!!.also { ids += it }

    /** 게이트를 전부 통과하는 `draft`. 발행 전이를 실제로 돌려야 감사가 남는다. */
    private fun publishableCocktail(login: MockHttpSession?): Long {
        val n = seq++
        val created = mvc.post("${ApiPaths.ADMIN}/cocktails") {
            with(csrf())
            session = login!!
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = json.writeValueAsString(
                mapOf(
                    "slug" to "audit-$n",
                    "nameKo" to "감사", "nameEn" to "Audit", "summary" to "요약",
                    "baseSpirit" to "gin", "stylePrimary" to "highball", "method" to "build",
                    "sweetness" to "dry", "glassType" to "하이볼 글라스",
                    "styles" to listOf("highball"), "aromaTags" to listOf("citrus"),
                    "tastingNote" to "쌉싸름한 향에 단맛이 얹힌다", "isClassic" to false,
                ),
            )
        }.andReturn()
        val id = json.readTree(created.response.getContentAsString(Charsets.UTF_8))["id"].asLong()

        val ingredientId = jdbc.queryForObject(
            """INSERT INTO ingredient (slug, name_ko, name_en, category, domestic_availability, is_approved)
               VALUES ('gin-${seq++}', '진', 'gin', 'spirit', 'common', true) RETURNING id""",
            Long::class.java,
        )!!
        val recipeId = jdbc.queryForObject(
            "INSERT INTO recipe (cocktail_id, version_type) VALUES ($id, 'standard') RETURNING id",
            Long::class.java,
        )!!
        jdbc.execute(
            "INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit) " +
                "VALUES ($recipeId, $ingredientId, 1, 45, 'ml')",
        )
        jdbc.execute("INSERT INTO recipe_step (recipe_id, step_no, text) VALUES ($recipeId, 1, '얼음을 채운다')")
        return id
    }

    private fun transition(id: Long, action: String, login: MockHttpSession?) {
        val result = mvc.post("${ApiPaths.ADMIN}/cocktails/$id/$action") {
            with(csrf()); session = login!!
        }.andReturn()
        check(result.response.status == 200) {
            "$action 이 실패했다 (${result.response.status}): ${result.response.getContentAsString(Charsets.UTF_8)}"
        }
    }

    private fun userId(displayName: String): Long = jdbc.queryForObject(
        """INSERT INTO "user" (provider, provider_uid, display_name)
           VALUES ('kakao', 'uid-${seq++}-${System.nanoTime()}', ?) RETURNING id""",
        Long::class.java,
        displayName,
    )!!

    private fun session(role: String): MockHttpSession? {
        val id = userId("테스터")
        jdbc.update("INSERT INTO user_role (user_id, role) VALUES (?, ?)", id, role)

        return MockHttpSession().apply {
            setAttribute(AbsoluteExpiryFilter.USER_ID, id)
            setAttribute(SessionPolicy.ISSUED_AT, Instant.now())
            setAttribute(SessionPolicy.ISSUED_ROLES, setOf(role))
        }
    }

    private fun userIdOf(session: MockHttpSession?) =
        session!!.getAttribute(AbsoluteExpiryFilter.USER_ID) as Long

    private fun bodyOf(result: MvcResult): JsonNode =
        json.readTree(result.response.getContentAsString(Charsets.UTF_8))

    private fun idsOf(result: MvcResult) = bodyOf(result)["items"].map { it["id"].asLong() }

    private fun reverseOrder() = Comparator<String> { a, b -> b.compareTo(a) }

    companion object {
        private val ADMIN = "${ApiPaths.ADMIN}/audit-logs"

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

            registry.add("spring.autoconfigure.exclude") {
                "org.springframework.boot.autoconfigure.session.SessionAutoConfiguration"
            }
        }
    }
}
