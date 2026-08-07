---
id: ISSUE-012
title: 당도 · 별칭
domain: COCKTAIL
layer: api
wave: 2
status: TODO
depends_on: [ISSUE-009]
fr: [FR-COCKTAIL-007, FR-COCKTAIL-009]
r: [R-F1.1-5, R-F2.1-3]
inv: []
nfr: []
migration: V012
owns:
  - apps/api/src/main/kotlin/kr/kcocktail/cocktail/taxonomy/**
  - apps/api/src/main/resources/db/migration/V012__*.sql
---

## 근거

**`FR-COCKTAIL-007`**: 당도는 에디터가 **4단계**(`dry`·`semi_dry`·`semi_sweet`·`sweet`)로 **수동 입력**한다. **자동 계산하지 않는다**

**SPEC-02 §2.5 당도**

> `R-F1.1-5`. **에디터 수동 입력이다.** 시럽·리큐르·시트러스의 상호작용 때문에 **자동 계산은 신뢰할 수 없다.**
> 4단계 — `dry` · `semi_dry` · `semi_sweet` · `sweet` (ADR-0002)

**`FR-COCKTAIL-009`**: 별칭(`aliases[]`)을 등록한다. **`올패` 같은 축약형 포함**

**`R-F2.1-3`** (`FR-SEARCH-006`): 한글·영문 양방향 검색. `올드패션드` / `올드 패션드` / `Old Fashioned` / `올패`가 모두 매칭

**SPEC-06 §1.4**: 배열은 조인 테이블로 가되 **예외는 `aliases[]`다** — 검색 전용이고 무결성 대상이 아니라 `TEXT[]` + GIN

**SPEC-06 §5**: `search_document(aliases)` GIN — 별칭 배열 검색 (동기화는 이슈 017)

### 프로토타입과의 차이

`packages/domain/src/types.ts`는 당도를 **숫자**로 둔다:

```ts
/** 0 드라이 · 1 세미 드라이 · 2 세미 스위트 · 3 스위트 */
export type SweetLevel = 0 | 1 | 2 | 3;
```

SPEC-06 §3.1은 `sweetness VARCHAR(12) CHECK ('dry','semi_dry','semi_sweet','sweet')` — **문자열**이다.
**Kotlin enum이 정본**(`PRIN-T02`)이므로 문자열로 간다. 이슈 037의 전환에서 프론트가 숫자 → 문자열로 바뀐다. 정렬이 필요하면 enum에 `ordinal`을 노출한다.

## RED

### 당도 (`FR-COCKTAIL-007`, ADR-0002)

1. `당도_4종만_허용` — `dry`·`semi_dry`·`semi_sweet`·`sweet`
2. `당도는_NOT_NULL이다`
3. `당도를_자동_계산하지_않는다` — 계산 함수·추론 경로 **부재** 단언 (SPEC-02 §2.5)
4. `재료를_바꿔도_당도가_변하지_않는다` — 수동 입력값 유지
5. `당도에_순서가_있다` — `dry < semi_dry < semi_sweet < sweet`. 필터 정렬용
6. `숫자_당도를_받지_않는다` — 프로토타입의 `0|1|2|3` 형태 거부. 전환 시 혼동 방지

### 별칭 (`FR-COCKTAIL-009`, `R-F2.1-3`)

7. `aliases가_TEXT_배열이다`
8. `aliases_기본값은_빈_배열이다`
9. `축약형을_등록할_수_있다` — `올패`
10. `띄어쓰기_변형을_등록할_수_있다` — `올드 패션드`
11. `영문명을_별칭으로_등록할_수_있다`
12. `중복_별칭은_제거된다`
13. `공백_문자열_별칭은_거부된다`
14. `별칭이_name_ko_name_en과_같으면_중복이므로_제외된다` **결정** — 검색은 이름도 본다. **저장은 허용하되 검색 색인에서 중복 제거**(이슈 017)
15. `aliases에_GIN_인덱스가_있다`
16. `별칭_변경이_search_document_동기화를_트리거한다` — 이슈 017. 여기서는 **이벤트 발행**까지

### 정합

17. `당도_변경이_감사_대상인가` **결정** — `PRIN-T08`은 **발행 상태 전이·제휴 등급·큐레이션 순위·바 검증** 4종만 감사 대상으로 열거한다. 당도는 포함 안 됨. **감사하지 않음**

## GREEN

### `V012__taxonomy.sql`

`sweetness`·`aliases` 컬럼은 이슈 009의 `V009`에 이미 있다. 이 마이그레이션은 **인덱스와 제약**만:

```sql
CREATE INDEX ON cocktail USING GIN (aliases);
-- 별칭 요소의 공백 방지 (RED 13)
ALTER TABLE cocktail ADD CONSTRAINT ck_aliases_nonblank
  CHECK (NOT EXISTS (SELECT 1 FROM unnest(aliases) a WHERE length(trim(a)) = 0));
```

> Postgres CHECK에 서브쿼리를 쓸 수 없다. 배열 검사는 `array_position(aliases, '') IS NULL` 같은 표현이나 **앱 검증 + 배치 확인**으로 간다 (SPEC-06 §4.3 패턴).

### `cocktail/taxonomy`

```kotlin
enum class Sweetness(val slug: String, val labelKo: String) {
    DRY("dry", "드라이"),
    SEMI_DRY("semi_dry", "세미 드라이"),
    SEMI_SWEET("semi_sweet", "세미 스위트"),
    SWEET("sweet", "스위트");
    // ordinal 이 곧 정렬 순서 (RED 5)
}
```

**계산 함수를 만들지 않는다** (RED 3). SPEC-02 §2.5가 "자동 계산은 신뢰할 수 없다"고 명시했다. 나중에 "참고값이라도 보여주자"는 요구가 오면 GAPS + ADR을 거친다.

### 별칭 정규화

```kotlin
object AliasNormalizer {
    fun normalize(raw: List<String>): List<String> =
        raw.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}
```

**띄어쓰기 제거·초성 분해는 여기서 하지 않는다** — 검색 색인(이슈 017)의 일이다. 여기서는 에디터가 입력한 문자열을 그대로 보존한다.

**하지 말 것**:
- 초성 분해 — 이슈 017
- 검색 매칭 — 이슈 024
- `search_document` 동기화 — 이슈 017 (이벤트 발행만)

## DoD

- [ ] RED 17항 전부 통과
- [ ] 당도 **자동 계산 함수 부재** (RED 3 — SPEC-02 §2.5)
- [ ] 당도가 문자열 enum이고 정렬 순서를 가짐 (RED 5·6)
- [ ] 별칭 GIN 인덱스 (RED 15)
- [ ] 미결은 [`DECISIONS.md`](DECISIONS.md) §1 확정분을 따른다 — **이슈에서 판단하지 않는다**
- [ ] 커밋: `feat(cocktail): 당도 수동입력·별칭 (FR-COCKTAIL-007·009, R-F1.1-5·R-F2.1-3)`
