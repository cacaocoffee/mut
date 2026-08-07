---
id: ISSUE-044
title: Schema.org Recipe + OG 태그
domain: COCKTAIL
layer: web
wave: 8
status: TODO
depends_on: [ISSUE-038]
fr: [FR-COCKTAIL-026, FR-USER-005]
r: [R-F1.1-6, R-F5-5]
inv: []
nfr: [NFR-S-05, NFR-S-06]
migration: —
owns:
  - apps/web/app/cocktails/[slug]/opengraph-image.tsx
  - apps/web/lib/structured-data.ts
---

## 근거

**`FR-COCKTAIL-026`**: **Schema.org `Recipe`** 구조화 데이터를 출력한다 (`R-F1.1-6`)
**`FR-USER-005`**: **OG 태그를 최적화**해 **카카오톡 공유 시 카드형 미리보기**가 뜨게 한다 (`R-F5-5`)

**`NFR-S-05`**: 칵테일 상세에 Schema.org `Recipe` — 측정: **리치 결과 테스트**. 실패 시 **경고**
**`NFR-S-06`**: 모든 공개 페이지에 OG 태그. **카카오톡 카드 미리보기 확인** — 측정: 수동. **발행 전 확인**

**`PRIN-T04`**: 검색 유입이 초기 성장의 절반이다 (PRD 2.2 — 유기 검색 50% 이상)

**SPEC-10 §6 지표**: **유기 검색 유입 비중 50%+** — 1a에서 검증 가능한 셋 중 하나

**SPEC-04 §9.2 수동 릴리즈 체크리스트**: `NFR-S-06` 포함

**SPEC-08 §8 법적 요구**: 브랜디드 콘텐츠 주류광고 고지 — OG 이미지에도 적용되나 ⚖️ (Phase 1a에 브랜디드 콘텐츠 없음)

### Schema.org Recipe에 담을 것

`FR-COCKTAIL-017`의 8개 블록 중 구조화 가능한 것:

| Schema.org | 출처 |
|---|---|
| `name` | `nameKo` |
| `description` | `summary` |
| `recipeIngredient` | 재료 목록 (용량 + 이름) |
| `recipeInstructions` | 스텝 |
| `recipeCategory` | 분류 3축 ⚖️ |
| `recipeYield` | 1잔 |
| `image` | 히어로 |
| `keywords` | 향·맛 태그 ⚖️ |

⚠️ **`nutrition`·`aggregateRating`을 넣지 않는다** — `PRIN-P04`("별점을 쌓지 않는다")가 총점을 금지했다. **`aggregateRating`은 없다.**

## RED

### Schema.org (`FR-COCKTAIL-026`, `NFR-S-05`)

1. `Recipe_타입_JSON_LD가_출력된다`
2. `초기_HTML에_있다` — 크롤러가 본다
3. `name이_있다`
4. `description이_있다`
5. `recipeIngredient가_있다`
6. `recipeInstructions가_있다`
7. `image가_있다`
8. **`aggregateRating이_없다`** (`PRIN-P04` — **별점을 쌓지 않는다**)
9. `review가_없다` (`PRIN-P04`)
10. `JSON_LD가_유효한_JSON이다`
11. `리치_결과_테스트를_통과한다` (`NFR-S-05`)
12. `발행분만_구조화_데이터를_갖는다`

### OG 태그 (`FR-USER-005`, `NFR-S-06`)

13. `og_title이_있다`
14. `og_description이_있다`
15. `og_image가_있다`
16. `og_url이_절대_URL이다`
17. `og_type이_설정된다`
18. `twitter_card가_설정되는가` ⚖️ — `R-F5-5`는 **카카오톡**만 명시. 보수적으로 **추가** + GAPS
19. **`카카오톡_카드_미리보기가_뜬다`** (`NFR-S-06` — 수동 확인)
20. `모든_공개_페이지에_OG가_있다` (`NFR-S-06`) — 상세·카테고리·홈
21. `OG_이미지_크기가_권장값이다` ⚖️ — 카카오 권장 800×400 등 + GAPS

