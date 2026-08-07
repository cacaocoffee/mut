---
id: ISSUE-034
title: POST /events + analytics_event
domain: —
layer: api
wave: 7
status: TODO
depends_on: [ISSUE-003, ISSUE-007]
fr: []
r: []
inv: []
nfr: [NFR-R-04]
migration: V034
owns:
  - apps/api/src/main/kotlin/kr/kcocktail/common/analytics/**
  - apps/api/src/main/resources/db/migration/V034__*.sql
---

## 근거

**SPEC-10 §1 왜 지금 쓰나**

> **이벤트는 소급이 안 된다.** 나중에 심으면 그 기간의 데이터가 **영원히 없다.**
> 3개월 뒤 "유입이 늘었나"를 물어도 비교할 과거가 없다.
> 지금 화면이 셋뿐이라 심을 지점도 적다. **코드가 커진 뒤에는 호출 지점을 찾아 흩뿌려야 한다.**

**SPEC-10 §2 원칙**

| | |
|---|---|
| **자체 저장이 기본** | 파트너 대시보드가 이 데이터를 조회한다. 외부 도구에만 쌓으면 우리 화면을 못 만든다 |
| **개인 식별 금지** | `session_id`(UUID)와 `user_id`만. **IP·좌표·User-Agent 원문을 저장하지 않는다** |
| **멱등** | `Idempotency-Key` 필수. **재시도가 집계를 부풀리지 않는다** (`PRIN-T07`) |
| **실패해도 조용히** | **계측 실패가 사용자 흐름을 막지 않는다.** 삼키고 로그만 남긴다 |
| **배치 전송** | 페이지당 여러 이벤트를 모아 한 번에 `POST /events` |

**SPEC-10 §3 공통 필드** — `eventType` · `sessionId`(UUID, **30분 무활동 시 갱신**) · `userId`(비로그인 null) · `occurredAt` · `path`(**쿼리스트링 제외**) · `referrerType` · `payload`

> `referrerType`을 **원본 URL이 아니라 분류값으로** 저장한다. 유기 검색 비중(PRD 2.2)을 세는 데는 이걸로 충분하고, **원본 URL은 개인정보가 섞일 수 있다.**
> 값: `organic` · `internal` · `social` · `direct` · `unknown`

**SPEC-10 §7 수집 API**

| 항목 | |
|---|---|
| 인증 | **불필요** |
| CSRF | **면제** — 부작용이 집계뿐 |
| 레이트 리밋 | **120 req/min** (세션 기준) |
| 배치 상한 | **요청당 50건** |
| 응답 | **`202 Accepted`.** 본문 없음 |

> **`202`를 쓰는 이유** — 클라이언트가 처리 결과를 기다릴 필요가 없다. **검증 실패한 이벤트는 버리고 서버 로그에만 남긴다.** 사용자 흐름을 막지 않는다.

**SPEC-06 §3.8 `analytics_event`**

| 컬럼 | 타입 |
|---|---|
| `event_type` | `VARCHAR(24)` |
| `session_id` | `UUID` |
| `user_id` | `BIGINT` NULL 허용 |
| `from_type` `from_id` `to_type` `to_id` | `cross_nav`용 (Phase 1b) |
| `payload` | `JSONB` |
| `occurred_at` | `TIMESTAMPTZ` |

> `occurred_at` 기준 **월 단위 파티셔닝을 전제**로 설계한다. **Phase 1에는 단일 테이블**로 두되 **파티션 키가 될 컬럼을 PK에 포함**시켜 나중에 쪼갤 수 있게 한다.

**SPEC-10 §8 저장·보존**: 원본 **13개월** (전년 동월 비교). 탈퇴 시 `user_id`만 **NULL 익명화, 행은 남긴다**
**SPEC-08 §5.3**: `analytics_event.user_id` → **NULL로 익명화** (집계 지표가 소급 변동하면 안 된다)
**`NFR-R-04`**: 이벤트 수집 실패가 **사용자 흐름을 막지 않는다** — 배포 차단

**SPEC-10 §9 구현 순서**: **1번이 "`POST /events` + `analytics_event` — 받을 곳이 먼저"**

## RED

### 수집 API (SPEC-10 §7)

1. `인증_없이_수집된다`
2. `CSRF_토큰_없이_수집된다` — 면제 (이슈 007 RED 7)
3. `202를_반환한다` — 200이 아니다
4. `응답_본문이_없다`
5. `배치로_여러_이벤트를_받는다`
6. `요청당_50건_상한이_있다`
7. `50건_초과시_거부되거나_절삭된다` ⚖️ — **보수적으로 400** + GAPS
8. `레이트_리밋_120rpm이_세션_기준이다` (SPEC-08 §6 — IP가 아니다)

### 멱등 (`PRIN-T07`, SPEC-10 §2)

9. `Idempotency_Key가_필수다`
10. `키가_없으면_400`
11. `같은_키_재요청이_중복_저장하지_않는다` — **재시도가 집계를 부풀리지 않는다**
12. `같은_키_재요청도_202를_반환한다`
13. `다른_키는_각각_저장된다`

### 실패 격리 (`NFR-R-04`, SPEC-10 §2·§7)

14. `검증_실패한_이벤트는_버려진다` — 전체 요청이 실패하지 않는다
15. `일부_이벤트가_잘못돼도_나머지는_저장된다`
16. `검증_실패가_서버_로그에_남는다`
17. `저장_실패가_202를_막지_않는다` ⚖️ — DB 장애 시. **보수적으로 202 유지 + 로그** (사용자 흐름 우선) + GAPS
18. `알_수_없는_event_type이_거부되고_나머지는_저장된다`

### 공통 필드 (SPEC-10 §3)

19. `eventType이_필수다`
20. `sessionId가_UUID다`
21. `userId가_null_허용이다` — 비로그인
22. `occurredAt이_필수다`
23. `path에_쿼리스트링이_없다` — **제거하거나 거부** ⚖️ 보수적으로 **서버에서 절삭** + GAPS
24. `referrerType_5종만_허용` — `organic`·`internal`·`social`·`direct`·`unknown`
25. `원본_referrer_URL을_저장하지_않는다` (SPEC-10 §3 — 개인정보)

### 개인 식별 금지 (SPEC-10 §2·§10, `PRIN-D04`)

26. `IP_컬럼이_없다`
27. `User_Agent_컬럼이_없다`
28. `좌표가_payload에_들어가면_거부된다` (이슈 033 RED 20·21)
29. `payload가_알려진_필드만_받는다` ⚖️ — 임의 필드 허용 시 개인정보가 샌다. **보수적으로 event_type별 스키마 검증** + GAPS

### 파티셔닝 대비 (SPEC-06 §3.8)

30. `PK에_occurred_at이_포함된다` — 나중에 쪼갤 수 있게
31. `Phase_1a는_단일_테이블이다`
32. `event_type_occurred_at_인덱스가_있다` (SPEC-06 §5)

### Phase 1b 자리 (SPEC-10 §5)

33. `cross_nav용_from_to_컬럼이_있다` — 1b에서 쓴다
34. `1b_event_type이_미리_정의돼_있다` — `bar_view`·`cross_nav`·`partner_action`

### 익명화 (SPEC-08 §5.3, SPEC-10 §8)

35. `탈퇴시_user_id가_NULL이_되고_행은_남는다` — **이슈 005 RED 26의 `@Disabled` 해제**
36. `익명화_후에도_집계가_변하지_않는다` — 행 수 동일

## GREEN

### `V034__analytics_event.sql`

```sql
CREATE TABLE analytics_event (
  id BIGINT GENERATED ALWAYS AS IDENTITY,
  event_type VARCHAR(24) NOT NULL,
  session_id UUID NOT NULL,
  user_id BIGINT,                          -- FK 없음: 탈퇴 시 NULL 익명화 (SPEC-08 §5.3)
  path VARCHAR(255),
  referrer_type VARCHAR(12) CHECK (referrer_type IN
    ('organic','internal','social','direct','unknown')),
  from_type VARCHAR(12), from_id BIGINT,   -- cross_nav (Phase 1b)
  to_type VARCHAR(12), to_id BIGINT,
  payload JSONB,
  occurred_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (id, occurred_at)            -- SPEC-06 §3.8 — 파티션 키 포함 (RED 30)
);
CREATE INDEX ON analytics_event (event_type, occurred_at);   -- SPEC-06 §5
CREATE INDEX ON analytics_event (session_id, occurred_at);
```

**IP·User-Agent 컬럼이 없다** (RED 26·27) — SPEC-10 §10.
`user_id`에 **FK를 걸지 않는다** — 이슈 014의 `audit_log`와 같은 이유(탈퇴 시 행 유지).

### `common/analytics`

```kotlin
@PostMapping("/api/v1/events")
fun collect(
    @RequestHeader("Idempotency-Key") key: String,
    @RequestBody req: EventBatch,
): ResponseEntity<Void> = ResponseEntity.accepted().build()   // 202, 본문 없음
```

### 부분 실패 (RED 14·15·18 — 이 이슈의 요체)

```kotlin
// SPEC-10 §7 — "검증 실패한 이벤트는 버리고 서버 로그에만 남긴다"
val (valid, invalid) = req.events.partition { it.isValid() }
invalid.forEach { log.warn("dropped invalid event: {}", it.eventType) }
repository.saveAll(valid)
// 언제나 202 — NFR-R-04
```

**전체를 롤백하지 않는다.** 하나가 잘못됐다고 페이지의 다른 이벤트를 버리면 데이터가 더 나빠진다.

### payload 스키마 검증 (RED 29)

```kotlin
sealed interface EventPayload
data class CocktailViewPayload(val cocktailSlug: String, val entryPoint: String) : EventPayload
data class SearchMissPayload(val query: String, val matchedCount: Int, val hadChosung: Boolean) : EventPayload
// ...
```

**event_type별 타입을 정의한다** (SPEC-10 §4). 임의 JSON을 받으면 좌표·개인정보가 샌다 (RED 28).

### 멱등 (RED 9~13)

이슈 003의 `IdempotencyFilter`를 재사용한다. `/events`가 SPEC-07 §1.7의 대상 중 하나다.

**하지 말 것**:
- 이벤트 심기 — 이슈 035 (FE)
- 집계·대시보드 — Phase 1b·2
- 파티셔닝 — Phase 1a는 단일 테이블
- 외부 분석 도구 연동 — 병행 가능하나 범위 밖 (SPEC-10 §2.1)

## DoD

- [ ] RED 36항 전부 통과
- [ ] **202 + 본문 없음** (RED 3·4 — SPEC-10 §7)
- [ ] **부분 실패 시 나머지 저장** (RED 15 — `NFR-R-04`)
- [ ] 멱등 (RED 11 — `PRIN-T07`)
- [ ] IP·UA·좌표 부재 (RED 26~28 — SPEC-10 §10, `PRIN-D04`)
- [ ] PK에 `occurred_at` 포함 (RED 30 — 파티셔닝 대비)
- [ ] **이슈 005 RED 26의 `@Disabled` 해제** (익명화)
- [ ] ⚖️ 4건(50건 초과·저장 실패·path 절삭·payload 스키마) `GAPS.md` 등재
- [ ] 커밋: `feat(api): 이벤트 수집 API·analytics_event (SPEC-10 §7, PRIN-T07, NFR-R-04)`
