package kr.mut.common.security.ratelimit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * ISSUE-007 RED 10~19 — 레이트 리밋 (SPEC-08 §6).
 *
 * ## 표를 손으로 다시 적는다
 *
 * [specTable] 은 SPEC-08 §6 을 **문서에서 읽어 옮긴 것**이다. `RateLimitPolicy` 를
 * 그대로 쓰면 동어반복이 된다 — 한도를 잘못 적어도 초록이다.
 *
 * DB 없이 돈다. 시계를 주입해 1분을 실제로 기다리지 않는다.
 */
class RateLimitPolicyTest {

    /** SPEC-08 §6 표 그대로. */
    private val specTable = listOf(
        Spec(RateLimitPolicy.PUBLIC_READ, limit = 300, keyBy = KeyBy.IP),
        Spec(RateLimitPolicy.SEARCH, limit = 60, keyBy = KeyBy.IP),
        Spec(RateLimitPolicy.EVENTS, limit = 120, keyBy = KeyBy.SESSION),
        Spec(RateLimitPolicy.AUTH_CALLBACK, limit = 10, keyBy = KeyBy.IP),
        Spec(RateLimitPolicy.ADMIN_WRITE, limit = 60, keyBy = KeyBy.USER),
    )

    // ── RED 10~15 : 5개 한도 전수 ─────────────────────────────────────────

    @Test
    fun `RED10-15 - SPEC-08 §6 한도 5종이 표와 일치한다`() {
        assertThat(RateLimitPolicy.entries).hasSize(specTable.size)

        assertAll(
            specTable.map<Spec, () -> Unit> { spec ->
                {
                    assertThat(spec.policy.defaultLimit)
                        .`as`("%s 한도", spec.policy)
                        .isEqualTo(spec.limit)
                    assertThat(spec.policy.window)
                        .`as`("%s 윈도우", spec.policy)
                        .isEqualTo(Duration.ofMinutes(1))
                    assertThat(spec.policy.keyBy)
                        .`as`("%s 기준", spec.policy)
                        .isEqualTo(spec.keyBy)
                }
            },
        )
    }

    /** 한도를 실제로 세는지 — 값만 맞고 세지 않으면 아무 의미가 없다. */
    @Test
    fun `RED10 - 공개조회는 300 을 넘으면 429 다`() {
        val limiter = limiter()

        repeat(300) {
            assertThat(limiter.check(RateLimitPolicy.PUBLIC_READ, IP)).isEqualTo(RateLimitResult.Allowed)
        }
        assertThat(limiter.check(RateLimitPolicy.PUBLIC_READ, IP))
            .isInstanceOf(RateLimitResult.Exceeded::class.java)
    }

    /** 검색이 더 조여야 한다 — 초성·별칭 매칭이 가장 비싼 조회다 (SPEC-06 §5). */
    @Test
    fun `RED11-12 - search 는 60 에서 막힌다`() {
        val limiter = limiter()

        repeat(60) { limiter.check(RateLimitPolicy.SEARCH, IP) }

        assertThat(limiter.check(RateLimitPolicy.SEARCH, IP))
            .isInstanceOf(RateLimitResult.Exceeded::class.java)
        assertThat(RateLimitPolicy.SEARCH.defaultLimit)
            .`as`("공개 조회보다 조여야 한다")
            .isLessThan(RateLimitPolicy.PUBLIC_READ.defaultLimit)
    }

    /**
     * RED 17 — **기준이 대상마다 다르다.**
     *
     * `/events` 를 IP 기준으로 잡으면 공유 IP 뒤의 사용자들이 서로를 막는다.
     * 어드민 쓰기를 IP 로 잡으면 같은 사무실의 두 에디터가 한 통을 나눠 쓴다.
     */
    @Test
    fun `RED13-15·17 - 기준이 대상마다 다르다`() {
        assertThat(RateLimitPolicy.EVENTS.keyBy)
            .`as`("공유 IP 뒤에서 서로를 막으면 안 된다")
            .isEqualTo(KeyBy.SESSION)
        assertThat(RateLimitPolicy.ADMIN_WRITE.keyBy)
            .`as`("같은 사무실의 두 에디터가 한 통을 나눠 쓰면 안 된다")
            .isEqualTo(KeyBy.USER)
        assertThat(RateLimitPolicy.AUTH_CALLBACK.keyBy).isEqualTo(KeyBy.IP)

        // 통이 키별로 갈리는지 — 갈리지 않으면 기준을 나눈 의미가 없다.
        val limiter = limiter()
        repeat(10) { limiter.check(RateLimitPolicy.AUTH_CALLBACK, "ip-a") }

        assertThat(limiter.check(RateLimitPolicy.AUTH_CALLBACK, "ip-a"))
            .isInstanceOf(RateLimitResult.Exceeded::class.java)
        assertThat(limiter.check(RateLimitPolicy.AUTH_CALLBACK, "ip-b"))
            .`as`("남의 통이 내 통을 막지 않는다")
            .isEqualTo(RateLimitResult.Allowed)
    }

    @Test
    fun `RED16 - 429 응답에 Retry-After 가 있다`() {
        val limiter = limiter()
        repeat(11) { limiter.check(RateLimitPolicy.AUTH_CALLBACK, IP) }

        val result = limiter.check(RateLimitPolicy.AUTH_CALLBACK, IP) as RateLimitResult.Exceeded

        assertThat(result.retryAfterSeconds)
            .`as`("윈도우 안이니 1..60 초 사이다")
            .isBetween(1L, 60L)
    }

