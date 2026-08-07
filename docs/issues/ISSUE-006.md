---
id: ISSUE-006
title: 권한 매트릭스 4역할
domain: USER
layer: api
wave: 1
status: TODO
depends_on: [ISSUE-005]
fr: []
r: []
inv: []
nfr: [NFR-SEC-03]
migration: —
owns:
  - apps/api/src/main/kotlin/kr/kcocktail/common/security/authz/**
---

## 근거

**SPEC-08 §1 역할** — 네 개다. **더 늘리지 않는다** — 역할이 늘면 조합이 폭발하고 아무도 전체를 이해하지 못한다.

| 역할 | 무엇을 하나 |
|---|---|
| `member` | 저장 · 컬렉션 · 내 술장 |
| `editor` | 콘텐츠 작성 · 발행 · 검증 태스크 처리 |
| `partner_owner` | **자기 바에 한정해** 정보 관리 · 통계 조회 |
| `admin` | 제휴 등급 · 재료 승인 · 감사 로그 |

**역할은 누적되지 않는다.** `editor`가 `admin` 권한을 갖지 않는다.

### SPEC-08 §2 권한 매트릭스 (정본 — 이 표가 테스트 데이터다)

`—` 불가 · `○` 가능 · `◐` 자기 것만 · `★` 자기 바만

| 대상 · 액션 | 비로그인 | `member` | `editor` | `partner_owner` | `admin` |
|---|---|---|---|---|---|
| 발행된 콘텐츠 조회 | ○ | ○ | ○ | ○ | ○ |
| `draft` 콘텐츠 조회 | — | — | ○ | — | ○ |
| 칵테일 · 바 · 재료 생성/수정 | — | — | ○ | — | ○ |
| **발행 / 회수** | — | — | ○ | — | ○ |
| ~~시그니처 메뉴 편집~~ | — | — | ○ | — | ○ |
| ~~전체 메뉴판 편집~~ | — | — | — | ★ | ○ |
| ~~바 정보 직접 수정~~ | — | — | ○ | ★ | ○ |
| ~~바 정보 수정 요청~~ | — | — | — | ★ | — |
| ~~파트너 통계 조회~~ | — | — | — | ★ | ○ |
| ~~제휴 등급 변경~~ | — | — | **—** | — | ○ |
| 재료 마스터 승인 | — | — | — | — | ○ |
| 검증 태스크 처리 | — | — | ○ | — | ○ |
| 감사 로그 조회 | — | — | — | — | ○ |
| 북마크 · 컬렉션 | — | ◐ | ◐ | ◐ | ◐ |
| ~~내 술장~~ | — | ◐ | ◐ | ◐ | ◐ |
| **노출 규칙 변경** | — | — | — | — | **—** |

### ⚠️ Phase 1a 범위 축소 (사용자 결정, 2026-08-07)

**취소선 6행과 `partner_owner` 열 전체는 Phase 1b로 미룬다.** 근거:

- **바 도메인이 없다.** 시그니처 메뉴·전체 메뉴판·바 정보·파트너 통계·제휴 등급은 전부 `bar`·`partner_contract` 테이블을 전제한다 (SPEC-01 §4.2 — 1b)
- **`partner_owner`가 소유할 대상이 없다.** SPEC-08 §3의 `bar_owner` 스코프·IDOR 방어는 1b에 실물이 생긴 뒤라야 검증된다
- **`내 술장`은 Phase 2** (`FR-STOCK-*`, SPEC-03 §8.1)
- SPEC-08 §9: `admin` 계정이 **2~3개 규모**라 2단계 인증도 후순위. 에디터는 사용자 본인과 주변인

**1a에서 구현하는 것**: 3역할(비로그인 · `member` · `editor` · `admin`) × 9행 = **약 30조합**

`member`는 남는다 — **`FR-USER-001`(소셜 로그인)·`FR-USER-004`(저장·컬렉션)가 Phase 1a P0**이기 때문이다 (SPEC-01 §4.1 USER 6건).

**enum·타입에는 `partner_owner`를 정의해 둔다** (SPEC-08 §1의 4역할). 나중에 늘리면 클라이언트가 깨진다.

**SPEC-08 §2.1 — 마지막 줄이 요점이다**

> **노출 규칙(부스팅 한도 · 홈 슬롯 비율)은 `admin`도 못 바꾼다.** API 표면에도 DB 컬럼에도 존재하지 않는다 (`PRIN-P02` · `FR-ADMIN-006`).
> 바꾸려면 코드를 고치고 배포해야 한다. **그게 의도다** — 영업 압박이 들어오는 순간 "어드민에서 잠깐만"이 가능하면 반드시 그렇게 된다.

**SPEC-08 §2.2 — 등급 변경을 `editor`에게 주지 않는 이유**

> `editor`는 큐레이션 리스트를 만드는 사람이고 `partner_tier`는 매출과 직결된다. 한 사람이 둘 다 쥐면 `R-F3.3-3`의 감시자가 사라진다. **권한 분리 자체가 중립성 장치다.**

**SPEC-08 §3.2 IDOR 방어** (Phase 1b `partner_owner`용 — 구조는 지금)

> 경로의 `barId`를 신뢰하지 않는다. `bar_owner`에 `(userId, barId)` 행이 없으면 **`403`이 아니라 `404`** — `403`이면 "그 바가 존재한다"는 사실이 새어나간다.

**SPEC-07 §1.4**: 비공개 리소스도 `404`. **존재 여부를 흘리지 않는다**

## RED

### 매트릭스 전수 (SPEC-08 §2 — 이 이슈의 요체)

1. `권한매트릭스_1a분_전수_검증` — 취소선 제외 **9행 × 4열(비로그인·member·editor·admin) = 약 30조합** 파라미터라이즈드. 표를 테스트 데이터로 옮기고 허용/거부가 정확히 일치
   - **`partner_owner` 열과 취소선 6행은 1b** — 테스트를 작성해 두고 `@Disabled` + `EPICS-1B-PHASE2.md` 참조 주석
2. `역할이_누적되지_않는다` — `editor`가 `admin` 전용 액션(등급 변경·재료 승인·감사 조회) 시도 → 403
3. `복수역할_보유시_합집합으로_평가된다` — `editor`+`admin` 둘 다 있으면 양쪽 가능
4. `매트릭스에_없는_조합은_기본_거부다` — allowlist 방식
5. `비로그인은_발행된_콘텐츠만_조회한다`

### draft 격리 (SPEC-07 §1.4·§5)

6. `draft_콘텐츠는_비로그인에게_404다` — 403이 아니다
7. `draft_콘텐츠는_member에게_404다`
8. `draft_콘텐츠는_editor에게_보인다`
9. `draft_콘텐츠는_admin에게_보인다`
10. `archived_콘텐츠도_공개_API에서_404다` (SPEC-07 §5)

### editor ≠ admin (SPEC-08 §2.2)

11. `editor는_재료_마스터를_승인할_수_없다`
12. `editor는_감사로그를_조회할_수_없다`
13. `admin은_발행도_할_수_있다` — 매트릭스상 `○`
14. `editor는_제휴등급을_변경할_수_없다` — **중립성 장치** (SPEC-08 §2.2). **1b** — `@Disabled`

### 노출 규칙 (SPEC-08 §2.1, `PRIN-P02`)

15. `노출규칙_변경_엔드포인트가_존재하지_않는다` — admin 포함 **누구도** 못 바꾼다. 라우트 스캔으로 부재 확인
16. `권한_enum에_노출규칙_관련_액션이_없다`

### IDOR — **Phase 1b로 이월** (사용자 결정)

> SPEC-08 §3의 `bar_owner` 스코프·IDOR 방어는 **`bar` 실물이 생긴 뒤**라야 검증된다.
> 이 이슈에서는 **403/404 구분 규칙만** 남기고(RED 6·7·12가 그것을 쓴다), `OwnershipGuard`는 만들지 않는다.
> **`EPICS-1B-PHASE2.md` 1B-E8**이 이어받는다.

17. `403과_404_구분_규칙이_정의돼_있다` — 존재가 비밀이면 404, 액션 권한만 없으면 403

### 본인 것 (`◐`) — `member`가 1a에 남는 이유

> **`FR-USER-001`·`FR-USER-004`가 Phase 1a P0**다 (SPEC-01 §4.1 USER 6건). `partner_owner`와 달리 `member`는 미룰 수 없다.

18. `member는_자기_북마크만_조회한다` — 북마크는 이슈 031, 여기서는 스코프 판정기만
19. `타인의_북마크_접근은_404다`

## GREEN

### `common/security/authz`

```kotlin
enum class Role { MEMBER, EDITOR, PARTNER_OWNER, ADMIN }

enum class Action {                       // SPEC-08 §2 표의 각 행
    // ── Phase 1a ──
    VIEW_PUBLISHED, VIEW_DRAFT,
    WRITE_CONTENT, PUBLISH,
    APPROVE_INGREDIENT, RESOLVE_TASK, VIEW_AUDIT_LOG,
    OWN_BOOKMARK,

    // ── Phase 1b·2 (enum 에는 지금 정의. 매트릭스 평가는 @Disabled) ──
    EDIT_SIGNATURE_MENU, EDIT_FULL_MENU,      // 1b
    EDIT_BAR_INFO, REQUEST_BAR_EDIT,          // 1b
    VIEW_PARTNER_STATS, CHANGE_TIER,          // 1b
    OWN_STOCK,                                // Phase 2
    // 노출 규칙 액션은 존재하지 않는다 — PRIN-P02 (RED 16)
}

object PermissionMatrix {                 // SPEC-08 §2 표 그대로. 선언적 데이터
    fun allows(roles: Set<Role>, action: Action, scope: Scope): Boolean
}
```

**표 하나 = 코드 한 곳.** `if` 문으로 흩뿌리지 않는다 — RED 1이 이것을 강제한다.

### Scope

```kotlin
sealed interface Scope {
    object Any : Scope                    // ○
    data class Own(val ownerId: Long) : Scope     // ◐
    data class OwnBar(val barId: Long) : Scope    // ★ (Phase 1b)
}
```

### 404 변환 (SPEC-07 §1.4, SPEC-08 §3.2)

```kotlin
// 권한 없음이지만 "존재를 흘리면 안 되는" 경우 404로 변환
class NotFoundForUnauthorized : RuntimeException()
```

**어디에 403을 쓰고 어디에 404를 쓰는지가 이 이슈의 판단이다.**
- 리소스의 **존재 자체가 비밀**(draft·타인 소유) → **404**
- 리소스는 공개인데 **액션 권한이 없음**(member가 발행 시도) → **403**

이 구분을 `PermissionMatrix`의 반환 타입으로 표현한다 (`Denied.Hidden` vs `Denied.Forbidden`).

**하지 말 것**: 엔드포인트별 애노테이션 부착 — 각 도메인 이슈가 SPEC-07 §1.3 표기(🔒)를 보고 붙인다.

## DoD

- [ ] RED 19항 통과 (14는 1b라 `@Disabled`)
- [ ] `PermissionMatrix` 가 SPEC-08 §2 표의 **1a분과 1:1, 약 30조합 전수 테스트** (RED 1)
- [ ] **`partner_owner`·바 관련 6행이 `@Disabled` + `EPICS-1B-PHASE2.md` 참조 주석**
- [ ] `Role` enum에 `PARTNER_OWNER` **정의는 있음** (나중에 늘리면 클라이언트가 깨진다)
- [ ] 노출 규칙 액션이 enum에 **부재** (RED 16 — `PRIN-P02`)
- [ ] 403/404 구분 규칙이 코드로 표현됨 (RED 17)
- [ ] 커밋: `feat(user): 권한 매트릭스 1a 범위 (SPEC-08 §2, PRIN-P02)`
