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
| 시그니처 메뉴 편집 (`source=editor`) | — | — | ○ | — | ○ |
| 전체 메뉴판 편집 (`source=partner`) | — | — | — | ★ | ○ |
| 바 정보 직접 수정 | — | — | ○ | ★ (`verified`+) | ○ |
| 바 정보 **수정 요청** | — | — | — | ★ (`listed`) | — |
| 파트너 통계 조회 | — | — | — | ★ | ○ |
| **제휴 등급 변경** | — | — | **—** | — | ○ |
| 재료 마스터 승인 | — | — | — | — | ○ |
| 검증 태스크 처리 | — | — | ○ | — | ○ |
| 감사 로그 조회 | — | — | — | — | ○ |
| 북마크 · 컬렉션 | — | ◐ | ◐ | ◐ | ◐ |
| 내 술장 | — | ◐ | ◐ | ◐ | ◐ |
| **노출 규칙 변경** | — | — | — | — | **—** |

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

1. `권한매트릭스_전수_검증` — 위 표 16행 × 5열 = **80조합 파라미터라이즈드**. 표를 테스트 데이터로 옮기고 허용/거부가 정확히 일치
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

11. `editor는_제휴등급을_변경할_수_없다` — **중립성 장치**
12. `editor는_재료_마스터를_승인할_수_없다`
13. `editor는_감사로그를_조회할_수_없다`
14. `admin은_발행도_할_수_있다` — 매트릭스상 `○`

### 노출 규칙 (SPEC-08 §2.1, `PRIN-P02`)

15. `노출규칙_변경_엔드포인트가_존재하지_않는다` — admin 포함 **누구도** 못 바꾼다. 라우트 스캔으로 부재 확인
16. `권한_enum에_노출규칙_관련_액션이_없다`

### IDOR 구조 (SPEC-08 §3.2 — Phase 1b 대비)

17. `소유_검증_실패는_404를_반환한다` (`NFR-SEC-03`) — `bar_owner` 테이블은 1b지만 **판정 인터페이스는 지금** 정의
18. `403을_반환하지_않는다` — 존재 노출 방지

> RED 17·18은 `bar` 도메인이 없어 실동작 테스트가 불가하다. **`OwnershipGuard` 인터페이스와 404 변환 규칙만 구현**하고 실제 검증은 `@Disabled` + Phase 1b 주석.

### 본인 것 (`◐`)

19. `member는_자기_북마크만_조회한다` — 북마크는 이슈 031, 여기서는 스코프 판정기만
20. `타인의_북마크_접근은_404다`

## GREEN

### `common/security/authz`

```kotlin
enum class Role { MEMBER, EDITOR, PARTNER_OWNER, ADMIN }

enum class Action {                       // SPEC-08 §2 표의 각 행
    VIEW_PUBLISHED, VIEW_DRAFT,
    WRITE_CONTENT, PUBLISH,
    EDIT_SIGNATURE_MENU, EDIT_FULL_MENU,
    EDIT_BAR_INFO, REQUEST_BAR_EDIT,
    VIEW_PARTNER_STATS, CHANGE_TIER,
    APPROVE_INGREDIENT, RESOLVE_TASK, VIEW_AUDIT_LOG,
    OWN_BOOKMARK, OWN_STOCK
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

- [ ] RED 20항 통과 (17·18은 인터페이스 수준)
- [ ] `PermissionMatrix` 가 SPEC-08 §2 표와 1:1, **80조합 전수 테스트** (RED 1)
- [ ] 노출 규칙 액션이 enum에 **부재** (RED 16 — `PRIN-P02`)
- [ ] 403/404 구분 규칙이 코드로 표현됨
- [ ] 커밋: `feat(user): 권한 매트릭스 4역할 (SPEC-08 §2, PRIN-P02)`
