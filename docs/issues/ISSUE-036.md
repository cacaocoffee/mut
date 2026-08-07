---
id: ISSUE-036
title: 시드 이관 — data.ts 24종 → Postgres
domain: —
layer: infra
wave: 8
status: TODO
depends_on: [ISSUE-013]
fr: []
r: []
inv: [INV-COCKTAIL-01, INV-COCKTAIL-03, INV-COCKTAIL-04, INV-COCKTAIL-07]
nfr: [NFR-D-01]
migration: R__seed
owns:
  - apps/api/src/main/resources/db/migration/R__seed_cocktail.sql
  - scripts/seed-from-prototype.*
---

## 근거

**SPEC-01 §6 현재 구현 상태**

> `apps/web`에 **프로토타입**이 있다. 칵테일 **24종**이 TypeScript 정적 배열에 있고 탐색·상세·파인더 3개 화면이 동작한다.

**API 연동 시점에 정리되는 것:**

| 지금 | 이후 |
|---|---|
| `packages/domain/src/data.ts` **24종** | **Postgres 시드 데이터로 이관** |

**SPEC-06 §6 마이그레이션**

> 시드 데이터는 별도 **`R__seed_*.sql`(repeatable)** 로 둔다.
> **현재 `packages/domain/src/data.ts`의 24종이 최초 시드가 된다** (SPEC-01 §6).

**`NFR-D-01`**: 발행분에 불변식 위반 **0건** — **`npm run check` 상당의 서버 배치**. 배포 차단

### 이관이 단순 복사가 아닌 이유

프로토타입 타입과 SPEC-06 스키마가 **여러 곳에서 다르다.** 이관은 그 변환이다.

| 프로토타입 (`types.ts`) | SPEC-06 스키마 | 변환 |
|---|---|---|
| `base: BaseSpirit` — **한국어**(`"진"`) | `base_spirit VARCHAR` — **슬러그**(`gin`) | `BASE_SLUGS` 맵 적용 |
| `sweet: SweetLevel` — **숫자** `0\|1\|2\|3` | `sweetness VARCHAR` — `dry`·`semi_dry`… | 숫자 → 문자열 |
| `id: string` (`"negroni"`) | `slug VARCHAR` + `id BIGINT` | `id` → `slug`, DB가 새 `id` 부여 |
| `ingredients: Ingredient[]` — **인라인 객체** (`ko`·`en`·`ml`·`sub`) | `recipe_ingredient.ingredient_id` **FK** | **재료 마스터를 먼저 만들어야 한다** |
| `styles[]` + `stylePrimary` | `cocktail_style` 조인 테이블 + 복합 FK | 행 분해 |
| `flavors[]` | `cocktail_aroma_tag` 조인 | 행 분해 |
| `steps: string[]` | `recipe_step(step_no, text)` | 인덱스 부여 |
| `story: {title, paragraphs}` | `story TEXT` | 직렬화 **결정** |
| `profile: [5개 숫자]` | **스키마에 없다** | ⚠️ **누락** — `FR-COCKTAIL-023`(P1 레이더)의 데이터 |
| `origin: {year, place, creator}` | `origin_year`·`origin_place`·`origin_creator` | 분해 |
| `summary` | `summary TEXT NOT NULL` | 그대로 |
| — | **`tasting_note`** (발행 필수) | ⚠️ **프로토타입에 없다** |

**`validate.ts` 주석**: "`R-F1.1-2` — 향과 맛 서술은 발행 필수. **현재는 `summary`가 그 자리를 대신한다.**"

→ **`tasting_note`가 비면 `GATE-COCKTAIL-01`로 발행이 막힌다.** 24종을 `published`로 넣으려면 서술이 있어야 한다.

## RED

### 재료 마스터 선행 (`PRIN-D01`)

1. `프로토타입_재료명에서_마스터가_생성된다` — 24종의 인라인 재료 → `ingredient` 행
2. `동일_재료가_중복_생성되지_않는다` — `ko` 기준 정규화
3. `재료에_slug가_부여된다`
4. `재료_카테고리가_지정된다` — 7종 중. **자동 추론 불가한 것은 수동 매핑표** **결정**
5. `garnish_재료의_counts_for_stock이_false다` (이슈 008 RED 2)
6. `domestic_availability가_지정된다` — NOT NULL. **기본값 `common`** **결정**
7. `재료가_is_approved_true로_시드된다` — 시드는 승인된 상태 **결정**

### 값 변환

8. `기주_한국어가_슬러그로_변환된다` — `"진"` → `gin`
9. `BASE_SLUGS_맵과_일치한다` — 10종 전수
10. `전통주가_korean이다` — ADR-0002 (`soju` 아님)
11. `당도_숫자가_문자열로_변환된다` — `0`→`dry`, `1`→`semi_dry`, `2`→`semi_sweet`, `3`→`sweet`
12. `프로토타입_id가_slug가_된다`
13. `origin이_3개_컬럼으로_분해된다`

### 조인 테이블 분해

