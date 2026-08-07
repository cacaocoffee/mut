---
id: ISSUE-020
title: GET /cocktails/{slug} 상세
domain: COCKTAIL
layer: api
wave: 4
status: TODO
depends_on: [ISSUE-013]
fr: [FR-COCKTAIL-017]
r: []
inv: []
nfr: []
migration: —
owns:
  - apps/api/src/main/kotlin/kr/kcocktail/cocktail/web/**
---

## 근거

**`FR-COCKTAIL-017`**: 히어로 · 분류 · 스펙 · 재료 · 만드는 법 · 향과 맛 · 국내 구매 가이드 · 액션을 **필수 블록**으로 노출한다 (PRD 6.1)

**`FR-COCKTAIL-018`**: 분류 3축 각각을 **해당 카테고리 페이지로 링크**한다 (`R-C-2`) — 링크 대상 slug를 응답이 제공해야 한다

**SPEC-07 §2.1**
- `GET /cocktails/{slug}` — 상세
- `GET /cocktails/{slug}/recipes` — 레시피 버전 목록

**SPEC-07 §1.1**: 공개 식별자는 **`slug`**. 내부 `id`를 노출하지 않는다
**SPEC-07 §5 공개 API의 노출 범위** — 담지 않는 것:
- 내부 `id`
- `draft`·`archived` 상태의 리소스 (**404**)
- **`abv_calculated` / `abv_override` 구분** — 표시값 `abv` 하나만
- 파트너 계약 조건, 통계 원본
- 에디터 노트의 미발행 초안

> **SSG 빌드와 브라우저가 같은 엔드포인트를 쓴다.** 별도의 내부 전용 조회 API를 두지 않는다 — **두 벌이 되면 반드시 어긋난다.**

**SPEC-07 §1.6 캐싱**: `ETag` + `Cache-Control: public, max-age=60, stale-while-revalidate=600`. **SSG 빌드가 같은 엔드포인트를 반복 호출하므로 실효가 크다**

**SPEC-05 §4**: `/cocktails/[slug]` — **SSG + ISR**, 발행 시 on-demand 재생성, 색인 ✅
**`NFR-P-05`**: 상세·카테고리 페이지 **TTFB ≤ 200ms** — SSG라 정적 응답이어야 한다

**PRD 6.1 블록** (SCREENS-01이 화면 정본):
히어로 · 분류 · 스펙(도수·당도·잔) · 재료 · 만드는 법 · 향과 맛 · 국내 구매 가이드 · 액션

**`FR-COCKTAIL-028`**: 과음 경고·미성년자 판매 금지 문구 **하단 고정** (`R-F1.1-8`, `NFR-L-01`) — 표현은 FE(이슈 032·038)

## RED

### 조회

1. `slug로_상세를_조회한다`
2. `없는_slug는_404`
3. `draft는_404다` — 403이 아니다 (SPEC-07 §5)
4. `archived도_404다`
5. `editor는_draft를_조회할_수_있다` ⚖️ — SPEC-08 §2 "draft 콘텐츠 조회 = editor ○". **공개 엔드포인트가 아니라 어드민 경로**(이슈 025)로 봐야 한다. 보수적으로 공개 경로는 항상 404 + GAPS

### 필수 블록 (`FR-COCKTAIL-017`)

6. `응답에_히어로_정보가_있다` — 이름·요약·이미지
7. `응답에_분류_3축이_있다`
8. `응답에_스펙이_있다` — 도수·당도·잔
9. `응답에_재료_목록이_있다`
10. `응답에_만드는_법_스텝이_있다`
11. `응답에_향과_맛_서술이_있다` — `tasting_note`
12. `응답에_국내_구매_가이드가_있다` — 재료별 `domestic_availability`·대체재·가격대
13. `8개_블록이_전부_있다` — 파라미터라이즈드. **하나라도 없으면 실패**

### 카테고리 링크 (`FR-COCKTAIL-018`)

14. `분류_3축에_각각_slug가_포함된다` — 링크 구성용
15. `base_slug가_ADR_0002_확정값이다` — `korean`·`non-alcoholic` 등
16. `style_primary_slug가_포함된다`
17. `method_slug가_포함된다`
18. `styles_전체도_포함된다` — 표시용. 링크는 primary만

### 레시피 (`FR-COCKTAIL-003`)

19. `기본_노출은_standard_레시피다` (SPEC-02 §2.6)
20. `recipes_엔드포인트가_버전_목록을_반환한다`
21. `Phase_1a에는_standard만_존재한다` — `bar_signature`는 1b
22. `재료에_대체재_정보가_포함된다` (`FR-COCKTAIL-021`)
23. `재료에_amount_label이_포함된다` — 배수 계산 제외 표기 (`FR-COCKTAIL-019`)
24. `재료에_counts_for_stock이_포함되는가` ⚖️ — Phase 2 역검색용. 지금 노출하면 쓸데없다. **보수적으로 미노출** + GAPS

### 노출 범위 (SPEC-07 §5)

25. `내부_id가_없다`
26. `abv_calculated와_abv_override가_없다` — `abv` 하나만
27. `is_classic_같은_내부_플래그가_노출되는가` ⚖️ — 클래식 배지 표시에 필요할 수 있다. 보수적으로 **노출** (콘텐츠 성격)
28. `status가_노출되지_않는다` — published만 나오므로 무의미

### 캐싱 (SPEC-07 §1.6)

29. `ETag가_붙는다`
30. `Cache_Control_max_age_60_swr_600이_붙는다`
31. `If_None_Match_일치시_304`
32. `내용이_바뀌면_ETag가_바뀐다`

### 색인 (`NFR-S-01`)

33. `noindex가_붙지_않는다` — 상세는 색인 대상 (이슈 018과 대비)

## GREEN

### `cocktail/web`

```kotlin
@GetMapping("/api/v1/cocktails/{slug}")
fun detail(@PathVariable slug: String): ResponseEntity<CocktailDetail>
```

```kotlin
data class CocktailDetail(
    val slug: String,                    // id 없음 (SPEC-07 §1.1)
    val nameKo: String, val nameEn: String,
    val summary: String,
    val classification: Classification,  // base/stylePrimary/styles/method + 각 slug
    val spec: Spec,                      // abv(표시값 하나) · sweetness · glassType
    val ingredients: List<IngredientLine>,
    val steps: List<Step>,
    val tastingNote: String,
    val story: String?,
    val origin: Origin?,
    val purchaseGuide: List<PurchaseGuideItem>,   // 국내 구매 가이드
    val isClassic: Boolean,
)
```

**`abv` 하나만** (RED 26). `abvCalculated`·`abvOverride`를 DTO에 넣지 않는다 — 넣으면 프론트가 언젠가 쓴다.

### 국내 구매 가이드 (RED 12)

`ingredient` 모듈의 `IngredientFacade`로 조회한다 (`PRIN-T03` — 리포지토리 직접 참조 금지).
`domestic_availability` · `substitute_note` · `price_band` · 브랜드(`is_sponsored` 포함).

### N+1 회피

재료 → 재료 마스터 조회가 재료 수만큼 나가면 안 된다. `IngredientFacade.findApproved(ids)` 벌크 조회 (이슈 008에 이미 정의).

### 캐싱

`ETag`는 `updated_at` 기반 약한 검증자로 충분하다. 강한 해시는 계산 비용 대비 실익이 없다 — SSG 빌드가 주 소비자다.

**하지 말 것**:
- 배리에이션 추천 — 이슈 021
- **"이 칵테일을 마실 수 있는 바"** (`FR-COCKTAIL-025`) — **Phase 1b** (BAR 의존)
- Schema.org·OG — 이슈 044 (FE)
- 잔 수 환산·단위 토글 — 이슈 043 (FE)

## DoD

- [ ] RED 33항 전부 통과
- [ ] **8개 필수 블록 전부** (RED 13 — `FR-COCKTAIL-017`)
- [ ] `abv` 표시값 하나만, 내부 `id` 없음 (RED 25·26 — SPEC-07 §5)
- [ ] `draft`·`archived` 404 (RED 3·4)
- [ ] N+1 없음, ETag 동작
- [ ] ⚖️ 4건(editor draft 조회·`counts_for_stock` 노출·`is_classic` 노출·`status` 노출) `GAPS.md` 등재
- [ ] 커밋: `feat(cocktail): 상세 조회 API (FR-COCKTAIL-017·018, SPEC-07 §5)`
