---
id: ISSUE-023
title: GET /ingredients 재료 사전
domain: INGREDIENT
layer: api
wave: 4
status: TODO
depends_on: [ISSUE-008, ISSUE-009]
fr: [FR-INGREDIENT-002, FR-INGREDIENT-005]
r: [R-F1.3-1, R-F1.3-3, R-F2.1-3]
inv: [INV-INGREDIENT-02]
nfr: []
migration: —
owns:
  - apps/api/src/main/kotlin/kr/kcocktail/ingredient/web/**
---

## 근거

**`FR-INGREDIENT-002`**: 재료 상세에 설명 · 도수 · 대표 브랜드 · 국내 유통 여부 · 가격대 · **이 재료를 쓰는 칵테일 목록**을 노출한다 (`R-F1.3-1`)
**`FR-INGREDIENT-005`**: 재료별 별칭을 등록해 검색에 반영한다 (`R-F2.1-3`) — 색인은 이슈 017, 여기서는 노출

**SPEC-07 §2.2**

| 메서드 | 경로 | 설명 |
|---|---|---|
| `GET` | `/ingredients` | 재료 사전 목록 |
| `GET` | `/ingredients/{slug}` | 상세 · 국내 유통 · 대체재 |
| `GET` | `/ingredients/{slug}/cocktails` | **이 재료를 쓰는 칵테일** (`R-F1.3-1`) |

**SPEC-06 §5**: `recipe_ingredient(ingredient_id)` B-tree — **역검색 · 재료 사전**. 이 인덱스가 `/{slug}/cocktails`의 경로다

**`INV-INGREDIENT-02`** (`R-F1.3-3`): 특정 브랜드 언급 시 **광고성 여부를 구분해 표기**
**SPEC-06 §4.3**: `INV-INGREDIENT-02` — "`is_sponsored` 저장은 DB, **라벨 렌더링 강제는 앱**"
**`NFR-L-02`**: `is_sponsored` 콘텐츠에 라벨이 **끌 수 없게** 표기 — 배포 차단 · **공정위 의무**

**`PRIN-P05` — 국내 기준으로 정규화한다**

> 이 서비스가 해외 DB의 번역판이 아닌 이유는 `domestic_availability` 하나다.

**`FR-INGREDIENT-001`**: 신규 추가는 **에디터 승인제** → 미승인 재료는 공개에 안 나온다 (이슈 008 RED 16)

**SPEC-07 §5**: 공개 응답에 내부 `id` 없음

## RED

### 목록

1. `승인된_재료만_반환된다` (`FR-INGREDIENT-001`)
2. `미승인_재료가_목록에_없다`
3. `카테고리_필터가_동작한다` — 7종
4. `국내유통_필터가_동작한다` — 4종
5. `페이징된다`
6. `내부_id가_없다`

### 상세 (`FR-INGREDIENT-002` 6개 항목)

7. `설명이_포함된다`
8. `도수가_포함된다`
9. `대표_브랜드가_포함된다`
10. `국내_유통_여부가_포함된다` — `domestic_availability`
11. `가격대가_포함된다` — `price_band`
12. `이_재료를_쓰는_칵테일_목록이_제공된다` (`R-F1.3-1`)
13. `6개_항목이_전부_있다` — 파라미터라이즈드
14. `대체재_안내가_포함된다` — `substitute_note`
15. `별칭이_포함된다` (`FR-INGREDIENT-005`)
16. `없는_slug는_404`
17. `미승인_재료_상세는_404다` — 403이 아니다

### 브랜드 광고성 (`INV-INGREDIENT-02`, `NFR-L-02`)

18. `브랜드마다_is_sponsored가_응답에_있다`
19. `is_sponsored_true면_라벨_표기_플래그가_참이다`
20. `라벨_플래그를_끌_수_있는_파라미터가_없다` — **`NFR-L-02` "끌 수 없게"**. 쿼리스트링·헤더로 억제 불가
21. `is_sponsored가_null이_아니다` — 항상 결정돼 있다

### 이 재료를 쓰는 칵테일 (`R-F1.3-1`)

22. `발행된_칵테일만_반환된다`
23. `draft_칵테일이_없다`
24. `표준_레시피_기준이다` **결정** — `bar_signature`(1b)에만 쓰인 재료도 셀지. **standard만**
25. `선택_재료도_포함되는가` **결정** — `is_optional=true`. **포함하되 표시**
26. `대체재로만_등장하는_칵테일도_포함되는가` **결정** — `substitute_ingredient_id`. **제외**
27. `페이징된다`
28. `인덱스를_탄다` — `recipe_ingredient(ingredient_id)` EXPLAIN (SPEC-06 §5)

### 캐싱·색인

29. `ETag와_Cache_Control이_붙는다`
30. `noindex가_붙지_않는다` **결정** — 재료 사전은 색인 가치가 있다. SPEC-05 §4 렌더링 표에 `/ingredients` 경로가 **없다**.

## GREEN

### `ingredient/web`

```kotlin
data class IngredientDetail(
    val slug: String,                       // id 없음
    val nameKo: String, val nameEn: String,
    val aliases: List<String>,
    val category: String,
    val abv: BigDecimal?,
    val description: String?,
    val domesticAvailability: String,       // PRIN-P05 — 이 서비스의 정체성
    val substituteNote: String?,
    val priceBand: String?,
    val brands: List<BrandItem>,            // is_sponsored 포함
)
data class BrandItem(val name: String, val purchaseUrl: String?, val isSponsored: Boolean)
```

**`isSponsored`가 DTO에 항상 있고 nullable이 아니다** (RED 21). 옵셔널이면 프론트가 빠뜨린다.

### 라벨 억제 불가 (RED 20 — `NFR-L-02`)

서버는 `isSponsored`를 **항상 그대로** 내려보낸다. "라벨 숨김" 파라미터를 만들지 않는다.
실제 렌더링 강제는 FE(이슈 032)의 몫이고, `NFR-L-02`가 배포 차단 조건이다.

> **공정위 추천·보증 심사지침상 의무이며 위반 시 제재 대상**이다 (`FR-PARTNER-005` 문구). Phase 1a에는 PARTNER가 없지만 **재료 브랜드에 이미 같은 규칙이 적용된다** (`R-F1.3-3`).

### 이 재료를 쓰는 칵테일 (RED 22~28)

```sql
SELECT DISTINCT c.slug, c.name_ko, c.summary
FROM recipe_ingredient ri
JOIN recipe r ON r.id = ri.recipe_id AND r.version_type = 'standard'   -- RED 24
JOIN cocktail c ON c.id = r.cocktail_id AND c.status = 'published'     -- RED 22
WHERE ri.ingredient_id = :id
```

`recipe_ingredient(ingredient_id)` 인덱스를 탄다 (RED 28).

### 모듈 경계

`cocktail` 테이블을 `ingredient` 모듈이 직접 조인한다 — **경계 위반이다** (`PRIN-T03`).

**결정** **해법 2안**:
1. `cocktail.api`에 `CocktailFacade.findByIngredient(id)` 를 두고 호출 — 경계 준수, 쿼리 2번
2. `SEARCH` 모듈이 담당 — SPEC-05 §3의 `SEARCH ──reads──▶ COCKTAIL · INGREDIENT` 방향에 부합

**1안**(`CocktailFacade`)을 택한다. SPEC-05 §3이 "조회 전용 서비스가 담당해 순환을 끊는다"고 했으나 여기엔 순환이 없다. GAPS 등재.

**하지 말 것**:
- 재료 승인 — 이슈 026
- 역검색 (내 술장) — Phase 2
- 재료 사전 화면 — FE (Phase 1a 화면 목록에 명시 없음 **결정**)

## DoD

- [ ] RED 30항 전부 통과
- [ ] `FR-INGREDIENT-002`의 **6개 항목 전부** (RED 13)
- [ ] `isSponsored` 항상 존재·억제 불가 (RED 20·21 — `NFR-L-02`)
- [ ] **⚠️ 1a 데이터에 `is_sponsored = true` 가 0건** — 구조는 만들되 켜지 않는다. 켜면 ADR-0004의 주류 광고 접점이 생겨 `NFR-L-05` 선행이 필요하다 (이슈 008 DoD와 쌍)
- [ ] `CocktailFacade` 경유로 모듈 경계 준수 (경계 테스트 통과)
- [ ] 인덱스 사용 (RED 28)
- [ ] 미결은 [`DECISIONS.md`](DECISIONS.md) §1 확정분을 따른다 — **이슈에서 판단하지 않는다**
- [ ] 커밋: `feat(ingredient): 재료 사전 조회 (FR-INGREDIENT-002·005, R-F1.3-1·R-F1.3-3)`
