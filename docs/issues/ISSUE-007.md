---
id: ISSUE-007
title: CSRF + 레이트 리밋
domain: USER
layer: api
wave: 1
status: TODO
depends_on: [ISSUE-005]
fr: []
r: []
inv: []
nfr: [NFR-SEC-02, NFR-SEC-05]
migration: —
owns:
  - apps/api/src/main/kotlin/kr/kcocktail/common/security/csrf/**
  - apps/api/src/main/kotlin/kr/kcocktail/common/security/ratelimit/**
---

## 근거

**SPEC-08 §4.3 CSRF** — 쿠키 인증이므로 CSRF 방어가 필수다.

- 상태 변경(`POST`·`PATCH`·`PUT`·`DELETE`)은 `X-CSRF-Token` 헤더를 요구한다
- 토큰은 `GET /auth/csrf`가 발급, **세션에 바인딩**
- `SameSite=Lax`가 1차 방어, CSRF 토큰이 2차

> **`POST /events`(이벤트 수집)는 예외다** — 인증이 필요 없고 부작용이 집계뿐이라 CSRF 대신 **레이트 리밋으로 방어**한다.

**SPEC-08 §6 레이트 리밋**

| 대상 | 한도 | 기준 |
|---|---|---|
| 공개 조회 API | **300 req/min** | IP |
| `/search` · `/search/suggest` | **60 req/min** | IP |
| `/events` | **120 req/min** | 세션 |
| `/auth/*/callback` | **10 req/min** | IP |
| 어드민 쓰기 | **60 req/min** | 사용자 |

초과 시 `429` + `Retry-After`.

> 검색을 더 조이는 이유는 초성·별칭 매칭이 GIN 인덱스를 타긴 해도 **가장 비싼 조회**이기 때문이다 (SPEC-06 §5).

**SPEC-08 §7 주요 위협**

| 위협 | 방어 |
|---|---|
| CSRF | `SameSite=Lax` + CSRF 토큰 |
| 대량 크롤링 | 레이트 리밋 + `analytics_event` 이상 탐지 |
| **저장 XSS (에디터 입력)** | 본문은 마크다운 → **서버에서 sanitize 후 저장** |

> sanitize 지점을 **저장 시점**으로 잡은 이유는, 렌더링 시점에만 처리하면 **SSG 빌드·어드민 미리보기·OG 태그 생성 등 출력 경로마다 놓칠 수 있기** 때문이다.

**SPEC-07 §1.2**: 상태 변경 요청은 CSRF 토큰을 요구한다. `GET /api/v1/auth/csrf`가 발급하고 `X-CSRF-Token` 헤더로 보낸다.

**`NFR-R-04`**: 이벤트 수집 실패가 사용자 흐름을 막지 않는다.

## RED

### CSRF (SPEC-08 §4.3)

1. `GET_auth_csrf가_토큰을_발급한다`
2. `토큰이_세션에_바인딩된다` — 다른 세션의 토큰은 거부
3. `POST에_CSRF_토큰이_없으면_403`
4. `PATCH_PUT_DELETE도_토큰을_요구한다`
5. `GET_HEAD_OPTIONS는_토큰을_요구하지_않는다`
6. `잘못된_토큰은_403`
7. `POST_events는_CSRF_면제다` (SPEC-08 §4.3 예외)
8. `events_외의_비인증_POST는_면제가_아니다` — 면제 목록이 최소인지 확인
9. `면제_경로_목록이_코드_상수다` — 설정으로 늘릴 수 없다

### 레이트 리밋 (SPEC-08 §6 — 5개 한도 전수)

10. `공개조회_300rpm_초과시_429` (IP 기준)
11. `search는_60rpm에서_제한된다` (IP)
12. `search_suggest도_60rpm이다`
13. `events는_120rpm이다` (**세션** 기준 — IP가 아니다)
14. `auth_callback은_10rpm이다` (IP)
15. `어드민_쓰기는_60rpm이다` (**사용자** 기준)
16. `429_응답에_Retry_After가_있다`
17. `한도_기준이_대상마다_다르다` — IP / 세션 / 사용자 구분 검증
18. `한도값이_설정으로_주입된다` — 하드코딩 금지
19. `제한_해제_후_다시_통과한다` — 윈도우 만료

### XSS sanitize (SPEC-08 §7)

20. `에디터_본문이_저장_시점에_sanitize된다`
21. `script_태그가_제거된다`
22. `onerror_등_이벤트_핸들러_속성이_제거된다`
23. `javascript_프로토콜_링크가_제거된다`
24. `마크다운_정상_문법은_보존된다` — 과잉 제거 방지
25. `렌더링_시점_sanitize에_의존하지_않는다` — 저장된 값 자체가 안전 (SPEC-08 §7의 근거)

### 실패 격리 (`NFR-R-04`)

26. `레이트리밋_저장소_장애가_요청을_막지_않는다` **결정** — fail-open vs fail-closed. **공개 조회는 fail-open, 어드민 쓰기는 fail-closed**

## GREEN

### CSRF

Spring Security의 `CookieCsrfTokenRepository` 가 아니라 **세션 바인딩 방식**을 쓴다 (SPEC-08 §4.3 "세션에 바인딩").

```kotlin
http.csrf { csrf ->
    csrf.csrfTokenRepository(HttpSessionCsrfTokenRepository())
        .ignoringRequestMatchers("/api/v1/events")     // RED 7 — 유일한 면제
}
```

면제 목록을 **코드 상수**로 둔다 (RED 9). 설정 파일로 빼면 늘어난다.

### 레이트 리밋

Phase 1에 Redis를 들이지 않는다 (SPEC-08 §9 — "Phase 1은 DB 세션으로 충분"). 인메모리 토큰 버킷(Bucket4j 등)으로 시작하되, **인스턴스가 늘면 재검토**한다는 주석을 남긴다.

```kotlin
enum class RateLimitPolicy(val limit: Int, val window: Duration, val key: KeyBy) {
    PUBLIC_READ(300, MINUTE, IP),        // SPEC-08 §6
    SEARCH(60, MINUTE, IP),
    EVENTS(120, MINUTE, SESSION),
    AUTH_CALLBACK(10, MINUTE, IP),
    ADMIN_WRITE(60, MINUTE, USER),
}
```

표를 그대로 enum으로. RED 10~15가 이것을 전수 검증한다.

### sanitize

```kotlin
// 저장 시점 (SPEC-08 §7) — 렌더링 시점이 아니다
object MarkdownSanitizer {
    fun sanitize(raw: String): String
}
```

OWASP Java HTML Sanitizer 또는 마크다운 파서 + allowlist. **마크다운을 HTML로 변환한 뒤 sanitize**할지, 마크다운 원문을 검사할지가 판단 지점 — 저장은 마크다운 원문이므로 **원문에서 위험 패턴을 제거**하고, 렌더링은 안전한 파서를 쓴다.

RED 25가 "렌더링 시점에 의존하지 않는다"를 강제한다.

**하지 말 것**:
- OAuth 콜백 구현 — 이슈 030 (레이트 리밋 정책만 정의)
- 이벤트 수집 엔드포인트 — 이슈 034 (면제 등록만)

## DoD

- [ ] RED 26항 전부 통과
- [ ] `RateLimitPolicy` 가 SPEC-08 §6 표와 1:1, 5종 전수 (RED 10~15)
- [ ] CSRF 면제가 `/events` **하나뿐**이고 코드 상수 (RED 7·9)
- [ ] sanitize가 **저장 시점** (RED 25)
- [ ] **결정** 레이트리밋 저장소 장애 시 fail 정책 `GAPS.md` 등재
- [ ] 커밋: `feat(user): CSRF·레이트 리밋·저장 시점 sanitize (SPEC-08 §4.3·§6·§7)`
