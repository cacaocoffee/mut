---
id: ISSUE-043
title: 상세 인터랙션 — 잔 수 · 단위 · 대체재
domain: COCKTAIL
layer: web
wave: 8
status: TODO
depends_on: [ISSUE-038]
fr: [FR-COCKTAIL-019, FR-COCKTAIL-020, FR-COCKTAIL-021]
r: [R-F1.3-2]
inv: []
nfr: [NFR-P-02, NFR-A-04]
migration: —
owns:
  - apps/web/components/recipe-panel.tsx
---

## 근거

**`FR-COCKTAIL-019`**: 잔 수를 **1~n으로 조절**하면 계량이 자동 환산된다. **고정 표기(`1조각`·`2 dash`)는 배수에서 제외한다** (PRD 6.1)
**`FR-COCKTAIL-020`**: 단위를 **`ml` / `oz`** 로 토글한다. **`dash`·`barspoon`·`piece`는 변환하지 않는다** (PRD 6.1)
**`FR-COCKTAIL-021`**: **대체 가능한 재료에 대체재 안내를 펼쳐** 보여준다 (`R-F1.3-2`)

**SPEC-06 §3.1 `recipe_ingredient`**

| 컬럼 | 의미 |
|---|---|
| `amount` · `unit` | `ml`·`dash`·`barspoon`·`piece`·`top_up` |
| **`amount_label`** | **`1조각`처럼 배수 계산 제외 표기** |
| `substitute_ingredient_id` · `substitute_note` | 대체재 |

**이슈 010 GREEN**: `isScalable()` — 배수 대상 판정을 **서버가 제공**한다. "그래야 FE와 어드민 미리보기가 같은 규칙을 쓴다"

**SPEC-10 §4.5 `recipe_interact`** (이슈 035)

| payload | |
|---|---|
| `cocktailSlug` | |
| `action` | `servings_change` · `unit_toggle` · `substitute_open` |
| `detail` | 잔 수 · `ml`/`oz` · 재료명 |

> **상세 화면에서 실제로 뭘 만지나. 아무도 안 쓰는 컨트롤은 화면을 복잡하게만 한다.**

**`NFR-P-02`**: INP ≤ 200ms — 배포 차단
**`NFR-A-04`**: 모든 인터랙션이 키보드로 도달·조작 가능 — 배포 차단

**현재**: `apps/web/components/recipe-panel.tsx` — 프로토타입에 이미 있다

## RED

### 잔 수 환산 (`FR-COCKTAIL-019`)

1. `잔_수를_1에서_n으로_조절한다`
2. `ml_계량이_배수로_환산된다` — 30ml × 2잔 = 60ml
3. `amount_label_재료는_환산되지_않는다` — `1조각`은 그대로
4. `dash가_환산되는가` ⚖️ — `FR-COCKTAIL-019`는 "**고정 표기**(`1조각`·`2 dash`)는 배수에서 제외"라 한다. **`2 dash`가 `amount_label`인지 `amount+unit`인지** 모호하다.
   → **보수적으로 `amount_label`이 있으면 제외, `unit=dash`는 환산**. 시드(이슈 036)가 `2 dash`를 어디에 넣었는지에 달렸다 + **GAPS 등재**
5. `top_up은_환산되지_않는다` — "채운다"
6. `서버의_isScalable_판정을_따른다` (이슈 010) — 프론트가 다시 판정하지 않는다
7. `잔_수_상한이_있다` ⚖️ — 보수적으로 8 + GAPS
8. `소수점_처리가_일관된다` — 7.5ml × 3 = 22.5ml

### 단위 토글 (`FR-COCKTAIL-020`)

9. `ml에서_oz로_토글된다`
10. `변환_비율이_일관된다` — 1 oz = 29.5735ml ⚖️ (30ml 반올림? + GAPS)
11. `dash는_변환되지_않는다`
12. `barspoon은_변환되지_않는다`
13. `piece는_변환되지_않는다`
14. `top_up도_변환되지_않는다`
15. `oz_표기가_읽기_쉽다` — 1.0141 oz 같은 값 처리 ⚖️ (분수 표기? + GAPS)
16. `토글_상태가_유지된다` ⚖️ — 세션·로컬 저장 + GAPS

### 잔 수 × 단위 조합

17. `둘을_동시에_적용해도_정확하다` — 30ml × 2잔 → oz
18. `순서가_결과에_영향을_주지_않는다`

