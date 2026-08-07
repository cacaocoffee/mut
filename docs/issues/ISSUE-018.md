---
id: ISSUE-018
title: GET /cocktails 목록 · 필터
domain: SEARCH
layer: api
wave: 4
status: TODO
depends_on: [ISSUE-013]
fr: [FR-SEARCH-001, FR-SEARCH-003, FR-SEARCH-005]
r: [R-F2.1-1]
inv: []
nfr: [NFR-S-02]
migration: —
owns:
  - apps/api/src/main/kotlin/kr/kcocktail/search/list/**
---

## 근거

**`FR-SEARCH-001`**: 필터 축은 **기주 · 스타일 · 메이킹 방법 · 당도 · 도수 · 향과 맛 6종**이다. 보유 재료는 P2
**`FR-SEARCH-003`**: 도수 필터는 **4구간**(논알콜 · 저 ~10% · 중 10–20% · 고 20%~). **연속 슬라이더를 쓰지 않는다** — 값별 카운트를 붙일 수 없어 `FR-SEARCH-002`와 충돌 (ADR-0003)
**`FR-SEARCH-005`**: 필터 조합을 **URL 쿼리스트링에 반영해 공유 가능**하게 하되 **`noindex`로 색인을 막는다**

**`PRIN-P06`**: 필터는 쿼리스트링이고 **색인하지 않는다.** 카테고리(3축 경로)와 다른 것이다

**SPEC-07 §3.1 `GET /cocktails`**

```
?base=gin,vodka        기주 (OR)
&style=sour            스타일 (OR)
&method=shake          메이킹 방법 (OR)
&sweet=semi_dry        당도 (단일)
&abv=mid,high          도수 구간 (OR) — ADR-0003
&flavor=citrus,herbal  향·맛 (AND)
&q=네그로니
&page=0&size=24
```

> **향·맛만 AND인 것이 의도다.** "시트러스 **그리고** 허브"를 원하지 "시트러스 또는 허브"가 아니다.
> 나머지 축은 OR — "진 **또는** 보드카"가 자연스럽다.
>
> 색인하지 않는다. 응답에 **`X-Robots-Tag: noindex`** 를 붙인다 (`R-F2.1-1`).

**SPEC-07 §2.1**: `GET /cocktails` — 목록 · 필터. **발행분만**
**SPEC-07 §5**: `draft`·`archived`는 **404**. 공개 응답에 내부 `id` 없음, `abv` 표시값 하나만

**SPEC-06 §5 인덱스**: `cocktail(status, base_spirit)` · `(status, style_primary)` · `(status, method)` · `(status, abv)`

**`NFR-S-02`**: 필터 결과는 `noindex`. **카테고리 경로는 색인** (배포 차단 조건)

**SPEC-05 §4**: `/cocktails/search?…` 클라이언트 필터 · `noindex`
**SPEC-05 §4**: 필터를 서버로 보내지 않는 이유 — Phase 1 규모에서는 **전체 목록을 받아 클라이언트에서 거르는 편이** 왕복 없이 즉각적이다. **데이터가 커지면 서버 필터로 옮기되 URL 계약은 유지한다**

> ⚠️ SPEC-05 §4는 클라이언트 필터, SPEC-07 §3.1은 서버 쿼리 파라미터를 정의한다. **모순이 아니다** — 서버가 계약을 제공하고(SSG 빌드·확장 대비), Phase 1의 프론트는 전체를 받아 클라이언트에서 거른다(이슈 040). **URL 계약이 동일해야 나중에 갈아끼울 수 있다.**

## RED

### 축별 필터 (`FR-SEARCH-001` 6종)

1. `base_필터가_동작한다`
2. `style_필터가_동작한다` — `styles` 조인 (`style_primary`가 아니라 **보유 전체**) **결정**
3. `method_필터가_동작한다`
4. `sweet_필터가_동작한다`
5. `abv_구간_필터가_동작한다`
6. `flavor_필터가_동작한다`
7. `6개_축_외의_파라미터는_무시되거나_400이다`

### OR / AND (SPEC-07 §3.1 — 이 이슈의 요체)

8. `base가_복수면_OR다` — `gin,vodka` → 둘 중 하나
9. `style이_복수면_OR다`
10. `method가_복수면_OR다`
11. `abv가_복수면_OR다`
12. `sweet는_단일값이다` — 복수 지정 시 400 또는 첫 값만 **결정**
13. **`flavor가_복수면_AND다`** — `citrus,herbal` → **둘 다** 가진 것만
14. `축이_다르면_AND로_결합된다` — `base=gin&style=sour` → 진 **그리고** 사워

### 도수 4구간 (`FR-SEARCH-003`, ADR-0003)

15. `구간_4종만_허용` — `na`·`low`·`mid`·`high`
16. `na는_abv_0이다`
17. `low는_0초과_10이하다`
18. `mid는_10초과_20이하다`
19. `high는_20초과다`
20. `구간_경계가_겹치지_않는다` — 10.0이 `low`인지 `mid`인지 결정론적
21. `연속값_파라미터를_받지_않는다` — `abvMin`·`abvMax` 부재 (`FR-SEARCH-003`)

### 발행분만 (SPEC-07 §2.1·§5)

22. `published만_반환된다`
23. `draft가_결과에_없다`
24. `archived가_결과에_없다`

### noindex (`FR-SEARCH-005`, `R-F2.1-1`, `NFR-S-02`)

25. `응답에_X_Robots_Tag_noindex가_있다`
26. `카테고리_경로_응답에는_noindex가_없다` — 이슈 022와 대비

### 규약 (SPEC-07 §1.5·§5)

27. `페이징이_동작한다` — `page`·`size`
28. `응답이_items와_page_형태다`
29. `공개_응답에_내부_id가_없다`
30. `abv_calculated_override가_노출되지_않는다`
31. `q_파라미터로_이름_검색이_된다` — 정밀 검색은 이슈 024

### 성능

32. `인덱스를_탄다` — `(status, base_spirit)` 등 EXPLAIN 단언
33. `정렬_파라미터가_허용목록으로_제한된다` (이슈 003 RED 21)

## GREEN

### `search/list`

```kotlin
data class CocktailFilter(
    val base: Set<BaseSpirit> = emptySet(),      // OR
    val style: Set<StyleKey> = emptySet(),       // OR
    val method: Set<Technique> = emptySet(),     // OR
    val sweet: Sweetness? = null,                // 단일
    val abv: Set<AbvBand> = emptySet(),          // OR
    val flavor: Set<FlavorKey> = emptySet(),     // AND  ← 유일하게 다르다
    val q: String? = null,
)
```

**`flavor`만 AND라는 것을 타입이나 주석으로 드러낸다.** 이 비대칭이 이 이슈에서 가장 틀리기 쉬운 지점이고, SPEC-07 §3.1이 이유를 명시했다.

```sql
-- flavor AND: 태그 개수만큼 매칭돼야 한다
WHERE c.id IN (
  SELECT cocktail_id FROM cocktail_aroma_tag
  WHERE aroma_tag = ANY(:flavors)
  GROUP BY cocktail_id HAVING count(*) = :flavorCount
)
```

### 도수 구간 (ADR-0003)

```kotlin
enum class AbvBand(val slug: String, val range: ClosedFloatingPointRange<Double>) {
    NA("na", 0.0..0.0),
    LOW("low", 0.0..10.0),      // 0 초과 — 경계 처리 주의 (RED 20)
    MID("mid", 10.0..20.0),
    HIGH("high", 20.0..100.0);
}
```

**경계를 명시적으로 결정한다** (RED 20). `low`가 `0 < abv <= 10` 인지 `0 <= abv < 10` 인지 문서에 없다 — ADR-0003을 확인하고, 없으면 **`(하한, 상한]`** + GAPS.

### `noindex` (RED 25)

```kotlin
@GetMapping("/cocktails")
fun list(...): ResponseEntity<PageResponse<CocktailListItem>> =
    ResponseEntity.ok().header("X-Robots-Tag", "noindex").body(...)
```

**하지 말 것**:
- 패싯 카운트 — 이슈 019
- 상세 — 이슈 020
- 카테고리 경로 — 이슈 022
- 통합 검색 — 이슈 024

## DoD

- [ ] RED 33항 전부 통과
- [ ] **`flavor`만 AND** (RED 13 — SPEC-07 §3.1)
- [ ] 도수 4구간, 연속 파라미터 부재 (RED 15·21 — ADR-0003)
- [ ] `X-Robots-Tag: noindex` (RED 25 — `NFR-S-02`)
- [ ] 인덱스 사용 확인 (RED 32)
- [ ] 미결은 [`DECISIONS.md`](DECISIONS.md) §1 확정분을 따른다 — **이슈에서 판단하지 않는다**
- [ ] 커밋: `feat(search): 칵테일 목록·필터 (FR-SEARCH-001·003·005, SPEC-07 §3.1)`
