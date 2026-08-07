---
id: ISSUE-013
title: 발행 게이트 6종
domain: COCKTAIL
layer: api
wave: 3
status: TODO
depends_on: [ISSUE-010, ISSUE-011, ISSUE-012]
fr: [FR-COCKTAIL-010, FR-COCKTAIL-011, FR-COCKTAIL-012, FR-COCKTAIL-013]
r: [R-F1.1-2, R-F1.1-3, R-F1.3-2]
inv: [GATE-COCKTAIL-01, GATE-COCKTAIL-02, GATE-COCKTAIL-03, GATE-COCKTAIL-04, GATE-COCKTAIL-05, GATE-COCKTAIL-06]
nfr: [NFR-D-02]
migration: V013
owns:
  - apps/api/src/main/kotlin/kr/kcocktail/cocktail/publish/**
  - apps/api/src/main/kotlin/kr/kcocktail/cocktail/api/PublishGate.kt
  - apps/api/src/main/resources/db/migration/V013__*.sql
---

> **소유 경로 주의**: `cocktail/api/CocktailFacade.kt` 는 ISSUE-009 소유다. **파일 단위로 나눈다.**
>
> **`PublishGate` 가 `cocktail.api` 에 있어야 하는 이유**: ISSUE-016(불변식 배치 검증)이 `admin` 모듈에서 이것을 호출한다. `cocktail/publish/**` 안에만 두면 **모듈 경계 테스트(001)가 016을 막는다.**

## 근거

**`PRIN-P03` — 만들어보지 않은 것은 쓰지 않는다**

> `R-F1.1-2`가 향과 맛을 발행 필수로 잡은 이유다. 다른 사이트 설명을 옮기면 레시피 나열형 블로그와 구별되지 않고, **그 순간 이 서비스의 존재 이유가 사라진다.**
>
> - 향과 맛 서술이 비면 **발행 자체가 불가능**해야 한다. **경고가 아니라 차단이다** (`PRIN-T05`)

**`PRIN-T05` — 불변식은 서버에서 강제한다**

> 프론트 검증은 **UX용 중복**이다. 없어도 데이터가 깨지지 않아야 한다.
> 발행 게이트와 도메인 불변식은 **서버 트랜잭션 안에서** 검사한다.
> 현재 `packages/domain/src/validate.ts`가 하는 일이며 **API 연동 시점에 Kotlin으로 옮긴다.**

### SPEC-02 §2.3 발행 게이트 (정본)

`draft → published` 전이를 막는 조건. **경고가 아니라 차단이다.**

| ID | 조건 | 근거 |
|---|---|---|
| `GATE-COCKTAIL-01` | `tasting_note`가 비면 발행 불가 | `R-F1.1-2` |
| `GATE-COCKTAIL-02` | 분류 3축 불변식 전부 통과 | `R-C-1` |
| `GATE-COCKTAIL-03` | 표준 레시피가 **재료 1개 이상 · 스텝 1개 이상** | — |
| `GATE-COCKTAIL-04` | 모든 `RecipeIngredient`가 마스터 참조 | `R-F1.1-1` |
| `GATE-COCKTAIL-05` | **명예의 전당 · 클래식** 분류면 `story` 필수 | `R-F1.1-3` |
| `GATE-COCKTAIL-06` | **국내 미유통 재료**가 있으면 대체재 명시 | `R-F1.3-2` |

**SPEC-07 §3.4 `POST /admin/cocktails/{id}/publish`**

> `GATE-COCKTAIL-01~06`을 **전부 검사한 뒤** 결과를 한 번에 돌려준다 (`FR-ADMIN-003`).
> 실패 `422`: `violations` 배열. **첫 실패에서 멈추지 않는다.**
> 이미 `published`면 `409`.

부수효과 — **성공 시에만**: ①`audit_log`에 `publish` ②`search_document` 동기화 ③프론트 재생성 호출 ④`slug` 확정

**`NFR-D-02`**: 발행 게이트를 **우회한 `published` 0건**. 일 1회 전수 스캔 → 실패 시 **즉시 회수**

**SPEC-06 §4.3**: 게이트는 전부 **앱 강제**(조건부·다중 테이블 참조라 DB 제약으로 표현 불가)

### 재사용 계약 (INDEX 결합점)

**`PublishGate`를 순수 함수로 분리한다.** 이슈 016(배치 검증)이 같은 함수를 쓴다. 두 벌 구현하면 반드시 어긋나고, `NFR-D-02`가 그 어긋남을 못 잡는다.

## RED

### 게이트 전수 (SPEC-02 §2.3 — 이 이슈의 요체)

1. `GATE_01_tasting_note가_비면_발행_거부` — 422 + `code=GATE-COCKTAIL-01`
2. `GATE_01_공백문자열도_빈_것으로_취급`
3. `GATE_02_3축_불변식_위반시_거부` — `INV-COCKTAIL-01~04` 재확인
4. `GATE_03_표준레시피_재료가_0개면_거부`
5. `GATE_03_표준레시피_스텝이_0개면_거부`
6. `GATE_03_표준레시피가_아예_없으면_거부` (`INV-COCKTAIL-07` 연계)
7. `GATE_04_모든_재료가_마스터_참조여야_한다` — FK가 이미 보장하나 **게이트가 재확인** (SPEC-06 §4.3)
8. `GATE_05_is_classic이면_story가_필수다`
9. `GATE_05_is_classic이_아니면_story가_없어도_된다`
10. `GATE_06_import_only_재료가_있으면_대체재_명시_필수`
11. `GATE_06_unavailable_재료가_있으면_대체재_명시_필수`
12. `GATE_06_common_specialty만_있으면_대체재_불요`
13. `GATE_06_대체재는_substitute_ingredient_id_또는_substitute_note로_충족` ⚖️ — 둘 중 하나면 되는지 보수적 판단 + GAPS
14. `게이트_6종_전부_통과하면_발행된다`

### violations 전부 반환 (`FR-ADMIN-003`, SPEC-07 §3.4)

15. `게이트_2개가_동시에_실패하면_violations가_2건이다` — **첫 실패에서 멈추지 않는다**
16. `게이트_6개_전부_실패하면_violations가_6건이다`
17. `각_violation에_GATE_ID_코드가_있다`
18. `각_violation에_field가_있다` — `tastingNote`·`story` 등
19. `violations_순서가_결정론적이다` — 게이트 번호순. 테스트 안정성

### 상태 전이 (SPEC-02 §8.1)

20. `draft에서_published로_전이한다`
21. `이미_published면_409` (SPEC-07 §3.4)
22. `archived에서_직접_published로_갈_수_있는가` ⚖️ — SPEC-02 §8.1 도식은 `draft ↔ published → archived`. **archived → draft → published** 경로만 허용하는 것이 보수적. + GAPS
23. `published에서_draft로_되돌릴_수_있다` (SPEC-02 §8.1 "되돌리기가 가능")
24. `회수시에는_게이트를_검사하지_않는다`

### 트랜잭션 (`PRIN-T05`)

25. `게이트_검사가_저장_트랜잭션_안에서_수행된다`
26. `게이트_실패시_status가_바뀌지_않는다`
27. `게이트_통과_후_부수효과_실패가_발행을_롤백시키지_않는다` — 재생성 훅 (`NFR-R-03`). 이슈 015 연계
28. `published_at이_기록된다`

### 우회 불가 (`NFR-D-02`, `PRIN-T05`)

29. `status를_직접_published로_UPDATE하는_경로가_없다` — 서비스 계층에 그런 메서드 부재
30. `어드민_PATCH로_status를_바꿀_수_없다` — 발행은 전용 엔드포인트만
31. `프론트_검증만_통과한_요청이_서버에서_막힌다` — `PRIN-T05` "프론트 검증은 UX용 중복"

### 재사용 (INDEX 결합점)

32. `PublishGate가_순수_함수다` — DB 없이 호출 가능
33. `배치_검증이_같은_함수를_쓴다` — 이슈 016이 재사용. 시그니처 고정
34. `PublishGate가_cocktail_api에_공개된다` — `admin` 모듈(016)이 경계 위반 없이 호출

### 도메인 이벤트 (SPEC-05 §3 — 이슈 017이 구독)

35. `발행_성공시_CocktailPublished_이벤트가_발행된다`
36. `이벤트에_색인에_필요한_필드가_담긴다` — slug·nameKo·nameEn·aliases
37. `게이트_실패시_이벤트가_발행되지_않는다`
38. `cocktail_모듈이_search를_참조하지_않는다` — 경계 테스트 (이슈 001)

## GREEN

### `V013__publish.sql`

```sql
-- 발행 이력 조회 최적화
CREATE INDEX ON cocktail (status, published_at DESC);
```

테이블은 만들지 않는다. `audit_log`는 이슈 014.

### `cocktail/publish` — 순수 함수

```kotlin
data class PublishCandidate(          // DB에서 조립한 스냅샷
    val tastingNote: String?,
    val isClassic: Boolean,
    val story: String?,
    val axes: AxisSnapshot,
    val standardRecipe: RecipeSnapshot?,
    val ingredients: List<IngredientSnapshot>,   // domesticAvailability 포함
)

object PublishGate {
    // SPEC-02 §2.3 — 6종을 전부 검사하고 결과를 모아 반환 (FR-ADMIN-003)
    fun check(c: PublishCandidate): List<Violation> = buildList {
        addAll(gate01(c)); addAll(gate02(c)); addAll(gate03(c))
        addAll(gate04(c)); addAll(gate05(c)); addAll(gate06(c))
    }
}
```

**`buildList`로 전부 모으는 것이 요점이다.** `require`·early return을 쓰면 RED 15·16이 깨진다.

각 게이트를 별도 함수로 두면 RED 1~13이 게이트별로 독립 테스트된다.

### `validate.ts`와의 관계 (`PRIN-T05`)

프로토타입의 `packages/domain/src/validate.ts`는 **남겨 둔다** — `PRIN-T05`가 "프론트 쪽은 남겨도 되지만 그때는 보조 수단이지 근거가 아니다"라고 했다. `npm run check`도 계속 돈다 (CONVENTIONS §3.4).

**다만 규칙의 정본은 이제 Kotlin이다.** 두 곳이 어긋나면 Kotlin이 맞다.

### 부수효과 순서 (SPEC-07 §3.4)

```
게이트 통과 → status/published_at 저장 → audit_log(이슈 014)
           → search_document 동기화(이슈 017) → 재생성 훅(이슈 015)
```

앞의 둘은 **같은 트랜잭션**, 뒤의 둘은 **커밋 후**(`NFR-R-03` — 훅 실패가 발행을 롤백시키지 않는다).

이슈 014·015·017이 아직 없으면 **인터페이스만 호출하고 no-op 구현**을 둔다. RED 27이 그 계약을 검증한다.

**하지 말 것**:
- `audit_log` 테이블 — 이슈 014
- 재생성 훅 — 이슈 015
- 배치 검증 — 이슈 016
- 어드민 엔드포인트 — 이슈 025 (여기서는 서비스 계층까지)

## DoD

- [ ] RED 38항 전부 통과
- [ ] **`violations`가 전부 반환** (RED 15·16 — `FR-ADMIN-003`, SPEC-07 §3.4)
- [ ] `PublishGate` 가 **순수 함수**이고 이슈 016이 재사용할 시그니처 (RED 32·33)
- [ ] 게이트 우회 경로 부재 (RED 29·30 — `NFR-D-02`)
- [ ] `validate.ts` 유지, 정본이 Kotlin임을 주석으로 명시
- [ ] ⚖️ 2건(대체재 충족 기준·archived 전이) `GAPS.md` 등재
- [ ] 커밋: `feat(cocktail): 발행 게이트 6종 서버 강제 (FR-COCKTAIL-010~013, GATE-COCKTAIL-01~06, PRIN-P03·T05)`
