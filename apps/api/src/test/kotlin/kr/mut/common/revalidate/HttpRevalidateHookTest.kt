package kr.mut.common.revalidate

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

/**
 * ISSUE-015 RED 1~3 · 12~16 · 20~22 — 호출과 실패 격리 (SPEC-07 §4, `NFR-R-03`).
 *
 * ## 이 이슈의 요체는 "안 터지는 것"이다
 *
 * 훅은 실패해도 된다. SPEC-07 §4 가 "ISR 주기가 결국 따라잡는다"고 했다.
 * 절대 안 되는 것은 **훅의 실패가 발행을 건드리는 것**이다.
 */
class HttpRevalidateHookTest {

    private var front: RevalidateMockFront? = null

    @AfterEach
    fun stop() {
        front?.stop()
        front = null
    }

    // ── RED 1~3 : 호출 모양 ────────────────────────────────────────────────

    @Test
    fun `RED1-3 - 시크릿 헤더와 paths 본문으로 POST 한다`() {
        val mock = RevalidateMockFront().also { front = it }
        val hook = hook(mock.baseUrl, secret = "s3cr3t")

        hook.revalidate(listOf("/cocktails/negroni", "/sitemap.xml"))

        val request = mock.awaitFirst()
        assertAll(
            { assertThat(request.method).`as`("RED1").isEqualTo("POST") },
            { assertThat(request.secret).`as`("RED2").isEqualTo("s3cr3t") },
            {
                assertThat(request.body)
                    .`as`("RED3 — { \"paths\": [...] }")
                    .contains("\"paths\"")
                    .contains("/cocktails/negroni")
                    .contains("/sitemap.xml")
            },
        )
    }

    @Test
    fun `경로가 비면 아예 부르지 않는다`() {
        val mock = RevalidateMockFront().also { front = it }

        hook(mock.baseUrl).revalidate(emptyList())

        Thread.sleep(200)
        assertThat(mock.received).isEmpty()
    }

    /** 꺼져 있으면 부르지 않는다 — 로컬에서 프론트를 안 띄웠다고 막히면 안 된다. */
    @Test
    fun `비활성화면 부르지 않는다`() {
        val mock = RevalidateMockFront().also { front = it }

        hook(mock.baseUrl, enabled = false).revalidate(listOf("/x"))

        Thread.sleep(200)
        assertThat(mock.received).isEmpty()
    }

    // ── RED 12~16 : 실패 격리 (NFR-R-03) ───────────────────────────────────

    /** RED 14 — 대상이 아예 없다. 연결이 거부된다. */
    @Test
    fun `RED14 - 훅 대상이 다운돼도 예외가 새지 않는다`() {
        val dead = deadPort()

        assertThatCode { hook("http://localhost:$dead").revalidate(listOf("/x")) }
            .doesNotThrowAnyException()

        Thread.sleep(300) // 다른 스레드에서 터져도 여기까지 오지 않는다
    }

    /** RED 13 — 응답이 느리다. 타임아웃이 나도 마찬가지다. */
    @Test
    fun `RED13 - 훅이 타임아웃돼도 예외가 새지 않는다`() {
        val mock = RevalidateMockFront(delayMs = 800).also { front = it }

        assertThatCode {
            hook(mock.baseUrl, timeoutMs = 100).revalidate(listOf("/x"))
        }.doesNotThrowAnyException()

        assertThat(mock.awaitFirst().method).`as`("보내기는 보냈다").isEqualTo("POST")
    }

    /** RED 16 — 프론트가 5xx 로 답해도 우리 쪽은 정상이다. */
    @Test
    fun `RED16 - 프론트가 500 을 줘도 예외가 새지 않는다`() {
        val mock = RevalidateMockFront(status = 500).also { front = it }

        assertThatCode { hook(mock.baseUrl).revalidate(listOf("/x")) }
            .doesNotThrowAnyException()

        assertThat(mock.awaitFirst().method).isEqualTo("POST")
    }

    // ── RED 22 : 비동기 (FR-COCKTAIL-016) ──────────────────────────────────

    /**
     * RED 22 — **에디터가 반영을 기다리지 않는다.**
     *
     * 프론트가 800ms 를 끌어도 `revalidate` 는 즉시 돌아와야 한다.
     * 동기로 부르면 발행 응답이 프론트의 재생성 시간만큼 늦어진다.
     */
    @Test
    fun `RED22 - 훅 호출이 발행을 막지 않는다`() {
        val mock = RevalidateMockFront(delayMs = 800).also { front = it }
        val hook = hook(mock.baseUrl)

        val elapsedMs = measureMs { hook.revalidate(listOf("/x")) }

        assertThat(elapsedMs)
            .`as`("프론트가 800ms 를 끌어도 호출부는 붙잡히지 않는다")
            .isLessThan(300)
        assertThat(mock.awaitFirst().method).`as`("그래도 결국 보낸다").isEqualTo("POST")
    }

    // ── RED 20 : 시크릿이 새지 않는다 ──────────────────────────────────────

    /**
     * RED 20 — 시크릿은 헤더로만 나간다.
     *
     * URL 에 실으면 프록시·액세스 로그에 그대로 남는다. 본문에 실어도 마찬가지로
     * 디버깅용 덤프에 섞인다. 헤더 하나로 못 박아 둔다.
     */
    @Test
    fun `RED20 - 시크릿이 URL 이나 본문에 실리지 않는다`() {
        val mock = RevalidateMockFront().also { front = it }

        hook(mock.baseUrl, secret = "top-secret").revalidate(listOf("/x"))

        val request = mock.awaitFirst()
        assertThat(request.body).doesNotContain("top-secret")
        assertThat(request.secret).isEqualTo("top-secret")
    }

    @Test
    fun `엔드포인트가 프론트 원본에 api revalidate 를 붙인다`() {
        assertAll(
            {
                assertThat(properties(url = "https://x.example").endpoint)
                    .isEqualTo("https://x.example/api/revalidate")
            },
            {
                assertThat(properties(url = "https://x.example/").endpoint)
                    .`as`("끝 슬래시가 있어도 // 가 생기지 않는다")
                    .isEqualTo("https://x.example/api/revalidate")
            },
        )
    }

    // ── 헬퍼 ───────────────────────────────────────────────────────────────

    private fun hook(
        url: String,
        secret: String = "test-secret",
        enabled: Boolean = true,
        timeoutMs: Long = 2_000,
    ) = HttpRevalidateHook(
        properties(url, secret, enabled, timeoutMs),
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 1
            initialize()
        },
    )

    private fun properties(
        url: String,
        secret: String = "test-secret",
        enabled: Boolean = true,
        timeoutMs: Long = 2_000,
    ) = RevalidateProperties(enabled = enabled, url = url, secret = secret, timeoutMs = timeoutMs)

    private fun measureMs(block: () -> Unit): Long {
        val startedAt = System.nanoTime()
        block()
        return (System.nanoTime() - startedAt) / 1_000_000
    }

    /** 아무도 안 듣는 포트. 열었다 바로 닫아 확실히 비운다. */
    private fun deadPort(): Int =
        java.net.ServerSocket(0).use { it.localPort }
}
