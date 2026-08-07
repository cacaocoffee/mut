---
id: ISSUE-003
title: REST 규약 — Problem Details · violations · 페이징 · ETag · 멱등
domain: —
layer: api
wave: 0
status: TODO
depends_on: [ISSUE-000]
fr: [FR-ADMIN-003]
r: []
inv: []
nfr: [NFR-R-04]
migration: V003
owns:
  - apps/api/src/main/kotlin/kr/kcocktail/common/web/**
  - apps/api/src/main/resources/db/migration/V003__*.sql
---

## 근거

**SPEC-07 §1.1 기본**

| 항목 | 규칙 |
|---|---|
| 베이스 | `/api/v1` |
| 형식 | `application/json; charset=utf-8` |
| 명명 | 경로는 `kebab-case` **복수형**, 필드는 `camelCase` |
| 공개 식별자 | **`slug`** — 공개 리소스는 `id`를 노출하지 않는다 (`PRIN-D02`) |
| 내부 식별자 | **어드민·파트너 API만** `id` 사용 |
| 시각 | ISO 8601 UTC |

> 주의: 경로는 복수형(`/cocktails`)이지만 **테이블은 단수형**(`cocktail`)이다 (SPEC-06 §1.1). 헷갈리기 쉬운 지점이다.

**SPEC-07 §1.4 에러 — RFC 9457 Problem Details** (`application/problem+json`)

```json
{
  "type": "https://api.example.kr/problems/validation-failed",
  "title": "요청을 처리할 수 없습니다",
  "status": 422,
  "detail": "향과 맛 서술이 비어 있어 발행할 수 없습니다.",
  "instance": "/api/v1/admin/cocktails/12/publish",
  "violations": [
    { "code": "GATE-COCKTAIL-01", "field": "tastingNote", "message": "향과 맛 서술은 발행 필수입니다." },
    { "code": "GATE-COCKTAIL-05", "field": "story",       "message": "클래식으로 분류된 항목은 관련 이야기가 필요합니다." }
  ]
}
```

- **`violations`는 항상 배열이고 실패한 항목을 전부 담는다.** `FR-ADMIN-003`이 "하나씩 고치게 하지 않는다"고 요구한 지점. **첫 실패에서 멈추지 않는다**
- `code`는 SPEC-02의 `INV-` / `GATE-` ID를 그대로 쓴다. **클라이언트가 문구가 아니라 코드로 분기**할 수 있어야 한다

| 상태 | 쓰는 곳 |
|---|---|
| `400` | 문법적으로 잘못된 요청 |
| `401` | 미인증 |
| `403` | 권한 없음 |
| `404` | 없음. **비공개 리소스도 404** — 존재 여부를 흘리지 않는다 |
| `409` | 상태 충돌 (이미 발행됨 등) |
| `422` | **도메인 규칙 위반** — `violations` 포함 |
| `429` | 레이트 리밋 |

**SPEC-07 §1.5 페이지네이션** — `?page=0&size=24&sort=abv,asc`

```json
{ "items": [ … ], "page": { "number": 0, "size": 24, "totalElements": 137, "totalPages": 6 } }
```

Phase 1 규모(칵테일 500)에서는 offset으로 충분하다. 커서는 수천 건을 넘을 때.

**SPEC-07 §1.6 캐싱** — 공개 조회 API에 `ETag` + `Cache-Control: public, max-age=60, stale-while-revalidate=600`. **SSG 빌드가 같은 엔드포인트를 반복 호출하므로 실효가 크다**

**SPEC-07 §1.7 멱등성** (`PRIN-T07`) — 재시도가 전제인 요청은 `Idempotency-Key` 요구. 같은 키는 첫 결과를 그대로 돌려준다. 대상: **이벤트 수집** · 쿠폰 사용(P3) · 알림 발송

**422와 400의 구분이 중요하다.** 400은 "JSON이 깨졌다", 422는 "값이 도메인 규칙을 어겼다". 이슈마다 다르게 쓰면 클라이언트가 분기할 수 없다.

## RED

### Problem Details (SPEC-07 §1.4)

1. `에러응답_Content_Type이_application_problem_json이다`
2. `에러응답에_type_title_status_detail_instance가_있다`
3. `422_응답에_violations_배열이_있다`
4. `violations는_실패항목을_전부_담는다` — 게이트 2개가 동시에 실패하면 2건 (FR-ADMIN-003)
5. `violations_각_항목에_code_field_message가_있다`
6. `code는_INV_또는_GATE_ID_형식이다` — 문자열 리터럴이 아니라 enum에서 나옴
7. `성공_응답에는_violations가_없다`

### 상태 코드 매핑

8. `문법오류는_400`
9. `미인증은_401`
10. `권한없음은_403`
11. `없는_리소스는_404`
12. `비공개_리소스도_404다` — `draft` 조회 시 403이 아니라 404 (SPEC-07 §1.4 "존재 여부를 흘리지 않는다")
13. `상태충돌은_409` — 이미 발행된 것 재발행
14. `도메인규칙_위반은_422`
15. `레이트리밋_초과는_429이고_Retry_After가_있다`
16. `처리되지_않은_예외는_500이고_내부정보를_노출하지_않는다` — 스택트레이스·SQL·클래스명 미포함

### 페이징 (SPEC-07 §1.5)

17. `기본값은_page0_size24`
18. `응답에_items와_page_객체가_있다`
19. `page에_number_size_totalElements_totalPages가_있다`
20. `size_상한을_넘으면_상한으로_절삭된다` — 무제한 조회 방지
21. `sort_파라미터가_허용목록_밖이면_400` — 인덱스 없는 컬럼 정렬로 풀스캔 유발 방지

### 캐싱 (SPEC-07 §1.6)

22. `공개_조회에_ETag가_붙는다`
23. `If_None_Match_일치시_304를_반환한다`
24. `공개_조회에_Cache_Control_max_age_60이_붙는다`
25. `어드민_API에는_캐시_헤더가_붙지_않는다`

### 멱등성 (SPEC-07 §1.7, `PRIN-T07`)

26. `같은_IdempotencyKey로_두번_POST하면_부수효과가_한번만_발생한다`
27. `같은_키_재요청은_최초_응답을_그대로_반환한다`
28. `다른_키는_각각_처리된다`
29. `같은_키에_다른_본문이_오면_거부된다` — 키 재사용 공격 방지

### 명명 규약

30. `공개_응답에_내부_id가_없다` — `slug`만 (SPEC-07 §1.1)
31. `응답_필드는_camelCase다`
32. `경로는_kebab_case_복수형이다`

## GREEN

### `V003__idempotency.sql`

```sql
CREATE TABLE idempotency_key (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  key VARCHAR(120) NOT NULL UNIQUE,
  request_fingerprint VARCHAR(64) NOT NULL,   -- RED 29
  response_status SMALLINT,
  response_body JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at TIMESTAMPTZ
);
```

TTL 정리는 배치로 (이슈 034 이후 또는 별도).

### `common/web`

```kotlin
// SPEC-02의 INV-/GATE- ID를 그대로 쓴다 (SPEC-07 §1.4)
enum class ViolationCode(val httpStatus: HttpStatus) {
    `INV-COCKTAIL-01`(UNPROCESSABLE_ENTITY),   // 또는 INV_COCKTAIL_01 + 직렬화 매핑
    `GATE-COCKTAIL-01`(UNPROCESSABLE_ENTITY),
    ...
}

data class Violation(val code: String, val field: String?, val message: String)
```

- `ProblemDetail`은 Spring 6의 내장 타입을 쓰고 `violations` 를 확장 프로퍼티로 추가
- `@RestControllerAdvice` — 예외 → 상태 코드 매핑을 **한 곳에서**
- `PageResponse<T>` — SPEC-07 §1.5 형태 그대로
- `IdempotencyFilter` — 헤더 존재 시 키 선점 → 처리 → 응답 저장
- `ETagFilter` 또는 `ShallowEtagHeaderFilter` + 캐시 헤더 인터셉터

### 도메인 예외 → violations

**게이트 검사는 첫 실패에서 멈추면 안 된다** (RED 4). 이 이슈는 그것을 **표현할 수 있는 구조**를 만든다:

```kotlin
class DomainViolationException(val violations: List<Violation>) : RuntimeException()
```

실제 게이트 로직은 이슈 013이 채운다. 여기서는 예외 → 응답 변환까지.

### `ViolationCode` 의 정본

SPEC-02의 `INV-*`·`GATE-*` 목록과 1:1이어야 한다. **문자열 리터럴을 코드에 흩뿌리지 않는다** (RED 6).

**하지 말 것**: 실제 엔드포인트. OpenAPI 생성 설정(이슈 004).

## DoD

- [ ] RED 32항 전부 통과
- [ ] `ViolationCode` enum이 SPEC-02의 `INV-`/`GATE-` ID와 대응 (RED 6)
- [ ] 500 응답에 내부 정보 없음 (RED 16)
- [ ] `violations`가 전부 반환되는 구조 (RED 4 — FR-ADMIN-003)
- [ ] 커밋: `feat(api): REST 규약 Problem Details·페이징·ETag·멱등 (SPEC-07 §1, FR-ADMIN-003)`
