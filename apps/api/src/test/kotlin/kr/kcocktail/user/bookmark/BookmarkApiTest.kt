package kr.kcocktail.user.bookmark

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kr.kcocktail.common.security.session.AbsoluteExpiryFilter
import kr.kcocktail.common.security.session.SessionPolicy
import kr.kcocktail.common.web.ApiPaths
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
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

/**
 * ISSUE-031 — 북마크 · 컬렉션 · 공유 링크 (`FR-USER-004` · `R-F5-2`).
 *
 * ## 이 이슈의 대가는 dangling 참조다
 *
 * `bookmark.target_id` 에 FK 가 없다 — 다형 참조라 걸 수 없고, 타입별로 쪼개면
 * **한 컬렉션이 세 종류를 섞어 담는다**(`R-F5-2`)를 못 한다.
 *
 * 그래서 무결성을 앱이 지고, 이 파일이 그 계약을 고정한다:
 * 저장할 때 발행 여부를 보고(RED 5·28), 조회할 때 사라진 것을 거른다(RED 25·26·27).
 *
 * ## 남의 것은 전부 404 다
 *
 * SPEC-08 §2 의 `◐`. 403 으로 답하면 순차 id 를 훑어 **누가 무엇을 저장했는지 셀 수 있다.**
 */
@SpringBootTest
@AutoConfigureMockMvc
class BookmarkApiTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var json: ObjectMapper
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var transactions: TransactionTemplate

    @BeforeEach
    fun clear() {
        jdbc.execute("TRUNCATE bookmark, bookmark_collection CASCADE")
        jdbc.execute("TRUNCATE cocktail, search_document CASCADE")
        jdbc.execute("""TRUNCATE user_role, "user" CASCADE""")
    }

    // ── RED 1~11 : 북마크 ─────────────────────────────────────────────────

    @Test
    fun `RED1,3 - 로그인 사용자가 targetSlug 로 북마크를 추가한다`() {
        publishedCocktail("negroni")
        val me = login()

        val result = add(me, "cocktail", "negroni")

        assertAll(
            { assertThat(result.response.status).isEqualTo(200) },
            { assertThat(bodyOf(result)["targetSlug"].asText()).isEqualTo("negroni") },
            { assertThat(bodyOf(result)["nameKo"].asText()).isNotBlank() },
            { assertThat(count("SELECT count(*) FROM bookmark")).isEqualTo(1) },
        )
    }

    /** RED 2 — 비로그인은 401 이다. SPEC-08 §2 에서 비로그인은 `—` 다. */
    @Test
    fun `RED2 - 비로그인은 401 이다`() {
        publishedCocktail("negroni")

        assertAll(
            { assertThat(add(null, "cocktail", "negroni").response.status).isEqualTo(401) },
            { assertThat(mvc.get("$ME/bookmarks").andReturn().response.status).isEqualTo(401) },
        )
    }

    @Test
    fun `RED4 - 없는 slug 는 404 다`() {
        assertThat(add(login(), "cocktail", "no-such-cocktail").response.status).isEqualTo(404)
    }

    /**
     * RED 5 — **발행되지 않은 대상은 저장할 수 없다.**
     *
     * `draft` 를 저장할 수 있으면 슬러그를 찔러 보는 것만으로 발행 전 콘텐츠의 존재가
     * 새어 나간다. 공개 조회가 404 인 것과 같은 이유다 (SPEC-07 §5).
     */
    @Test
    fun `RED5 - 미발행 대상은 404 다`() {
        draftCocktail("secret-recipe")

        assertAll(
            { assertThat(add(login(), "cocktail", "secret-recipe").response.status).isEqualTo(404) },
            { assertThat(count("SELECT count(*) FROM bookmark")).isZero() },
        )
    }

    /**
     * RED 6 — **중복은 멱등이다** (409 가 아니다).
     *
     * 저장 버튼은 네트워크가 느리면 반드시 두 번 눌린다. 결과 상태가 같으므로
     * 멱등이 사실에 맞는 응답이고, 사용자에게 아무 일도 안 일어난 것처럼 보이는 편이 맞다.
     */
    @Test
    fun `RED6 - 중복 북마크는 멱등이다`() {
        publishedCocktail("negroni")
        val me = login()

        val first = add(me, "cocktail", "negroni")
        val second = add(me, "cocktail", "negroni")

        assertAll(
            { assertThat(second.response.status).isEqualTo(200) },
            { assertThat(bodyOf(second)["id"].asLong()).isEqualTo(bodyOf(first)["id"].asLong()) },
            { assertThat(count("SELECT count(*) FROM bookmark")).isEqualTo(1) },
        )
    }

    /** RED 7 — 3종 밖은 400 이다. CHECK 제약과 같은 목록을 앱이 먼저 거른다. */
    @Test
    fun `RED7 - target_type 3종만 허용된다`() {
        publishedCocktail("negroni")
        val me = login()

        assertAll(
            { assertThat(add(me, "playlist", "negroni").response.status).isEqualTo(400) },
            {
                assertThat(BookmarkTarget.entries.map { it.code })
                    .containsExactlyInAnyOrder("cocktail", "bar", "article")
            },
        )
    }

    /**
     * RED 8 — **`bar` · `article` 은 열거에 있고 대상이 없다.**
     *
     * 404 인 것이 맞다 — "아직 지원 안 함"(501)이 아니라 **그런 것이 없다**.
     * 열거를 지금 정의해 두는 이유는 나중에 늘리면 클라이언트가 그때 깨져서다.
     */
    @ParameterizedTest
    @ValueSource(strings = ["bar", "article"])
    fun `RED8 - Phase 1a 에는 cocktail 만 실제로 추가된다`(type: String) {
        publishedCocktail("negroni")

        assertThat(add(login(), type, "negroni").response.status).isEqualTo(404)
    }

    @Test
    fun `RED9 - 북마크를 삭제한다`() {
        publishedCocktail("negroni")
        val me = login()
        val id = bodyOf(add(me, "cocktail", "negroni"))["id"].asLong()

        val result = mvc.delete("$ME/bookmarks/$id") { with(csrf()); session = me!! }.andReturn()

        assertAll(
            { assertThat(result.response.status).isEqualTo(204) },
            { assertThat(count("SELECT count(*) FROM bookmark")).isZero() },
        )
    }

    /**
     * RED 10·11 — **남의 것은 404 다.**
     *
     * 403 으로 답하면 "그 id 는 존재한다" 가 새어 나가고, 순차 id 를 훑어
     * 누가 무엇을 저장했는지 셀 수 있다 (`Action.OWN_BOOKMARK` 가 `HIDE` 인 이유).
     */
    @Test
    fun `RED10,11 - 타인의 북마크는 삭제도 조회도 못 한다`() {
        publishedCocktail("negroni")
        val owner = login()
        val stranger = login()
        val id = bodyOf(add(owner, "cocktail", "negroni"))["id"].asLong()

        val deletion = mvc.delete("$ME/bookmarks/$id") { with(csrf()); session = stranger!! }.andReturn()
        val theirList = itemsOf(list(stranger))

        assertAll(
            { assertThat(deletion.response.status).`as`("RED10 — 403 이 아니다").isEqualTo(404) },
            { assertThat(count("SELECT count(*) FROM bookmark")).`as`("지워지지 않았다").isEqualTo(1) },
            { assertThat(theirList).`as`("RED11 남의 목록은 안 보인다").isEmpty() },
            { assertThat(itemsOf(list(owner))).hasSize(1) },
        )
    }

    // ── RED 12~17 : 컬렉션 ───────────────────────────────────────────────

    @Test
    fun `RED12,13,18 - 컬렉션을 만들고 북마크를 담는다`() {
        publishedCocktail("negroni")
        val me = login()
        val collection = bodyOf(createCollection(me, "여름에 마실 것"))

        val bookmark = bodyOf(add(me, "cocktail", "negroni", collection["id"].asLong()))

        assertAll(
            { assertThat(collection["name"].asText()).isEqualTo("여름에 마실 것") },
            { assertThat(collection["shareToken"].asText()).`as`("RED18 생성 시점에 발급").isNotBlank() },
            { assertThat(bookmark["collectionId"].asLong()).isEqualTo(collection["id"].asLong()) },
        )
    }

    /**
     * RED 14 — `collection_id` 가 `null` 이면 기본 컬렉션이다 (SPEC-06 §3.5).
     *
     * 기본 컬렉션 행을 따로 만들지 않는다 — 가입할 때마다 심어야 하고, 그 행이 없는
     * 계정이 생기면 조회가 `null` 을 만난다. 어차피 `null` 을 다뤄야 하면 그것이 기본값이다.
     */
    @Test
    fun `RED14 - collectionId 가 없으면 기본 컬렉션이다`() {
        publishedCocktail("negroni")
        publishedCocktail("martini")
        val me = login()
        val named = bodyOf(createCollection(me, "이름 있는 것"))["id"].asLong()

        add(me, "cocktail", "negroni")
        add(me, "cocktail", "martini", named)

        assertAll(
            { assertThat(bodyOf(list(me, collectionId = 0))["0"]).isNull() }, // 형태만 확인
            {
                assertThat(itemsOf(list(me, collectionId = 0)).map { it["targetSlug"].asText() })
                    .`as`("0 은 기본 컬렉션이다")
                    .containsExactly("negroni")
            },
            {
                assertThat(itemsOf(list(me, collectionId = named)).map { it["targetSlug"].asText() })
                    .containsExactly("martini")
            },
            { assertThat(itemsOf(list(me))).`as`("생략하면 전체").hasSize(2) },
            { assertThat(count("SELECT count(*) FROM bookmark WHERE collection_id IS NULL")).isEqualTo(1) },
        )
    }

    /**
     * RED 15 — **한 컬렉션에 여러 타입이 섞인다.**
     *
     * `R-F5-2` 의 요구이자 타입별 테이블로 쪼개지 않은 이유다. Phase 1a 에는 `cocktail`
     * 하나뿐이라 API 로는 섞을 수 없어, **스키마가 섞인 것을 받아 주는지**를 본다 —
     * 1b 에서 `bar` 가 붙을 때 마이그레이션이 필요하면 그때는 늦다.
     */
    @Test
    fun `RED15 - 한 컬렉션에 세 타입이 섞인다`() {
        val me = login()
        val userId = userIdOf(me)
        val collectionId = bodyOf(createCollection(me, "섞어 담기"))["id"].asLong()

        listOf("cocktail", "bar", "article").forEachIndexed { index, type ->
            jdbc.update(
                "INSERT INTO bookmark (user_id, collection_id, target_type, target_id) VALUES (?, ?, ?, ?)",
                userId, collectionId, type, index + 1L,
            )
        }

        assertThat(
            jdbc.queryForList(
                "SELECT target_type FROM bookmark WHERE collection_id = $collectionId",
                String::class.java,
            ),
        ).containsExactlyInAnyOrder("cocktail", "bar", "article")
    }

    /** RED 16 — 남의 컬렉션에는 담을 수 없다. 존재 여부도 알려 주지 않는다. */
    @Test
    fun `RED16 - 타인의 컬렉션에 담을 수 없다`() {
        publishedCocktail("negroni")
        val owner = login()
        val stranger = login()
        val theirs = bodyOf(createCollection(owner, "남의 것"))["id"].asLong()

        assertAll(
            { assertThat(add(stranger, "cocktail", "negroni", theirs).response.status).isEqualTo(404) },
            { assertThat(count("SELECT count(*) FROM bookmark")).isZero() },
        )
    }

    /** RED 17 — 이름이 필수다. 공백만 있는 이름은 없는 것으로 친다. */
    @Test
    fun `RED17 - 컬렉션 이름이 필수다`() {
        val me = login()

        assertAll(
            { assertThat(createCollection(me, null).response.status).isEqualTo(400) },
            { assertThat(createCollection(me, "   ").response.status).isEqualTo(400) },
            { assertThat(count("SELECT count(*) FROM bookmark_collection")).isZero() },
        )
    }

    // ── RED 18~25 : 공유 링크 ────────────────────────────────────────────

    /**
     * RED 19 — **비로그인도 조회한다.** 그것이 공유의 뜻이다 (SPEC-07 §2.5 권한 `—`).
     */
    @Test
    fun `RED19 - 공유 링크는 비로그인도 조회 가능하다`() {
        publishedCocktail("negroni")
        val me = login()
        val collection = bodyOf(createCollection(me, "공유할 것"))
        add(me, "cocktail", "negroni", collection["id"].asLong())

        val shared = bodyOf(shared(collection["shareToken"].asText()))

        assertAll(
            { assertThat(shared["name"].asText()).isEqualTo("공유할 것") },
            { assertThat(shared["items"]).hasSize(1) },
            { assertThat(shared["items"][0]["targetSlug"].asText()).isEqualTo("negroni") },
        )
    }

    /**
     * RED 20 — **토큰이 추측 불가능하다.**
     *
     * 순차 id 를 쓰면 1 부터 훑어 남의 컬렉션을 전부 읽을 수 있다. 공유 링크는
     * 아는 사람만 여는 것이지 공개된 것이 아니다.
     */
    @Test
    fun `RED20 - 공유 토큰이 추측 불가능하다`() {
        val me = login()
        val tokens = (1..5).map { bodyOf(createCollection(me, "컬렉션 $it"))["shareToken"].asText() }

        assertAll(
            { assertThat(tokens).doesNotHaveDuplicates() },
            { assertThat(tokens).allSatisfy { assertThat(it.length).isGreaterThanOrEqualTo(32) } },
            {
                assertThat(tokens)
                    .`as`("순차 id 나 그 변형이 아니다")
                    .noneMatch { it.toLongOrNull() != null }
            },
            {
                // 이어진 두 토큰이 접두사를 공유하면 시드가 순차라는 뜻이다.
                assertThat(tokens.zipWithNext().map { (a, b) -> a.commonPrefixWith(b).length })
                    .allSatisfy { assertThat(it).isLessThan(4) }
            },
        )
    }

    @Test
    fun `RED21 - 잘못된 토큰은 404 다`() {
        assertThat(shared("made-up-token").response.status).isEqualTo(404)
    }

    /**
     * RED 22·23 — **소유자 정보도 내부 id 도 없다.**
     *
     * 컬렉션을 공유한 것이지 자기를 공개한 것이 아니다. 카카오톡으로 링크가 굴러다니는 것을
     * 전제하면(`FR-USER-005`) 표시명조차 붙어 다닐 이유가 없다 —
     * **필요해지면 넣는 편이, 넣어 두고 빼는 것보다 쉽다.**
     */
    @Test
    fun `RED22,23 - 공유 응답에 소유자와 내부 id 가 없다`() {
        publishedCocktail("negroni")
        val me = login("공유한사람")
        val collection = bodyOf(createCollection(me, "공유할 것"))
        add(me, "cocktail", "negroni", collection["id"].asLong())

        val raw = shared(collection["shareToken"].asText()).response.getContentAsString(Charsets.UTF_8)

        assertAll(
            { assertThat(raw).`as`("소유자 표시명").doesNotContain("공유한사람") },
            { assertThat(raw).doesNotContain("userId").doesNotContain("owner") },
            { assertThat(raw).`as`("RED23 내부 id").doesNotContain("\"id\"") },
            { assertThat(raw).doesNotContain("collectionId") },
            { assertThat(raw).`as`("slug 는 공개 식별자라 있어야 한다").contains("negroni") },
        )
    }

    /**
     * RED 24 — **해제·재발급을 제공하지 않는다.** SPEC 에 없다.
     *
     * 부재가 결정이라 경로가 없다는 사실 자체를 고정한다.
     */
    @Test
    fun `RED24 - 공유 토큰 재발급·해제 경로가 없다`() {
        val me = login()
        val id = bodyOf(createCollection(me, "공유할 것"))["id"].asLong()

        listOf("$ME/collections/$id/share", "$ME/collections/$id/unshare").forEach { path ->
            assertThat(mvc.post(path) { with(csrf()); session = me!! }.andReturn().response.status)
                .`as`("%s 가 존재한다", path)
                .isEqualTo(404)
        }
    }

    /**
     * RED 25 — **공유된 컬렉션에서 미발행 항목이 빠진다.**
     *
     * 공유 링크가 발행 전 콘텐츠를 새는 통로가 되면 안 된다. 저장할 때는 발행돼 있었는데
     * 그 사이에 회수된 경우가 이것이다.
     */
    @Test
    fun `RED25 - 공유 컬렉션에서 미발행 항목이 제외된다`() {
        publishedCocktail("negroni")
        publishedCocktail("martini")
        val me = login()
        val collection = bodyOf(createCollection(me, "공유할 것"))
        add(me, "cocktail", "negroni", collection["id"].asLong())
        add(me, "cocktail", "martini", collection["id"].asLong())

        jdbc.execute("UPDATE cocktail SET status = 'draft', published_at = NULL WHERE slug = 'martini'")

        val items = bodyOf(shared(collection["shareToken"].asText()))["items"]

        assertAll(
            { assertThat(items.map { it["targetSlug"].asText() }).containsExactly("negroni") },
            {
                assertThat(count("SELECT count(*) FROM bookmark"))
                    .`as`("행은 남는다 — 다시 발행되면 살아나야 한다")
                    .isEqualTo(2)
            },
        )
    }

    // ── RED 26~28 : 참조 무결성 (앱 책임) ────────────────────────────────

    /**
     * RED 26·27 — **조회에서 거르되 행은 지우지 않는다.**
     *
     * FK 가 없어 dangling 이 생긴다. 그때 행까지 지우면 `archived` 가 되돌아왔을 때
     * 사용자가 저장해 둔 것이 사라진다 — **잠깐 내려갔다고 남의 저장 목록을 지울 권한은 없다.**
     */
    @Test
    fun `RED26,27 - 사라진 대상은 조회에서 빠지고 행은 남는다`() {
        publishedCocktail("negroni")
        val me = login()
        add(me, "cocktail", "negroni")

        jdbc.execute("UPDATE cocktail SET status = 'archived' WHERE slug = 'negroni'")
        val whileArchived = itemsOf(list(me))

        jdbc.execute("UPDATE cocktail SET status = 'published' WHERE slug = 'negroni'")
        val afterRestore = itemsOf(list(me))

        assertAll(
            { assertThat(whileArchived).`as`("RED26·27 내려가면 안 보인다").isEmpty() },
            { assertThat(count("SELECT count(*) FROM bookmark")).`as`("행은 남는다").isEqualTo(1) },
            { assertThat(afterRestore).`as`("되돌아오면 다시 보인다").hasSize(1) },
        )
    }

    /**
     * RED 28 — 저장 시점에 앱이 검증한다.
     *
     * DB 는 못 막는다 — FK 가 없으니 아무 `target_id` 나 들어간다. 직접 INSERT 로
     * 그 사실을 확인하고, API 경로에서는 막히는지도 함께 본다.
     */
    @Test
    fun `RED28 - 앱이 참조 무결성을 검증한다`() {
        val me = login()
        val userId = userIdOf(me)

        // DB 는 없는 대상을 그대로 받는다. 이것이 FK 를 포기한 대가다.
        jdbc.update(
            "INSERT INTO bookmark (user_id, target_type, target_id) VALUES (?, 'cocktail', 999999)",
            userId,
        )

        assertAll(
            { assertThat(count("SELECT count(*) FROM bookmark")).`as`("DB 는 막지 않는다").isEqualTo(1) },
            { assertThat(itemsOf(list(me))).`as`("앱이 조회에서 거른다").isEmpty() },
            {
                assertThat(add(me, "cocktail", "ghost").response.status)
                    .`as`("API 경로는 애초에 막힌다")
                    .isEqualTo(404)
            },
        )
    }

    // ── RED 31~32 : 규약 ─────────────────────────────────────────────────

    /**
     * RED 31 — 응답에 `id` 가 있다. **본인 리소스라 허용되는 예외**다.
     *
     * `DELETE /me/bookmarks/{id}` 가 이것을 쓴다 (SPEC-07 §2.5). 남의 것은 애초에
     * 목록에 없고, 있어도 삭제가 404 라 id 를 알아도 할 수 있는 것이 없다.
     *
     * 공유 응답에는 없다 (RED 23) — 거기는 남이 보는 화면이다.
     */
    @Test
    fun `RED31 - 내 응답에는 id 가 있고 공유 응답에는 없다`() {
        publishedCocktail("negroni")
        val me = login()
        val collection = bodyOf(createCollection(me, "공유할 것"))
        add(me, "cocktail", "negroni", collection["id"].asLong())

        assertAll(
            { assertThat(itemsOf(list(me))[0].has("id")).isTrue() },
            {
                assertThat(bodyOf(shared(collection["shareToken"].asText()))["items"][0].has("id"))
                    .isFalse()
            },
        )
    }

    /** RED 32 — 개인 데이터라 캐시하지 않는다. */
    @Test
    fun `RED32 - 캐시 헤더가 없다`() {
        val response = list(login()).response

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

    private fun add(
        login: MockHttpSession?,
        targetType: String,
        targetSlug: String,
        collectionId: Long? = null,
    ) = mvc.post("$ME/bookmarks") {
        with(csrf())
        login?.let { this.session = it }
        contentType = MediaType.APPLICATION_JSON
        content = json.writeValueAsString(
            buildMap {
                put("targetType", targetType)
                put("targetSlug", targetSlug)
                collectionId?.let { put("collectionId", it) }
            },
        )
    }.andReturn()

    private fun list(login: MockHttpSession?, collectionId: Long? = null) =
        mvc.get("$ME/bookmarks") {
            login?.let { this.session = it }
            collectionId?.let { param("collectionId", it.toString()) }
        }.andReturn()

    private fun createCollection(login: MockHttpSession?, name: String?) =
        mvc.post("$ME/collections") {
            with(csrf())
            login?.let { this.session = it }
            contentType = MediaType.APPLICATION_JSON
            content = if (name == null) "{}" else json.writeValueAsString(mapOf("name" to name))
        }.andReturn()

    private fun shared(token: String) =
        mvc.get("${ApiPaths.BASE}/collections/$token").andReturn()

    /** 게이트를 신경 쓰지 않는다 — 발행 상태만 있으면 되므로 직접 심는다. */
    private fun publishedCocktail(slug: String) = insertCocktail(slug, "published")

    private fun draftCocktail(slug: String) = insertCocktail(slug, "draft")

    /**
     * 3축 중 스타일·향은 **별도 테이블**이다 (V009). 배열 컬럼이 아니다.
     *
     * ## 한 트랜잭션으로 묶어야 한다
     *
     * `fk_cocktail__style_primary` 가 `DEFERRABLE INITIALLY DEFERRED` 다 —
     * `style_primary` 는 `cocktail_style` 에 그 행이 있어야 하는데, 칵테일이 먼저 생겨야
     * 스타일 행을 넣을 수 있어서 **한쪽만 보면 항상 순환**이다.
     *
     * `jdbc.update` 는 문장마다 커밋해서 그 유예가 무의미해진다 (`R-C-3` · `INV-COCKTAIL-01`).
     * 커밋 시점에 함께 검사되도록 `TransactionTemplate` 안에서 돌린다 — 이슈 013 의
     * `PublishServiceTest` 가 같은 함정을 밟았다.
     */
    private fun insertCocktail(slug: String, status: String) {
        transactions.executeWithoutResult {
            val id = jdbc.queryForObject(
                """
                INSERT INTO cocktail (slug, name_ko, name_en, summary, base_spirit, style_primary,
                                      method, sweetness, glass_type, status, published_at)
                VALUES (?, ?, ?, '요약', 'gin', 'highball', 'build', 'dry', '하이볼 글라스', ?, ?)
                RETURNING id
                """.trimIndent(),
                Long::class.java,
                slug, "칵테일-$slug", "Cocktail $slug", status,
                if (status == "published") java.sql.Timestamp.from(Instant.now()) else null,
            )!!

            jdbc.update("INSERT INTO cocktail_style (cocktail_id, style) VALUES (?, 'highball')", id)
            jdbc.update("INSERT INTO cocktail_aroma_tag (cocktail_id, aroma_tag) VALUES (?, 'citrus')", id)
        }
    }

    private fun login(displayName: String = "테스터"): MockHttpSession? {
        val userId = jdbc.queryForObject(
            """INSERT INTO "user" (provider, provider_uid, display_name)
               VALUES ('kakao', 'uid-${seq++}-${System.nanoTime()}', ?) RETURNING id""",
            Long::class.java,
            displayName,
        )!!
        jdbc.update("INSERT INTO user_role (user_id, role) VALUES (?, 'member')", userId)

        return MockHttpSession().apply {
            setAttribute(AbsoluteExpiryFilter.USER_ID, userId)
            setAttribute(SessionPolicy.ISSUED_AT, Instant.now())
            setAttribute(SessionPolicy.ISSUED_ROLES, setOf("member"))
        }
    }

    private fun userIdOf(session: MockHttpSession?) =
        session!!.getAttribute(AbsoluteExpiryFilter.USER_ID) as Long

    private fun bodyOf(result: MvcResult): JsonNode =
        json.readTree(result.response.getContentAsString(Charsets.UTF_8))

    private fun itemsOf(result: MvcResult): List<JsonNode> = bodyOf(result).toList()

    private fun count(sql: String) = jdbc.queryForObject(sql, Long::class.java)!!

    companion object {
        private val ME = "${ApiPaths.BASE}/me"

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

            // 이슈 025 와 같은 이유 — `SessionRepositoryFilter` 가 요청 세션을 갈아끼운다.
            registry.add("spring.autoconfigure.exclude") {
                "org.springframework.boot.autoconfigure.session.SessionAutoConfiguration"
            }
        }
    }
}
