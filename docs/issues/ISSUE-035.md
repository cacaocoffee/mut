---
id: ISSUE-035
title: Phase 1a 이벤트 7종 심기
domain: —
layer: web
wave: 8
status: TODO
depends_on: [ISSUE-034, ISSUE-038, ISSUE-040, ISSUE-041, ISSUE-042, ISSUE-043]
fr: []
r: []
inv: []
nfr: [NFR-R-04]
migration: —
owns:
  - apps/web/lib/analytics/**
---

## 근거

**SPEC-10 §4 Phase 1a 이벤트** — 바 없이 성립하는 것들. **이게 지금 심을 전부다.**

### 4.1 `cocktail_view`
`cocktailSlug` · `entryPoint`(`search`·`category`·`related`·`finder`·`external`)
> 어떤 칵테일이 실제로 읽히나. 카테고리 페이지가 유입을 만드나. **`entryPoint`가 `external`인 비율이 곧 SEO 성과다.**

### 4.2 `filter_apply`
`axis`(`sweet`·`base`·`style`·`flavor`·`abv`·`query`) · `value` · `resultCount` · `activeAxisCount`
> 축이 여섯인데 실제로 뭘 쓰나. **안 쓰는 축은 UI에서 내릴 수 있다.** `resultCount`가 0인 비율이 높은 축은 **패싯 카운트가 제 역할을 못 하고 있다는 신호**다.

### 4.3 `search_miss` ★ — **Phase 1a에서 가장 쓸모 있는 이벤트다**
`query` · `matchedCount`(0) · `hadChosung`
> 에디터 1명이 하루 3~5종을 쓰는 상황에서 **"다음에 뭘 등재할까"에 데이터로 답한다.** 검색됐는데 없는 칵테일이 곧 **수요가 확인된 콘텐츠 후보**다.
> `hadChosung`을 따로 두는 이유 — 초성 검색이 0건이면 콘텐츠가 없는 게 아니라 **초성 색인이 고장난 것**일 수 있다.

### 4.4 `finder_step`
`step`(1~4) · `answered` · `candidateCount`
> `finder_complete`는 별도 이벤트로 두지 않는다. **`step=4` 도달로 완주를 판정한다.**
> 어느 질문에서 이탈하나. `candidateCount`가 1~2로 급감하면 질문이 너무 좁게 거른다.

### 4.5 `recipe_interact`
`cocktailSlug` · `action`(`servings_change`·`unit_toggle`·`substitute_open`) · `detail`
> 상세 화면에서 실제로 뭘 만지나. **아무도 안 쓰는 컨트롤은 화면을 복잡하게만 한다.**

### 4.6 `bookmark_add` · `share_click`
`targetType`(`cocktail`) · `targetSlug` · `channel`(`share_click`만 — `kakao`·`link`·`system`)

**SPEC-10 §9 구현 순서**

| 순서 | 무엇 | 왜 |
|---|---|---|
| 1 | `POST /events` + `analytics_event` | 이슈 034 |
| 2 | **`cocktail_view` · `search_miss`** | **가장 값싸고 가장 쓸모 있다** |
| 3 | `filter_apply` · `finder_step` | UI 정리 근거 |
| 4 | `recipe_interact` · `bookmark_add` · `share_click` | |

> **2번까지만 해도 "유기 검색이 들어오나"와 "다음에 뭘 쓸까"에 답할 수 있다.** Phase 1a에서 알아야 할 것의 대부분이 여기 있다.

**SPEC-10 §3 공통 필드**: `sessionId`(UUID, **30분 무활동 시 갱신**) · `path`(**쿼리스트링 제외**) · `referrerType`(분류값)
**SPEC-10 §2**: **배치 전송** — 페이지당 여러 이벤트를 모아 한 번에. **실패해도 조용히**
**`NFR-R-04`**: 이벤트 수집 실패가 사용자 흐름을 막지 않는다 — 배포 차단

**SPEC-10 §6 지표 매핑** — 1a에서 검증 가능한 지표는 **셋뿐**: 등록 수 · MAU(`DISTINCT sessionId`) · **유기 검색 비중**(`referrerType='organic'`)

## RED

### 세션 (SPEC-10 §3)

1. `sessionId가_UUID로_생성된다`
2. `sessionId가_30분_무활동시_갱신된다`
3. `활동이_있으면_유지된다`
4. `sessionId가_localStorage_또는_쿠키에_저장된다` **결정**
5. `비로그인은_userId가_null이다`

### 공통 필드 (SPEC-10 §3)

6. `path에_쿼리스트링이_없다`
7. `referrerType이_5종_중_하나로_분류된다`
8. `유기_검색이_organic으로_분류된다` — 검색엔진 referrer
9. `내부_이동이_internal이다`
10. `소셜이_social이다`
11. `referrer가_없으면_direct다`
12. `분류_불가는_unknown이다`
13. `원본_referrer_URL을_보내지_않는다` (SPEC-10 §3 — 개인정보)

### 7종 이벤트 (SPEC-10 §4)

14. `cocktail_view가_상세_진입시_발생한다`
15. `entryPoint가_정확하다` — 5종 각각 (`search`·`category`·`related`·`finder`·`external`)
16. `external_진입이_구분된다` — **SEO 성과 측정** (SPEC-10 §4.1)
17. `filter_apply가_필터_변경시_발생한다`
18. `axis와_value와_resultCount가_담긴다`
19. `activeAxisCount가_정확하다`
20. **`search_miss가_결과_0건일_때_발생한다`**
21. `hadChosung이_정확하다` — 초성 검색 구분 (SPEC-10 §4.3)
22. `matchedCount가_0이다`
23. `finder_step이_각_단계에서_발생한다`
24. `step_4_도달이_완주다` — `finder_complete` 별도 이벤트 없음 (SPEC-10 §4.4)
25. `candidateCount가_담긴다`
26. `recipe_interact가_3종_액션에서_발생한다` — `servings_change`·`unit_toggle`·`substitute_open`
27. `bookmark_add가_저장시_발생한다`
28. `share_click이_공유시_발생한다`
29. `share_click에_channel이_담긴다` — `kakao`·`link`·`system`

### 배치 전송 (SPEC-10 §2)

30. `여러_이벤트가_모여_한_번에_전송된다`
31. `페이지_이탈시_남은_이벤트가_전송된다` — `sendBeacon` 또는 `visibilitychange`
32. `50건_상한을_넘지_않는다` (이슈 034 RED 6)

### 실패 격리 (`NFR-R-04`, SPEC-10 §2)

33. `전송_실패가_사용자_흐름을_막지_않는다`
34. `전송_실패가_UI_에러로_보이지_않는다`
35. `전송_실패가_콘솔_에러를_쏟지_않는다` **결정** — 조용히 (SPEC-10 §2). **debug 레벨**
36. `네트워크_차단_환경에서_페이지가_정상_동작한다` — 광고 차단기

### 멱등 (`PRIN-T07`)

37. `Idempotency_Key가_배치마다_생성된다`
38. `재시도가_같은_키를_쓴다`

### 하지 않는 것 (SPEC-10 §10)

39. `마우스_궤적을_수집하지_않는다`
40. `스크롤_히트맵을_수집하지_않는다`
41. `좌표를_수집하지_않는다` (이슈 033)
42. `개별_행동_리플레이가_없다`

## GREEN

### `apps/web/lib/analytics/`

```ts
// SPEC-10 §2 — 배치 전송, 실패해도 조용히
class EventQueue {
  private queue: AnalyticsEvent[] = [];
  push(e: AnalyticsEvent) { this.queue.push(e); this.scheduleFlush(); }
  private flush() {
    // navigator.sendBeacon 우선, 실패해도 삼킨다 (NFR-R-04)
  }
}
```

### 세션 (RED 1~4)

```ts
// SPEC-10 §3 — 30분 무활동 시 갱신
const SESSION_TTL = 30 * 60 * 1000;
function getSessionId(): string { /* localStorage + lastActivity 검사 */ }
```

**결정** `localStorage` vs 쿠키: SPEC에 없다. **`sessionStorage`가 아닌 `localStorage`**(탭 간 공유) + GAPS.

### 구현 순서 (SPEC-10 §9)

**이 이슈를 한 번에 다 하지 않아도 된다.** SPEC-10 §9가 순서를 줬다:

1. `cocktail_view` · `search_miss` ← **여기까지만 해도 1a 목표의 대부분**
2. `filter_apply` · `finder_step`
3. `recipe_interact` · `bookmark_add` · `share_click`

착수 시 이 순서로 커밋을 나누면 중간에 멈춰도 가치가 남는다.

### `search_miss` (RED 20~22) — 가장 중요

이슈 024가 응답에 `matchedCount`·`hadChosung`을 담는다. **프론트는 그것을 그대로 이벤트로 옮긴다** — 판정을 프론트가 다시 하지 않는다.

### 실패 격리 (RED 33~36)

```ts
try { navigator.sendBeacon(url, body); } catch { /* SPEC-10 §2 — 삼킨다 */ }
```

**광고 차단기가 `/events`를 막는 것이 흔하다** (RED 36). 그래도 페이지는 멀쩡해야 한다.

**하지 말 것**:
- 1b 이벤트(`bar_view`·`cross_nav`·`partner_action`) — SPEC-10 §5 "**지금 구현하지 않는다**"
- 외부 분석 도구 — 병행 가능하나 범위 밖
- A/B 테스트 — SPEC-10 §10

## DoD

- [ ] RED 42항 전부 통과
- [ ] **`cocktail_view`·`search_miss` 우선 구현** (SPEC-10 §9 — 커밋 분리)
- [ ] `referrerType` 분류만 전송, 원본 URL 미전송 (RED 13)
- [ ] **전송 실패가 사용자 흐름을 막지 않음** (RED 33~36 — `NFR-R-04`)
- [ ] 1b 이벤트 미구현 (SPEC-10 §5)
- [ ] 미결은 [`DECISIONS.md`](DECISIONS.md) §1 확정분을 따른다 — **이슈에서 판단하지 않는다**
- [ ] 커밋: `feat(web): Phase 1a 이벤트 7종 (SPEC-10 §4·§9)`
