package kr.kcocktail.common.security.ratelimit

import java.time.Duration

/**
 * SPEC-08 §6 레이트 리밋. **표를 그대로 옮긴 것이다.**
 *
 * | 대상 | 한도 | 기준 |
 * |---|---|---|
 * | 공개 조회 API | 300 req/min | IP |
 * | `/search` · `/search/suggest` | 60 req/min | IP |
 * | `/events` | 120 req/min | **세션** |
 * | `/auth/{provider}/callback` | 10 req/min | IP |
 * | 어드민 쓰기 | 60 req/min | **사용자** |
 *
 * ## 기준이 대상마다 다르다
 *
 * 하나로 뭉뚱그리면 안 된다. `/events` 를 IP 기준으로 잡으면 **공유 IP 뒤의 사용자들이 서로를 막고**,
 * 어드민 쓰기를 IP 로 잡으면 같은 사무실의 두 에디터가 한 통을 나눠 쓴다.
 *
 * ## 검색을 더 조이는 이유
 *
 * 초성·별칭 매칭이 GIN 인덱스를 타긴 해도 **가장 비싼 조회**다 (SPEC-06 §5).
 *
 * ## 한도는 설정으로 주입한다
 *
 * [limit] 은 기본값이고 `kcocktail.rate-limit.<정책>.limit` 으로 덮는다 (RED 18).
 * 하드코딩하면 사고가 났을 때 배포 없이 조일 수 없다.
 */
enum class RateLimitPolicy(
    val defaultLimit: Int,
    val window: Duration,
    val keyBy: KeyBy,
    /** 장애 시 열 것인가 (DECISIONS §1). */
    val onStoreFailure: FailMode,
) {
    /** 공개 조회 — 읽기라 막히면 서비스가 안 보인다. 열어 둔다. */
    PUBLIC_READ(300, MINUTE, KeyBy.IP, FailMode.OPEN),

    SEARCH(60, MINUTE, KeyBy.IP, FailMode.OPEN),

    /** 세션 기준. 인증이 필요 없는 엔드포인트라 CSRF 대신 이것이 방어다 (SPEC-08 §4.3). */
    EVENTS(120, MINUTE, KeyBy.SESSION, FailMode.OPEN),

    /** 콜백은 인증 경로다. 무차별 시도를 막아야 하므로 장애 시에도 닫는다. */
    AUTH_CALLBACK(10, MINUTE, KeyBy.IP, FailMode.CLOSED),

    /** 어드민 쓰기 — 사용자 기준. 쓰기는 되돌리기 어려우니 장애 시 닫는다. */
    ADMIN_WRITE(60, MINUTE, KeyBy.USER, FailMode.CLOSED),
    ;

    /** 설정 키. `kcocktail.rate-limit.public-read.limit` 형태. */
    val configKey: String get() = name.lowercase().replace('_', '-')
}

/**
 * enum 상수보다 먼저 초기화돼야 한다 — companion object 에 두면
 * "uninitialized here" 로 컴파일이 막힌다. enum 상수가 companion 보다 먼저 만들어지기 때문이다.
 */
private val MINUTE: Duration = Duration.ofMinutes(1)

/**
 * 무엇을 한 통으로 셀 것인가.
 *
 * > `IP` 는 **메모리 키로만** 쓴다. 저장하지도 로그에 남기지도 않는다 (DECISIONS §1 · `PRIN-D04`).
 */
enum class KeyBy { IP, SESSION, USER }

/**
 * 카운터 저장소가 죽었을 때 (DECISIONS §1).
 *
 * | | 뜻 | 어디에 |
 * |---|---|---|
 * | [OPEN] | 통과시킨다 | 공개 조회 — 막으면 사이트가 통째로 안 보인다 |
 * | [CLOSED] | 거부한다 | 어드민 쓰기 · 인증 콜백 — 되돌리기 어려운 쪽 |
 *
 * 읽기는 열고 쓰기는 닫는다. 레이트 리밋 장애로 서비스 전체가 죽는 것이
 * 그 순간의 과도한 트래픽보다 나쁘고, 반대로 쓰기는 열어 두면 그 틈에 벌어진 일을 되돌려야 한다.
 */
enum class FailMode { OPEN, CLOSED }
