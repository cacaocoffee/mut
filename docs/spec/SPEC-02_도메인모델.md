# SPEC-02 — 도메인 모델

| | |
|---|---|
| 버전 | v1.0 |
| 최종 수정 | 2026-08-06 |
| 상위 문서 | [SPEC-00 개발원칙](./SPEC-00_개발원칙.md) |
| 하위 문서 | [SPEC-06 ERD](./SPEC-06_데이터모델_ERD.md) — 테이블·컬럼은 그쪽 |

이 문서는 **개념과 규칙**을 정의한다. 물리 스키마가 아니다.
여기서 정한 불변식은 서버가 강제한다 (`PRIN-T05`).

---

## 1. 애그리게이트 지도

```
┌─ COCKTAIL ─────────────────────────┐
│  Cocktail (루트)                    │
│   └ Recipe (1..n)                  │  ← 표준 1 + 바 버전 n (PRIN-D03)
│       └ RecipeIngredient (1..n)    │  ─┐
└────────────────────────────────────┘   │ 참조
                                          ▼
┌─ INGREDIENT ───────────────────────┐
│  Ingredient (루트)                  │  ← 문자열 아님 (PRIN-D01)
└────────────────────────────────────┘
                                          ▲ 참조
┌─ BAR ──────────────────────────────┐   │
│  Bar (루트)                         │   │
│   ├ BarMenuItem (0..n) ────────────┼───┘  시그니처 → Cocktail
│   └ Bartender (0..n)               │
└────────────────────────────────────┘
       ▲ 확장
┌─ PARTNER ──────────────────────────┐
│  PartnerContract · PartnerStats    │
│  Coupon                            │
└────────────────────────────────────┘

┌─ CONTENT ───┐  ┌─ USER ────┐  ┌─ STOCK ────┐
│ Article     │  │ User      │  │ UserStock  │
│ CurationList│  │ Bookmark  │  └────────────┘
└─────────────┘  └───────────┘
```

**애그리게이트 경계 = 트랜잭션 경계.** `Recipe`를 `Cocktail` 없이 수정하지 않는다.
`BarMenuItem`이 `Cocktail`을 가리키지만 **참조일 뿐** 같은 트랜잭션에 넣지 않는다.

---

## 2. COCKTAIL

### 2.1 Cocktail — 칵테일 그 자체

"네그로니라는 음료"를 가리킨다. **어떻게 만드는가는 `Recipe`가 가진다.**

| 개념 | 설명 |
|---|---|
| 정체 | `name_ko` · `name_en` · `aliases[]` · `slug` |
| **분류 3축** | `base_spirit`(단일) · `styles[]` + `style_primary` · `method`(단일) |
| 필터 필드 | `sweetness`(4단계) · `abv` · `aroma_tags[]`(1~3) |
| 서술 | `tasting_note`(향과 맛, **발행 필수**) · `story`(선택) |
| 상태 | `status`: `draft` · `published` · `archived` |

### 2.2 불변식

| ID | 불변식 | 근거 |
|---|---|---|
| `INV-COCKTAIL-01` | 분류 3축은 전부 NOT NULL | `R-C-1` |
| `INV-COCKTAIL-02` | `styles`는 최소 1개 | `R-C-1` |
| `INV-COCKTAIL-03` | `style_primary ∈ styles` | `R-C-3` |
| `INV-COCKTAIL-04` | `aroma_tags` 1~3개 | `R-F1.2-1` |
| `INV-COCKTAIL-05` | `slug`는 발행 후 불변 | `PRIN-D02` |
| `INV-COCKTAIL-06` | `base_spirit = non-alcoholic` ⟺ `abv = 0` | 정합성 |
| `INV-COCKTAIL-07` | 표준 레시피(`version_type = standard`)가 정확히 1개 | `R-F1.1-7` |

### 2.3 발행 게이트

`draft → published` 전이를 막는 조건. **경고가 아니라 차단이다** (`PRIN-P03`).

| ID | 조건 | 근거 |
|---|---|---|
| `GATE-COCKTAIL-01` | `tasting_note`가 비면 발행 불가 | `R-F1.1-2` |
| `GATE-COCKTAIL-02` | 분류 3축 불변식 전부 통과 | `R-C-1` |
| `GATE-COCKTAIL-03` | 표준 레시피가 재료 1개 이상 · 스텝 1개 이상 | — |
| `GATE-COCKTAIL-04` | 모든 `RecipeIngredient`가 마스터 참조 | `R-F1.1-1` |
| `GATE-COCKTAIL-05` | 명예의 전당 · 클래식 분류면 `story` 필수 | `R-F1.1-3` |
| `GATE-COCKTAIL-06` | 국내 미유통 재료가 있으면 대체재 명시 | `R-F1.3-2` |

### 2.4 도수 계산

`R-F1.1-4`. 재료의 도수 · 용량과 기법별 희석률로 자동 계산하고 **수동 오버라이드를 둔다.**

| 기법 | 희석률 |
|---|---|
| Shake | 25% |
| Stir | 20% |
| Build | 10% |
| Blend · Etc | 수동 |

