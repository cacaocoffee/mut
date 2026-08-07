---
id: ISSUE-035
title: 계측 기반 + cocktail_view · search_miss
domain: —
layer: web
wave: 8
status: TODO
depends_on: [ISSUE-034, ISSUE-038, ISSUE-042]
fr: []
r: []
inv: []
nfr: [NFR-R-04]
migration: —
owns:
  - apps/web/lib/analytics/core/**
---

> **분할됨** — SPEC-10 §9가 준 순서를 그대로 따른다.
> 이 이슈는 **기반 + 2단계**(가장 값싸고 가장 쓸모 있다). 3~4단계는 [049](ISSUE-049.md).
>
> **소유 경로 주의**: 계측 호출은 화면 파일(038·042 소유)에 들어간다.
> 이 이슈는 **그 이슈들이 `DONE`인 뒤 착수**하므로 동시 작업이 아니다 (CONVENTIONS §4의 취지).

## 근거

**SPEC-10 §1 왜 지금 쓰나**

> **이벤트는 소급이 안 된다.** 나중에 심으면 그 기간의 데이터가 **영원히 없다.**
> 지금 화면이 셋뿐이라 심을 지점도 적다. **코드가 커진 뒤에는 호출 지점을 찾아 흩뿌려야 한다.**

**SPEC-10 §9 구현 순서**

| 순서 | 무엇 | 이슈 |
|---|---|---|
| 1 | `POST /events` + `analytics_event` | 034 |
| **2** | **`cocktail_view` · `search_miss`** — **가장 값싸고 가장 쓸모 있다** | **이 이슈** |
| 3 | `filter_apply` · `finder_step` | 049 |
| 4 | `recipe_interact` · `bookmark_add` · `share_click` | 049 |

> **2번까지만 해도 "유기 검색이 들어오나"와 "다음에 뭘 쓸까"에 답할 수 있다.** Phase 1a에서 알아야 할 것의 대부분이 여기 있다.

**SPEC-10 §2 원칙** — 개인 식별 금지 · **멱등**(`PRIN-T07`) · **실패해도 조용히** · **배치 전송**

**SPEC-10 §3 공통 필드**: `eventType` · `sessionId`(UUID, **30분 무활동 시 갱신**) · `userId` · `occurredAt` · `path`(**쿼리스트링 제외**) · `referrerType` · `payload`

> `referrerType`을 **원본 URL이 아니라 분류값으로** 저장한다. **원본 URL은 개인정보가 섞일 수 있다.**
> 값: `organic` · `internal` · `social` · `direct` · `unknown`

### §4.1 `cocktail_view`
`cocktailSlug` · `entryPoint`(`search`·`category`·`related`·`finder`·`external`)
> **`entryPoint`가 `external`인 비율이 곧 SEO 성과다.**

### §4.3 `search_miss` ★ — Phase 1a에서 가장 쓸모 있는 이벤트
`query` · `matchedCount`(0) · `hadChosung`
> 검색됐는데 없는 칵테일이 곧 **수요가 확인된 콘텐츠 후보**다.
> `hadChosung`을 따로 두는 이유 — 초성 검색이 0건이면 콘텐츠가 없는 게 아니라 **초성 색인이 고장난 것**일 수 있다.

**SPEC-10 §6**: 1a에서 검증 가능한 지표 셋 — 등록 수 · MAU(`DISTINCT sessionId`) · **유기 검색 비중**
**`NFR-R-04`**: 이벤트 수집 실패가 사용자 흐름을 막지 않는다 — 배포 차단

## RED

### 세션 (SPEC-10 §3)

1. `sessionId가_UUID로_생성된다`
2. `sessionId가_30분_무활동시_갱신된다`
3. `활동이_있으면_유지된다`
4. `localStorage에_저장된다` — 탭 간 공유 ([DECISIONS §1.11](DECISIONS.md))
5. `비로그인은_userId가_null이다`

### 공통 필드 (SPEC-10 §3)

6. `path에_쿼리스트링이_없다`
7. `referrerType이_5종_중_하나로_분류된다`
8. `유기_검색이_organic으로_분류된다`
9. `내부_이동이_internal이다`
10. `소셜이_social이다`
11. `referrer가_없으면_direct다`
12. `분류_불가는_unknown이다`
13. `원본_referrer_URL을_보내지_않는다` — 개인정보

### `cocktail_view` (SPEC-10 §4.1)

14. `상세_진입시_발생한다`
15. `entryPoint_5종이_정확하다` — `search`·`category`·`related`·`finder`·`external`
16. **`external_진입이_구분된다`** — **SEO 성과 측정**

### `search_miss` ★ (SPEC-10 §4.3)

17. **`결과_0건일_때_발생한다`**
18. `hadChosung이_서버_응답에서_온다` — **프론트가 다시 판정하지 않는다** (이슈 024)
19. `matchedCount가_0이다`
20. `초성_0건과_일반_0건이_구분된다` — 두 원인을 나눠야 한다

### 배치 전송 (SPEC-10 §2)

21. `여러_이벤트가_모여_한_번에_전송된다`
22. `페이지_이탈시_남은_이벤트가_전송된다` — `sendBeacon`
23. `50건_상한을_넘지_않는다` (이슈 034)

### 실패 격리 (`NFR-R-04`)

24. `전송_실패가_사용자_흐름을_막지_않는다`
25. `전송_실패가_UI_에러로_보이지_않는다`
26. `실패_로그가_debug_레벨이다` ([DECISIONS §1.11](DECISIONS.md))
27. `광고차단기_환경에서_페이지가_정상_동작한다`

### 멱등 (`PRIN-T07`)

28. `Idempotency_Key가_배치마다_생성된다`
29. `재시도가_같은_키를_쓴다`

### 하지 않는 것 (SPEC-10 §10)

30. `마우스_궤적·스크롤_히트맵을_수집하지_않는다`
31. `좌표를_수집하지_않는다` (이슈 033)
32. `1b_이벤트를_구현하지_않는다` — `bar_view`·`cross_nav`·`partner_action` (SPEC-10 §5)

## GREEN

```ts
// apps/web/lib/analytics/core/ — SPEC-10 §2: 배치 전송, 실패해도 조용히
class EventQueue {
  push(e: AnalyticsEvent) { /* 모았다가 flush */ }
  private flush() { try { navigator.sendBeacon(url, body) } catch { /* 삼킨다 */ } }
}
```

이슈 049가 이 큐를 **그대로 재사용**한다. 전송 로직은 여기 한 곳뿐이다.

### `search_miss` (RED 17~20) — 가장 중요

이슈 024가 응답에 `matchedCount`·`hadChosung`을 담는다. **프론트는 그것을 그대로 옮긴다** — 판정을 다시 하지 않는다. 서버와 다른 답을 내면 SPEC-10 §4.3의 "두 원인 구분"이 무너진다.

**하지 말 것**: 3~4단계 이벤트 5종 — 이슈 049 · 1b 이벤트 (SPEC-10 §5 "지금 구현하지 않는다")

## DoD

- [ ] RED 32항 전부 통과
- [ ] **`cocktail_view`·`search_miss` 동작** — SPEC-10 §9의 2단계
- [ ] `hadChosung`이 서버 응답 (RED 18)
- [ ] 전송 실패가 흐름을 막지 않음 (RED 24~27 — `NFR-R-04`)
- [ ] `EventQueue`가 049가 재사용할 형태로 공개
- [ ] 커밋: `feat(web): 계측 기반·cocktail_view·search_miss (SPEC-10 §4.1·§4.3·§9)`
