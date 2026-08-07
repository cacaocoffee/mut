# SPEC-06 — 데이터 모델 · ERD

| | |
|---|---|
| 버전 | v1.0 |
| 최종 수정 | 2026-08-06 |
| 대상 | PostgreSQL 16 |
| 상위 문서 | [SPEC-02 도메인모델](./SPEC-02_도메인모델.md) — 개념과 불변식은 그쪽 |

이 문서는 **물리 스키마**다. 왜 그런 규칙이 있는지는 SPEC-02를 본다.

---

## 1. 규약

### 1.1 명명

- 테이블 · 컬럼은 `snake_case` 단수형 (`cocktail`, `recipe_ingredient`)
- 조인 테이블은 `<주>_<종>` (`cocktail_style`)
- 불리언은 `is_` / `has_` 접두 (`is_signature`)
- 시각은 `_at` 접미 (`published_at`), 날짜는 `_on`

### 1.2 공통 컬럼

모든 실체 테이블이 갖는다.

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `id` | `BIGINT GENERATED ALWAYS AS IDENTITY` | 내부 식별자 |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` | |
| `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` | 트리거로 갱신 |

**공개 식별자는 `slug`다.** URL에 `id`를 노출하지 않는다 — 슬러그가 SEO 자산이고
`PRIN-D02`가 불변을 요구한다.

### 1.3 enum은 `VARCHAR` + `CHECK`

Postgres 네이티브 `ENUM`을 쓰지 않는다.

- 값 추가는 쉽지만 **삭제·순서 변경이 사실상 불가능**하다. 분류 축은 앞으로 늘어난다.
- JPA 매핑에 커스텀 타입이 필요해 코드가 지저분해진다.
- `CHECK` 제약은 마이그레이션으로 자유롭게 교체할 수 있다.

허용값의 정본은 Kotlin enum이고(`PRIN-T02`), `CHECK`는 DB 레벨 이중 방어다.

### 1.4 배열은 조인 테이블로

PRD 11장이 `styles[]` · `aroma_tags[]`로 적었지만 물리 설계는 **조인 테이블**로 간다.

- 카테고리 페이지 조회(`/cocktails/style/sour/`)가 단순 조인이 된다
- **패싯 카운트(`R-F2.1-2`)가 `GROUP BY` 한 방으로 끝난다.** 배열이면 `unnest`를 거쳐야 한다
- `style_primary ∈ styles` 불변식을 **복합 FK로 DB가 강제**할 수 있다 (§4.2)

예외는 `aliases[]`다. 검색 전용이고 무결성 대상이 아니라 `TEXT[]` + GIN으로 둔다.

---

## 2. ERD 개요

```
                    ┌───────────────┐
                    │  ingredient   │
                    └───────┬───────┘
                            │ ▲ substitute_ingredient_id (self)
                            │
        ┌───────────────────▼──────────────┐
        │      recipe_ingredient           │
        └───────────────────┬──────────────┘
                            │
   ┌────────────┐   ┌───────▼───────┐
   │  cocktail  │◀──┤    recipe     │
   └──────┬─────┘   └───────────────┘
          │  ├── cocktail_style        (styles[])
          │  ├── cocktail_aroma_tag    (aroma_tags[])
          │  └── style_primary ──FK──▶ cocktail_style
          │
          │ ◀── bar_menu_item.cocktail_id      ★ 그래프의 연결점
          │
   ┌──────┴─────┐
   │    bar     │──┬── bar_menu_item
   └──────┬─────┘  ├── bartender
          │        └── bar_reservation_link
          │
   ┌──────▼─────────────┐
   │ partner_contract   │──── partner_daily_stat
   └────────────────────┘

   ┌──────────┐  ┌─────────────┐  ┌──────────────┐
   │   user   │──┤  bookmark   │  │  user_stock  │
   └────┬─────┘  └─────────────┘  └──────────────┘
        ├── user_role      (역할 다대다)
        └── bar_owner ──────────────▶ bar   ★ partner_owner 스코프 판정

   ┌──────────┐  ┌──────────────┐  ┌──────────────────┐
   │ article  │  │ curation_list│  │ curation_item    │
   └──────────┘  └──────────────┘  └──────────────────┘

   ┌──────────────────┐  ┌───────────┐  ┌───────────────────┐
   │ search_document  │  │ audit_log │  │ analytics_event   │
   └──────────────────┘  └───────────┘  └───────────────────┘