### OG 이미지

22. `칵테일마다_고유한_OG_이미지가_있다` ⚖️ — 동적 생성 vs 히어로 이미지 재사용.
    ⚠️ **히어로 이미지가 없다** — `media_asset`이 미구현(G-07 이미지 저장소 미정).
    → **보수적으로 정적 기본 이미지 + 텍스트 오버레이 동적 생성**(`opengraph-image.tsx`) + **GAPS 등재**
23. `이미지가_없어도_OG가_깨지지_않는다` — 기본 이미지 폴백
24. `이미지_생성이_빌드를_느리게_하지_않는다`

### 법적 (`NFR-L-01` 정신)

25. `OG_description에_과음_경고가_필요한가` ⚖️ — `NFR-L-01`은 "페이지 하단"이라 OG는 대상 밖. 보수적으로 **미포함** + GAPS

### 성능 (`NFR-P-01`)

26. `구조화_데이터가_LCP에_영향을_주지_않는다`
27. `OG_이미지가_페이지_로딩을_막지_않는다`

## GREEN

### `lib/structured-data.ts`

```ts
// FR-COCKTAIL-026 · R-F1.1-6
export function recipeJsonLd(c: CocktailDetail) {
  return {
    "@context": "https://schema.org",
    "@type": "Recipe",
    name: c.nameKo,
    description: c.summary,
    recipeIngredient: c.ingredients.map(fmt),
    recipeInstructions: c.steps.map((s) => ({ "@type": "HowToStep", text: s.text })),
    recipeYield: "1잔",
    image: ogImageUrl(c.slug),
    // PRIN-P04 — aggregateRating·review 를 넣지 않는다. 별점을 쌓지 않는 서비스다
  };
}
```

**`aggregateRating`을 넣고 싶어지는 지점이다** (리치 결과에서 별이 뜬다). `PRIN-P04`가 금지했고, RED 8이 그것을 고정한다. **주석에 근거를 남긴다.**

### OG 이미지 (RED 22 — ⚠️ G-07)

**이미지 저장소가 미정**이라 히어로 이미지가 없다.

```tsx
// app/cocktails/[slug]/opengraph-image.tsx
// G-07(이미지 저장소) 미정이라 텍스트 기반 동적 생성으로 시작한다.
// 저장소 확정 후 히어로 이미지로 교체 — GAPS 등재
export default async function Image({ params }) {
  return new ImageResponse(<div>{/* 이름 + 기주 + Modernist 토큰 */}</div>);
}
```

`packages/ui` 토큰을 **읽어서** 쓰되 **수정하지 않는다** (CONVENTIONS §4).

### 카카오톡 확인 (RED 19 — `NFR-S-06`)

**수동 측정**이다 (SPEC-04 §9.2 릴리즈 체크리스트). 자동화할 수 없으므로 **체크리스트 항목으로 남긴다**.

카카오는 자체 캐시가 있어 **디버거로 갱신**해야 한다 — README에 절차를 적는다.

**하지 말 것**:
- `aggregateRating`·`review` (`PRIN-P04`)
- 히어로 이미지 파이프라인 — G-07 미정
- 사이트맵 — 이슈 039

## DoD

- [ ] RED 27항 전부 통과
- [ ] **`aggregateRating`·`review` 부재** (RED 8·9 — `PRIN-P04`, 근거 주석)
- [ ] 리치 결과 테스트 통과 (RED 11 — `NFR-S-05`)
- [ ] **카카오톡 카드 미리보기 수동 확인** + README에 갱신 절차 (RED 19 — `NFR-S-06`)
- [ ] 모든 공개 페이지에 OG (RED 20)
- [ ] `packages/ui` 무수정
- [ ] ⚖️ 5건(twitter card·이미지 크기·OG 이미지 전략 G-07·OG 경고 문구·기본 폴백) `GAPS.md` 등재
- [ ] 커밋: `feat(web): Schema.org Recipe·OG 태그 (FR-COCKTAIL-026, FR-USER-005, NFR-S-05·S-06)`
