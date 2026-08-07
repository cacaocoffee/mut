---
id: ISSUE-017
title: search_document 동기화 + 초성 분해
domain: SEARCH
layer: api
wave: 3
status: TODO
depends_on: [ISSUE-014]
fr: [FR-SEARCH-006, FR-SEARCH-007]
r: [R-F2.1-3, R-F2.1-4]
inv: []
nfr: []
migration: V017
owns:
  - apps/api/src/main/kotlin/kr/kcocktail/search/index/**
  - apps/api/src/main/kotlin/kr/kcocktail/search/api/SearchIndexSync.kt
  - apps/api/src/main/resources/db/migration/V017__*.sql
---

> **소유 경로 주의**: `SearchIndexSync` 를 `search.api` 로 공개한다.
>
> ⚠️ **의존 방향 — 이 이슈가 구독한다.** `ingredient`(008)·`cocktail`(013·014)이 `search`를 호출하면 **순환이 생긴다**(008 → 017 → 014 → 013 → 010 → 008).
> **SPEC-05 §3**: "부수효과(알림·집계·검증 태스크 생성)는 **도메인 이벤트로 발행하고 리스너가 처리**한다."
> → 각 도메인은 **이벤트만 발행**하고 이 이슈가 **리스너를 등록**한다. `SearchIndexSync`는 리스너가 쓰는 내부 계약이지 타 모듈이 호출하는 표면이 아니다.

## 근거

**`FR-SEARCH-006`**: 한글·영문 양방향 검색. **`올드패션드` / `올드 패션드` / `Old Fashioned` / `올패`가 모두 매칭**되도록 별칭 테이블
**`FR-SEARCH-007`**: **초성 검색**. `ㅁㄹㄴ` → 마르가리타

**SPEC-05 §6 검색**

> `R-F2.1-3`(한/영 별칭) · `R-F2.1-4`(초성)가 요구사항이다. **DB `LIKE`로는 초성이 안 된다** (G-13).
>
> Phase 1 방식:
> - 칵테일·바·재료에 **검색 색인 컬럼**을 둔다 — 한글명 · 영문명 · 별칭 · **초성 분해 문자열**
> - **저장 시점에 초성을 분해**해 컬럼에 넣는다. 조회는 `LIKE` 프리픽스 매칭 + GIN 인덱스
> - 별칭은 `aliases[]`로 관리하고 어드민에서 편집한다
>
> **Postgres만으로 처리한다.** 별도 검색엔진은 코퍼스가 **수천 건을 넘을 때** 검토한다 — 그 전에 도입하면 운영 부담만 늘어난다.

**SPEC-06 §3.8 `search_document`** — `R-F5-1`(타입별 그룹핑) · `R-F2.1-3` · `R-F2.1-4`를 한 테이블로 받는다

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `entity_type` | `VARCHAR(12)` | `cocktail`·`bar`·`ingredient`·`article` |
| `entity_id` | `BIGINT` | **PK (`entity_type`, `entity_id`)** |
| `slug` | `VARCHAR(120)` | |
| `name_ko` `name_en` | | |
| `aliases` | `TEXT[]` | |
| **`chosung`** | `TEXT` | 초성 분해 — `마르가리타` → `ㅁㄹㄱㄹㅌ` |
| `weight` | `SMALLINT` | 정렬 가중치 |
| `is_published` | `BOOLEAN` | |

> 각 엔티티 저장 시 동기화한다. **초성은 저장 시점에 분해한다** — 조회 때 계산하면 인덱스가 안 붙는다.

**SPEC-06 §5 인덱스**
- `search_document(chosung)` — **GIN + `pg_trgm`** (초성 프리픽스 매칭)
- `search_document(aliases)` — **GIN** (별칭 배열 검색)

**SPEC-07 §3.4**: 발행 부수효과 ② `search_document` 동기화 (초성 분해 포함)

**SPEC-06 §7 미정**: 검색 **가중치(`weight`) 산정식** — G-13

## RED

### 초성 분해 (`FR-SEARCH-007`, `R-F2.1-4`) — 순수 함수, 전수

1. `마르가리타가_ㅁㄹㄱㄹㅌ로_분해된다`
2. `올드패션드가_ㅇㄷㅍㅅㄷ로_분해된다`
3. `초성이_없는_문자는_그대로_둔다` — 영문·숫자
4. `공백이_제거된다` — `올드 패션드` → `ㅇㄷㅍㅅㄷ` (RED 12와 정합)
5. `복합_초성이_정확하다` — `ㄲ`·`ㄸ`·`ㅃ`·`ㅆ`·`ㅉ`
6. `받침이_초성에_영향을_주지_않는다` — `강` → `ㄱ`
7. `한글이_아닌_문자열은_빈_결과가_아니다` — `Negroni` → 원문 유지 또는 빈 문자열 **결정**
8. `이모지_특수문자가_안전하게_처리된다`
9. `유니코드_정규화가_적용된다` — NFC/NFD 자모 분리 입력

### 동기화 (SPEC-06 §3.8)

10. `칵테일_저장시_search_document가_동기화된다`
11. `발행시_is_published가_true가_된다`
12. `회수시_is_published가_false가_된다`
13. `이름_변경시_색인이_갱신된다`
14. `별칭_변경시_색인이_갱신된다` — 이슈 012 RED 16 연계
15. `재료_저장시에도_동기화된다` — `entity_type='ingredient'`
16. `PK가_entity_type과_entity_id_복합이다`
17. `동기화가_멱등하다` — 두 번 호출해도 1행 (UPSERT)
18. `동기화_실패가_발행을_롤백시키는가` **결정** — SPEC-07 §3.4는 부수효과로 열거. 재생성 훅과 달리 **색인은 검색 정확성에 직결**된다. **같은 트랜잭션**(실패 시 롤백)

### 별칭 색인 (`FR-SEARCH-006`, `R-F2.1-3`)

19. `aliases가_색인에_복사된다`
20. `name_ko와_name_en도_매칭_대상이다`
21. `이름과_중복되는_별칭이_제거된다` — 이슈 012 RED 14의 처리 지점
22. `띄어쓰기_변형이_별칭으로_저장돼_있으면_매칭된다` — `올드 패션드`
23. `축약형이_매칭된다` — `올패`

### 인덱스 (SPEC-06 §5)

24. `chosung에_GIN_pg_trgm_인덱스가_있다`
25. `aliases에_GIN_인덱스가_있다`
26. `초성_프리픽스_매칭이_인덱스를_탄다` — `EXPLAIN` 단언

### 가중치 (G-13 미정)

27. `weight_기본값이_있다` **결정** — 산정식이 미정(SPEC-06 §7, G-13). **entity_type별 고정값**(cocktail > ingredient 등)으로 시작

### 범위

28. `Phase_1a는_cocktail과_ingredient만_색인한다` — `bar`·`article`은 1b·Phase 2
29. `entity_type_4종이_미리_정의돼_있다` — 나중에 늘리면 클라이언트가 깨진다

## GREEN

### `V017__search_document.sql`

```sql
CREATE TABLE search_document (
  entity_type VARCHAR(12) NOT NULL CHECK (entity_type IN ('cocktail','bar','ingredient','article')),
  entity_id BIGINT NOT NULL,
  slug VARCHAR(120) NOT NULL,
  name_ko VARCHAR(120) NOT NULL,
  name_en VARCHAR(120),
  aliases TEXT[] NOT NULL DEFAULT '{}',
  chosung TEXT NOT NULL DEFAULT '',
  weight SMALLINT NOT NULL DEFAULT 0,
  is_published BOOLEAN NOT NULL DEFAULT false,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (entity_type, entity_id)
);
CREATE INDEX ON search_document USING GIN (chosung gin_trgm_ops);   -- SPEC-06 §5
CREATE INDEX ON search_document USING GIN (aliases);
CREATE INDEX ON search_document (is_published, entity_type);
```

`pg_trgm` 확장은 이슈 002의 `V001`에서 이미 설치됐다.

### `search/index` — 초성 분해는 순수 함수

```kotlin
object Chosung {
    private const val BASE = 0xAC00
    private val INITIALS = charArrayOf(
        'ㄱ','ㄲ','ㄴ','ㄷ','ㄸ','ㄹ','ㅁ','ㅂ','ㅃ','ㅅ',
        'ㅆ','ㅇ','ㅈ','ㅉ','ㅊ','ㅋ','ㅌ','ㅍ','ㅎ'
    )
    fun of(text: String): String = ...    // 공백 제거 + 한글만 초성화
}
```

**DB 없이 전수 테스트 가능하다** — RED 1~9가 전부 단위 테스트. 한글 초성 추출은 경계가 많아(복합 초성·받침·정규화) 이 부분이 이 이슈의 실질적 난이도다.

### 동기화 지점 — **이벤트 구독** (SPEC-05 §3)

```kotlin
// search/internal — 리스너. 타 모듈이 호출하지 않는다
@Component
class SearchIndexListener(private val sync: SearchIndexSync) {
    @EventListener fun on(e: CocktailPublished) { ... }
    @EventListener fun on(e: CocktailUnpublished) { ... }
    @EventListener fun on(e: IngredientSaved) { ... }
}
```

**각 도메인은 이벤트만 발행하고 `search`를 모른다.** SPEC-05 §3의 두 규칙이 동시에 지켜진다:

| 규칙 | 어떻게 |
|---|---|
| `SEARCH ──reads──▶ COCKTAIL · INGREDIENT` | 조회 시 SEARCH가 읽는다 (이슈 024) |
| 부수효과는 **도메인 이벤트 → 리스너** | 색인 갱신을 리스너가 한다 |
| 순환 의존 금지 | `cocktail`·`ingredient` 가 `search` 를 참조하지 않는다 |

**호출 방식(의존 역전)으로 하면 순환이 생긴다** — `008 → 017 → 014 → 013 → 010 → 008`. 이벤트가 그것을 끊는다.

⚠️ **이 이슈가 이벤트를 정의하지 않는다.** `CocktailPublished`·`IngredientSaved`는 발행 측(013·014·008) 소유다. **008·013·014의 GREEN에 이벤트 발행이 없으면 착수 전에 확인**한다.

### 가중치 (RED 27, G-13)

```kotlin
// G-13 미정 — 산정식이 정해지면 교체. 지금은 타입별 고정값
val DEFAULT_WEIGHT = mapOf("cocktail" to 100, "ingredient" to 50, "bar" to 80, "article" to 30)
```

**하지 말 것**:
- 검색 조회 API — 이슈 024
- 자동완성 — 이슈 024
- `bar`·`article` 색인 — Phase 1b·2

## DoD

- [ ] RED 29항 전부 통과
- [ ] `Chosung` 이 **순수 함수**, RED 1~9 단위 테스트 전수
- [ ] GIN + `pg_trgm` 인덱스가 초성 프리픽스에 사용됨 (RED 26 — EXPLAIN)
- [ ] 동기화가 멱등 (RED 17)
- [ ] `SearchIndexSync` 방향이 경계 테스트에 반영 (의존 역전 근거 주석)
- [ ] 미결은 [`DECISIONS.md`](DECISIONS.md) §1 확정분을 따른다 — **이슈에서 판단하지 않는다**
- [ ] 커밋: `feat(search): search_document 동기화·초성 분해 (FR-SEARCH-006·007, SPEC-05 §6)`