```
abv_calculated = Σ(ingredient.abv × amount_ml) / Σ(amount_ml) × (1 - 희석률)
표시값 = abv_override ?? abv_calculated
```

`counts_for_stock = false`인 가니시는 계산에서 제외한다.

### 2.5 당도

`R-F1.1-5`. **에디터 수동 입력이다.** 시럽·리큐르·시트러스의 상호작용 때문에 자동 계산은
신뢰할 수 없다. 4단계 — `dry` · `semi_dry` · `semi_sweet` · `sweet`
([ADR-0002](../decisions/ADR-0002-taxonomy.md)).

### 2.6 Recipe — 만드는 법

하나의 칵테일에 여러 버전이 공존한다 (`PRIN-D03`).

| `version_type` | 누가 | 기본 노출 |
|---|---|---|
| `standard` | 에디터 | ✅ |
| `bar_signature` | 제휴 바 | 선택 시 |
| `user` | 유저 | v2 |

`bar_signature`가 파트너 상품의 가장 강력한 셀링 포인트다 (`R-F4.1-1`) —
홈텐더가 레시피 검색으로 들어와 바를 알게 되는 **역방향 유입**이 여기서 생긴다.

### 2.7 RecipeIngredient

| 필드 | 의미 |
|---|---|
| `ingredient_id` | 마스터 참조. 프리텍스트 금지 (`PRIN-D01`) |
| `amount` · `unit` | `ml` · `dash` · `barspoon` · `piece` · `top_up` |
| `role` | `base` · `modifier` · `sweetener` · `citrus` · `garnish` |
| `is_optional` | 선택 재료 |
| `substitute_ingredient_id` | 대체재 |
| **`counts_for_stock`** | 역검색 판정 대상 여부 |

`counts_for_stock`이 없으면 **민트 잎 하나 없다고 모히토가 안 나온다** (`R-F2.2-5`).
가니시 · 얼음 · 물은 기본 `false`.

---

## 3. INGREDIENT

재료 마스터. **국내 유통 기준 200~300개로 상한을 둔다** — 무한정 늘리면 역검색 UX가 무너진다.
신규 추가는 에디터 승인제.

| 필드 | 의미 |
|---|---|
| `category` | `spirit` · `liqueur` · `bitters` · `syrup` · `juice` · `garnish` · `mixer` |
| `abv` | 도수 자동 계산의 입력 |
| **`domestic_availability`** | `common` · `specialty` · `import_only` · `unavailable` |
| `price_band` · `purchase_links[]` | 국내 구매 가이드 |
| `aliases[]` | 검색 별칭 |

`domestic_availability`가 이 서비스의 국내판 정체성을 만드는 필드다 (`PRIN-P05`).

| ID | 불변식 |
|---|---|
| `INV-INGREDIENT-01` | `import_only` · `unavailable`이면 대체재 또는 자가제조 안내 필수 (`R-F1.3-2`) |
| `INV-INGREDIENT-02` | 특정 브랜드 언급 시 광고성 여부를 구분해 표기 (`R-F1.3-3`) |

---

## 4. BAR

평점을 쌓지 않는다 (`PRIN-P04`). 취재한 문장과 구조화된 사실만.

| 개념 | 설명 |
|---|---|
| 위치 | `district`(상권 단위) · `address` · `lat` · `lng` |
| 영업 | `opening_hours[]` · `closed_days[]` · **`hours_verified_at`** |
| 예약 | `reservation_type` · `reservation_links[]` |
| 성격 | `style_tags[]` · `mood_tags[]` · `price_band` · `seat_count` |
| 서술 | `editor_note` (취재 기반 2~4문단) |
| 제휴 | `partner_tier` |

**`district`는 행정구역이 아니라 상권이다** (`R-F3.2-1`) —
을지로 · 이태원 · 압구정·청담 · 성수 · 연남·연희 · 강남역 · 서촌.

### 4.1 예약 유형

`R-F3.1-3`. `reservation_required` · `reservation_recommended` · `walk_in_ok` · `walk_in_only`

### 4.2 불변식

| ID | 불변식 | 근거 |
|---|---|---|
| `INV-BAR-01` | 별점·총점 필드를 두지 않는다 | `R-F3.1-4` · `PRIN-P04` |
| `INV-BAR-02` | `hours_verified_at`은 항상 표시된다 | `R-F3.1-2` |
| `INV-BAR-03` | 물리 삭제하지 않는다 — 폐업도 상태 전이 | `PRIN-D05` |
| `INV-BAR-04` | 미제휴 바도 등재한다 | `R-F3.1-5` |

`hours_verified_at` 90일 경과 시 관리자 검증 태스크를 자동 생성한다.

### 4.3 BarMenuItem — 그래프의 연결점

```
BarMenuItem
  bar_id, cocktail_id (nullable), name, description, price
  is_signature, is_seasonal
```

`cocktail_id`가 채워지면 **양방향 링크가 생긴다** — 이게 `PRIN-P01`의 물리적 구현이다.
`nullable`인 이유는 아직 DB에 없는 창작 메뉴도 실어야 하기 때문이고,
그런 메뉴를 정식 등재하는 것이 파트너 전환의 유인이 된다.

