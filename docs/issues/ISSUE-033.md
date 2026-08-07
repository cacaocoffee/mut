---
id: ISSUE-033
title: 위치 미저장 검증
domain: USER
layer: api
wave: 6
status: TODO
depends_on: [ISSUE-005]
fr: [FR-USER-006]
r: []
inv: []
nfr: [NFR-SEC-04]
migration: —
owns:
  - apps/api/src/test/kotlin/kr/kcocktail/architecture/LocationAbsenceTest.kt
  - apps/api/src/main/kotlin/kr/kcocktail/common/logging/**
---

## 근거

**`PRIN-D04` — 위치 정보는 저장하지 않는다**

> **세션 내 사용만 한다** (PRD 12장). **유저의 좌표를 DB에 쓰지 않는다.**

**`FR-USER-006`**: 위치 정보는 **세션 내에서만 쓰고 저장하지 않는다** (`PRIN-D04`)

**SPEC-08 §5.2 위치 정보**

> **저장하지 않는다** (`PRIN-D04`). "내 주변 바" 기능은 브라우저 좌표를 받아 **그 요청에서만 쓰고 버린다. DB 컬럼도 로그도 남기지 않는다.**
>
> 거리 계산은 서버가 하되 **좌표를 로그에 남기지 않도록 요청 로깅에서 해당 파라미터를 마스킹**한다.

**`NFR-SEC-04`**: 위치 좌표가 **DB·로그 어디에도 없다** — 측정: **코드·로그 검사**

**SPEC-06 §3.5**: `user` 테이블 — **위치 정보를 저장하는 컬럼도 없다** (`PRIN-D04`)

**SPEC-10 §10 하지 않는 것**

| | 왜 |
|---|---|
| IP · User-Agent 원문 저장 | `PRIN-D04` 정신. `referrerType`으로 충분 |
| **위치 좌표** | **저장 금지** (`PRIN-D04`) |

**SPEC-10 §2 원칙**: **개인 식별 금지** — `session_id`(UUID)와 `user_id`만. **IP·좌표·User-Agent 원문을 저장하지 않는다**

### 이 이슈가 특이한 이유

**이슈 027과 같은 성격이다** — 산출물이 "부재 검증"이다.

Phase 1a에는 "내 주변 바"가 없다(BAR가 1b). 좌표를 받을 일 자체가 없다.
그런데 `FR-USER-006`은 **P0**이고, `NFR-SEC-04`가 "코드·로그 검사"를 측정 방법으로 못박았다.

**1b에서 "내 주변 바"를 만들 때 누군가 `user.last_lat` 컬럼을 추가하거나 좌표를 그대로 로그에 남긴다.** 그때 이 테스트가 빨갛게 된다.

## RED

### DB 컬럼 부재 (`PRIN-D04`, SPEC-06 §3.5)

1. `좌표_컬럼이_전_테이블에_없다` — `lat`·`lng`·`latitude`·`longitude`·`coord`·`location`·`geo` 스캔
2. `bar_테이블은_예외다` ⚖️ — SPEC-06 §3.3의 `bar.lat`·`lng`는 **바의 위치**이지 유저 위치가 아니다. **화이트리스트에 `bar` 등록** (Phase 1b 대비)
3. `화이트리스트가_코드_상수이고_주석이_있다` — 왜 예외인지
4. `user_테이블에_좌표가_없다`
5. `analytics_event에_좌표가_없다` (SPEC-10 §2·§10)
6. `금지_컬럼명_패턴이_코드_상수다`

### 로그 마스킹 (SPEC-08 §5.2, `NFR-SEC-04`)

7. `요청_로깅에서_좌표_파라미터가_마스킹된다`
8. `lat_파라미터가_로그에_없다`
9. `lng_파라미터가_로그에_없다`
10. `마스킹이_쿼리스트링과_본문_양쪽에_적용된다`
11. `마스킹_대상_파라미터명_목록이_코드_상수다`
12. `에러_로그에도_좌표가_없다` — 예외 스택에 요청 본문이 딸려 나가는 경로
13. `접근_로그에_좌표가_없다`

### IP · User-Agent (SPEC-10 §2·§10)

14. `analytics_event에_IP_컬럼이_없다`
15. `analytics_event에_User_Agent_컬럼이_없다`
16. `IP가_로그에_원문으로_남는가` ⚖️ — 레이트 리밋(SPEC-08 §6)이 IP 기준이라 **메모리에서는 쓴다**. SPEC-10 §10은 **"저장"** 금지다. **보수적으로 저장·로그 금지, 메모리 사용만 허용** + GAPS

### 세션 내 사용 (`FR-USER-006`, SPEC-08 §5.2)

17. `좌표를_받는_엔드포인트가_Phase_1a에_없다` — 현재 상태 확인
18. `좌표_수신_계약이_정의돼_있다` — 1b 대비. 쿼리 파라미터로 받고 **응답 후 버린다**
19. `좌표가_세션에_저장되지_않는다` — 세션 속성에도 금지 ⚖️ ("세션 내 사용"이 세션 저장을 뜻하는지. **보수적으로 요청 스코프만**) + GAPS

### 이벤트 (SPEC-10)

20. `이벤트_payload에_좌표가_들어가지_않는다`
21. `payload_검증이_좌표_필드를_거부한다` — 이슈 034 연계

## GREEN

### `LocationAbsenceTest.kt`

```kotlin
/**
 * PRIN-D04 · FR-USER-006 · SPEC-08 §5.2 · NFR-SEC-04 · SPEC-10 §10
 *
 * 이 테스트는 "저장하지 않았다"를 검증한다. 이슈 027과 같은 성격이다.
 *
 * Phase 1a에는 좌표를 받는 기능이 없다. 이 테스트는 1b의 "내 주변 바"에서
 * 좌표가 DB나 로그로 새는 것을 막기 위해 지금 세운다.
 *
 * 되돌리는 조건: SPEC-00 PRIN-D04 개정 + ADR (SPEC-00 §4)
 */
