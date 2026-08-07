---
id: ISSUE-010
title: recipe · recipe_ingredient · recipe_step
domain: COCKTAIL
layer: api
wave: 2
status: TODO
depends_on: [ISSUE-008, ISSUE-009]
fr: [FR-COCKTAIL-003, FR-COCKTAIL-004, FR-COCKTAIL-005]
r: [R-F1.1-1, R-F1.1-7, R-F2.2-5]
inv: [INV-COCKTAIL-07]
nfr: [NFR-D-03]
migration: V010
owns:
  - apps/api/src/main/kotlin/kr/kcocktail/cocktail/recipe/**
  - apps/api/src/main/resources/db/migration/V010__*.sql
---

## 근거

**`PRIN-D03` — Cocktail과 Recipe를 분리한다**

> 하나의 칵테일에 **에디터 표준 1개 + 제휴 바 버전 n개**가 공존해야 한다 (`R-F1.1-7`).
> 이게 파트너 상품의 핵심이고, **나중에 분리하면 마이그레이션 비용이 크다.**

**`PRIN-D01` — 재료는 참조지 문자열이 아니다.** `R-F1.1-1`. 프리텍스트 입력란을 제공하지 않는다.

**SPEC-02 §2.6 Recipe**

| `version_type` | 누가 | 기본 노출 |
|---|---|---|
| `standard` | 에디터 | ✅ |
| `bar_signature` | 제휴 바 | 선택 시 |
| `user` | 유저 | v2 |

> `bar_signature`가 파트너 상품의 가장 강력한 셀링 포인트다 — 홈텐더가 레시피 검색으로 들어와 바를 알게 되는 **역방향 유입**이 여기서 생긴다.

**SPEC-02 §2.2** — `INV-COCKTAIL-07`: 표준 레시피(`version_type = standard`)가 **정확히 1개**

**SPEC-02 §2.7 RecipeIngredient**

| 필드 | 의미 |
|---|---|
| `ingredient_id` | 마스터 참조. **프리텍스트 금지** |
| `amount` · `unit` | `ml` · `dash` · `barspoon` · `piece` · `top_up` |
| `role` | `base` · `modifier` · `sweetener` · `citrus` · `garnish` |
| `is_optional` | 선택 재료 |
| `substitute_ingredient_id` | 대체재 |
| **`counts_for_stock`** | 역검색 판정 대상 여부 |

**SPEC-06 §3.1**

```sql
-- INV-COCKTAIL-07: 표준 레시피는 칵테일당 정확히 1개
CREATE UNIQUE INDEX uq_recipe_standard
  ON recipe (cocktail_id) WHERE version_type = 'standard';
```

`recipe_step`: PK (`recipe_id`, `step_no`), `text` NOT NULL, `technique_ref` — 툴팁 용어 키 (`FR-COCKTAIL-022`, P1)
`recipe_ingredient`: PK (`recipe_id`, `position`), `amount_label` — `1조각`처럼 **배수 계산 제외 표기**

**SPEC-06 §5**: `recipe_ingredient(ingredient_id)` 인덱스 — **역검색 · 재료 사전**

**`FR-COCKTAIL-019`**: 잔 수 환산 시 **고정 표기(`1조각`·`2 dash`)는 배수에서 제외** — `amount_label`이 그 근거

## RED

### 표준 레시피 1개 (`INV-COCKTAIL-07`)

1. `version_type_3종만_허용` — `standard`·`bar_signature`·`user`
2. `standard_레시피가_1개면_통과`
3. `standard_레시피_2개는_DB가_거부한다` — **부분 유니크 인덱스** (SPEC-06 §3.1)
4. `bar_signature는_여러개_가능`
5. `standard가_없는_칵테일은_발행할_수_없다` — 게이트는 이슈 013, 여기서는 조회 함수 제공
6. `bar_signature는_author_bar_id가_필수다` ⚖️ — Phase 1b에 `bar` 테이블이 생긴다. 지금은 컬럼만 두고 FK는 1b
7. `user_타입은_author_user_id가_필수다` — v2

### 재료 참조 (`PRIN-D01`, `R-F1.1-1`)

8. `ingredient_id가_NOT_NULL이다` — 프리텍스트 금지 (`NFR-D-03`)
9. `프리텍스트_재료명_컬럼이_존재하지_않는다` — 스키마 단언. **있으면 반드시 쓰인다**
10. `존재하지_않는_ingredient_id는_FK가_거부한다`
11. `미승인_재료도_draft_레시피에는_넣을_수_있다` ⚖️ (이슈 008 RED 17과 같은 판단)

### 단위·역할 (SPEC-02 §2.7)

12. `unit_5종만_허용` — `ml`·`dash`·`barspoon`·`piece`·`top_up`
13. `role_5종만_허용` — `base`·`modifier`·`sweetener`·`citrus`·`garnish`
14. `amount가_음수면_거부`
15. `amount_label이_있으면_배수_계산에서_제외된다` — 판정 함수. 실제 환산은 FE(이슈 043)
16. `top_up_단위는_amount가_없어도_된다` — "채운다"

### counts_for_stock (`R-F2.2-5`, `PRIN-D01`)

17. `counts_for_stock_기본값이_재료_카테고리에서_온다` — 이슈 008의 `IngredientFacade.defaultCountsForStock`
18. `garnish_재료는_기본_false다`
19. `기본값을_명시적으로_덮어쓸_수_있다` — 레시피마다 다를 수 있다
20. `counts_for_stock은_NOT_NULL이다`

### 대체재

21. `substitute_ingredient_id가_자기_자신이면_거부`
22. `substitute_note를_함께_저장할_수_있다`
23. `대체재가_없어도_저장된다` — 필수는 미유통 재료일 때만 (`GATE-COCKTAIL-06`, 이슈 013)

### 스텝

24. `step_no가_1부터_연속이다` — 누락·중복 거부
25. `text가_비면_거부`
26. `technique_ref는_선택이다` (`FR-COCKTAIL-022`는 P1)

### 순서·인덱스

27. `position이_recipe_내에서_유일하다`
28. `recipe_ingredient에_ingredient_id_인덱스가_있다` (SPEC-06 §5 — 역검색 경로)

### 애그리게이트 경계 (SPEC-02 §1)

29. `Recipe를_Cocktail_없이_생성할_수_없다` — "애그리게이트 경계 = 트랜잭션 경계"
30. `Cocktail_삭제시_Recipe가_CASCADE된다` — 단 `cocktail`은 `REVOKE DELETE`라 실제로는 안 일어남

## GREEN

### `V010__recipe.sql`

```sql
CREATE TABLE recipe (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  cocktail_id BIGINT NOT NULL REFERENCES cocktail ON DELETE CASCADE,
  version_type VARCHAR(16) NOT NULL CHECK (version_type IN ('standard','bar_signature','user')),
  author_bar_id BIGINT,          -- Phase 1b 에 bar FK 추가
  author_user_id BIGINT REFERENCES "user",
  serving_count SMALLINT NOT NULL DEFAULT 1,
  note TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_recipe_author CHECK (
    (version_type = 'standard'      AND author_bar_id IS NULL AND author_user_id IS NULL) OR
    (version_type = 'bar_signature' AND author_bar_id IS NOT NULL) OR
    (version_type = 'user'          AND author_user_id IS NOT NULL)
  )
);
-- INV-COCKTAIL-07
CREATE UNIQUE INDEX uq_recipe_standard ON recipe (cocktail_id) WHERE version_type = 'standard';

CREATE TABLE recipe_step (
  recipe_id BIGINT NOT NULL REFERENCES recipe ON DELETE CASCADE,
  step_no SMALLINT NOT NULL,
  text TEXT NOT NULL CHECK (length(trim(text)) > 0),
  technique_ref VARCHAR(40),
  PRIMARY KEY (recipe_id, step_no)
);

CREATE TABLE recipe_ingredient (
  recipe_id BIGINT NOT NULL REFERENCES recipe ON DELETE CASCADE,
  ingredient_id BIGINT NOT NULL REFERENCES ingredient,     -- PRIN-D01
  position SMALLINT NOT NULL,
  amount NUMERIC(6,2) CHECK (amount IS NULL OR amount >= 0),
  unit VARCHAR(12) CHECK (unit IN ('ml','dash','barspoon','piece','top_up')),
  amount_label VARCHAR(40),
  role VARCHAR(16) CHECK (role IN ('base','modifier','sweetener','citrus','garnish')),
  is_optional BOOLEAN NOT NULL DEFAULT false,
  substitute_ingredient_id BIGINT REFERENCES ingredient,
  substitute_note TEXT,
  counts_for_stock BOOLEAN NOT NULL DEFAULT true,          -- R-F2.2-5
  PRIMARY KEY (recipe_id, position),
  CONSTRAINT ck_no_self_substitute CHECK (substitute_ingredient_id <> ingredient_id)
);
CREATE INDEX ON recipe_ingredient (ingredient_id);          -- SPEC-06 §5 역검색
```

`ck_recipe_author` 는 SPEC-06에 명시돼 있지 않지만 SPEC-06 §3.1이 "`bar_signature`일 때만"·"`user`일 때만"이라고 적었다. **DB로 표현 가능하므로 건다** (SPEC-06 §4 서두).

### `cocktail/recipe`

```kotlin
// FR-COCKTAIL-019 — 배수 계산 제외 판정
fun RecipeIngredient.isScalable(): Boolean = amountLabel == null && amount != null
```

**환산 자체는 FE(이슈 043)가 한다.** 여기서는 "무엇이 배수 대상인가"의 판정만 서버가 제공한다 — 그래야 FE와 어드민 미리보기가 같은 규칙을 쓴다.

### `counts_for_stock` 기본값 주입 (RED 17·18)

레시피 재료 생성 시 값이 주어지지 않으면 `IngredientFacade.defaultCountsForStock(ingredientId)` 를 호출한다 (이슈 008). **`ingredient` 테이블을 직접 조회하지 않는다** — 모듈 경계 (`PRIN-T03`).

**하지 말 것**:
- 도수 계산 — 이슈 011
- 발행 게이트 — 이슈 013
- 잔 수 환산 UI — 이슈 043

## DoD

- [ ] RED 30항 전부 통과
- [ ] `INV-COCKTAIL-07`이 **부분 유니크 인덱스**로 DB 강제 (RED 3)
- [ ] 프리텍스트 재료 컬럼 **부재** (RED 9 — `PRIN-D01`)
- [ ] `counts_for_stock` 기본값이 `IngredientFacade` 경유 (모듈 경계)
- [ ] `recipe_ingredient(ingredient_id)` 인덱스 (RED 28)
- [ ] ⚖️ 2건(`bar_signature` FK 시점·미승인 재료) `GAPS.md` 등재
- [ ] 커밋: `feat(cocktail): 레시피·재료·스텝 (FR-COCKTAIL-003·004·005, PRIN-D01·D03, INV-COCKTAIL-07)`
