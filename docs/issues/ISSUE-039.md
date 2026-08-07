---
id: ISSUE-039
title: 카테고리 페이지 SSG (축 조합 0개)
domain: COCKTAIL
layer: web
wave: 8
status: TODO
depends_on: [ISSUE-022, ISSUE-037]
fr: [FR-COCKTAIL-029, FR-COCKTAIL-030, FR-COCKTAIL-031]
r: [R-C-2]
inv: []
nfr: [NFR-S-01, NFR-S-02, NFR-S-03, NFR-S-04, NFR-S-07]
migration: —
owns:
  - apps/web/app/cocktails/base/[slug]/**
  - apps/web/app/cocktails/style/[slug]/**
  - apps/web/app/cocktails/method/[slug]/**
  - apps/web/app/sitemap.ts
---

## 근거

**`PRIN-P06` — 카테고리와 필터는 다른 것이다.** 이 구분이 무너지면 **URL 구조와 SEO가 함께 무너진다**

> 당도나 도수를 카테고리로 올리면 `/cocktails/sweet/high-abv/gin/` 같은 **조합 폭발**이 생기고 **중복 콘텐츠로 SEO 페널티**를 받는다. **축 조합 경로는 만들지 않는다** (`R-C-2`)

**`FR-COCKTAIL-029`**: `/cocktails/base/<slug>/` · `/cocktails/style/<slug>/` · `/cocktails/method/<slug>/` **단일 축 경로만** 생성하고 색인
**`FR-COCKTAIL-030`**: **축 조합 경로를 만들지 않는다.** 조합은 쿼리스트링 필터로만
**`FR-COCKTAIL-031`** (P1): 카테고리마다 **고유한 소개 문구**. 목록만 있는 페이지는 **색인 가치가 없다**

**SPEC-05 §4**: `/cocktails/base/[slug]` 외 카테고리 2종 — **SSG + ISR**, 발행 시 on-demand, 색인 ✅

**`NFR-S-01`**: 카테고리는 **SSG + ISR** — 배포 차단
**`NFR-S-02`**: 필터 결과는 `noindex`. **카테고리 경로는 색인** — 배포 차단
**`NFR-S-03`**: **축 조합 경로가 0개** — **사이트맵 검사**, 배포 차단
**`NFR-S-04`**: 발행분 전체가 사이트맵에 포함 — 발행 시 재생성
**`NFR-S-07`**: 카테고리마다 **고유 소개 문구** — 어드민 필수 입력, **발행 차단**

**SPEC-04 §1.1**: 카테고리 **LCP ≤ 2.0s**
**`PRIN-D02`**: 카테고리 슬러그는 **노출되는 순간 URL**이고 바꾸면 리다이렉트 부채. 확정본은 ADR-0002

**이슈 022**가 `GET /categories`를 제공한다 (`generateStaticParams`용).

## RED

### 3축 경로 (`FR-COCKTAIL-029`)

1. `base_카테고리_페이지가_생성된다`
2. `style_카테고리_페이지가_생성된다`
3. `method_카테고리_페이지가_생성된다`
4. `3축_외의_카테고리_라우트가_없다` — 당도·도수·향맛 디렉터리 부재 (`PRIN-P06`)
5. `슬러그가_ADR_0002_확정값이다` — `korean`·`non-alcoholic`·`agave`
6. `없는_슬러그는_404`

### 축 조합 0개 (`FR-COCKTAIL-030`, `R-C-2`, `NFR-S-03`) — 요체

7. `중첩_동적_라우트가_없다` — `base/[slug]/style/[slug]` 형태의 디렉터리 부재
8. `사이트맵에_조합_경로가_0개다` (`NFR-S-03` — 배포 차단)
9. `내부_링크에_조합_경로가_없다`
10. `조합_경로를_만드는_헬퍼가_없다` (이슈 015 RED 9와 같은 장치)

### SSG (`NFR-S-01`, SPEC-05 §4)

11. `generateStaticParams가_categories_API를_쓴다` (이슈 022)
12. `SSG로_생성된다`
13. `ISR이_설정돼_있다`
14. `요청시_렌더하지_않는다`
15. `LCP가_2_0s_이하다` (SPEC-04 §1.1)

### 색인 (`NFR-S-02`)

16. `카테고리에_noindex가_없다`
17. `필터_경로에는_noindex가_있다` — 대비 (이슈 040)
18. `canonical이_설정된다`

### 소개 문구 (`FR-COCKTAIL-031`, `NFR-S-07`)

19. `카테고리마다_소개_문구가_렌더된다`
20. `문구가_카테고리마다_다르다` — 고유성
21. `문구가_없으면_어떻게_되는가` ⚖️ — `NFR-S-07`은 "발행 차단"인데 `FR-COCKTAIL-031`은 **P1**. 이슈 022 RED 17과 **같은 판단**: 구조는 만들되 없어도 페이지는 나온다 + GAPS
22. `문구가_초기_HTML에_있다` — 색인 대상

### 사이트맵 (`NFR-S-04`)

23. `사이트맵에_발행_칵테일_전체가_있다`
24. `사이트맵에_카테고리_경로가_있다`
25. `사이트맵에_필터_경로가_없다` — `noindex` 대상
26. `사이트맵에_draft가_없다`
27. `발행시_사이트맵이_재생성된다` (이슈 015 RED 23)

### 목록 렌더

28. `해당_카테고리_칵테일이_렌더된다`
29. `발행분만_보인다`
30. `style_카테고리는_style_primary_기준이다` — 이슈 022 RED 14와 정합
31. `빈_카테고리_페이지가_생성되지_않는다` — 이슈 022 RED 10

### 법적

32. `과음_경고가_하단에_있다` (`NFR-L-01`)

## GREEN

### 디렉터리 구조 (RED 4·7)

```
app/cocktails/
├─ [slug]/page.tsx           상세 (이슈 038)
├─ base/[slug]/page.tsx
├─ style/[slug]/page.tsx
└─ method/[slug]/page.tsx
```

**중첩이 없다.** `base/[slug]/style/[slug]/` 를 만들 수 없는 구조인 것이 `R-C-2`의 물리적 구현이다 (RED 7).

당도·도수·향맛 디렉터리가 **없는 것**이 `PRIN-P06`이다 (RED 4).

### `generateStaticParams` (RED 11)

```tsx
export async function generateStaticParams() {
  const { base } = await fetch(`${API}/api/v1/categories`).then(r => r.json());
  return base.map((c) => ({ slug: c.slug }));   // 이슈 022가 빈 카테고리를 이미 제외
}
```

### 사이트맵 (RED 23~27, `NFR-S-03`·`S-04`)

```ts
// app/sitemap.ts
export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  // 상세 + 카테고리 3축만. 필터 경로는 넣지 않는다 (NFR-S-02)
}
```

**`NFR-S-03`의 "축 조합 경로 0개"를 사이트맵 검사로 측정**한다 (RED 8). CI에 넣는다:

```
// 사이트맵의 모든 경로가 아래 패턴 중 하나여야 한다
/cocktails/{slug}
/cocktails/(base|style|method)/{slug}
```

패턴 밖 경로가 있으면 실패 — `NFR-S-03`의 배포 차단이 이것으로 구현된다.

### 소개 문구 (RED 19~22)

이슈 022의 `category_intro` 테이블에서 온다. **`GET /categories` 응답의 `intro` 필드.**

없으면 ⚖️ 보수적으로 **페이지는 렌더하되 CI 경고**. `NFR-S-07`의 "발행 차단"은 P1 착수 시 (이슈 022와 동일 판단).

**하지 말 것**:
- 필터 UI — 이슈 040
- 조합 경로 (`R-C-2`)
- 당도·도수 카테고리 (`PRIN-P06`)

## DoD

- [ ] RED 32항 전부 통과
- [ ] **축 조합 라우트 부재** (RED 7 — 디렉터리 구조로 성립)
- [ ] **사이트맵 조합 경로 0개 CI 검사** (RED 8 — `NFR-S-03` 배포 차단)
- [ ] 3축 외 카테고리 디렉터리 부재 (RED 4 — `PRIN-P06`)
- [ ] SSG + ISR, 색인됨 (RED 12·16 — `NFR-S-01·S-02`)
- [ ] 슬러그가 ADR-0002 확정값 (RED 5 — `PRIN-D02`)
- [ ] ⚖️ 1건(소개 문구 P0/P1 충돌 — 이슈 022와 동일) `GAPS.md` 등재
- [ ] 커밋: `feat(web): 카테고리 페이지 SSG (FR-COCKTAIL-029·030·031, R-C-2, NFR-S-03)`
