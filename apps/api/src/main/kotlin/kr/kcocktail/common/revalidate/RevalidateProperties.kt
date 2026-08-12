package kr.kcocktail.common.revalidate

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 재생성 훅 설정 (SPEC-07 §4).
 *
 * ```yaml
 * kcocktail:
 *   revalidate:
 *     enabled: true
 *     url: https://k-cocktail.example
 *     secret: ${REVALIDATE_SECRET}
 * ```
 *
 * **시크릿은 환경변수에서만 온다** (RED 18). 기본값을 두지 않는 이유는,
 * 기본값이 있으면 설정을 빠뜨린 채로도 기동해서 **훅이 조용히 401 로 실패**하기 때문이다.
 * 빠뜨렸으면 기동에서 걸리는 편이 낫다 ([RevalidateStartupCheck]).
 *
 * `enabled` 기본이 `false` 인 이유는 DECISIONS §1 이 "로컬 비활성화 허용, 운영 필수" 라고
 * 정했기 때문이다. 로컬에서 프론트를 안 띄웠다고 발행이 막히면 안 된다 —
 * 어차피 실패해도 발행은 유지된다 (`NFR-R-03`).
 */
@ConfigurationProperties(prefix = "kcocktail.revalidate")
data class RevalidateProperties(
    val enabled: Boolean = false,

    /** 프론트 원본. 훅은 여기에 `/api/revalidate` 를 붙인다. */
    val url: String = "",

    /** `X-Revalidate-Secret` 헤더로 나간다. **로그에 찍지 않는다** (RED 20). */
    val secret: String = "",

    /**
     * 연결·응답 타임아웃. 짧게 잡는다 — 훅은 발행을 막지 않지만
     * 스레드를 오래 붙들고 있을 이유도 없다 (`NFR-O-02` 30초 예산의 서버 몫).
     */
    val timeoutMs: Long = 2_000,
) {
    val endpoint: String get() = "${url.trimEnd('/')}/api/revalidate"

    /** 설정이 갖춰졌는가. 시크릿 값 자체는 절대 밖으로 내보내지 않는다. */
    val isConfigured: Boolean get() = url.isNotBlank() && secret.isNotBlank()
}