### 대체재 (`FR-COCKTAIL-021`, `R-F1.3-2`)

19. `대체재가_있는_재료에_안내_버튼이_있다`
20. `펼치면_대체재_정보가_보인다`
21. `substitute_note가_표시된다`
22. `대체재가_없으면_버튼이_없다`
23. `미유통_재료에_대체재가_반드시_있다` — `GATE-COCKTAIL-06`이 보장 (이슈 013)

### 계측 (SPEC-10 §4.5 — 이슈 035)

24. `servings_change_이벤트가_발생한다`
25. `unit_toggle_이벤트가_발생한다`
26. `substitute_open_이벤트가_발생한다`
27. `detail에_값이_담긴다` — 잔 수 · `ml`/`oz` · 재료명

### 접근성 (`NFR-A-04`·`A-05`·`A-07`)

28. `키보드로_잔_수를_조절한다`
29. `키보드로_단위를_토글한다`
30. `키보드로_대체재를_펼친다`
31. `변경이_스크린리더에_안내된다` — `aria-live`
32. `focus_visible_아웃라인이_있다`
33. `펼침_상태가_aria_expanded로_표현된다`

### 성능

34. `INP가_200ms_이하다` (`NFR-P-02`)
35. `환산에_서버_왕복이_없다` — 클라이언트 계산

## GREEN

### 환산 로직

```ts
// FR-COCKTAIL-019 — 서버의 isScalable 판정을 따른다 (이슈 010)
function scale(ing: IngredientLine, servings: number) {
  if (!ing.isScalable) return ing.amountLabel ?? ing.display;   // 1조각
  return { ...ing, amount: ing.amount * servings };
}

// FR-COCKTAIL-020 — ml 만 변환. dash·barspoon·piece·top_up 제외
const CONVERTIBLE_UNITS = new Set(["ml"]);
```

**변환 대상 단위를 화이트리스트로** 둔다 (RED 11~14). 블랙리스트면 새 단위가 생겼을 때 잘못 변환된다.

### `isScalable` 재판정 금지 (RED 6)

이슈 010이 서버에서 판정한다 — "**그래야 FE와 어드민 미리보기가 같은 규칙을 쓴다**".
프론트가 `amountLabel == null` 을 다시 검사하지 않는다.

⚖️ 다만 이슈 010의 `isScalable()`이 **응답 DTO에 포함되는지** 이슈 020에서 확인해야 한다. 없으면 020에 추가 요청 (CONVENTIONS §4 — `owns:` 밖) + GAPS.

### oz 변환 (RED 10·15)

```ts
const ML_PER_OZ = 29.5735;
```

⚖️ **바텐딩 관례로는 1 oz ≈ 30ml**로 반올림하는 경우가 많다. 정확한 변환은 `1.0141 oz` 같은 값을 만든다.
**보수적으로 정확한 비율 + 소수 1자리 반올림**. 표기 방식(분수 vs 소수)은 GAPS 등재 — SCREENS-01 확인 필요.

### 계측 (RED 24~27)

이슈 035의 큐에 넣는다. **환산이 일어날 때마다가 아니라 사용자 조작 시**에만 (디바운스).

**하지 말 것**:
- 기법 툴팁 (`FR-COCKTAIL-022`) — **P1**
- 맛 프로필 레이더 (`FR-COCKTAIL-023`) — **P1** (데이터는 이슈 036의 `profile` ⚖️)
- 서버 왕복 (RED 35)

## DoD

- [ ] RED 35항 전부 통과
- [ ] **`amount_label` 재료가 환산되지 않음** (RED 3 — `FR-COCKTAIL-019`)
- [ ] **`ml`만 변환, 4종 단위 제외** (RED 11~14 — `FR-COCKTAIL-020`)
- [ ] 서버 `isScalable` 판정 사용, 프론트 재판정 없음 (RED 6)
- [ ] `recipe_interact` 3종 계측 (RED 24~26 — SPEC-10 §4.5)
- [ ] 키보드 전체 조작 (RED 28~30 — `NFR-A-04` 배포 차단)
- [ ] ⚖️ 6건(`2 dash` 분류·잔 수 상한·oz 비율·oz 표기·토글 상태 유지·`isScalable` DTO 노출) `GAPS.md` 등재
- [ ] 커밋: `feat(web): 잔 수 환산·단위 토글·대체재 (FR-COCKTAIL-019·020·021)`
