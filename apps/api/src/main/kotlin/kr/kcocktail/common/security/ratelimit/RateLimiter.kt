package kr.kcocktail.common.security.ratelimit

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * 고정 윈도우 카운터 (ISSUE-007, SPEC-08 §6).
 *
 * ## 인메모리다
 *
 * SPEC-08 §9 가 "Phase 1 은 DB 세션으로 충분"이라고 했고 Redis 를 들이지 않는다.
 * **인스턴스가 하나라는 전제**이고, 늘어나면 통이 인스턴스마다 따로 생겨 실효 한도가 N배가 된다 —
 * 그때 재검토한다. 지금 Redis 를 넣으면 운영할 것이 하나 더 늘 뿐이다.
 *
 * ## 슬라이딩이 아니라 고정 윈도우다
 *
 * 경계에서 최대 2배까지 통과할 수 있다 (59초에 300개 + 61초에 300개). 알고 쓰는 것이다 —
 * 여기 한도는 남용 방지지 정밀 과금이 아니고, 슬라이딩은 요청마다 타임스탬프를 들고 있어야 한다.
 */
@Component
class RateLimiter(
    private val properties: RateLimitProperties,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val buckets = ConcurrentHashMap<String, Bucket>()

    /**
     * 한 번 센다.
     *
     * @param key `KeyBy` 로 뽑은 식별자. **저장하지 않는다** — 맵의 키로만 산다 (DECISIONS §1)
     */
    fun check(policy: RateLimitPolicy, key: String): RateLimitResult {
        if (!properties.enabled) return RateLimitResult.Allowed

        return try {
            consume(policy, key)
        } catch (e: RuntimeException) {
            // 카운터 자체가 죽는 일은 드물지만, 죽었을 때 무엇을 할지가 정책이다.
            log.error("레이트 리밋 저장소 장애 (policy={}, mode={})", policy, policy.onStoreFailure, e)
            when (policy.onStoreFailure) {
                FailMode.OPEN -> RateLimitResult.Allowed
                FailMode.CLOSED -> RateLimitResult.Exceeded(policy.window.seconds)
            }
        }
    }

    private fun consume(policy: RateLimitPolicy, key: String): RateLimitResult {
        val now = clock.instant()
        val limit = properties.limitOf(policy)

        val bucket = buckets.compute("${policy.name}:$key") { _, existing ->
            if (existing == null || existing.isExpired(now)) Bucket(now.plus(policy.window), 1)
            else existing.copy(count = existing.count + 1)
        }!!

        return if (bucket.count <= limit) {
            RateLimitResult.Allowed
        } else {
            RateLimitResult.Exceeded(retryAfterSeconds(bucket, now))
        }
    }

    private fun retryAfterSeconds(bucket: Bucket, now: Instant): Long =
        maxOf(1, bucket.resetsAt.epochSecond - now.epochSecond)

    /** 만료된 통을 걷어낸다. 스케줄러 없이 접근 시점에만 지우면 죽은 키가 쌓인다. */
    fun evictExpired() {
        val now = clock.instant()
        buckets.entries.removeIf { it.value.isExpired(now) }
    }

    internal fun reset() = buckets.clear()

    private data class Bucket(val resetsAt: Instant, val count: Int) {
        fun isExpired(now: Instant) = !now.isBefore(resetsAt)
    }
}

sealed interface RateLimitResult {
    data object Allowed : RateLimitResult

    /** `429` + `Retry-After` (SPEC-08 §6). */
    data class Exceeded(val retryAfterSeconds: Long) : RateLimitResult
}
