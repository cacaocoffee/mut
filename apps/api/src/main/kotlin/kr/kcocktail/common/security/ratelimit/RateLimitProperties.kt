package kr.kcocktail.common.security.ratelimit

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * SPEC-08 §6 한도를 **설정으로 덮는다** (RED 18).
 *
 * ```yaml
 * kcocktail:
 *   rate-limit:
 *     enabled: true
 *     limits:
 *       search: 30        # 사고 났을 때 배포 없이 조인다
 * ```
 *
 * 하드코딩하면 사고가 났을 때 코드를 고치고 배포해야 조일 수 있다.
 * **기본값은 스펙 표 그대로**이고 설정은 덮어쓰기일 뿐이다 — 설정이 비면 스펙대로 돈다.
 */
@ConfigurationProperties(prefix = "kcocktail.rate-limit")
open class RateLimitProperties(
    val enabled: Boolean = true,

    /** 정책 키(`public-read` · `search` …) → 한도. 없으면 [RateLimitPolicy.defaultLimit]. */
    val limits: Map<String, Int> = emptyMap(),
) {
    /** `open` 인 이유는 테스트가 저장소 장애를 흉내 내기 때문이다 (RED 26). */
    open fun limitOf(policy: RateLimitPolicy): Int =
        limits[policy.configKey] ?: policy.defaultLimit
}
