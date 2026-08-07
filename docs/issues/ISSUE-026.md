---
id: ISSUE-026
title: 재료 승인 워크플로
domain: ADMIN
layer: api
wave: 5
status: TODO
depends_on: [ISSUE-008, ISSUE-006]
fr: [FR-ADMIN-007, FR-INGREDIENT-001]
r: []
inv: []
nfr: []
migration: —
owns:
  - apps/api/src/main/kotlin/kr/kcocktail/admin/ingredient/**
---

## 근거

**`FR-ADMIN-007`**: 재료 마스터 신규 추가는 **에디터 승인 단계**를 거친다
**`FR-INGREDIENT-001`**: 재료 마스터를 관리한다. **국내 유통 기준 200~300개로 상한**을 두고, 신규 추가는 **에디터 승인제**

**SPEC-02 §3 INGREDIENT**

> 재료 마스터. **국내 유통 기준 200~300개로 상한을 둔다** — **무한정 늘리면 역검색 UX가 무너진다.** 신규 추가는 에디터 승인제.

**SPEC-06 §3.2**: `ingredient.is_approved BOOLEAN` — 에디터 승인제 (`FR-ADMIN-007`)

**SPEC-07 §2.2**

| 메서드 | 경로 | 권한 | 설명 |
|---|---|---|---|
| `POST` | `/admin/ingredients` | 🔒 `editor` | 생성 — **승인 대기 상태** |
| `POST` | `/admin/ingredients/{id}/approve` | 🔒 **`admin`** | 승인 (`FR-ADMIN-007`) |

**SPEC-08 §2**: **재료 마스터 승인 = `admin`만** (`editor` ―)

> ⚠️ **`FR-ADMIN-007`은 "에디터 승인 단계"라고 하는데 SPEC-07·SPEC-08은 `admin` 권한이다.**
> **SPEC-08 §2 표가 권한의 정본**이고(SPEC-07 §1.3이 "스코프 상세는 SPEC-08"이라고 위임), SPEC-08 §2.2가 권한 분리를 중립성 장치로 규정했다.
> → **`admin` 승인으로 간다.** `FR-ADMIN-007`의 "에디터"는 "에디터가 만들고 관리자가 승인"의 축약으로 읽는다. **GAPS 등재.**

**SPEC-08 §2.2** — 권한을 나누는 이유: **권한 분리 자체가 중립성 장치다**

**`PRIN-D01`**: 재료가 문자열이 아니라 참조인 이유 — 마스터가 오염되면 역검색과 바 연결이 무너진다. **승인제가 그 방어선**이다

## RED

### 생성 (`FR-INGREDIENT-001`)

1. `editor가_재료를_생성할_수_있다`
2. `생성시_is_approved가_false다` — **승인 대기** (SPEC-07 §2.2)
3. `member는_생성_불가`
4. `admin도_생성_가능`

### 승인 (`FR-ADMIN-007`, SPEC-08 §2)

5. `admin만_승인_가능`
6. `editor는_승인_불가` — **SPEC-08 §2 정본** (**결정** FR-ADMIN-007 문구와 차이 — GAPS)
7. `승인시_is_approved가_true가_된다`
8. `이미_승인된_재료_재승인은_409` **결정** — 또는 멱등 200. **409**
9. `승인이_감사에_기록되는가` **결정** — `PRIN-T08`의 4종 열거에 재료 승인이 **없다**. **기록**(마스터 오염 방지가 중요)
10. `승인_취소가_가능한가` **결정** — SPEC에 없다. **제공 안 함**

### 승인 대기 큐

11. `미승인_재료_목록을_조회할_수_있다` — admin·editor
12. `미승인_재료가_공개_API에_없다` (이슈 008 RED 16, 이슈 023 RED 2)
13. `미승인_재료_상세는_공개에서_404` (이슈 023 RED 17)

### 사용 제약

14. `미승인_재료를_draft_레시피에_쓸_수_있다` — [DECISIONS §1.1](DECISIONS.md)
15. `미승인_재료가_있으면_발행이_차단된다` — `GATE-COCKTAIL-04`(마스터 참조)의 연장 **결정**. **차단**
16. `차단_사유가_violations에_담긴다`

### 상한 (`FR-INGREDIENT-001`, SPEC-02 §3)

17. `승인된_재료_수를_조회할_수_있다`
18. `300개_초과시_경고가_반환된다` — 이슈 008 RED 19와 동일 판단(차단 아님)
19. `경고가_승인을_막지_않는다`
20. `상한값이_설정이다` — 하드코딩 금지

### 규약

21. `어드민_경로는_id를_쓴다`
22. `어드민_응답에_캐시_헤더가_없다`

## GREEN

### `admin/ingredient`

```kotlin
@PostMapping("/api/v1/admin/ingredients")
@PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
fun create(...): AdminIngredientDetail    // is_approved = false

@PostMapping("/api/v1/admin/ingredients/{id}/approve")
@PreAuthorize("hasRole('ADMIN')")          // SPEC-08 §2 — editor 불가 (RED 6)
fun approve(@PathVariable id: Long): AdminIngredientDetail
```

**`@PreAuthorize`의 역할이 두 엔드포인트에서 다른 것이 이 이슈의 요점이다.** SPEC-08 §2.2의 권한 분리를 코드로 옮긴 것.

### 문서 충돌 처리 (RED 6)

`FR-ADMIN-007`("에디터 승인") vs SPEC-08 §2(`admin` 승인).

**CONVENTIONS §6**: 스펙 두 곳이 충돌하면 SPEC-00이 최상위, 그 아래끼리면 `GAPS.md`.
여기서는 SPEC-07 §1.3이 "스코프 상세는 SPEC-08"이라 **명시적으로 위임**했으므로 SPEC-08이 이긴다. 그래도 `GAPS.md`에 등재해 SPEC-03 문구를 고칠지 결정받는다.

### 상한 경고 (RED 17~20)

```kotlin
// SPEC-02 §3 — "무한정 늘리면 역검색 UX가 무너진다"
// 차단이 아니라 경고: 상한 근거가 UX이지 데이터 무결성이 아니다
const val INGREDIENT_SOFT_LIMIT = 300     // 설정 주입
```

승인 응답에 `warning` 필드로 내려보내 어드민 UI(이슈 045)가 표시한다.

### 발행 차단 (RED 15·16)

이슈 013의 `PublishGate`에 조건을 추가할지가 판단 지점이다.

**결정** `GATE-COCKTAIL-04`는 "모든 `RecipeIngredient`가 마스터 참조"다. FK가 이미 보장하므로 **승인 여부까지 볼지는 스펙에 없다.**
**게이트에 추가**하되, 이슈 013의 `owns:` 밖이므로 **CONVENTIONS §4에 따라 조율**한다 — 실무적으로는 013이 `PublishGate`에 확장점을 남겨 두는 편이 낫다. GAPS 등재.

**하지 말 것**:
- 재료 승인 UI — 이슈 045
- 재료 조회 API — 이슈 023

## DoD

- [ ] RED 22항 전부 통과
- [ ] **승인은 `admin`만** (RED 5·6 — SPEC-08 §2 정본)
- [ ] 생성 시 `is_approved=false` (RED 2)
- [ ] 상한이 **경고이지 차단이 아님** (RED 19)
- [ ] 미결은 [`DECISIONS.md`](DECISIONS.md) §1 확정분을 따른다 — **이슈에서 판단하지 않는다**
- [ ] 커밋: `feat(admin): 재료 승인 워크플로 (FR-ADMIN-007, FR-INGREDIENT-001, SPEC-08 §2)`
