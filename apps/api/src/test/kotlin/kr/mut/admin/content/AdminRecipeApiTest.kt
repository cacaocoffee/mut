package kr.mut.admin.content

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
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.Instant

/**
 * ISSUE-051 — 어드민 레시피 편집 ([G-38](../../../../../../../docs/prd/GAPS.md) · `NFR-O-01`).
 *
 * ## 이 파일이 증명하려는 것은 하나다
 *
 * > 에디터가 개발자 없이 **신규 1건을 어드민만으로 발행**한다 (`NFR-O-01`)
 *
 * 이슈 025 의 테스트는 레시피를 **SQL 로 직접 넣어** 발행까지 갔다. 그 SQL 이 곧
 * "어드민만으로는 안 된다" 는 증거였다 — 운영에서는 그 자리에 개발자가 선다.
 * 여기서는 [인수 시나리오][`NFR-O-01 - 어드민만으로 신규 1건을 발행한다`] 가 **HTTP 만으로**
 * 처음부터 끝까지 간다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminRecipeApiTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var json: ObjectMapper
    @Autowired private lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun clear() {
        jdbc.execute("TRUNCATE cocktail, ingredient, audit_log, search_document CASCADE")
        jdbc.execute("""TRUNCATE user_role, "user" CASCADE""")
    }

    /**
     * **이것이 이 이슈의 전부다.** 어드민 HTTP 만으로 생성 → 재료 → 레시피 → 발행까지 간다.
     *
     * SQL 이 한 줄도 없다는 것이 요점이다 (세션·역할 픽스처만 예외 — 로그인은 이슈 030 이다).
     */
    @Test
    fun `NFR-O-01 - 어드민만으로 신규 1건을 발행한다`() {
        val editor = session("editor")
        val admin = session("admin")

        val cocktailId = idOf(createCocktail(editor))
        // 재료 마스터도 어드민으로 만든다. 승인은 admin 이다 (SPEC-08 §2)
        val ginId = idOf(createIngredient(editor, "gin"))
        mvc.post("${ApiPaths.ADMIN}/ingredients/$ginId/approve") { with(csrf()); session = admin!! }

        val saved = saveRecipe(
            cocktailId,
            editor,
            ingredients = listOf(mapOf("ingredientId" to ginId, "amount" to 45, "unit" to "ml")),
            steps = listOf(mapOf("text" to "얼음을 채우고 젓는다")),
        )

        val published = mvc.post("${ApiPaths.ADMIN}/cocktails/$cocktailId/publish") {
            with(csrf()); session = editor!!
        }.andReturn()

        assertAll(
            { assertThat(saved.response.status).`as`("레시피 저장").isEqualTo(200) },
            { assertThat(published.response.status).`as`("발행").isEqualTo(200) },
            { assertThat(statusOf(cocktailId)).isEqualTo("published") },
        )
    }

    /** 저장은 통째로 덮는다. 두 번 저장해도 표준은 **하나**다 (`INV-COCKTAIL-07`). */
    @Test
    fun `두 번 저장해도 표준 레시피는 하나다`() {
        val editor = session("editor")
        val cocktailId = idOf(createCocktail(editor))
        val ginId = idOf(createIngredient(editor, "gin"))

        saveRecipe(cocktailId, editor, listOf(mapOf("ingredientId" to ginId, "amount" to 45, "unit" to "ml")))
        val second = saveRecipe(
            cocktailId,
            editor,
            listOf(mapOf("ingredientId" to ginId, "amount" to 60, "unit" to "ml")),
            steps = listOf(mapOf("text" to "섞는다"), mapOf("text" to "따른다")),
        )

        val body = json.readTree(second.response.getContentAsString(Charsets.UTF_8))

        assertAll(
            {
                assertThat(count("SELECT count(*) FROM recipe WHERE cocktail_id = $cocktailId"))
                    .`as`("표준이 둘이 됐다").isEqualTo(1)
            },
            { assertThat(body["ingredients"][0]["amount"].asDouble()).isEqualTo(60.0) },
            // 스텝 번호는 1부터 다시 매겨진다 — 중간을 지워도 구멍이 남지 않는다
            { assertThat(body["steps"].map { it["stepNo"].asInt() }).containsExactly(1, 2) },
        )
    }

    /**
     * 저장 직후 `abv_calculated` 가 채워진다 (이슈 011).
     *
     * 비동기로 미루면 에디터가 저장하고 화면을 봤을 때 도수가 비어 있다.
     */
    @Test
    fun `저장하면 도수가 다시 계산된다`() {
        val editor = session("editor")
        val cocktailId = idOf(createCocktail(editor))
        val ginId = idOf(createIngredient(editor, "gin", abv = 40))

        val saved = saveRecipe(
            cocktailId,
            editor,
            listOf(mapOf("ingredientId" to ginId, "amount" to 60, "unit" to "ml")),
            steps = listOf(mapOf("text" to "젓는다")),
        )
        val body = json.readTree(saved.response.getContentAsString(Charsets.UTF_8))

        assertThat(body["abvCalculated"].isNull).`as`("도수가 비어 있다").isFalse()
    }

    /**
     * 없는 재료 id 는 **400** 이다.
     *
     * 마스터 참조 자체는 `GATE-COCKTAIL-04` 가 발행 때 보지만, 없는 id 는 게이트가 아니라
     * 오타다. FK 로 터뜨리면 500 이 되고 어느 줄이 틀렸는지 알 수 없다.
     */
    @Test
    fun `없는 재료는 400 이고 어느 것인지 알려 준다`() {
        val editor = session("editor")
        val cocktailId = idOf(createCocktail(editor))

        val result = saveRecipe(cocktailId, editor, listOf(mapOf("ingredientId" to 999_999)))

        assertAll(
            { assertThat(result.response.status).isEqualTo(400) },
            {
                assertThat(result.response.getContentAsString(Charsets.UTF_8))
                    .`as`("어느 재료인지 안 알려 준다").contains("999999")
            },
        )
    }

    /** 레시피는 칵테일 편집이다 — `editor` 가 쓴다. 재료 **마스터** 승인만 `admin` 이다. */
    @ParameterizedTest
    @CsvSource("editor, 200", "admin, 200", "member, 403")
    fun `레시피 저장은 editor 이상만 된다`(role: String, expected: Int) {
        val cocktailId = idOf(createCocktail(session("editor")))

        assertThat(saveRecipe(cocktailId, session(role)).response.status).isEqualTo(expected)
    }

    /** 아직 안 쓴 레시피는 `exists: false` 다. 404 는 **없는 칵테일**의 몫이다. */
    @Test
    fun `안 쓴 레시피는 빈 것으로 답한다`() {
        val editor = session("editor")
        val cocktailId = idOf(createCocktail(editor))

        val empty = json.readTree(
            mvc.get("${ApiPaths.ADMIN}/cocktails/$cocktailId/recipe") { session = editor!! }
                .andReturn().response.getContentAsString(Charsets.UTF_8),
        )

        assertAll(
            { assertThat(empty["exists"].asBoolean()).isFalse() },
            { assertThat(empty["ingredients"]).isEmpty() },
            {
                assertThat(
                    mvc.get("${ApiPaths.ADMIN}/cocktails/999999/recipe") { session = editor!! }
                        .andReturn().response.status,
                ).`as`("없는 칵테일도 200 을 준다").isEqualTo(404)
            },
        )
    }

    /**
     * 재료 고르기 목록에는 **미승인도 나온다** (DECISIONS §1.1).
     *
     * 공개 사전은 승인분만이라 방금 만든 재료를 레시피에 넣을 수가 없다. 승인을 기다리며
     * 초안을 멈추게 하지 않는 것이 이 목록의 존재 이유다.
     */
    @Test
    fun `재료 고르기 목록은 미승인도 보여 준다`() {
        val editor = session("editor")
        createIngredient(editor, "campari")

        val found = json.readTree(
            mvc.get("${ApiPaths.ADMIN}/ingredients?q=campari") { session = editor!! }
                .andReturn().response.getContentAsString(Charsets.UTF_8),
        )

        assertAll(
            { assertThat(found).hasSize(1) },
            { assertThat(found[0]["isApproved"].asBoolean()).`as`("승인된 것만 나온다").isFalse() },
        )
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────

    private var seq = 0

    private fun createCocktail(login: MockHttpSession?) = mvc.post("${ApiPaths.ADMIN}/cocktails") {
        with(csrf())
        login?.let { this.session = it }
        contentType = MediaType.APPLICATION_JSON
        content = json.writeValueAsString(
            mapOf(
                "slug" to "recipe-test-${seq++}",
                "nameKo" to "테스트", "nameEn" to "Test", "summary" to "요약",
                "baseSpirit" to "gin", "stylePrimary" to "highball", "method" to "build",
                "sweetness" to "dry", "glassType" to "하이볼 글라스",
                "styles" to listOf("highball"), "aromaTags" to listOf("citrus"),
                "tastingNote" to "쌉싸름한 향에 단맛이 얹힌다",
            ),
        )
    }.andReturn()

    private fun createIngredient(login: MockHttpSession?, name: String, abv: Int? = null) =
        mvc.post("${ApiPaths.ADMIN}/ingredients") {
            with(csrf())
            login?.let { this.session = it }
            contentType = MediaType.APPLICATION_JSON
            content = json.writeValueAsString(
                mapOf(
                    "slug" to "$name-${seq++}",
                    "nameKo" to name, "nameEn" to name,
                    "category" to "spirit", "domesticAvailability" to "common",
                    "aliases" to emptyList<String>(),
                    "abv" to abv,
                ),
            )
        }.andReturn()

    private fun saveRecipe(
        cocktailId: Long,
        login: MockHttpSession?,
        ingredients: List<Map<String, Any?>> = emptyList(),
        steps: List<Map<String, Any?>> = listOf(mapOf("text" to "젓는다")),
    ) = mvc.put("${ApiPaths.ADMIN}/cocktails/$cocktailId/recipe") {
        with(csrf())
        login?.let { this.session = it }
        contentType = MediaType.APPLICATION_JSON
        content = json.writeValueAsString(
            mapOf("servingCount" to 1, "ingredients" to ingredients, "steps" to steps),
        )
    }.andReturn()

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

    private fun idOf(result: org.springframework.test.web.servlet.MvcResult): Long {
        val body = result.response.getContentAsString(Charsets.UTF_8)
        return json.readTree(body)["id"]?.asLong()
            ?: error("생성 응답에 id 가 없다 (status=${result.response.status}): $body")
    }

    private fun statusOf(id: Long) =
        jdbc.queryForObject("SELECT status FROM cocktail WHERE id = $id", String::class.java)

    private fun count(sql: String) = jdbc.queryForObject(sql, Long::class.java)!!

    companion object {
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
            registry.add("spring.autoconfigure.exclude") {
                "org.springframework.boot.autoconfigure.session.SessionAutoConfiguration"
            }
        }
    }
}
