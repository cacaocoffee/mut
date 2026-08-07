---
id: ISSUE-049
title: 계측 3~4단계 — filter_apply · finder_step · recipe_interact · bookmark_add · share_click
domain: —
layer: web
wave: 8
status: TODO
depends_on: [ISSUE-035, ISSUE-040, ISSUE-041, ISSUE-043]
fr: []
r: []
inv: []
nfr: [NFR-R-04]
migration: —
owns:
  - apps/web/lib/analytics/events/**
---

> **[ISSUE-035](ISSUE-035.md)에서 분할.** SPEC-10 §9의 **3~4단계**다.
> 기반과 2단계(`cocktail_view`·`search_miss`)는 035가 끝냈다.
>
> **소유 경로 주의**: 계측 호출은 화면 파일(040·041·043 소유)에 들어간다. **그 이슈들이 `DONE`인 뒤 착수**하므로 동시 작업이 아니다.

## 근거

**SPEC-10 §9 구현 순서** — 3단계는 "UI 정리 근거", 4단계는 그다음

### §4.2 `filter_apply`
`axis`(`sweet`·`base`·`style`·`flavor`·`abv`·`query`) · `value` · `resultCount` · `activeAxisCount`

> 축이 여섯인데 실제로 뭘 쓰나. **안 쓰는 축은 UI에서 내릴 수 있다.**
> `resultCount`가 0인 비율이 높은 축은 **패싯 카운트가 제 역할을 못 하고 있다는 신호**다.

### §4.4 `finder_step`
`step`(1~4) · `answered` · `candidateCount`

> `finder_complete`는 별도 이벤트로 두지 않는다. **`step=4` 도달로 완주를 판정한다.**
> `candidateCount`가 1~2로 급감하면 **질문이 너무 좁게 거른다**는 뜻이다.

### §4.5 `recipe_interact`
`cocktailSlug` · `action`(`servings_change`·`unit_toggle`·`substitute_open`) · `detail`

> 상세 화면에서 실제로 뭘 만지나. **아무도 안 쓰는 컨트롤은 화면을 복잡하게만 한다.**

### §4.6 `bookmark_add` · `share_click`
`targetType`(`cocktail`) · `targetSlug` · `channel`(`share_click`만 — `kakao`·`link`·`system`)

**SPEC-10 §6.1 파생 지표** — 이 5종이 무엇에 쓰이나

| 지표 | 계산 | 쓰임 |
|---|---|---|
| 필터 축 사용률 | 축별 `filter_apply` 분포 | **UI 정리** |
| 빈 결과율 | `resultCount=0` / `filter_apply` | **패싯 카운트 건강도** |
| 파인더 완주율 | `step=4` / `step=1` | **파인더 존치 판단** |

## RED

### `filter_apply` (SPEC-10 §4.2)

1. `필터_변경시_발생한다`
2. `axis_6종이_정확하다` — `sweet`·`base`·`style`·`flavor`·`abv`·`query`
3. `value가_담긴다`
4. `resultCount가_적용_후_결과_수다`
5. `activeAxisCount가_동시에_걸린_축_개수다`
6. `연속_조작이_디바운스된다` — 슬라이더 없이 칩이라 과하지 않지만 다중 선택 시

### `finder_step` (SPEC-10 §4.4)

7. `각_단계에서_발생한다`
8. `step_1부터_4까지_기록된다`
9. `answered와_candidateCount가_담긴다`
10. `step_4_도달이_완주다` — **`finder_complete` 별도 이벤트 없음**
11. `이탈해도_직전_단계까지_기록된다`

### `recipe_interact` (SPEC-10 §4.5)

12. `servings_change에서_발생한다`
13. `unit_toggle에서_발생한다`
14. `substitute_open에서_발생한다`
15. `detail에_값이_담긴다` — 잔 수 · `ml`/`oz` · 재료명
16. `3종_외의_액션이_없다`

### `bookmark_add` · `share_click` (SPEC-10 §4.6)

17. `저장시_bookmark_add가_발생한다`
18. `targetType이_cocktail이다` — 1a
19. `공유시_share_click이_발생한다`
20. `channel_3종이_정확하다` — `kakao`·`link`·`system`

### 공통 (035 기반 재사용)

21. `035의_EventQueue를_쓴다` — 전송 로직을 다시 만들지 않는다
22. `공통_필드가_035와_동일하다`
23. `전송_실패가_흐름을_막지_않는다` (`NFR-R-04`)

## GREEN

035가 만든 `EventQueue`와 공통 필드 조립을 **그대로 재사용한다** (RED 21). 이 이슈는 **호출 지점과 payload 타입**만 추가한다.

```ts
// SPEC-10 §4 — event_type 별 payload 타입 (이슈 034의 서버 스키마와 쌍)
type FilterApplyPayload = { axis: Axis; value: string; resultCount: number; activeAxisCount: number };
type FinderStepPayload  = { step: 1|2|3|4; answered: string; candidateCount: number };
...
```

**하지 말 것**: 전송·세션·공통 필드 로직 재구현 (035) · 1b 이벤트 (SPEC-10 §5)

## DoD

- [ ] RED 23항 전부 통과
- [ ] **035의 `EventQueue` 재사용** (RED 21 — 두 벌 구현 금지)
- [ ] `step=4` 완주 판정, `finder_complete` 부재 (RED 10)
- [ ] payload 타입이 이슈 034의 서버 스키마와 일치
- [ ] 커밋: `feat(web): 계측 3~4단계 이벤트 5종 (SPEC-10 §4.2·4.4·4.5·4.6·§9)`