```

---

## 3. 테이블

### 3.1 COCKTAIL

#### `cocktail`

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| `id` | `BIGINT` | PK | |
| `slug` | `VARCHAR(120)` | **UNIQUE, NOT NULL** | 발행 후 불변 (`INV-COCKTAIL-05`) |
| `name_ko` | `VARCHAR(120)` | NOT NULL | |
| `name_en` | `VARCHAR(120)` | NOT NULL | |
| `aliases` | `TEXT[]` | DEFAULT `'{}'` | 검색용. `올패` 등 |
| `summary` | `TEXT` | NOT NULL | 한 줄 요약 |
| `base_spirit` | `VARCHAR(24)` | **NOT NULL**, CHECK | 축 1 (`R-C-1`) |
| `style_primary` | `VARCHAR(24)` | **NOT NULL** | 축 2 대표. FK는 §4.2 |
| `method` | `VARCHAR(12)` | **NOT NULL**, CHECK | 축 3 |
| `sweetness` | `VARCHAR(12)` | NOT NULL, CHECK | `dry`·`semi_dry`·`semi_sweet`·`sweet` |
| `abv_calculated` | `NUMERIC(4,1)` | | 자동 계산 (`R-F1.1-4`) |
| `abv_override` | `NUMERIC(4,1)` | | 수동 우선 |
| `abv` | `NUMERIC(4,1)` | **GENERATED** | `COALESCE(abv_override, abv_calculated)` |
| `glass_type` | `VARCHAR(40)` | NOT NULL | |
| `prep_time_min` | `SMALLINT` | | |
| `tasting_note` | `TEXT` | | **발행 시 필수** (`GATE-COCKTAIL-01`) |
| `story` | `TEXT` | | 클래식은 발행 시 필수 |
| `is_classic` | `BOOLEAN` | NOT NULL DEFAULT false | `GATE-COCKTAIL-05` 판정용 |
| `origin_year` `origin_place` `origin_creator` | `VARCHAR(80)` | | |
| `hero_media_id` | `BIGINT` | FK → `media_asset` | |
| `status` | `VARCHAR(12)` | NOT NULL, CHECK | `draft`·`published`·`archived` |
| `published_at` | `TIMESTAMPTZ` | | |
| `flavor_profile` | `SMALLINT[5]` | | 단맛·산미·쓴맛·향 강도·알코올 각 0~5 (`FR-COCKTAIL-023`) |

`flavor_profile`은 `FR-COCKTAIL-023`(맛 프로필 레이더, P1)의 데이터다. **기능은 P1이지만 컬럼은 Phase 1a에 둔다** —
프로토타입 `packages/domain/src/data.ts`의 24종이 이미 이 값을 갖고 있고, **이관 때 버리면 다시 만들 수 없다**
([G-19](../prd/GAPS.md#g-19) · 이슈 036). 배열이지만 고정 길이 5의 표시 전용이라 조인 테이블로 쪼개지 않는다(§1.4 예외).

`abv`를 생성 컬럼으로 둔 이유는 **조회·필터가 항상 표시값을 봐야 하기 때문**이다.
매 쿼리에서 `COALESCE`를 쓰면 인덱스가 안 붙는다.

```sql
CONSTRAINT ck_cocktail_na CHECK (
  (base_spirit = 'non-alcoholic') = (abv = 0)
)   -- INV-COCKTAIL-06
```

#### `cocktail_style` · `cocktail_aroma_tag`

| 컬럼 | 타입 | |
|---|---|---|
| `cocktail_id` | `BIGINT` | FK → `cocktail` ON DELETE CASCADE |
| `style` / `aroma_tag` | `VARCHAR(24)` | CHECK |
| | | PK (`cocktail_id`, 값) |

#### `recipe`

| 컬럼 | 타입 | 제약 |
|---|---|---|
| `id` | `BIGINT` | PK |
| `cocktail_id` | `BIGINT` | FK, NOT NULL |
| `version_type` | `VARCHAR(16)` | NOT NULL, CHECK — `standard`·`bar_signature`·`user` |
| `author_bar_id` | `BIGINT` | FK → `bar`. `bar_signature`일 때만 |
| `author_user_id` | `BIGINT` | FK → `user`. `user`일 때만 |
| `serving_count` | `SMALLINT` | NOT NULL DEFAULT 1 |
| `note` | `TEXT` | |

```sql
-- INV-COCKTAIL-07: 표준 레시피는 칵테일당 정확히 1개
CREATE UNIQUE INDEX uq_recipe_standard
  ON recipe (cocktail_id) WHERE version_type = 'standard';
