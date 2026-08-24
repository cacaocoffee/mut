package kr.mut.common.web

import com.fasterxml.jackson.databind.ObjectMapper
import kr.mut.common.web.cache.CacheControlFilter
import kr.mut.common.web.error.ViolationCode
import kr.mut.common.web.idempotency.IdempotencyFilter
import kr.mut.support.PostgresSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/**
 * ISSUE-003 — REST 규약 (SPEC-07 §1).
 *
 * 규약이 필터 · `@RestControllerAdvice` · 아규먼트 리졸버에 흩어져 있다.
 * **조립 결과를 HTTP 로 확인한다** — 필터 순서가 틀리면 헤더가 조용히 안 붙는다.
 *
 * 인증은 이슈 005·006·007 이 채운다. 여기서는 예외 → 상태 코드 매핑까지가 범위라
 * 시큐리티를 열어 두고 프로브가 예외를 직접 던진다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(RestProbes.PROFILE)
class RestConventionTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var json: ObjectMapper
    @Autowired private lateinit var events: EventProbe

    // ── RED 1~7 : Problem Details (SPEC-07 §1.4) ───────────────────────────

    @Test
    fun `RED1 - 에러응답 Content-Type 이 application problem+json 이다`() {
        mvc.get("$BASE/errors/not-found").andExpect {
            status { isNotFound() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
        }
    }

    @Test
    fun `RED2 - 에러응답에 type title status detail instance 가 있다`() {
        val body = problemOf(mvc.get("$BASE/errors/not-found").andReturn())

        assertThat(body.keys).contains("type", "title", "status", "detail", "instance")
        assertThat(body["status"]).isEqualTo(404)
        assertThat(body["instance"]).isEqualTo("$BASE/errors/not-found")
        assertThat(body["type"].toString()).contains("not-found")
    }

    @Test
    fun `RED3 - 422 응답에 violations 배열이 있다`() {
        val body = problemOf(mvc.get("$BASE/errors/domain").andReturn())

        assertThat(body["status"]).isEqualTo(422)
        assertThat(body["violations"]).isInstanceOf(List::class.java)
    }

    /** `FR-ADMIN-003` — 하나씩 고치게 하지 않는다. 첫 실패에서 멈추면 안 된다. */
    @Test
    fun `RED4 - violations 는 실패항목을 전부 담는다`() {
        assertThat(violationsOf(mvc.get("$BASE/errors/domain").andReturn()))
            .`as`("게이트 2개가 동시에 실패하면 2건")
            .hasSize(2)
            .extracting("code")
            .containsExactlyInAnyOrder("GATE-COCKTAIL-01", "GATE-COCKTAIL-05")
    }

    @Test
    fun `RED5 - violations 각 항목에 code field message 가 있다`() {
        violationsOf(mvc.get("$BASE/errors/domain").andReturn()).forEach {
            assertThat(it.keys).contains("code", "field", "message")
            assertThat(it["message"] as String).isNotBlank()
        }
    }

    /** 문자열 리터럴이 아니라 enum 에서 나와야 한다 — 스펙과 함께 움직인다. */
    @Test
    fun `RED6 - code 는 INV 또는 GATE ID 형식이다`() {
        violationsOf(mvc.get("$BASE/errors/domain").andReturn()).forEach {
            val code = it["code"] as String
            assertThat(code).matches(ViolationCode.ID_PATTERN.pattern)
            assertThat(ViolationCode.of(code)).`as`("SPEC-02 에 있는 코드여야 한다").isNotNull()
        }
    }

    @Test
    fun `RED7 - 성공 응답에는 violations 가 없다`() {
        val body = bodyOf(mvc.get("$BASE/probe/resource/gin-tonic").andReturn())
        assertThat(body).doesNotContainKey("violations")
    }

    // ── RED 8~16 : 상태 코드 매핑 ──────────────────────────────────────────

    @Test
    fun `RED8 - 문법오류는 400`() =
        mvc.get("$BASE/errors/bad-request").andExpect { status { isBadRequest() } }.let {}

    @Test
    fun `RED9 - 미인증은 401`() =
        mvc.get("$BASE/errors/unauthenticated").andExpect { status { isUnauthorized() } }.let {}

    @Test
    fun `RED10 - 권한없음은 403`() =
        mvc.get("$BASE/errors/forbidden").andExpect { status { isForbidden() } }.let {}

    @Test
    fun `RED11 - 없는 리소스는 404`() =
        mvc.get("$BASE/errors/not-found").andExpect { status { isNotFound() } }.let {}

    /**
     * RED 11 보강 — **매핑 자체가 없는 경로도 404 다** (이슈 026 에서 발견).
     *
     * 부트 3.2 부터 이 경우는 `NoHandlerFoundException` 이 아니라 `NoResourceFoundException`
     * 으로 온다. 핸들러가 옛 예외만 알고 있으면 catch-all 이 받아 **오탈자 경로가 전부 500** 이 되고,
     * 상태가 틀린 것보다 나쁜 일이 따라온다 — 없는 경로를 긁는 봇 하나가 `ERROR` 로그를 채워
     * 진짜 500 을 묻는다.
     */
    @Test
    fun `RED11 - 매핑이 없는 경로도 404 다`() {
        val problem = problemOf(mvc.get("$BASE/there-is-no-such-thing").andReturn())

        assertThat(problem["status"]).`as`("500 이 아니다").isEqualTo(404)
        assertThat(problem["title"])
            .isEqualTo(problemOf(mvc.get("$BASE/errors/not-found").andReturn())["title"])
    }

    /**
     * SPEC-07 §1.4 — `403` 이면 "그 슬러그는 존재한다"가 새어 나간다.
     *
     * `instance` 는 요청 URI 를 그대로 되비치므로 검사 대상이 아니다 — 클라이언트가 이미 아는 값이다.
     * 흘리면 안 되는 것은 **리소스의 상태**다. `title` 과 `detail` 이 없는 것과 구별되지 않아야 한다.
     */
    @Test
    fun `RED12 - 비공개 리소스도 404 다`() {
        val hidden = problemOf(mvc.get("$BASE/errors/draft").andReturn())
        val missing = problemOf(mvc.get("$BASE/errors/not-found").andReturn())

        assertThat(hidden["status"]).`as`("403 이 아니다").isEqualTo(404)
        assertThat(hidden["title"]).isEqualTo(missing["title"])
        assertThat(hidden["detail"])
            .`as`("없는 것과 숨긴 것이 구별되지 않아야 한다")
            .isEqualTo(missing["detail"])
        assertThat("${hidden["title"]} ${hidden["detail"]}")
            .doesNotContainIgnoringCase("draft")
            .doesNotContainIgnoringCase("초안")
    }

    @Test
    fun `RED13 - 상태충돌은 409`() =
        mvc.get("$BASE/errors/conflict").andExpect { status { isConflict() } }.let {}

    @Test
    fun `RED14 - 도메인규칙 위반은 422`() =
        mvc.get("$BASE/errors/domain").andExpect { status { isUnprocessableEntity() } }.let {}

    @Test
    fun `RED15 - 레이트리밋 초과는 429 이고 Retry-After 가 있다`() {
        val result = mvc.get("$BASE/errors/rate-limit").andReturn()

        assertThat(result.response.status).isEqualTo(429)
        assertThat(result.response.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("30")
    }

    /** 스택트레이스 · SQL · 클래스명이 새면 공격자에게 지도를 주는 것이다. */
    @Test
    fun `RED16 - 처리되지 않은 예외는 500 이고 내부정보를 노출하지 않는다`() {
        val result = mvc.get("$BASE/errors/boom").andReturn()
        val body = result.response.contentAsString

        assertThat(result.response.status).isEqualTo(500)
        assertThat(body)
            .doesNotContain("IllegalStateException")
            .doesNotContain("org.postgresql")
            .doesNotContain("SELECT")
            .doesNotContain("secret")
            .doesNotContain("kr.mut")
    }

    // ── RED 17~21 : 페이징 (SPEC-07 §1.5) ──────────────────────────────────

    @Test
    fun `RED17 - 기본값은 page0 size24`() {
        val page = pageOf(mvc.get("$BASE/probe/paged").andReturn())

        assertThat(page["number"]).isEqualTo(0)
        assertThat(page["size"]).isEqualTo(24)
    }

    @Test
    fun `RED18 - 응답에 items 와 page 객체가 있다`() {
        val body = bodyOf(mvc.get("$BASE/probe/paged").andReturn())
        assertThat(body.keys).containsExactlyInAnyOrder("items", "page")
    }

    @Test
    fun `RED19 - page 에 number size totalElements totalPages 가 있다`() {
        val page = pageOf(mvc.get("$BASE/probe/paged").andReturn())

        assertThat(page.keys).containsExactlyInAnyOrder(
            "number", "size", "totalElements", "totalPages",
        )
        assertThat(page["totalElements"]).isEqualTo(137)
        assertThat(page["totalPages"]).`as`("137 / 24 → 6").isEqualTo(6)
    }

    /** 무제한 조회를 막는다. 400 이 아니라 절삭 — 흔한 실수를 실패로 만들지 않는다. */
    @Test
    fun `RED20 - size 상한을 넘으면 상한으로 절삭된다`() {
        val page = pageOf(mvc.get("$BASE/probe/paged") { param("size", "10000") }.andReturn())
        assertThat(page["size"]).isEqualTo(100)
    }

    /** 인덱스 없는 컬럼 정렬을 받으면 그것이 곧 풀스캔 경로다. */
    @Test
    fun `RED21 - sort 파라미터가 허용목록 밖이면 400`() {
        mvc.get("$BASE/probe/paged") { param("sort", "abv,asc") }
            .andExpect { status { isOk() } }

        val result = mvc.get("$BASE/probe/paged") { param("sort", "secret_column,asc") }.andReturn()
        assertThat(result.response.status).isEqualTo(400)
        assertThat(result.response.contentAsString)
            .`as`("무엇이 가능한지 알려 준다")
            .contains("abv", "name")
    }

    // ── RED 22~25 : 캐싱 (SPEC-07 §1.6) ────────────────────────────────────

    @Test
    fun `RED22 - 공개 조회에 ETag 가 붙는다`() {
        val etag = mvc.get("$BASE/probe/resource/gin-tonic").andReturn().response.getHeader(HttpHeaders.ETAG)
        assertThat(etag).isNotBlank()
    }

    /** SSG 빌드가 같은 엔드포인트를 500번 호출한다. 내용이 그대로면 304 로 끝나야 한다. */
    @Test
    fun `RED23 - If-None-Match 일치시 304 를 반환한다`() {
        val etag = mvc.get("$BASE/probe/resource/gin-tonic").andReturn().response.getHeader(HttpHeaders.ETAG)!!

        val second = mvc.get("$BASE/probe/resource/gin-tonic") {
            header(HttpHeaders.IF_NONE_MATCH, etag)
        }.andReturn()

        assertThat(second.response.status).isEqualTo(304)
        assertThat(second.response.contentAsString).isEmpty()
    }

    @Test
    fun `RED24 - 공개 조회에 Cache-Control max-age 60 이 붙는다`() {
        val header = mvc.get("$BASE/probe/resource/gin-tonic")
            .andReturn().response.getHeader(HttpHeaders.CACHE_CONTROL)

        assertThat(header).isEqualTo(CacheControlFilter.PUBLIC_CACHE)
        assertThat(header).contains("max-age=60", "stale-while-revalidate=600")
    }

    /**
     * 어드민 응답은 **공개 캐시 대상이 아니다.**
     *
     * `Cache-Control` 이 아예 없기를 기대하지 않는다 — Spring Security 가
     * `no-cache, no-store, must-revalidate` 를 붙이고, 그것이 더 안전하다.
     * 확인할 것은 우리가 공개 조회용으로 붙이는 값이 새어 들어가지 않았는가다.
     */
    @Test
    fun `RED25 - 어드민 API 에는 공개 캐시 헤더가 붙지 않는다`() {
        val response = mvc.get("${ApiPaths.ADMIN}/cache-probe").andReturn().response

        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL))
            .isNotEqualTo(CacheControlFilter.PUBLIC_CACHE)
            .doesNotContain("public")
            .doesNotContain("max-age=60")
        assertThat(response.getHeader(HttpHeaders.ETAG))
            .`as`("발행 전 데이터에 검증자를 붙이지 않는다")
            .isNull()
    }

    /**
     * RED 25 보강 — **개인 응답도 공개 캐시 대상이 아니다** (이슈 031 에서 드러났다).
     *
     * `/me/bookmarks` 가 `isPublicApi` 를 통과해 `public, max-age=60` 과 ETag 를 달고 나갔다.
     * 중간 캐시가 **한 사람의 북마크를 다른 사람에게 줄 수 있다**는 뜻이다.
     *
     * `/auth` 도 같다 — CSRF 토큰이 60초 캐시되면 토큰을 세션에 바인딩한 의미가 없다.
     *
     * 인증이 필요한 경로를 열거하지 않고 접두사로 판정한다: 열거하면 새 개인 경로가
     * 목록에서 빠진 채 들어오고, **그때 새는 것은 조용하다.**
     */
    @ParameterizedTest
    @ValueSource(strings = ["/me/bookmarks", "/me/collections", "/auth/csrf"])
    fun `개인 API 에는 공개 캐시 헤더가 붙지 않는다`(path: String) {
        val response = mvc.get("$BASE$path").andReturn().response

        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL).orEmpty())
            .`as`("%s 가 공유 캐시에 올라간다", path)
            .doesNotContain("public")
            .doesNotContain("max-age=60")
        assertThat(response.getHeader(HttpHeaders.ETAG))
            .`as`("%s 에 검증자가 붙으면 그것을 근거로 재사용된다", path)
            .isNull()
    }

    /** 판정 함수 자체도 고정한다 — 필터가 바뀌어도 규칙은 한 곳이다. */
    @Test
    fun `공유 캐시 판정이 개인 경로를 공개로 보지 않는다`() {
        assertThat(ApiPaths.isPubliclyCacheable("$BASE/cocktails")).isTrue()
        assertThat(ApiPaths.isPubliclyCacheable("$BASE/collections/some-token"))
            .`as`("공유 링크는 토큰이 URL 에 있어 캐시 키가 갈린다")
            .isTrue()

        assertThat(ApiPaths.isPubliclyCacheable("$BASE/me/bookmarks")).isFalse()
        assertThat(ApiPaths.isPubliclyCacheable("$BASE/auth/csrf")).isFalse()
        assertThat(ApiPaths.isPubliclyCacheable("${ApiPaths.ADMIN}/cocktails")).isFalse()
    }

    /** 에러를 캐시시키면 발행 직후가 60초 동안 비어 보인다. */
    @Test
    fun `에러 응답에는 공개 캐시 헤더가 붙지 않는다`() {
        assertThat(
            mvc.get("$BASE/errors/not-found").andReturn().response.getHeader(HttpHeaders.CACHE_CONTROL),
        ).doesNotContain("max-age=60")
    }

    // ── RED 26~29 : 멱등성 (SPEC-07 §1.7) ──────────────────────────────────

    @Test
    fun `RED26 - 같은 IdempotencyKey 로 두번 POST 하면 부수효과가 한번만 발생한다`() {
        val before = events.sideEffects.get()
        val key = "idem-${System.nanoTime()}"

        repeat(2) { postEvent(key, """{"name":"page_view"}""") }

        assertThat(events.sideEffects.get() - before).isEqualTo(1)
    }

    @Test
    fun `RED27 - 같은 키 재요청은 최초 응답을 그대로 반환한다`() {
        val key = "idem-${System.nanoTime()}"
        val first = postEvent(key, """{"name":"page_view"}""")
        val second = postEvent(key, """{"name":"page_view"}""")

        assertThat(second.response.status).isEqualTo(first.response.status)
        assertThat(second.response.contentAsString).isEqualTo(first.response.contentAsString)
        assertThat(second.response.getHeader(IdempotencyFilter.REPLAY_HEADER))
            .`as`("재생된 응답임을 알 수 있어야 한다")
            .isEqualTo("true")
    }

    @Test
    fun `RED28 - 다른 키는 각각 처리된다`() {
        val before = events.sideEffects.get()

        postEvent("idem-a-${System.nanoTime()}", """{"name":"page_view"}""")
        postEvent("idem-b-${System.nanoTime()}", """{"name":"page_view"}""")

        assertThat(events.sideEffects.get() - before).isEqualTo(2)
    }

    /** 키를 가로채 남의 응답을 받거나, 서로 다른 요청이 한 결과를 공유하는 것을 막는다. */
    @Test
    fun `RED29 - 같은 키에 다른 본문이 오면 거부된다`() {
        val key = "idem-${System.nanoTime()}"
        postEvent(key, """{"name":"page_view"}""")

        val before = events.sideEffects.get()
        val conflict = postEvent(key, """{"name":"완전히_다른_이벤트"}""")

        assertThat(conflict.response.status).isEqualTo(409)
        assertThat(events.sideEffects.get()).`as`("처리되지 않았다").isEqualTo(before)
    }

    @Test
    fun `Idempotency-Key 가 없으면 매번 처리된다`() {
        val before = events.sideEffects.get()

        repeat(2) {
            mvc.post(PROBE) {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"page_view"}"""
            }.andExpect { status { isOk() } }
        }

        assertThat(events.sideEffects.get() - before).isEqualTo(2)
    }

    // ── RED 30~32 : 명명 규약 (SPEC-07 §1.1) ───────────────────────────────

    /** 공개 식별자는 `slug` 다. `id` 는 어드민·파트너 API 에서만 쓴다 (`PRIN-D02`). */
    @Test
    fun `RED30 - 공개 응답에 내부 id 가 없다`() {
        val body = bodyOf(mvc.get("$BASE/probe/resource/gin-tonic").andReturn())

        assertThat(body).containsKey("slug")
        assertThat(body).doesNotContainKey("id")
    }

    @Test
    fun `RED31 - 응답 필드는 camelCase 다`() {
        val body = bodyOf(mvc.get("$BASE/probe/resource/gin-tonic").andReturn())

        assertThat(body.keys).allSatisfy { key ->
            assertThat(key).matches("^[a-z][a-zA-Z0-9]*$")
        }
    }

    /**
     * 경로는 **복수형**(`/cocktails`)이고 테이블은 **단수형**(`cocktail`)이다 (SPEC-06 §1.1).
     * 헷갈리기 쉬운 지점이라 양쪽을 각각 고정한다 — 테이블 쪽은 `SchemaLintTest` 가 본다.
     */
    /**
     * **프로브가 아니라 진짜 엔드포인트를 본다.** 규약은 실제로 나가는 경로에 걸려야 뜻이 있고,
     * 프로브 경로(`/probe/…`)를 검사하면 내가 지은 이름을 내가 확인하는 것뿐이다.
     * 이슈 018 이 `GET /api/v1/cocktails` 를 만들면서 볼 대상이 생겼다.
     */
    @Test
    fun `RED32 - 경로는 kebab-case 복수형이다`() {
        assertThat(ApiPaths.BASE).isEqualTo("/api/v1")

        mvc.get("$BASE/cocktails").andExpect { status { isOk() } }

        assertThat("$BASE/cocktails".removePrefix("$BASE/"))
            .matches("^[a-z][a-z0-9-]*$")
            .endsWith("s")
    }

    @Test
    fun `공개 경로 판정이 어드민을 공개로 보지 않는다`() {
        assertThat(ApiPaths.isPublicApi("/api/v1/cocktails")).isTrue()
        assertThat(ApiPaths.isPublicApi("/api/v1/admin/cocktails")).isFalse()
        assertThat(ApiPaths.isPublicApi("/actuator/health")).isFalse()
    }

    // ── 헬퍼 ───────────────────────────────────────────────────────────────

    private fun postEvent(key: String, body: String) = mvc.post(PROBE) {
        contentType = MediaType.APPLICATION_JSON
        header(IdempotencyFilter.HEADER, key)
        content = body
    }.andReturn()

    @Suppress("UNCHECKED_CAST")
    private fun bodyOf(result: MvcResult): Map<String, Any> =
        json.readValue(result.response.contentAsString, Map::class.java) as Map<String, Any>

    private fun problemOf(result: MvcResult) = bodyOf(result)

    @Suppress("UNCHECKED_CAST")
    private fun violationsOf(result: MvcResult): List<Map<String, Any>> =
        problemOf(result)["violations"] as List<Map<String, Any>>

    @Suppress("UNCHECKED_CAST")
    private fun pageOf(result: MvcResult): Map<String, Any> =
        bodyOf(result)["page"] as Map<String, Any>


    companion object {
        const val BASE = ApiPaths.BASE

        /**
         * 멱등 프로브 경로.
         *
         * 실제 수집 엔드포인트(`/events`)를 점유하지 않는다 — 이슈 034 가 그 자리를 채우면서
         * `Ambiguous mapping` 으로 이 파일 전체가 죽었다. [EventProbe] 의 KDoc 참조.
         */
        const val PROBE = "$BASE/events/idempotency-probe"

        @JvmStatic
        @DynamicPropertySource
        fun datasource(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { PostgresSupport.container.jdbcUrl }
            registry.add("spring.datasource.username") { PostgresSupport.container.username }
            registry.add("spring.datasource.password") { PostgresSupport.container.password }
            registry.add("spring.flyway.enabled") { true }
            registry.add("spring.flyway.user") { PostgresSupport.container.username }
            registry.add("spring.flyway.password") { PostgresSupport.container.password }
            registry.add("spring.jpa.hibernate.ddl-auto") { "none" }
        }
    }
}
