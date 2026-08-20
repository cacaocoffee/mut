# SPEC-10 — 계측 · 이벤트

| | |
|---|---|
| 버전 | v1.0 |
| 최종 수정 | 2026-08-06 |
| 상위 문서 | [SPEC-01 범위](./SPEC-01_시스템개요_범위.md) · [SPEC-06 ERD §3.8](./SPEC-06_데이터모델_ERD.md) |
| 해소 | [G-10](../prd/GAPS.md#g-10) |

---

## 1. 왜 지금 쓰나

**이벤트는 소급이 안 된다.** 나중에 심으면 그 기간의 데이터가 영원히 없다.
3개월 뒤 "유입이 늘었나"를 물어도 비교할 과거가 없다.

지금 화면이 셋뿐이라 심을 지점도 적다. 코드가 커진 뒤에는 호출 지점을 찾아 흩뿌려야 한다.

## 2. 원칙

| | |
|---|---|
| **자체 저장이 기본** | 파트너 대시보드가 이 데이터를 조회한다. 외부 도구에만 쌓으면 우리 화면을 못 만든다 ([SPEC-06 §3.8](./SPEC-06_데이터모델_ERD.md)) |
| **개인 식별 금지** | `session_id`(UUID)와 `user_id`만. IP·좌표·User-Agent 원문을 저장하지 않는다 |
| **멱등** | `Idempotency-Key` 필수. 재시도가 집계를 부풀리지 않는다 (`PRIN-T07`) |
| **실패해도 조용히** | 계측 실패가 사용자 흐름을 막지 않는다. 삼키고 로그만 남긴다 |
| **배치 전송** | 페이지당 여러 이벤트를 모아 한 번에 `POST /events` |

### 2.1 외부 분석 도구

병행해도 된다. 다만 **`cross_nav`와 `partner_action`은 반드시 자체 저장**한다 —
`R-F4.3-2`(유입 칵테일 랭킹)가 이 두 개에 의존하고, 파트너에게 보여줘야 하는 화면이다.

---

## 3. 공통 필드

모든 이벤트가 갖는다.

| 필드 | 타입 | 비고 |
|---|---|---|
| `eventType` | string | 아래 목록 |
| `sessionId` | UUID | 클라이언트 생성. **30분 무활동 시 갱신** |
| `userId` | bigint? | 비로그인은 `null` |
| `occurredAt` | ISO8601 | 클라이언트 시각 |
| `path` | string | 발생 경로. 쿼리스트링 제외 |
| `referrerType` | enum | `organic` · `internal` · `social` · `direct` · `unknown` |
| `payload` | object | 이벤트별 |

`referrerType`을 **원본 URL이 아니라 분류값으로** 저장한다. 유기 검색 비중(PRD 2.2)을
세는 데는 이걸로 충분하고, 원본 URL은 개인정보가 섞일 수 있다.

---

## 4. Phase 1a 이벤트

바 없이 성립하는 것들. **이게 지금 심을 전부다.**

### 4.1 `cocktail_view`

| payload | |
|---|---|
| `cocktailSlug` | |
| `entryPoint` | `search` · `category` · `related` · `finder` · `external` |

**무엇을 답하나** — 어떤 칵테일이 실제로 읽히나. 카테고리 페이지가 유입을 만드나.
`entryPoint`가 `external`인 비율이 곧 SEO 성과다.

### 4.2 `filter_apply`

| payload | |
|---|---|
| `axis` | `sweet` · `base` · `style` · **`method`** · `flavor` · `abv` · `query` |
| `value` | 선택값 |
| `resultCount` | 적용 후 결과 수 |
| `activeAxisCount` | 동시에 걸린 축 개수 |

`method`(메이킹)는 이슈 040 이 `FR-SEARCH-001` 을 채우며 더한 축이다. 이 문서는 그 전에
쓰였고 화면은 이미 그 축을 보내고 있었다 — 목록에서 빼면 **메이킹만 판단 대상 밖**이 된다
([G-37](../prd/GAPS.md#g-37)).

**무엇을 답하나** — 축이 일곱인데 실제로 뭘 쓰나. **안 쓰는 축은 UI에서 내릴 수 있다.**
`resultCount`가 0인 비율이 높은 축은 패싯 카운트가 제 역할을 못 하고 있다는 신호다.

### 4.3 `search_miss` ★

| payload | |
|---|---|
| `query` | 검색어 원문 |
| `matchedCount` | 0 |
| `hadChosung` | 초성 검색이었나 |

**Phase 1a에서 가장 쓸모 있는 이벤트다.**

에디터 1명이 하루 3~5종을 쓰는 상황에서 "다음에 뭘 등재할까"에 데이터로 답한다.
검색됐는데 없는 칵테일이 곧 **수요가 확인된 콘텐츠 후보**다.

`hadChosung`을 따로 두는 이유 — 초성 검색이 0건이면 콘텐츠가 없는 게 아니라
**초성 색인이 고장난 것**일 수 있다. 두 원인을 구분해야 한다.

### 4.4 `finder_step`

| payload | |
|---|---|
| `step` | 1~4 |
| `answered` | 선택값 |
| `candidateCount` | 남은 후보 수 |

`finder_complete`는 별도 이벤트로 두지 않는다. `step=4` 도달로 완주를 판정한다.

**무엇을 답하나** — 어느 질문에서 이탈하나. `candidateCount`가 1~2로 급감하면
질문이 너무 좁게 거른다는 뜻이다.

### 4.5 `recipe_interact`

| payload | |
|---|---|
| `cocktailSlug` | |
| `action` | `servings_change` · `unit_toggle` · `substitute_open` |
| `detail` | 잔 수 · `ml`/`oz` · 재료명 |

**무엇을 답하나** — 상세 화면에서 실제로 뭘 만지나. 아무도 안 쓰는 컨트롤은
화면을 복잡하게만 한다.

### 4.6 `bookmark_add` · `share_click`

| payload | |
|---|---|
| `targetType` | `cocktail` (1a) |
| `targetSlug` | |
| `channel` | `share_click`만 — `kakao` · `link` · `system` |

---

## 5. Phase 1b 이벤트 — 자리만 잡아둠

바·파트너 착수 시 심는다. **지금 구현하지 않는다.**

| 이벤트 | payload | 근거 |
|---|---|---|
| `bar_view` | `barSlug` · `entryPoint` | PRD 2.2 |
| **`cross_nav`** ★ | `fromType` `fromId` `toType` `toId` | **`PRIN-P01` 가설 검증** |
| `partner_action` | `barSlug` · `action`(`reserve`·`map`·`call`·`instagram`) | `R-F4.3-1` |

`cross_nav`가 없으면 크로스 이동률(세션의 20%)을 셀 수 없고, 그게 PRD가
"진짜 검증 지표"라고 한 숫자다. **1b의 가장 중요한 이벤트다.**

---

## 6. 지표 매핑

PRD 2.2의 성공 지표 8개가 어떤 이벤트로 계산되나.

| 지표 | 6개월 목표 | 계산 | 단계 |
|---|---|---|---|
| 등록 칵테일 수 | 500 | `cocktail.status='published'` 카운트 | 1a |
| 등록 바 수 | 300 | `bar.status='published'` 카운트 | 1b |
| MAU | 30,000 | `DISTINCT sessionId` 월별 | 1a |
| **유기 검색 유입 비중** | 50%+ | `referrerType='organic'` / 전체 | **1a** |
| **칵테일↔바 크로스 이동률** | 세션 20% | `cross_nav` 있는 세션 / 전체 세션 | **1b** |
| ~~내 술장 3개+ 등록 유저~~ — 폐기, 대체 지표는 [G-50](../prd/GAPS.md) | ~~가입자 25%~~ | — | — |
| 유료 제휴사 수 | 30 | `partner_contract.tier >= partner` | 1b |
| 제휴사 외부 액션 CTR | 12% | `partner_action` / `bar_view` | 1b |

**1a에서 검증 가능한 지표는 셋뿐이다** — 등록 수 · MAU · 유기 검색 비중.
나머지 다섯은 1b 또는 Phase 2를 기다린다. 이게 [SPEC-01 §4.3](./SPEC-01_시스템개요_범위.md)에
적은 분할 비용의 구체적인 모습이다.

### 6.1 파생 지표

지표표에 없지만 운영에 쓴다.

| 이름 | 계산 | 쓰임 |
|---|---|---|
| **검색 실패율** | `search_miss` / 전체 검색 | 콘텐츠 우선순위 |
| **미등재 수요 랭킹** | `search_miss.query` 빈도순 | **다음에 쓸 칵테일** |
| 필터 축 사용률 | 축별 `filter_apply` 분포 | UI 정리 |
| 파인더 완주율 | `step=4` / `step=1` | 파인더 존치 판단 |
| 빈 결과율 | `resultCount=0` / `filter_apply` | 패싯 카운트 건강도 |

---

## 7. 수집 API

```
POST /api/v1/events
Idempotency-Key: <uuid>
Content-Type: application/json

{ "events": [ { …공통필드…, "payload": {…} }, … ] }
```

| 항목 | |
|---|---|
| 인증 | 불필요 |
| CSRF | 면제 — 부작용이 집계뿐 |
| 레이트 리밋 | 120 req/min (세션 기준) |
| 배치 상한 | 요청당 50건 |
| 응답 | `202 Accepted`. 본문 없음 |

**`202`를 쓰는 이유** — 클라이언트가 처리 결과를 기다릴 필요가 없다.
검증 실패한 이벤트는 버리고 서버 로그에만 남긴다. 사용자 흐름을 막지 않는다.

---

## 8. 저장 · 보존

`analytics_event` 테이블 ([SPEC-06 §3.8](./SPEC-06_데이터모델_ERD.md)).

| 항목 | |
|---|---|
| 원본 보존 | **13개월** — 전년 동월 비교가 가능해야 한다 |
| 집계 테이블 | `partner_daily_stat` 등은 영구 |
| 파티셔닝 | `occurredAt` 월 단위. Phase 1a는 단일 테이블 |
| 탈퇴 시 | `user_id`만 `NULL`로 익명화. **행은 남긴다** ([SPEC-08 §5.3](./SPEC-08_보안_권한_개인정보.md)) |

원본을 지우지 않는 이유는 집계 지표가 소급 변동하면 안 되기 때문이다.

---

## 9. 구현 순서

| 순서 | 무엇 | 왜 |
|---|---|---|
| 1 | `POST /events` + `analytics_event` | 받을 곳이 먼저 |
| 2 | `cocktail_view` · `search_miss` | **가장 값싸고 가장 쓸모 있다** |
| 3 | `filter_apply` · `finder_step` | UI 정리 근거 |
| 4 | `recipe_interact` · `bookmark_add` · `share_click` | |
| 5 | *(1b)* `bar_view` · `cross_nav` · `partner_action` | 바 착수 시 |

**2번까지만 해도 "유기 검색이 들어오나"와 "다음에 뭘 쓸까"에 답할 수 있다.**
Phase 1a에서 알아야 할 것의 대부분이 여기 있다.

---

## 10. 하지 않는 것

| | 왜 |
|---|---|
| 마우스 궤적 · 스크롤 히트맵 | 개인 식별 위험 대비 실익 없음 |
| IP · User-Agent 원문 저장 | `PRIN-D04` 정신. `referrerType`으로 충분 |
| 위치 좌표 | **저장 금지** (`PRIN-D04`) |
| A/B 테스트 인프라 | Phase 1a 트래픽으론 유의성이 안 나온다 |
| 개별 사용자 행동 리플레이 | 파트너 대시보드는 집계값만 (`SPEC-08 §5.4`) |