---

## 5. PARTNER

### 5.1 등급

| 등급 | 제공 |
|---|---|
| `listed` (무료) | 기본 등재 · 지도 노출 · 정보 수정 요청 |
| `verified` | 사업자 배지 · 직접 관리 · 사진 5장 · 인스타 연동 · 기본 통계 |
| `partner` (주력) | 전용 콘텐츠 전체 + 대시보드 + 큐레이션 편성 검토 |
| `signature` | Partner + 연 1회 에디토리얼 피처 + 홈 상단 슬롯 + 브랜디드 콘텐츠 |

### 5.2 노출 규칙 — 코드 상수

`PRIN-P02`. **어드민에서 조정 가능하게 만들지 않는다.**

| ID | 제약 |
|---|---|
| `INV-PARTNER-01` | 정렬 상위 3개 중 파트너 부스팅 최대 1개 (`R-F4.2-2`) |
| `INV-PARTNER-02` | 홈 파트너 슬롯 ≤ 전체의 30% (`R-F4.2-4`) |
| `INV-PARTNER-03` | 순위형 리스트에서 제휴가 순위에 영향 없음 (`R-F3.3-3`) |
| `INV-PARTNER-04` | 유료 대가 콘텐츠에 `제휴 콘텐츠` 라벨 (`R-F4.2-3`) — **공정위 의무** |

`INV-PARTNER-04`는 `Article.is_sponsored`가 근거다. 이 플래그가 켜지면 라벨 렌더링을
끌 수 없어야 한다.

### 5.3 배지 색 규칙

파트너 배지와 `제휴 콘텐츠` 라벨은 **절대 같은 색을 쓰지 않는다.**
앞은 등급 정보고 뒤는 법적 고지라 성격이 다르다.
표현 규칙은 [SCREENS-00 §9](../screens/SCREENS-00_인덱스_공통규칙.md)에 있다.

---

## 6. CONTENT

| 개념 | 설명 |
|---|---|
| `Article` | `interview` · `guide` · `trend` · `photo_essay`. `is_sponsored` + `sponsor_ref` |
| `CurationList` | `ranked` · `unranked`. 각 항목에 **선정 사유 1~2문장 필수** (`R-F3.3-2`) |

| ID | 불변식 |
|---|---|
| `INV-CONTENT-01` | `CurationList` 항목마다 `reason`이 비면 발행 불가 |
| `INV-CONTENT-02` | `is_sponsored = true`면 라벨 표기가 강제된다 |

---

## 7. USER · STOCK

| 개념 | 설명 |
|---|---|
| `User` | 카카오 · 네이버 · 애플 소셜. 이메일 가입 후순위 |
| ~~`AgeVerification`~~ | **두지 않는다.** 전면 성인 인증 게이트 폐기 ([ADR-0004](../decisions/ADR-0004-age-gate.md)) |
| `Bookmark` | `cocktail` · `bar` · `article`을 컬렉션으로 묶고 공유 링크 생성 |
| `UserStock` | 보유 재료. **비로그인은 로컬 저장**, 저장 시점에 로그인 유도 (`R-F2.2-4`) |

### 7.1 역검색 (Phase 2)

`R-F2.2-1` · `R-F2.2-2`. Difford's가 유료로 파는 기능이고 국내엔 없다.

```
지금 만들 수 있는 것 = 모든 counts_for_stock 재료가 UserStock에 있는 칵테일
재료 1개만 더 있으면 = 부족한 counts_for_stock 재료가 정확히 1개인 칵테일
```

**"1개만 더 있으면"이 체류와 재방문의 핵심 동인이다.** 부족한 재료에 국내 구매처나
대체재를 함께 제시한다.

---

## 8. 상태 전이

### 8.1 콘텐츠 (Cocktail · Bar · Article · CurationList 공통)

```
   draft ──발행 게이트 통과──▶ published ──▶ archived
     ▲                            │
     └────────────────────────────┘
```

- `draft → published`는 게이트를 통과해야 한다 (2.3절).
- 전이는 전부 감사 로그를 남긴다 (`PRIN-T08`).
- `published → draft` 되돌리기가 가능하나, 이미 색인된 URL은 유지된다 (`PRIN-D02`).

### 8.2 제휴

```
listed ──▶ verified ──▶ partner ──▶ signature
   ◀────────────────────────────────────┘  (계약 종료 시 강등)
```

`partner_until` 경과 시 자동 강등한다. **강등되어도 등재는 유지된다** (`INV-BAR-04`).

---

## 9. 용어

| 용어 | 뜻 | 아닌 것 |
|---|---|---|
| 칵테일 | 음료의 정체 | 레시피 |
| 레시피 | 만드는 법의 한 버전 | 칵테일 |
| 카테고리 | 색인되는 3축 | 필터 |
| 필터 | 색인 안 되는 탐색 도구 | 카테고리 |
| 스타일 | 레시피 **구조** (사워 · 하이볼) | 시대 구분 (클래식 · 모던) |
| 상권 | 을지로 · 성수 | 행정구역 (중구 · 성동구) |
| 내 술장 | 유저 보유 재료 | 즐겨찾기 |