```

#### `recipe_step`

| 컬럼 | 타입 | |
|---|---|---|
| `recipe_id` | `BIGINT` | FK |
| `step_no` | `SMALLINT` | PK (`recipe_id`, `step_no`) |
| `text` | `TEXT` | NOT NULL |
| `technique_ref` | `VARCHAR(40)` | 툴팁 용어 키 (`FR-COCKTAIL-022`) |

#### `recipe_ingredient`

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `recipe_id` | `BIGINT` | FK |
| `ingredient_id` | `BIGINT` | FK, **NOT NULL** — 프리텍스트 금지 (`PRIN-D01`) |
| `position` | `SMALLINT` | 표시 순서. PK (`recipe_id`, `position`) |
| `amount` | `NUMERIC(6,2)` | |
| `unit` | `VARCHAR(12)` | CHECK — `ml`·`dash`·`barspoon`·`piece`·`top_up` |
| `amount_label` | `VARCHAR(40)` | `1조각`처럼 배수 계산 제외 표기 |
| `role` | `VARCHAR(16)` | CHECK — `base`·`modifier`·`sweetener`·`citrus`·`garnish` |
| `is_optional` | `BOOLEAN` | NOT NULL DEFAULT false |
| `substitute_ingredient_id` | `BIGINT` | FK → `ingredient` |
| `substitute_note` | `TEXT` | "아페롤 — 쓴맛이 절반으로…" |
| **`counts_for_stock`** | `BOOLEAN` | NOT NULL DEFAULT true. 역검색 판정 대상 (`R-F2.2-5`) |

### 3.2 INGREDIENT

#### `ingredient`

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `id` `slug` `name_ko` `name_en` | | |
| `aliases` | `TEXT[]` | |
| `category` | `VARCHAR(16)` | CHECK — `spirit`·`liqueur`·`bitters`·`syrup`·`juice`·`garnish`·`mixer` |
| `abv` | `NUMERIC(4,1)` | 도수 자동 계산 입력 |
| `description` | `TEXT` | |
| **`domestic_availability`** | `VARCHAR(16)` | NOT NULL, CHECK — `common`·`specialty`·`import_only`·`unavailable` |
| `substitute_note` | `TEXT` | `import_only`·`unavailable`이면 필수 (`INV-INGREDIENT-01`) |
| `price_band` | `VARCHAR(12)` | |
| `is_approved` | `BOOLEAN` | 에디터 승인제 (`FR-ADMIN-007`) |

#### `ingredient_brand`

브랜드 언급의 광고성 구분 (`INV-INGREDIENT-02`).

| 컬럼 | 타입 | |
|---|---|---|
| `ingredient_id` | `BIGINT` | FK |
| `name` | `VARCHAR(80)` | |
| `purchase_url` | `TEXT` | |
| **`is_sponsored`** | `BOOLEAN` | NOT NULL DEFAULT false — 켜지면 라벨 강제 |

### 3.3 BAR

#### `bar`

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `id` `slug` `name_ko` `name_en` | | |
| `district` | `VARCHAR(40)` | NOT NULL — **상권 단위** (`R-F3.2-1`) |
| `address` | `TEXT` | |
| `lat` `lng` | `NUMERIC(9,6)` | |
| `closed_days` | `TEXT[]` | |
| **`hours_verified_at`** | `TIMESTAMPTZ` | 90일 경과 시 검증 태스크 (`R-F3.1-2`) |
| `reservation_type` | `VARCHAR(24)` | CHECK — `required`·`recommended`·`walk_in_ok`·`walk_in_only` |
| `price_band` | `VARCHAR(12)` | |
| `seat_count` | `SMALLINT` | |
| `editor_note` | `TEXT` | 발행 필수 |
| `instagram_url` `phone` | | |
| `status` | `VARCHAR(12)` | CHECK — `draft`·`published`·`archived`·**`closed`** (폐업) |

**별점·총점 컬럼을 두지 않는다** (`INV-BAR-01`). 나중에 "임시로" 추가하지 못하도록
이 문장을 스키마 주석에 남긴다.

#### `bar_opening_hour` · `bar_reservation_link` · `bar_style_tag` · `bar_mood_tag`

각각 `bar_id` FK를 갖는 단순 자식 테이블.
`bar_opening_hour`는 (`bar_id`, `day_of_week`, `opens_at`, `closes_at`).

#### `bar_menu_item` ★ 그래프의 연결점

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `bar_id` | `BIGINT` | FK, NOT NULL |
| `cocktail_id` | `BIGINT` | FK, **NULL 허용** — 아직 DB에 없는 창작 메뉴 (`FR-BAR-015`) |
| `name` `description` | | |
| `price` | `INTEGER` | |
| `is_price_public` | `BOOLEAN` | 바가 선택 (`R-F4.1-5`) |
| **`is_signature`** | `BOOLEAN` | NOT NULL |
| `is_seasonal` | `BOOLEAN` | |
| **`source`** | `VARCHAR(12)` | NOT NULL, CHECK — `editor`·`partner` |
| `verified_at` | `TIMESTAMPTZ` | 시그니처는 연 1회 (ADR-0003) |

`source`가 [ADR-0003](../decisions/ADR-0003-graph-source-and-abv-bands.md)의 입력 주체 분리를
데이터로 표현한 것이다. `is_signature = false`인 행은 `source = 'partner'`만 허용한다.

```sql
CONSTRAINT ck_menu_source CHECK (
  is_signature OR source = 'partner'
)
```

#### `bartender`

`bar_id` FK, `name`, `bio`, `career` `JSONB`, `awards` `JSONB`, `image_media_id`.

### 3.4 PARTNER

#### `partner_contract`

| 컬럼 | 타입 | |
|---|---|---|
| `bar_id` | `BIGINT` | FK, UNIQUE |
| `tier` | `VARCHAR(12)` | CHECK — `listed`·`verified`·`partner`·`signature` |
| `since` `until` | `DATE` | `until` 경과 시 자동 강등 (`FR-PARTNER-001`) |

**노출 규칙(부스팅 한도 · 홈 슬롯 비율)에 해당하는 컬럼을 만들지 않는다.**
`PRIN-P02` — 저장 가능하게 만들면 조정된다. 코드 상수로만 존재한다.

#### `partner_daily_stat`

| 컬럼 | 타입 | |
|---|---|---|
| `bar_id` `stat_on` | | PK |
| `view_count` `bookmark_count` | `INTEGER` | |
| `action_reserve` `action_map` `action_call` `action_instagram` | `INTEGER` | `R-F4.3-1` |

유입 칵테일 랭킹(`R-F4.3-2`)은 `analytics_event`에서 집계한다 (§3.8).

### 3.5 USER · STOCK

#### `user`

| 컬럼 | 타입 | |
|---|---|---|
| `id` | | |
| `provider` | `VARCHAR(12)` | CHECK — `kakao`·`naver`·`apple` |
| `provider_uid` | `VARCHAR(120)` | UNIQUE (`provider`, `provider_uid`) |
| `display_name` | `VARCHAR(60)` | NOT NULL |
| `email` | `VARCHAR(255)` | **NULL 허용** — 애플 비공개 릴레이 등 |

**성인 인증 관련 테이블은 없다** ([ADR-0004](../decisions/ADR-0004-age-gate.md)).
위치 정보를 저장하는 컬럼도 없다 (`PRIN-D04`).

#### `user_role`

역할을 `user` 컬럼이 아니라 별도 테이블로 둔다. **팀 규모가 작아 한 사람이
에디터이면서 관리자인 경우가 실제로 생긴다** — 단일 컬럼이면 둘 중 하나를 포기해야 한다.

| 컬럼 | 타입 | |
|---|---|---|
| `user_id` | `BIGINT` | FK |
| `role` | `VARCHAR(16)` | CHECK — `member`·`editor`·`partner_owner`·`admin` |
| `granted_by` `granted_at` | | 부여 이력 |
| | | PK (`user_id`, `role`) |

#### `bar_owner`

`partner_owner`가 "자기 바"를 판정하는 근거. **[SPEC-08](./SPEC-08_보안_권한_개인정보.md)을
쓰면서 발견한 누락**이다 — `partner_contract`는 바와 등급만 이었고 사용자 연결이 없었다.

| 컬럼 | 타입 | |
|---|---|---|
| `user_id` | `BIGINT` | FK → `user` |
| `bar_id` | `BIGINT` | FK → `bar` |
| `granted_by` | `BIGINT` | FK → `user`. 누가 부여했나 |
| `granted_at` | `TIMESTAMPTZ` | |
| | | PK (`user_id`, `bar_id`) |

한 사람이 여러 바를 소유할 수 있고(체인) 한 바에 오너가 여럿일 수 있다(공동 운영).
**모든 `/partner/**` 요청이 이 테이블을 조회해 IDOR을 막는다** (SPEC-08 §3.2).

#### `bookmark_collection` · `bookmark`

| `bookmark` 컬럼 | 타입 | |
|---|---|---|
| `user_id` | `BIGINT` | FK |
| `collection_id` | `BIGINT` | FK, NULL이면 기본 컬렉션 |
| `target_type` | `VARCHAR(12)` | CHECK — `cocktail`·`bar`·`article` |
| `target_id` | `BIGINT` | |
| | | UNIQUE (`user_id`, `target_type`, `target_id`) |

다형 참조라 FK를 걸 수 없다. **타입별 테이블로 쪼개지 않는 이유**는 컬렉션이
세 종류를 섞어 담아야 하기 때문이다(`R-F5-2`). 참조 무결성은 앱이 책임진다.

#### `user_stock` (Phase 2)

`user_id`, `ingredient_id`, `added_at`. PK (`user_id`, `ingredient_id`).

### 3.6 CONTENT

#### `article`

`slug`, `type`(CHECK `interview`·`guide`·`trend`·`photo_essay`), `title`, `body`,
`cover_media_id`, **`is_sponsored`**, `sponsor_bar_id`, `status`, `published_at`.

`is_sponsored = true`면 라벨 렌더링을 끌 수 없다 (`INV-CONTENT-02`) — 앱 레벨 강제.

#### `article_related_bar` · `article_related_cocktail`

단순 조인 테이블.

#### `curation_list` · `curation_item`

| `curation_item` 컬럼 | 타입 | |
|---|---|---|
| `list_id` | `BIGINT` | FK |
| `bar_id` | `BIGINT` | FK |
| `rank` | `SMALLINT` | 비순위형이면 NULL |
| **`reason`** | `TEXT` | **NOT NULL** — 선정 사유 필수 (`R-F3.3-2`) |

`reason`을 `NOT NULL`로 두는 것이 `INV-CONTENT-01`의 DB 레벨 강제다.

### 3.7 미디어

#### `media_asset`

| 컬럼 | 타입 | |
|---|---|---|
| `id` | | |
| `storage_key` | `TEXT` | NOT NULL — 저장소 내 경로 |
| `mime_type` `width` `height` `byte_size` | | |
| `alt_text` | `TEXT` | 접근성 |

**저장소 백엔드는 미정이다** ([G-07](../prd/GAPS.md#g-07)). `storage_key`가 추상화 지점이라
백엔드가 정해져도 스키마는 바뀌지 않는다.

### 3.8 횡단 테이블

#### `search_document` — 통합 검색

`R-F5-1`(타입별 그룹핑) · `R-F2.1-3`(한/영 별칭) · `R-F2.1-4`(초성)를 한 테이블로 받는다.

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `entity_type` | `VARCHAR(12)` | `cocktail`·`bar`·`ingredient`·`article` |
| `entity_id` | `BIGINT` | PK (`entity_type`, `entity_id`) |
| `slug` | `VARCHAR(120)` | |
| `name_ko` `name_en` | | |
| `aliases` | `TEXT[]` | |
| **`chosung`** | `TEXT` | 초성 분해 문자열 — `마르가리타` → `ㅁㄹㄱㄹㅌ` |
| `weight` | `SMALLINT` | 정렬 가중치 |
| `is_published` | `BOOLEAN` | |

각 엔티티 저장 시 동기화한다. **초성은 저장 시점에 분해한다** — 조회 때 계산하면 인덱스가 안 붙는다.

#### `audit_log`

| 컬럼 | 타입 | |
|---|---|---|
| `entity_type` `entity_id` | | |
| `action` | `VARCHAR(32)` | `publish`·`unpublish`·`tier_change`·`rank_change`·`verify` |
| `actor_user_id` | `BIGINT` | |
| `before` `after` | `JSONB` | |
| `at` | `TIMESTAMPTZ` | |

`PRIN-T08`의 4종 대상을 전부 받는다.

#### `verification_task` — 검증 태스크 큐

`FR-ADMIN-004`(**P0**)가 요구하는 저장소다. [G-19](../prd/GAPS.md#g-19)에서 누락이 확인돼 추가했다.
§4.3이 "위반 건을 관리자 태스크로 올린다"고 한 것도 이 테이블을 전제한다.

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `task_type` | `VARCHAR(32)` | CHECK — `invariant_violation`(1a) · `hours_expired`(1b) · `instagram_signal`(1b) |
| `entity_type` `entity_id` | | 대상 |
| `code` | `VARCHAR(40)` | `INV-`/`GATE-` ID |
| `detail` | `JSONB` | |
| `status` | `VARCHAR(12)` | CHECK — `open`·`resolved`·`dismissed` |
| `detected_at` `resolved_at` `resolved_by` | | |
| | | **UNIQUE (`task_type`, `entity_type`, `entity_id`, `code`)** — 멱등 (`PRIN-T07`) |

**세 종류가 한 테이블을 쓴다.** 불변식 위반(§4.3 배치) · 바 정보 만료(`FR-BAR-004`, 1b) · 인스타 폐업 신호(SPEC-05 §8, 1b).
`task_type`으로 구분하며, **유니크 제약이 배치 재실행 시 중복 생성을 막는다.**

#### `category_intro` — 카테고리 소개 문구

`FR-COCKTAIL-031`(P1) + **`NFR-S-07`(발행 차단)** 이 요구한다. 카테고리는 테이블이 아니라 enum이라 문구를 둘 곳이 필요하다.

| 컬럼 | 타입 | |
|---|---|---|
| `axis` | `VARCHAR(8)` | CHECK — `base`·`style`·`method` |
| `slug` | `VARCHAR(24)` | 3축 슬러그 (ADR-0002) |
| `intro` | `TEXT` | |
| | | PK (`axis`, `slug`) |

> `slug`는 `cocktail`의 축 값을 가리키지만 **FK를 걸지 않는다** — 축 값은 Kotlin enum이 정본이고(`PRIN-T02`) DB에 마스터 테이블이 없다.

#### `analytics_event`

`PRIN-P01`의 크로스 이동률을 세기 위한 최소 이벤트 저장소 ([G-10](../prd/GAPS.md#g-10)).

| 컬럼 | 타입 | |
|---|---|---|
| `id` | `BIGINT` | |
| `event_type` | `VARCHAR(24)` | `cocktail_view`·`bar_view`·**`cross_nav`**·`partner_action`·`filter_apply` |
| `session_id` | `UUID` | |
| `user_id` | `BIGINT` | NULL 허용 |
| `from_type` `from_id` `to_type` `to_id` | | `cross_nav`용 |
| `payload` | `JSONB` | 이벤트별 추가 필드 |
| `occurred_at` | `TIMESTAMPTZ` | |

> **왜 외부 분석 도구만 쓰지 않는가.**
> `R-F4.3-2`(유입 칵테일 랭킹)는 **파트너 대시보드에 보여줘야 하는 데이터**다.
> GA4·Amplitude에만 쌓으면 우리 화면에서 조회할 수 없다. 핵심 이벤트는 자체 저장한다.
> 제품 분석용 외부 도구는 별개로 병행해도 된다.

`occurred_at` 기준 월 단위 파티셔닝을 전제로 설계한다. Phase 1에는 단일 테이블로 두되
파티션 키가 될 컬럼을 PK에 포함시켜 나중에 쪼갤 수 있게 한다.

---

## 4. 불변식을 어디서 강제하나

`PRIN-T05`는 서버 강제를 요구한다. **DB로 강제할 수 있는 것은 DB에서 한다** — 앱 버그가
데이터를 깨뜨리지 못하게 하는 마지막 방어선이다.

### 4.1 DB가 강제

| 불변식 | 수단 |
|---|---|
| `INV-COCKTAIL-01` 3축 NOT NULL | `NOT NULL` |
| `INV-COCKTAIL-05` slug 유일 | `UNIQUE` |
| `INV-COCKTAIL-06` 무알콜 ⟺ abv 0 | `CHECK` |
| `INV-COCKTAIL-07` 표준 레시피 1개 | 부분 유니크 인덱스 |
| `INV-CONTENT-01` 선정 사유 필수 | `NOT NULL` |
| ADR-0003 메뉴 입력 주체 | `CHECK (is_signature OR source='partner')` |
| 재료 마스터 참조 | `FK NOT NULL` |
| `INV-BAR-03` 물리 삭제 금지 | **앱 DB 역할에서 `DELETE` 권한을 회수한다.** `bar` · `cocktail` · `article` · `curation_list`에 `REVOKE DELETE`. 폐업은 `status='closed'` 전이다 |

### 4.2 `style_primary ∈ styles` — 복합 FK

`INV-COCKTAIL-03`을 DB가 강제한다. 이게 조인 테이블을 택한 가장 큰 이유다.

```sql
ALTER TABLE cocktail_style
  ADD CONSTRAINT uq_cocktail_style UNIQUE (cocktail_id, style);

ALTER TABLE cocktail
  ADD CONSTRAINT fk_style_primary
  FOREIGN KEY (id, style_primary)
  REFERENCES cocktail_style (cocktail_id, style)
  DEFERRABLE INITIALLY DEFERRED;
```

`DEFERRABLE`이 필요한 이유는 칵테일과 스타일 행이 같은 트랜잭션에서 삽입되기 때문이다.

### 4.3 앱이 강제

집계나 조건부 판단이라 DB 제약으로 표현할 수 없는 것들.

| 불변식 | 이유 |
|---|---|
| `INV-COCKTAIL-02` 스타일 1개 이상 | 자식 행 개수 — 트리거는 과하다 |
| `INV-COCKTAIL-04` 향 태그 1~3개 | 같음 |
| `GATE-COCKTAIL-01` 향과 맛 서술 | 조건부 (발행 시에만) |
| `GATE-COCKTAIL-02` 3축 불변식 통과 | 다중 테이블 참조 |
| `GATE-COCKTAIL-03` 표준 레시피 재료·스텝 1개 이상 | 자식 행 개수 |
| `GATE-COCKTAIL-04` 재료가 전부 마스터 참조 | FK가 이미 보장. 게이트는 재확인 |
| `GATE-COCKTAIL-05` 클래식은 story 필수 | `is_classic` 조건부 |
| `GATE-COCKTAIL-06` 미유통 재료 대체재 명시 | 다중 테이블 조건부 |
| `INV-INGREDIENT-01` 미유통 대체재 필수 | 조건부 |
| `INV-INGREDIENT-02` 브랜드 광고성 표기 | `is_sponsored` 저장은 DB, 라벨 렌더링 강제는 앱 |
| `INV-CONTENT-02` 협찬 라벨 렌더링 강제 | 표현 계층 |

**앱 강제 항목은 배치 검증으로 이중 확인한다.** 일 1회 전수 스캔해 위반 건을
관리자 태스크로 올린다 — 현재 `packages/domain/src/validate.ts`가 하는 일의 서버판이다.

### 4.4 제약이 아니라 규칙인 것

불변식으로 적혀 있지만 데이터 제약이 아닌 것들. **여기 없으면 누락으로 오해되므로 명시한다.**

| 불변식 | 성격 | 어디서 지키나 |
|---|---|---|
| `INV-BAR-01` 별점·총점 없음 | **스키마 부재로 성립** — 컬럼 자체가 없다 | SPEC-06 §3.3 주석 |
| `INV-BAR-02` `hours_verified_at` 항상 표시 | 화면 규칙 | SCREENS — 바 상세 |
| `INV-BAR-04` 미제휴 바도 등재 | 구조로 성립 — `partner_contract` 없이 `bar`가 존재 가능 | 스키마 |
| `INV-PARTNER-01` 부스팅 상위 3중 1 | 정렬 로직의 코드 상수 | `PRIN-P02` — DB에 저장하지 않는다 |
| `INV-PARTNER-02` 홈 슬롯 30% | 같음 | 같음 |
| `INV-PARTNER-03` 순위에 제휴 영향 없음 | 랭킹 로직에 파트너 입력이 없음 | 같음 |
| `INV-PARTNER-04` 제휴 콘텐츠 라벨 | `article.is_sponsored`가 근거, 렌더링은 표현 계층 | 앱 |

`INV-PARTNER-01~04`가 "DB에 없다"는 것이 곧 `PRIN-P02`의 구현이다 —
**저장 가능하게 만들지 않아야 조정할 수 없다.**

---

## 5. 인덱스

| 대상 | 인덱스 | 왜 |
|---|---|---|
| `cocktail(slug)` `bar(slug)` | UNIQUE | 상세 조회 |
| `cocktail(status, base_spirit)` | B-tree | 카테고리 페이지 |
| `cocktail(status, style_primary)` `cocktail(status, method)` | B-tree | 같음 |
| `cocktail(status, abv)` | B-tree | 도수 구간 필터 |
| `cocktail_style(style, cocktail_id)` | B-tree | 패싯 카운트 `GROUP BY` |
| `cocktail_aroma_tag(aroma_tag, cocktail_id)` | B-tree | 같음 |
| `search_document(chosung) ` | **GIN + `pg_trgm`** | 초성 프리픽스 매칭 |
| `search_document(aliases)` | **GIN** | 별칭 배열 검색 |
| `recipe_ingredient(ingredient_id)` | B-tree | **역검색 · 재료 사전** |
| `bar_menu_item(cocktail_id)` | B-tree | ★ "이 칵테일을 마실 수 있는 바" |
| `bar(district, status)` | B-tree | 상권 필터 |
| `bar(hours_verified_at)` | B-tree | 검증 태스크 배치 |
| `bar_owner(user_id, bar_id)` | PK | **모든 `/partner/**` 요청이 탄다** — 권한 검증 경로 |
| `user_role(user_id)` | PK 선두 | 인증 시 역할 로드 |
| `analytics_event(event_type, occurred_at)` | B-tree | 집계 배치 |
| `audit_log(entity_type, entity_id, at)` | B-tree | 이력 조회 |

`pg_trgm` 확장이 필요하다. 초성 검색을 위해 `CREATE EXTENSION pg_trgm`을 마이그레이션 첫 단계에 둔다.

---

## 6. 마이그레이션

- **Flyway.** `V<번호>__<설명>.sql` 형식, 앞으로만 간다. 적용된 마이그레이션을 수정하지 않는다.
- **`slug` 값을 바꾸는 마이그레이션을 쓰지 않는다** (`PRIN-D02`).
- enum 값 추가는 `CHECK` 제약 교체로 처리한다. Kotlin enum과 같은 마이그레이션에 넣는다.
- 시드 데이터는 별도 `R__seed_*.sql`(repeatable)로 둔다.
  현재 `packages/domain/src/data.ts`의 24종이 최초 시드가 된다 (SPEC-01 §6).

---

## 7. 미정

| 항목 | 영향 | 갭 |
|---|---|---|
| 이미지 저장소 백엔드 | `media_asset.storage_key`의 의미. **스키마는 안 바뀐다** | [G-07](../prd/GAPS.md#g-07) |
| `analytics_event` 파티셔닝 시점 | Phase 1은 단일 테이블 | — |
| 검색 가중치(`weight`) 산정식 | 통합 검색 정렬 품질 | [G-13](../prd/GAPS.md#g-13) |
| `role` 스코프 상세 | SPEC-08에서 정의 | [G-11](../prd/GAPS.md#g-11) |
