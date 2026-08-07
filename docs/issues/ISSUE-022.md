---
id: ISSUE-022
title: GET /categories 3축 카테고리
domain: COCKTAIL
layer: api
wave: 4
status: TODO
depends_on: [ISSUE-013]
fr: [FR-COCKTAIL-029, FR-COCKTAIL-030]
r: [R-C-2]
inv: []
nfr: [NFR-S-02, NFR-S-03, NFR-S-04, NFR-S-07]
migration: V022
owns:
  - apps/api/src/main/kotlin/kr/kcocktail/cocktail/category/**
  - apps/api/src/main/resources/db/migration/V022__*.sql
---

## 근거

**`PRIN-P06` — 카테고리와 필터는 다른 것이다**

> 이 구분이 무너지면 **URL 구조와 SEO가 함께 무너진다.**
>
> 당도나 도수를 카테고리로 올리면 `/cocktails/sweet/high-abv/gin/` 같은 **조합 폭발**이 생기고 **중복 콘텐츠로 SEO 페널티**를 받는다. **축 조합 경로는 만들지 않는다** (`R-C-2`).

**`FR-COCKTAIL-029`**: `/cocktails/base/<slug>/` · `/cocktails/style/<slug>/` · `/cocktails/method/<slug>/` **단일 축 경로만** 생성하고 색인한다
**`FR-COCKTAIL-030`**: **축 조합 경로를 만들지 않는다.** 조합은 쿼리스트링 필터로만 표현한다
**`FR-COCKTAIL-031`** (P1): 카테고리 페이지마다 **고유한 소개 문구**. 목록만 있는 페이지는 색인 가치가 없다

**SPEC-07 §2.1**: `GET /categories` — **3축 슬러그 전체.** 사이트맵·`generateStaticParams`용

**`NFR-S-02`**: 필터 결과는 `noindex`. **카테고리 경로는 색인** — 배포 차단
**`NFR-S-03`**: **축 조합 경로가 0개** — 사이트맵 검사, 배포 차단
**`NFR-S-04`**: 발행분 전체가 사이트맵에 포함
**`NFR-S-07`**: 카테고리 페이지마다 **고유 소개 문구** — 어드민 필수 입력, **발행 차단**

**SPEC-05 §4**: `/cocktails/base/[slug]` 외 카테고리 2종 — **SSG + ISR**, 발행 시 on-demand, 색인 ✅
**`PRIN-D02`**: 카테고리 슬러그는 **노출되는 순간 URL**이고 바꾸면 리다이렉트 부채. 확정본은 ADR-0002

**ADR-0002 슬러그 확정** — `korean`(not `soju`) 등. `packages/domain/src/types.ts`의 `BASE_SLUGS`가 현재 정본이고 이슈 004에서 Kotlin으로 옮겨졌다

### 이 엔드포인트의 소비자

`generateStaticParams` (Next.js SSG) — **어떤 카테고리 경로를 정적 생성할지**를 이 응답이 결정한다. 그래서 "코퍼스에 실제로 존재하는 값만" 낼지 "enum 전체"를 낼지가 중요하다.

**`types.ts` 주석**: "enum은 PRD 기준으로 완전하게 두고(카테고리 URL이 되므로), **화면에는 아카이브에 실제로 존재하는 값만 노출**한다 — `basesInCorpus` 등을 쓸 것"

## RED

### 3축 경로 (`FR-COCKTAIL-029`)

1. `base_카테고리_목록을_반환한다`
2. `style_카테고리_목록을_반환한다`
3. `method_카테고리_목록을_반환한다`
4. `3축_외의_카테고리가_없다` — 당도·도수·향맛은 카테고리가 아니다 (`PRIN-P06`)
5. `각_항목에_slug와_라벨과_건수가_있다`
6. `slug가_ADR_0002_확정값이다` — `korean`·`non-alcoholic`·`agave` 등 전수

### 축 조합 금지 (`FR-COCKTAIL-030`, `R-C-2`, `NFR-S-03`)

7. `조합_경로가_응답에_없다` — `base/gin/style/sour` 형태 0건
8. `조합_경로를_생성하는_코드가_없다` — 축 2개를 이어붙이는 경로 조립 부재
9. `사이트맵에_조합_경로가_0개다` (`NFR-S-03`)

### 코퍼스 존재 여부

10. `발행분이_있는_카테고리만_반환하는가` ⚖️ — `generateStaticParams`가 빈 페이지를 만들면 안 된다. **보수적으로 건수 0인 축값은 제외** + GAPS
11. `건수가_정확하다` — 발행분 기준
12. `draft만_있는_카테고리는_제외된다`
13. `enum_전체_목록도_별도로_제공하는가` ⚖️ — 필터 UI는 전체가 필요할 수 있다. **보수적으로 `?include=all` 옵션** 또는 별도 필드 + GAPS

### style 축의 특수성

14. `style_카테고리는_style_primary_기준이다` ⚖️ — 아니면 `styles` 전체. **`R-C-3`이 primary를 대표로 규정**했으므로 primary 기준. + GAPS
15. `styles에만_있고_primary가_아닌_값도_카테고리가_되는가` — RED 14의 반대면

### 소개 문구 (`FR-COCKTAIL-031`, `NFR-S-07`)

16. `카테고리마다_소개_문구_필드가_있다`
17. `소개_문구가_없으면_어떻게_되는가` ⚖️ — `NFR-S-07`은 "**발행 차단**"이라 하지만 `FR-COCKTAIL-031`은 **P1**이다. 충돌.
    **보수적 해석**: 문구 저장 구조는 지금 만들되(P0 스키마), 없어도 카테고리 페이지는 나온다. `NFR-S-07`의 발행 차단은 **P1 착수 시** 적용. + GAPS 등재

### 색인 (`NFR-S-02`)

18. `카테고리_응답에_noindex가_붙지_않는다` — 이슈 018과 대비
19. `사이트맵에_카테고리_경로가_포함된다` (`NFR-S-04`)

### 캐싱

20. `ETag와_Cache_Control이_붙는다`

## GREEN

### `cocktail/category`

```kotlin
@GetMapping("/api/v1/categories")
fun categories(): CategoriesResponse
```

```kotlin
data class CategoriesResponse(
    val base: List<CategoryItem>,
    val style: List<CategoryItem>,
    val method: List<CategoryItem>,
    // 축이 셋뿐이다 — 당도·도수·향맛 필드가 없는 것이 PRIN-P06의 구현 (RED 4)
)
data class CategoryItem(val slug: String, val labelKo: String, val count: Int, val intro: String?)
```

**타입에 3축만 있는 것이 `PRIN-P06`의 물리적 구현이다.** 당도 필드를 추가하려면 이 타입을 고쳐야 하고, 그때 이 주석을 읽게 된다.

### 소개 문구 저장 (RED 16)

카테고리는 테이블이 아니라 enum이다. 문구를 어디 두나?

```sql
CREATE TABLE category_intro (
  axis VARCHAR(8) NOT NULL CHECK (axis IN ('base','style','method')),
  slug VARCHAR(24) NOT NULL,
  intro TEXT,
  PRIMARY KEY (axis, slug)
);
```

⚠️ **SPEC-06에 이 테이블이 없다.** `FR-COCKTAIL-031`·`NFR-S-07`이 요구하는데 ERD에 누락됐다.
→ **CONVENTIONS §6**: `GAPS.md`에 "SPEC-06에 `category_intro` 누락" 등재 후 추가. 마이그레이션 번호는 이 이슈의 `V022`... **frontmatter에 `migration: —`로 적혀 있다.**

→ **이 이슈의 frontmatter를 `migration: V022`로 고치고** 마이그레이션을 만든다. 착수 시 INDEX.md도 함께 갱신한다.

### 축 조합 방지 (RED 8)

경로 조립 함수가 **축 하나만** 받는 시그니처여야 한다:

```kotlin
fun categoryPath(axis: Axis, slug: String): String = "/cocktails/${axis.slug}/$slug"
// 축 2개를 받는 오버로드를 만들지 않는다 — R-C-2
```

**하지 말 것**:
- 카테고리 페이지 렌더링 — 이슈 039 (FE)
- 사이트맵 생성 — 이슈 039 또는 044 (FE가 Next.js `sitemap.ts`로)

## DoD

- [ ] RED 20항 전부 통과
- [ ] 응답 타입에 **3축만** 존재 (RED 4 — `PRIN-P06`)
- [ ] 축 조합 경로 생성 **불가 구조** (RED 8 — `R-C-2`, `NFR-S-03`)
- [ ] slug가 ADR-0002 확정값 (RED 6)
- [ ] **`category_intro` 테이블 추가 + `GAPS.md`에 SPEC-06 누락 등재**, frontmatter `migration: V022`로 갱신
- [ ] ⚖️ 5건(빈 카테고리·enum 전체 제공·style 기준·소개 문구 P0/P1 충돌) `GAPS.md` 등재
- [ ] 커밋: `feat(cocktail): 3축 카테고리 API (FR-COCKTAIL-029·030, R-C-2, PRIN-P06)`
