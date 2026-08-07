---
id: ISSUE-009
title: cocktail + 3축 불변식
domain: COCKTAIL
layer: api
wave: 2
status: TODO
depends_on: [ISSUE-002]
fr: [FR-COCKTAIL-001, FR-COCKTAIL-002, FR-COCKTAIL-008]
r: [R-C-1, R-C-3, R-F1.2-1]
inv: [INV-COCKTAIL-01, INV-COCKTAIL-02, INV-COCKTAIL-03, INV-COCKTAIL-04, INV-COCKTAIL-06]
nfr: [NFR-D-01]
migration: V009
owns:
  - apps/api/src/main/kotlin/kr/kcocktail/cocktail/domain/**
  - apps/api/src/main/kotlin/kr/kcocktail/cocktail/repository/**
  - apps/api/src/main/resources/db/migration/V009__*.sql
---

## 근거

**`PRIN-P06` — 카테고리와 필터는 다른 것이다**

| | 카테고리 | 필터 |
|---|---|---|
| 무엇 | **기주 · 스타일 · 메이킹 방법 3축** | 당도 · 도수 · 향과 맛 · 보유 재료 |
| 형태 | 경로 `/cocktails/base/gin/` | 쿼리스트링 |
| 색인 | 한다 | **하지 않는다** |
| 필수성 | **모든 칵테일이 반드시 하나씩** (`R-C-1`) | 없어도 됨 |

**SPEC-02 §2.2 불변식**

| ID | 불변식 | 근거 |
|---|---|---|
| `INV-COCKTAIL-01` | **분류 3축은 전부 NOT NULL** | `R-C-1` |
| `INV-COCKTAIL-02` | `styles`는 **최소 1개** | `R-C-1` |
| `INV-COCKTAIL-03` | **`style_primary ∈ styles`** | `R-C-3` |
| `INV-COCKTAIL-04` | `aroma_tags` **1~3개** | `R-F1.2-1` |
| `INV-COCKTAIL-06` | `base_spirit = non-alcoholic` ⟺ `abv = 0` | 정합성 |

**SPEC-06 §1.4 — 배열은 조인 테이블로**

> PRD가 `styles[]`·`aroma_tags[]`로 적었지만 물리 설계는 **조인 테이블**이다.
> - 카테고리 페이지 조회가 단순 조인이 된다
> - **패싯 카운트가 `GROUP BY` 한 방으로 끝난다.** 배열이면 `unnest`를 거쳐야 한다
> - **`style_primary ∈ styles` 불변식을 복합 FK로 DB가 강제**할 수 있다

**SPEC-06 §4.2 — `style_primary ∈ styles` 복합 FK** (조인 테이블을 택한 가장 큰 이유)

```sql
ALTER TABLE cocktail_style
  ADD CONSTRAINT uq_cocktail_style UNIQUE (cocktail_id, style);

ALTER TABLE cocktail
  ADD CONSTRAINT fk_style_primary
  FOREIGN KEY (id, style_primary)
  REFERENCES cocktail_style (cocktail_id, style)
  DEFERRABLE INITIALLY DEFERRED;
```

**`DEFERRABLE`이 필요한 이유는 칵테일과 스타일 행이 같은 트랜잭션에서 삽입되기 때문이다.**

**SPEC-06 §4.1 DB 강제** — `INV-COCKTAIL-01`(NOT NULL) · `05`(UNIQUE) · `06`(CHECK) · `07`(부분 유니크)
**SPEC-06 §4.3 앱 강제** — `INV-COCKTAIL-02`(자식 행 개수) · `04`(같음)

**SPEC-06 §4.1**: `cocktail` 은 **`REVOKE DELETE`** 대상 (`PRIN-D05` 삭제는 상태 전이)

### 프로토타입과의 차이 (전환 시 주의)

`packages/domain/src/types.ts`는 `BaseSpirit`을 **한국어 리터럴**로 두고 `BASE_SLUGS`로 슬러그를 맵핑한다.
**Kotlin이 정본이 되면 값이 슬러그로 통일된다** (이슈 004 RED 12·15). 이 이슈의 enum이 그 정본이다.

## RED

### 3축 NOT NULL (`INV-COCKTAIL-01`, `R-C-1`)

1. `base_spirit이_없으면_저장_거부` — DB NOT NULL
2. `style_primary가_없으면_저장_거부`
3. `method가_없으면_저장_거부`
4. `3축이_전부_있으면_저장된다`
5. `base_spirit_10종만_허용` — ADR-0002 확정 슬러그 전수 (`gin`·`vodka`·`whisky`·`rum`·`agave`·`brandy`·`liqueur`·`wine`·**`korean`**·`non-alcoholic`)
6. `method_5종만_허용` — `Build`·`Shake`·`Stir`·`Blend`·`Etc`
7. `style_9종만_허용` — `highball`·`sour`·`spirit-forward`·`spritz`·`tiki`·`creamy`·`hot`·`frozen`·`shot`

### `style_primary ∈ styles` (`INV-COCKTAIL-03`, `R-C-3`) — 이 이슈의 요체

8. `style_primary가_styles에_있으면_저장된다`
9. `style_primary가_styles에_없으면_DB가_거부한다` — **복합 FK** (SPEC-06 §4.2). 앱 검증이 아니라 DB
10. `같은_트랜잭션에서_cocktail과_style을_삽입할_수_있다` — `DEFERRABLE INITIALLY DEFERRED`
11. `styles에서_style_primary를_제거하면_거부된다` — FK가 막는다
12. `cocktail_style에_uq_cocktail_style_유니크가_있다`

### styles 최소 1개 (`INV-COCKTAIL-02`)

13. `styles가_비면_거부된다` — 앱 강제 (SPEC-06 §4.3)
14. `styles가_1개면_통과`
15. `styles_복수_허용`
16. `styles_중복은_거부된다` — 유니크 제약

### 향·맛 태그 1~3개 (`INV-COCKTAIL-04`, `R-F1.2-1`)

17. `aroma_tags가_0개면_거부`
18. `aroma_tags가_1개면_통과`
19. `aroma_tags가_3개면_통과`
20. `aroma_tags가_4개면_거부` — `FR-COCKTAIL-008` "4개째는 UI가 막는다" + 서버도 막는다 (`PRIN-T05`)
21. `flavor_10종만_허용` — `citrus`·`sour`·`fruity`·`floral`·`herbal`·`spicy`·`smoky`·`bitter`·`nutty`·`creamy`
22. `aroma_tags_중복은_거부된다`

### 무알콜 정합 (`INV-COCKTAIL-06`)

23. `base_spirit이_non_alcoholic이면_abv가_0이어야_한다` — DB CHECK
24. `abv가_0이면_base_spirit이_non_alcoholic이어야_한다` — **양방향(⟺)**
25. `non_alcoholic인데_abv_15면_거부`
26. `gin인데_abv_0이면_거부`

### 상태·슬러그

27. `status_3종만_허용` — `draft`·`published`·`archived`
28. `생성시_status는_draft다`
29. `slug가_유일하다`
30. `cocktail_테이블에_DELETE_권한이_없다` (SPEC-06 §4.1, `PRIN-D05`)

### 규약

31. `공개_응답에_내부_id가_없다`
32. `공개_응답에_abv_calculated와_abv_override_구분이_없다` (SPEC-07 §5) — 표시값 `abv` 하나

## GREEN

### `V009__cocktail.sql`

```sql
CREATE TABLE cocktail (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  slug VARCHAR(120) NOT NULL UNIQUE,
  name_ko VARCHAR(120) NOT NULL,
  name_en VARCHAR(120) NOT NULL,
  aliases TEXT[] NOT NULL DEFAULT '{}',
  summary TEXT NOT NULL,
  base_spirit VARCHAR(24) NOT NULL CHECK (base_spirit IN (...10종...)),
  style_primary VARCHAR(24) NOT NULL,
  method VARCHAR(12) NOT NULL CHECK (method IN ('Build','Shake','Stir','Blend','Etc')),
  sweetness VARCHAR(12) NOT NULL CHECK (sweetness IN ('dry','semi_dry','semi_sweet','sweet')),
  abv_calculated NUMERIC(4,1),
  abv_override NUMERIC(4,1),
  abv NUMERIC(4,1) GENERATED ALWAYS AS (COALESCE(abv_override, abv_calculated)) STORED,
  glass_type VARCHAR(40) NOT NULL,
  prep_time_min SMALLINT,
  tasting_note TEXT,                         -- 발행 시 필수 (GATE-COCKTAIL-01)
  story TEXT,
  is_classic BOOLEAN NOT NULL DEFAULT false,
  origin_year VARCHAR(80), origin_place VARCHAR(80), origin_creator VARCHAR(80),
  status VARCHAR(12) NOT NULL DEFAULT 'draft' CHECK (status IN ('draft','published','archived')),
  published_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_cocktail_na CHECK ((base_spirit = 'non-alcoholic') = (abv = 0))
);

CREATE TABLE cocktail_style (
  cocktail_id BIGINT NOT NULL REFERENCES cocktail ON DELETE CASCADE,
  style VARCHAR(24) NOT NULL CHECK (style IN (...9종...)),
  PRIMARY KEY (cocktail_id, style),
  CONSTRAINT uq_cocktail_style UNIQUE (cocktail_id, style)   -- SPEC-06 §4.2
);
CREATE TABLE cocktail_aroma_tag (
  cocktail_id BIGINT NOT NULL REFERENCES cocktail ON DELETE CASCADE,
  aroma_tag VARCHAR(24) NOT NULL CHECK (aroma_tag IN (...10종...)),
  PRIMARY KEY (cocktail_id, aroma_tag)
);

ALTER TABLE cocktail ADD CONSTRAINT fk_style_primary
  FOREIGN KEY (id, style_primary) REFERENCES cocktail_style (cocktail_id, style)
  DEFERRABLE INITIALLY DEFERRED;               -- INV-COCKTAIL-03

REVOKE DELETE ON cocktail FROM kcocktail_app; -- SPEC-06 §4.1, PRIN-D05
```

**`ck_cocktail_na`의 함정**: `abv`가 생성 컬럼이라 `abv_calculated`·`abv_override`가 둘 다 NULL이면 `abv`도 NULL이고 CHECK가 `NULL = false` → NULL(통과)이 된다. **draft 단계에서는 도수가 없을 수 있으므로 의도된 동작**이지만, 발행 게이트에서 다시 확인해야 한다 → 이슈 013에 전달.

### `cocktail/domain` — enum이 정본 (`PRIN-T02`)

```kotlin
enum class BaseSpirit(val slug: String, val labelKo: String) {
    GIN("gin", "진"), VODKA("vodka", "보드카"), WHISKY("whisky", "위스키"),
    RUM("rum", "럼"), AGAVE("agave", "데킬라 · 메즈칼"), BRANDY("brandy", "브랜디"),
    LIQUEUR("liqueur", "리큐르"), WINE("wine", "와인 · 스파클링"),
    KOREAN("korean", "전통주"),            // ADR-0002: soju → korean
    NON_ALCOHOLIC("non-alcoholic", "무알콜");
}
enum class StyleKey(val slug: String) { HIGHBALL("highball"), SOUR("sour"), ... }
enum class FlavorKey(val slug: String) { CITRUS("citrus"), ... }
enum class Technique { Build, Shake, Stir, Blend, Etc }
```

### 앱 강제 불변식 — 순수 함수로

```kotlin
object CocktailInvariants {               // SPEC-06 §4.3 앱 강제분
    fun check(c: CocktailDraft): List<Violation>   // INV-COCKTAIL-02, 04
}
```

**이슈 013의 발행 게이트와 016의 배치 검증이 이것을 재사용한다.** 두 벌 구현 금지 (INDEX 결합점).

**하지 말 것**:
- 레시피 — 이슈 010
- 도수 계산 — 이슈 011 (`abv_calculated` 컬럼만)
- 당도·별칭 세부 — 이슈 012 (컬럼만)
- 발행 게이트 — 이슈 013
- 조회 API — 이슈 018·020

## DoD

- [ ] RED 32항 전부 통과
- [ ] **`INV-COCKTAIL-03`이 복합 FK로 DB 강제** (RED 9~11 — SPEC-06 §4.2)
- [ ] `DEFERRABLE`로 같은 트랜잭션 삽입 가능 (RED 10)
- [ ] enum 4종이 ADR-0002 슬러그와 일치, 이슈 004의 생성 파이프라인에 반영
- [ ] `REVOKE DELETE` 적용 (RED 30)
- [ ] `CocktailInvariants` 가 순수 함수 (013·016이 재사용)
- [ ] 커밋: `feat(cocktail): 칵테일 엔티티·3축 불변식 (FR-COCKTAIL-001·002·008, INV-COCKTAIL-01~04·06)`