14. `styles가_cocktail_style_행으로_분해된다`
15. `stylePrimary가_styles에_포함된다` — 복합 FK 통과 (`INV-COCKTAIL-03`)
16. `flavors가_cocktail_aroma_tag_행으로_분해된다`
17. `flavors가_1개_이상_3개_이하다` (`INV-COCKTAIL-04`)

### 레시피

18. `칵테일마다_standard_레시피가_정확히_1개다` (`INV-COCKTAIL-07`)
19. `steps가_step_no_1부터_부여된다`
20. `재료가_ingredient_id_FK로_연결된다` — 프리텍스트 없음 (`PRIN-D01`)
21. `ml이_amount와_unit_ml로_들어간다`
22. `amount_문자열이_amount_label로_들어간다` — `"1조각"` (배수 제외 표기)
23. `sub가_substitute_note로_들어간다`

### 발행 게이트 (`NFR-D-01`, 이슈 013)

24. **`24종_전부가_발행_게이트를_통과한다`** — `PublishGate.check()` 위반 0건
25. `tasting_note가_채워져_있다` (`GATE-COCKTAIL-01`)
26. `is_classic인_항목에_story가_있다` (`GATE-COCKTAIL-05`)
27. `미유통_재료에_대체재가_있다` (`GATE-COCKTAIL-06`)
28. `표준_레시피에_재료와_스텝이_1개_이상이다` (`GATE-COCKTAIL-03`)

### 도수 (`INV-COCKTAIL-06`, 이슈 011)

29. `abv가_이관된다` — 프로토타입은 실측값이라 `abv_override`로 **결정**
30. `무알콜은_abv가_0이다`
31. `abv_0인데_기주가_무알콜이_아닌_항목이_없다`

### 멱등 (`R__` repeatable)

32. `시드를_두_번_적용해도_중복되지_않는다` — repeatable 마이그레이션의 성질
33. `기존_데이터를_덮어쓰지_않는다` **결정** — 운영 데이터 보호. **없을 때만 삽입**

### 손실 항목

34. `profile_5축이_어디로_가는가` ⚖️ — **SPEC-06에 컬럼이 없다.** `FR-COCKTAIL-023`(P1 레이더)의 데이터인데 스키마 누락. **GAPS 등재 필수** — Phase 1a는 P1이라 버려도 되지만 **데이터를 잃으면 다시 못 만든다**
35. `story_구조가_보존되는가` **결정** — `{title, paragraphs[2]}` → `TEXT`. 마크다운 직렬화

## GREEN

### 순서

```
1. 재료 마스터 추출·정규화 (수동 매핑표 필요)
2. tasting_note 작성 (24종 — 에디터 작업)
3. R__seed_ingredient.sql
4. R__seed_cocktail.sql
5. PublishGate 검증 → 전부 통과해야 published
```

**2번이 사람의 일이다.** `PRIN-P03`("만들어보지 않은 것은 쓰지 않는다")이 `tasting_note`를 발행 필수로 만든 이유가 이것이다. 자동 생성하면 원칙 위반이다.

⚖️ **24종을 `draft`로 넣고 `tasting_note` 작성 후 발행**하는 것이 정직하다. **그렇게 한다**.

### 변환 스크립트

```
scripts/seed-from-prototype.ts   # data.ts → SQL 생성
```

**일회성이지만 커밋한다** — 변환 규칙이 곧 이관 근거이고, 재실행이 필요할 수 있다.

### `R__seed_*.sql` (SPEC-06 §6)

Flyway repeatable. 체크섬이 바뀌면 재적용된다 — 시드 수정 시 자동 반영.

**결정** **운영에서 위험하다** (RED 33): 시드가 운영 데이터를 덮어쓰면 안 된다.
**보수적으로**: `INSERT ... ON CONFLICT (slug) DO NOTHING`.

### `profile` 처리 (RED 34)

**SPEC-06에 컬럼이 없다.** 선택지:
1. 버린다 — `FR-COCKTAIL-023`은 P1
2. `cocktail`에 컬럼 추가 — SPEC-06 개정 필요
3. JSONB로 임시 보관

**2안**: SPEC-06 §3.1에 `flavor_profile SMALLINT[5]` 추가를 `GAPS.md`에 올리고, **데이터는 지금 넣는다**. 나중에 다시 만들 수 없는 자료다.

**하지 말 것**:
- `tasting_note` 자동 생성 (`PRIN-P03` 위반)
- `data.ts` 삭제 — 이슈 037까지 프론트가 쓴다

## DoD

- [ ] RED 35항 전부 통과
- [ ] **24종 전부 발행 게이트 통과** (RED 24 — `NFR-D-01`)
- [ ] 재료 마스터가 FK로 연결, 프리텍스트 0건 (RED 20 — `PRIN-D01`)
- [ ] `BASE_SLUGS` 10종 일치 (RED 9 — ADR-0002)
- [ ] 시드 멱등 (RED 32·33)
- [ ] 변환 스크립트 커밋
- [ ] 미결은 [`DECISIONS.md`](DECISIONS.md) §1 확정분을 따른다 — **이슈에서 판단하지 않는다**
- [ ] 커밋: `feat(api): 프로토타입 24종 Postgres 시드 이관 (SPEC-01 §6, SPEC-06 §6)`