@Tag("boundary")
class LocationAbsenceTest {
    private val FORBIDDEN = listOf("lat", "lng", "latitude", "longitude", "coord", "geo_")
    private val ALLOWED_TABLES = setOf("bar")     // 바의 위치는 업소 정보다 (RED 2·3)
}
```

`boundaryTest`에 포함 (이슈 001).

### 로그 마스킹 (`common/logging`)

```kotlin
// SPEC-08 §5.2 — "요청 로깅에서 해당 파라미터를 마스킹한다"
object SensitiveParamMasker {
    private val MASKED = setOf("lat", "lng", "latitude", "longitude")
    fun mask(params: Map<String, String>): Map<String, String> =
        params.mapValues { (k, v) -> if (k.lowercase() in MASKED) "***" else v }
}
```

**요청 로깅 필터·에러 핸들러 양쪽에 적용한다** (RED 10·12). 한쪽만 하면 예외 경로로 샌다.

### 컬럼 스캔 (RED 1)

```sql
SELECT table_name, column_name FROM information_schema.columns WHERE table_schema='public'
```

금지 패턴과 대조하되 `ALLOWED_TABLES` 제외.

⚖️ **`bar.lat`/`lng` 예외의 근거**: SPEC-06 §3.3이 명시했고, 이것은 **업소의 공개 위치**이지 개인의 위치가 아니다. `PRIN-D04`는 "**유저의** 좌표"를 금지한다. 주석에 이 구분을 남긴다 (RED 3).

### IP (RED 16)

레이트 리밋(SPEC-08 §6)이 IP 기준이라 **런타임에는 IP를 안다**. SPEC-10 §10은 "저장"을 금지한다.
→ **메모리 버킷 키로만 사용하고 DB·로그에 남기지 않는다.** GAPS 등재.

**하지 말 것**:
- "내 주변 바" — Phase 1b
- 거리 계산 — Phase 1b

## DoD

- [ ] RED 21항 전부 통과
- [ ] 테스트가 `boundaryTest`에 포함돼 **CI 상시 실행**
- [ ] `bar` 예외에 **근거 주석** (RED 3 — 업소 위치 vs 유저 위치)
- [ ] 로그 마스킹이 **정상·에러 경로 양쪽** (RED 10·12)
- [ ] `EPICS-1B-PHASE2.md`에 "내 주변 바" 착수 시 이 테스트 확인 항목 등재
- [ ] ⚖️ 3건(IP 런타임 사용·세션 저장 범위·좌표 수신 계약) `GAPS.md` 등재
- [ ] 커밋: `test(user): 위치 미저장 검증 (FR-USER-006, PRIN-D04, NFR-SEC-04)`
