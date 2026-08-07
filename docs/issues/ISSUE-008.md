---
id: ISSUE-008
title: ingredient 마스터 + 불변식
domain: INGREDIENT
layer: api
wave: 2
status: TODO
depends_on: [ISSUE-002]
fr: [FR-INGREDIENT-001, FR-INGREDIENT-003, FR-INGREDIENT-004, FR-INGREDIENT-006]
r: [R-F1.3-2, R-F1.3-3, R-F2.2-5]
inv: [INV-INGREDIENT-01, INV-INGREDIENT-02]
nfr: [NFR-D-03]
migration: V008
owns:
  - apps/api/src/main/kotlin/kr/kcocktail/ingredient/domain/**
  - apps/api/src/main/kotlin/kr/kcocktail/ingredient/repository/**
  - apps/api/src/main/kotlin/kr/kcocktail/ingredient/api/**
  - apps/api/src/main/kotlin/kr/kcocktail/ingredient/internal/**
  - apps/api/src/main/resources/db/migration/V008__*.sql
---

> **소유 경로 주의**: `ingredient/web/**` 은 ISSUE-023(재료 사전 조회) 소유다.

## 근거

**`PRIN-D01` — 재료는 참조지 문자열이 아니다**

> `R-F1.1-1`. 재료를 문자열로 저장하면 **역검색(내 술장)과 바 연결이 전부 불가능해진다.**
> 나중에 정규화하면 마이그레이션 비용이 크다. 처음부터 `ingredient_id` 참조로 간다.
>
> `counts_for_stock` 플래그로 가니시·얼음·물을 역검색 판정에서 뺀다 (`R-F2.2-5`).
> **이게 없으면 민트 잎 하나 없다고 모히토가 안 나온다.**

**`PRIN-P05` — 국내 기준으로 정규화한다**

> 이 서비스가 해외 DB의 번역판이 아닌 이유는 `domestic_availability` 하나다.

| FR | 요구 |
|---|---|
| `FR-INGREDIENT-001` | 재료 마스터를 관리한다. **국내 유통 기준 200~300개로 상한**을 두고, 신규 추가는 **에디터 승인제** |
| `FR-INGREDIENT-003` | `domestic_availability`가 `import_only`·`unavailable`이면 **대체재 또는 자가제조 안내를 필수 입력** |
| `FR-INGREDIENT-004` | 특정 브랜드 언급 시 **광고성 여부를 시스템상 구분해 표기** |
| `FR-INGREDIENT-006` | 카테고리 7종 지정. **`garnish`는 `counts_for_stock` 기본값이 `false`** |

**SPEC-02 §3 불변식**

| ID | 불변식 |
|---|---|
| `INV-INGREDIENT-01` | `import_only` · `unavailable`이면 대체재 또는 자가제조 안내 필수 (`R-F1.3-2`) |
| `INV-INGREDIENT-02` | 특정 브랜드 언급 시 광고성 여부를 구분해 표기 (`R-F1.3-3`) |

**SPEC-06 §3.2**

```
ingredient
  slug name_ko name_en
  aliases                TEXT[]
  category               VARCHAR(16) CHECK — spirit·liqueur·bitters·syrup·juice·garnish·mixer
  abv                    NUMERIC(4,1)   도수 자동 계산 입력
  domestic_availability  VARCHAR(16) NOT NULL CHECK — common·specialty·import_only·unavailable
  substitute_note        TEXT   import_only·unavailable 이면 필수 (INV-INGREDIENT-01)
  price_band             VARCHAR(12)
  is_approved            BOOLEAN   에디터 승인제 (FR-ADMIN-007)

ingredient_brand           -- 브랜드 언급의 광고성 구분 (INV-INGREDIENT-02)
  ingredient_id  name  purchase_url
  is_sponsored   BOOLEAN NOT NULL DEFAULT false   -- 켜지면 라벨 강제
```

**SPEC-06 §4.3** — `INV-INGREDIENT-01`·`02`는 **앱이 강제**한다 (조건부라 DB 제약으로 표현 불가). 단 **배치 검증으로 이중 확인**한다 (이슈 016).

**SPEC-06 §5 인덱스**: `recipe_ingredient(ingredient_id)` — **역검색 · 재료 사전**

## RED

### 카테고리·기본값 (`FR-INGREDIENT-006`, `R-F2.2-5`)

1. `카테고리_7종만_허용` — `spirit`·`liqueur`·`bitters`·`syrup`·`juice`·`garnish`·`mixer` 파라미터라이즈드 + 임의 값 거부
2. `garnish_카테고리는_counts_for_stock_기본값이_false다` — **`PRIN-D01`의 요체**
3. `그_외_카테고리는_counts_for_stock_기본값이_true다`
4. `기본값은_레시피_재료_생성_시점에_적용된다` — `recipe_ingredient` 는 이슈 010. 여기서는 **기본값 결정 함수**를 제공하고 010이 소비

### 국내 유통 (`INV-INGREDIENT-01`, `PRIN-P05`)

5. `domestic_availability_4종만_허용` — `common`·`specialty`·`import_only`·`unavailable`
6. `domestic_availability는_NOT_NULL이다`
7. `import_only면_substitute_note가_필수다` → 위반 시 422 + `code=INV-INGREDIENT-01`
8. `unavailable이면_substitute_note가_필수다`
9. `common_specialty는_substitute_note가_없어도_된다`
10. `substitute_note가_공백문자열이면_없는_것으로_취급된다`

### 브랜드 광고성 (`INV-INGREDIENT-02`, `R-F1.3-3`)

11. `브랜드에_is_sponsored_플래그가_있다`
12. `is_sponsored_기본값은_false다`
13. `브랜드가_있으면_광고성_여부가_반드시_결정돼_있다` — NULL 불가
14. `is_sponsored_true면_응답에_라벨_표기_플래그가_내려간다` — 렌더링 강제의 근거 (표현은 FE)

### 승인제 (`FR-INGREDIENT-001`, `FR-ADMIN-007`)

15. `신규_재료의_is_approved_기본값은_false다`
16. `미승인_재료는_공개_API에_노출되지_않는다`
17. `미승인_재료를_레시피에_쓸_수_없다` ⚖️ — 또는 draft 레시피에는 허용. **보수적으로 발행 게이트에서 차단**(이슈 013)하고 draft는 허용 + GAPS
18. `승인_액션은_admin만` (SPEC-08 §2 "재료 마스터 승인" 행) — 실제 엔드포인트는 이슈 026

### 상한 (`FR-INGREDIENT-001`)

19. `승인된_재료가_300개를_넘으면_경고한다` ⚖️ — "200~300개로 상한"이 하드 차단인지 가이드인지 불명. **보수적으로 경고 + 배치 리포트**, 차단하지 않음 + GAPS 등재

### 별칭 (`FR-INGREDIENT-005` — 이슈 023이 검색 반영)

20. `aliases가_TEXT_배열이다` (SPEC-06 §1.4 예외)
21. `aliases에_GIN_인덱스가_있다`

### 규약

22. `slug가_유일하다`
23. `공개_응답에_내부_id가_없다`
24. `삭제_대신_상태_전이거나_참조가_있으면_거부된다` ⚖️ — `ingredient`는 SPEC-06 §4.1의 `REVOKE DELETE` 목록에 **없다**. 레시피가 참조 중이면 FK가 막는다. 보수적으로 **참조 있으면 거부**

### 도메인 이벤트 (SPEC-05 §3 — 이슈 017이 구독)

> **부수효과는 도메인 이벤트로 발행하고 리스너가 처리한다.** `ingredient` 가 `search` 를 호출하면 **순환이 생긴다** — `008 → 017 → 014 → 013 → 010 → 008`.

25. `재료_저장시_IngredientSaved_이벤트가_발행된다`
26. `이벤트에_entityId와_slug와_name과_aliases가_담긴다` — 색인에 필요한 것
27. `이_모듈이_search를_참조하지_않는다` — 경계 테스트 (이슈 001)
28. `이벤트_발행이_저장_트랜잭션과_같이_커밋된다`

## GREEN

### `V008__ingredient.sql`

```sql
CREATE TABLE ingredient (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  slug VARCHAR(120) NOT NULL UNIQUE,
  name_ko VARCHAR(120) NOT NULL,
  name_en VARCHAR(120) NOT NULL,
  aliases TEXT[] NOT NULL DEFAULT '{}',
  category VARCHAR(16) NOT NULL CHECK (category IN
    ('spirit','liqueur','bitters','syrup','juice','garnish','mixer')),
  abv NUMERIC(4,1),
  description TEXT,
  domestic_availability VARCHAR(16) NOT NULL CHECK (domestic_availability IN
    ('common','specialty','import_only','unavailable')),
  substitute_note TEXT,
  price_band VARCHAR(12),
  is_approved BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON ingredient USING GIN (aliases);
CREATE INDEX ON ingredient (category, is_approved);

CREATE TABLE ingredient_brand (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  ingredient_id BIGINT NOT NULL REFERENCES ingredient ON DELETE CASCADE,
  name VARCHAR(80) NOT NULL,
  purchase_url TEXT,
  is_sponsored BOOLEAN NOT NULL DEFAULT false,   -- INV-INGREDIENT-02
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

`INV-INGREDIENT-01`은 조건부라 **CHECK로 못 쓴다**... 실은 쓸 수 있다:

```sql
CONSTRAINT ck_substitute CHECK (
  domestic_availability NOT IN ('import_only','unavailable')
  OR (substitute_note IS NOT NULL AND length(trim(substitute_note)) > 0)
)
```

SPEC-06 §4.3이 이것을 "앱 강제"로 분류했지만 **DB로도 표현 가능하다.** SPEC-06 §4 서두가 "DB로 강제할 수 있는 것은 DB에서 한다"고 했으므로 **양쪽 다** 건다. 문서와 다르니 `GAPS.md`에 근거를 남긴다 (더 강한 방향의 이탈이므로 ADR까지는 불필요 ⚖️).

### `ingredient/domain`

```kotlin
enum class IngredientCategory(val slug: String, val defaultCountsForStock: Boolean) {
    SPIRIT("spirit", true), LIQUEUR("liqueur", true), BITTERS("bitters", true),
    SYRUP("syrup", true), JUICE("juice", true),
    GARNISH("garnish", false),          // R-F2.2-5 — 민트 잎 하나로 모히토가 막히면 안 된다
    MIXER("mixer", true);
}
```

RED 2·4가 이 기본값을 검증한다. 이슈 010의 `recipe_ingredient` 가 이 함수를 호출한다.

### `ingredient/api`

```kotlin
interface IngredientFacade {                    // PRIN-T03 — 타 모듈은 이것만
    fun findApproved(ids: Collection<Long>): List<IngredientView>
    fun defaultCountsForStock(ingredientId: Long): Boolean
    fun requiresSubstitute(ingredientId: Long): Boolean   // 이슈 013 게이트가 소비
}
```

`GATE-COCKTAIL-06`(미유통 재료 대체재 명시)이 `requiresSubstitute` 를 쓴다.

**하지 말 것**:
- 재료 사전 조회 API — 이슈 023
- 승인 엔드포인트 — 이슈 026
- 역검색 — Phase 2

## DoD

- [ ] RED 28항 전부 통과
- [ ] `garnish` 기본값 `false` (RED 2 — `R-F2.2-5`)
- [ ] `INV-INGREDIENT-01` 이 DB CHECK + 앱 양쪽 (SPEC-06 §4 서두 근거, GAPS에 문서 차이 기록)
- [ ] `IngredientFacade` 가 `ingredient.api` 로 공개, 엔티티 미노출 (경계 테스트)
- [ ] ⚖️ 3건(미승인 재료 사용·300개 상한·삭제 정책) `GAPS.md` 등재
- [ ] 커밋: `feat(ingredient): 재료 마스터·국내유통 불변식 (FR-INGREDIENT-001·003·004·006, PRIN-D01·P05)`
