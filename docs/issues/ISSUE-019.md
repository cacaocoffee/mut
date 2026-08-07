---
id: ISSUE-019
title: GET /cocktails/facets 패싯 카운트
domain: SEARCH
layer: api
wave: 4
status: TODO
depends_on: [ISSUE-018]
fr: [FR-SEARCH-002, FR-SEARCH-009]
r: [R-F2.1-2]
inv: []
nfr: [NFR-A-06]
migration: —
owns:
  - apps/api/src/main/kotlin/kr/kcocktail/search/facet/**
---

## 근거

**`FR-SEARCH-002`** (**P0**): **모든 필터 값 옆에 실시간 결과 개수를 표시하고, 0건인 값은 비활성 처리한다.** **초기부터 넣지 않으면 나중에 UI를 다시 짜야 한다**

**`FR-SEARCH-009`**: 스타일과 메이킹 방법은 **상관관계가 높아 빈 결과가 빈발**한다. **조합 불가능한 값이 즉시 0으로 떨어져야 한다**

**SPEC-05 §5 패싯 카운트를 어디서 세나**

> `R-F2.1-2`는 모든 필터 값 옆에 실시간 결과 개수를 요구하고 0건은 비활성 처리하라고 한다. 이건 **"초기부터 넣지 않으면 나중에 UI를 다시 짜야 한다"고 PRD가 못박은 항목**이다.

| 단계 | 방식 |
|---|---|
| Phase 1 | 목록 응답에 전체 코퍼스가 담기므로 **클라이언트에서 계산**. 현재 `facetCounts()`가 하는 일 |
| 확장 후 | `GET /api/v1/cocktails/facets?…`가 축별 카운트를 반환 |

> **UI 계약은 두 단계에서 동일하다.** 값 옆에 숫자, 0이면 비활성. **계산 위치만 바뀐다.**

**SPEC-07 §3.2 `GET /cocktails/facets`** — 현재 필터를 그대로 받아 **각 값을 선택했을 때의 결과 수**를 돌려준다

```json
{
  "base":   { "gin": 5, "vodka": 4, "whisky": 5, "korean": 3, "non-alcoholic": 1 },
  "style":  { "highball": 8, "sour": 7, "spirit-forward": 6, "tiki": 1, "creamy": 2 },
  "abv":    { "na": 1, "low": 2, "mid": 8, "high": 13 },
  "flavor": { "citrus": 9, "sour": 7, "floral": 0, … },
  "sweet":  { "dry": 3, "semi_dry": 9, "semi_sweet": 10, "sweet": 1 }
}
```

**축마다 계산 방식이 다르다** — 이 이슈의 요체.

| 축 | 계산 |
|---|---|
| 기주 · 스타일 · 메이킹 · 당도 · 도수 | **같은 축의 현재 선택을 무시**하고 그 값만 골랐을 때의 수 |
| 향·맛 | AND라서 **현재 선택에 이 태그를 더했을 때**의 수 |

> 향·맛만 다른 이유는 **조합 불가능한 태그가 즉시 0으로 떨어져야** 하기 때문이다 (`FR-SEARCH-009`).
>
> **0인 값도 응답에 포함한다.** 클라이언트가 비활성 처리하려면 존재를 알아야 한다.
> 다만 **코퍼스에 아예 없는 값은 제외한다** — `floral`처럼 항목이 0건이면 필터에 띄우지 않는다 (ADR-0002 §5).

**SPEC-06 §1.4**: 조인 테이블을 택한 이유 중 하나가 **패싯 카운트가 `GROUP BY` 한 방으로 끝난다**는 것
**SPEC-06 §5**: `cocktail_style(style, cocktail_id)` · `cocktail_aroma_tag(aroma_tag, cocktail_id)` — 패싯 `GROUP BY`용

**`NFR-A-06`**: 비활성 필터 칩은 `disabled` + **개수를 함께 읽어준다** (접근성 — FE)

**현재 프로토타입**: `packages/domain/src/search.ts`의 `facetCounts()`가 이미 이 일을 한다. **이슈 040이 그것을 유지**하고, 이 엔드포인트는 확장 대비 + SSG 빌드용이다 (INDEX 결합점).

## RED

### 축별 계산 방식 (SPEC-07 §3.2 — 요체)

1. `기주_카운트는_같은_축_선택을_무시한다` — `base=gin` 상태에서 `vodka` 카운트가 0이 아니다
2. `스타일_카운트도_같은_축_선택을_무시한다`
3. `메이킹_카운트도_같다`
4. `당도_카운트도_같다`
5. `도수_카운트도_같다`
6. **`향맛_카운트는_현재_선택에_더했을_때의_수다`** — `flavor=citrus` 상태에서 `herbal` 카운트 = citrus AND herbal
7. `향맛은_같은_축_선택을_무시하지_않는다` — 1~5와 반대
8. `다른_축_선택은_모든_카운트에_반영된다` — `base=gin` 이면 style 카운트도 진 한정

### 0 처리 (SPEC-07 §3.2, `FR-SEARCH-002`)

9. `0인_값도_응답에_포함된다` — 클라이언트가 비활성 처리하려면 존재를 알아야 한다
10. `코퍼스에_아예_없는_값은_제외된다` — ADR-0002 §5 (`floral` 예시)
11. `0과_부재를_구분할_수_있다` — 키가 있고 값이 0 vs 키 자체가 없음

### 조합 불가 즉시 0 (`FR-SEARCH-009`)

12. `스타일과_메이킹의_불가능한_조합이_0이_된다`
13. `0인_값을_선택하면_결과가_0건이다` — 카운트와 실제 결과 일치
14. `카운트가_실제_목록_결과와_항상_일치한다` — 축별 파라미터라이즈드. **이 테스트가 패싯의 정확성을 보장한다**

### 발행분만

15. `published만_집계된다`
16. `draft가_카운트에_포함되지_않는다`

### 성능 (SPEC-06 §1.4·§5)

17. `축별_카운트가_GROUP_BY_한번으로_계산된다` — N+1 없음
18. `조인_테이블_인덱스를_탄다` — `cocktail_style(style, cocktail_id)` EXPLAIN
19. `500종_기준_응답이_충분히_빠르다`

### 계약 (SPEC-05 §5)

20. `응답_형태가_축별_맵이다` — SPEC-07 §3.2 예시와 동일
21. `필터_파라미터가_목록_API와_동일하다` — 같은 쿼리스트링을 받는다 (SPEC-05 §5 "UI 계약은 동일")
22. `noindex가_붙는다` — 필터 계열

## GREEN

### `search/facet`

```kotlin
object FacetCalculator {                     // 순수 함수 — 클라이언트와 같은 규칙
    fun counts(corpus: List<CocktailFacetRow>, filter: CocktailFilter): FacetCounts
}
```

**순수 함수로 두는 이유**: SPEC-05 §5가 "Phase 1은 클라이언트 계산, 확장 후 서버"라고 했다. 두 곳이 다른 답을 내면 UI가 깨진다. 서버 구현을 **같은 알고리즘**으로 두고 RED 14가 목록 API와의 일치를 강제한다.

### 축별 분기 (RED 1~7)

```kotlin
// 기주·스타일·메이킹·당도·도수: 같은 축 선택 제거 후 계산
val baseCounts = corpus.filter { filter.withoutBase().matches(it) }
                       .groupingBy { it.base }.eachCount()

// 향맛: 현재 선택 유지 + 태그 하나 추가 (AND)
val flavorCounts = FlavorKey.entries.associateWith { tag ->
    corpus.count { filter.plusFlavor(tag).matches(it) }
}
```

**이 비대칭이 SPEC-07 §3.2의 표 그대로다.** 주석에 근거를 남긴다.

### 코퍼스 부재 값 제외 (RED 10)

```kotlin
// ADR-0002 §5 — 항목이 0건인 값은 필터에 띄우지 않는다
val presentValues = corpus.map { it.base }.toSet()
```

**"현재 필터로 0건"(포함)과 "코퍼스에 아예 없음"(제외)의 구분**이 RED 9·10·11이다. 헷갈리기 쉽다.

### 프로토타입과의 관계 (INDEX 결합점)

`packages/domain/src/search.ts`의 `facetCounts()`를 **지우지 않는다.** 이슈 040이 Phase 1 동안 그것을 계속 쓴다. 이 엔드포인트는 SSG 빌드와 확장 대비다.

**결정** 두 구현이 갈라질 위험이 있다. **보수적으로**: 이슈 040에서 "클라이언트 계산 결과와 서버 응답이 일치하는지" 대조 테스트를 넣는다. GAPS 등재.

**하지 말 것**: 프론트 필터 UI — 이슈 040

## DoD

- [ ] RED 22항 전부 통과
- [ ] **축별 계산 방식이 SPEC-07 §3.2 표와 일치** (RED 1~7)
- [ ] 카운트가 실제 목록 결과와 일치 (RED 14)
- [ ] 0 포함 / 코퍼스 부재 제외 구분 (RED 9·10·11)
- [ ] `GROUP BY` 한 번, 인덱스 사용 (RED 17·18)
- [ ] **결정** 클라이언트/서버 계산 이중화 위험 `GAPS.md` 등재
- [ ] 커밋: `feat(search): 패싯 카운트 (FR-SEARCH-002·009, R-F2.1-2, SPEC-07 §3.2)`