    /** 하드코딩하면 사고가 났을 때 배포 없이 조일 수 없다. */
    @Test
    fun `RED18 - 한도값이 설정으로 주입된다`() {
        val tightened = RateLimitProperties(limits = mapOf("search" to 2))
        val limiter = RateLimiter(tightened, fixedClock())

        repeat(2) {
            assertThat(limiter.check(RateLimitPolicy.SEARCH, IP)).isEqualTo(RateLimitResult.Allowed)
        }
        assertThat(limiter.check(RateLimitPolicy.SEARCH, IP))
            .`as`("설정이 기본값(60)을 덮는다")
            .isInstanceOf(RateLimitResult.Exceeded::class.java)

        // 설정에 없는 정책은 스펙 기본값 그대로다.
        assertThat(tightened.limitOf(RateLimitPolicy.PUBLIC_READ)).isEqualTo(300)
    }

    @Test
    fun `RED19 - 윈도우가 지나면 다시 통과한다`() {
        val clock = MovableClock(START)
        val limiter = RateLimiter(RateLimitProperties(limits = mapOf("search" to 1)), clock)

        limiter.check(RateLimitPolicy.SEARCH, IP)
        assertThat(limiter.check(RateLimitPolicy.SEARCH, IP))
            .isInstanceOf(RateLimitResult.Exceeded::class.java)

        clock.advance(Duration.ofMinutes(1).plusSeconds(1))

        assertThat(limiter.check(RateLimitPolicy.SEARCH, IP))
            .`as`("윈도우 만료 후 다시 열린다")
            .isEqualTo(RateLimitResult.Allowed)
    }

    // ── RED 26 : 저장소 장애 (DECISIONS §1) ───────────────────────────────

    /**
     * **읽기는 열고 쓰기는 닫는다.**
     *
     * 레이트 리밋 장애로 사이트 전체가 안 보이는 것이 그 순간의 과도한 트래픽보다 나쁘고,
     * 반대로 쓰기를 열어 두면 그 틈에 벌어진 일을 되돌려야 한다.
     */
    @Test
    fun `RED26 - 저장소 장애시 공개조회는 열고 어드민 쓰기는 닫는다`() {
        val limiter = RateLimiter(BrokenProperties(), fixedClock())

        assertThat(limiter.check(RateLimitPolicy.PUBLIC_READ, IP))
            .`as`("공개 조회 fail-open")
            .isEqualTo(RateLimitResult.Allowed)
        assertThat(limiter.check(RateLimitPolicy.SEARCH, IP)).isEqualTo(RateLimitResult.Allowed)
        assertThat(limiter.check(RateLimitPolicy.EVENTS, "s")).isEqualTo(RateLimitResult.Allowed)

        assertThat(limiter.check(RateLimitPolicy.ADMIN_WRITE, "u"))
            .`as`("어드민 쓰기 fail-closed")
            .isInstanceOf(RateLimitResult.Exceeded::class.java)
        assertThat(limiter.check(RateLimitPolicy.AUTH_CALLBACK, IP))
            .`as`("인증 콜백도 닫는다 — 무차별 시도를 막아야 한다")
            .isInstanceOf(RateLimitResult.Exceeded::class.java)
    }

    @Test
    fun `비활성화하면 세지 않는다`() {
        val limiter = RateLimiter(RateLimitProperties(enabled = false), fixedClock())

        repeat(1000) {
            assertThat(limiter.check(RateLimitPolicy.AUTH_CALLBACK, IP))
                .isEqualTo(RateLimitResult.Allowed)
        }
    }

    /** 만료된 통이 쌓이면 메모리가 샌다. 접근하지 않는 키는 스스로 사라지지 않는다. */
    @Test
    fun `만료된 통을 걷어낸다`() {
        val clock = MovableClock(START)
        val limiter = RateLimiter(RateLimitProperties(), clock)

        repeat(100) { limiter.check(RateLimitPolicy.PUBLIC_READ, "ip-$it") }
        clock.advance(Duration.ofMinutes(2))
        limiter.evictExpired()

        // 걷어낸 뒤에는 처음부터 다시 센다.
        assertThat(limiter.check(RateLimitPolicy.PUBLIC_READ, "ip-0"))
            .isEqualTo(RateLimitResult.Allowed)
    }

    // ── 헬퍼 ───────────────────────────────────────────────────────────────

    private data class Spec(val policy: RateLimitPolicy, val limit: Int, val keyBy: KeyBy)

    private fun limiter() = RateLimiter(RateLimitProperties(), fixedClock())

    private fun fixedClock(): Clock = Clock.fixed(START, ZoneOffset.UTC)

    /** 카운트할 때마다 터진다 — 저장소 장애를 흉내 낸다. */
    private class BrokenProperties : RateLimitProperties() {
        override fun limitOf(policy: RateLimitPolicy): Int =
            throw IllegalStateException("카운터 저장소 장애")
    }

    private class MovableClock(private var now: Instant) : Clock() {
        override fun instant() = now
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId): Clock = this
        fun advance(by: Duration) { now = now.plus(by) }
    }

    private companion object {
        const val IP = "203.0.113.7"
        val START: Instant = Instant.parse("2026-08-10T09:00:00Z")
    }
}
