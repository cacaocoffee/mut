---
id: ISSUE-028
title: 검증 태스크 큐
domain: ADMIN
layer: api
wave: 5
status: TODO
depends_on: [ISSUE-025]
fr: [FR-ADMIN-004]
r: [R-F3.1-2]
inv: []
nfr: [NFR-D-01]
migration: —
owns:
  - apps/api/src/main/kotlin/kr/kcocktail/admin/task/**
---

## 근거

**`FR-ADMIN-004`**: 검증 태스크 큐를 제공한다 — **`hours_verified_at` 만료, 인스타 폐업 신호** 등 (`R-F3.1-2`)

**SPEC-07 §2.7**

| 메서드 | 경로 | 권한 |
|---|---|---|
| `GET` | `/admin/tasks` | 🔒 `editor` |
| `POST` | `/admin/tasks/{id}/resolve` | 🔒 `editor` |

**SPEC-08 §2**: 검증 태스크 처리 = `editor` ○, `admin` ○, 그 외 —

**SPEC-05 §8 배치** — 태스크 생성 주체

| 작업 | 주기 | 무엇 |
|---|---|---|
| 바 정보 검증 태스크 생성 | 일 1회 | `hours_verified_at` 90일 경과 (`R-F3.1-2`) — **Phase 1b** |
| 인스타 피드 동기화 | 일 1회 | 폐업 감지 신호 — **Phase 1b** |

**SPEC-06 §4.3**: 앱 강제 불변식은 **배치 검증으로 이중 확인**한다. 일 1회 전수 스캔해 **위반 건을 관리자 태스크로 올린다** → 이슈 016이 생성자

### Phase 1a에 실재하는 태스크 소스

`FR-ADMIN-004`가 예로 든 둘(`hours_verified_at`·인스타)은 **전부 BAR 도메인이라 Phase 1b**다.

**Phase 1a에서 실제로 태스크를 만드는 것은 이슈 016의 불변식 배치 하나**다 (SPEC-06 §4.3).
그것만으로도 큐가 필요하다 — `NFR-D-01`(위반 0건)·`NFR-D-02`(게이트 우회 0건)의 처리 창구다.

**테이블은 이슈 016이 이미 만들었다** (`verification_task`). 이 이슈는 **조회·해소 API**다.

## RED

### 조회 (`FR-ADMIN-004`)

1. `editor가_태스크_목록을_조회한다`
2. `admin도_조회_가능`
3. `member는_403`
4. `비로그인은_401`
5. `open_상태만_기본_조회된다`
6. `status_필터가_동작한다` — `open`·`resolved`·`dismissed`
7. `task_type_필터가_동작한다`
8. `entity_type_필터가_동작한다`
9. `페이징된다`
10. `최근_탐지순_정렬이_기본이다`

### 태스크 내용 (이슈 016 연계)

11. `불변식_위반_태스크가_조회된다` — `task_type='invariant_violation'`
12. `태스크에_INV_또는_GATE_코드가_있다`
13. `태스크에_대상_엔티티가_있다` — `entity_type`·`entity_id`
14. `태스크에_상세_정보가_있다` — `detail` JSONB
15. `태스크에서_대상_엔티티로_이동할_수_있다` — 어드민 경로 구성용 식별자

### 해소 (SPEC-07 §2.7)

16. `태스크를_resolved로_처리한다`
17. `resolved_at과_resolved_by가_기록된다`
18. `dismissed로_처리할_수_있다` ⚖️ — "무시"가 필요한가. **보수적으로 제공하되 사유 필수** + GAPS
19. `이미_해소된_태스크_재해소는_409`
20. `해소가_감사에_기록되는가` ⚖️ — `PRIN-T08` 4종에 없다. **보수적으로 기록 안 함**(태스크 테이블 자체가 이력) + GAPS

### 자동 해소 (이슈 016 RED 24)

21. `위반이_해소되면_다음_배치에서_태스크가_자동_종료된다`
22. `자동_종료된_태스크의_resolved_by가_시스템이다` — null 또는 시스템 표식
23. `수동_해소_후_위반이_남아_있으면_재오픈된다` — 배치 멱등 (이슈 016 RED 22·23)

### Phase 1b 확장 자리

24. `task_type에_1b용_값이_미리_정의돼_있다` — `hours_expired`·`instagram_signal`. 나중에 enum을 늘리면 클라이언트가 깨진다
25. `1b_task_type으로_조회해도_에러가_아니다` — 빈 결과

### 규약

26. `어드민_경로라_id를_쓴다`
27. `캐시_헤더가_없다`

## GREEN

### `admin/task`

```kotlin
@GetMapping("/api/v1/admin/tasks")
@PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
fun list(...): PageResponse<VerificationTaskItem>

@PostMapping("/api/v1/admin/tasks/{id}/resolve")
@PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
fun resolve(@PathVariable id: Long, @RequestBody req: ResolveRequest): VerificationTaskItem
```

테이블(`verification_task`)은 **이슈 016이 소유**한다. 이 이슈는 읽고 쓰기만 — `migration: —`인 이유다.

### task_type enum (RED 24)

```kotlin
enum class TaskType(val slug: String, val phase: String) {
    INVARIANT_VIOLATION("invariant_violation", "1a"),   // 이슈 016
    HOURS_EXPIRED("hours_expired", "1b"),               // R-F3.1-2 · SPEC-05 §8
    INSTAGRAM_SIGNAL("instagram_signal", "1b"),         // SPEC-05 §8
}
```

**1b 값을 지금 정의한다.** `FR-ADMIN-004`가 그 둘을 명시적으로 예로 들었으므로 계약에 포함돼야 한다.

### 대상 이동 (RED 15)

```kotlin
data class VerificationTaskItem(
    val id: Long,
    val taskType: String,
    val entityType: String, val entityId: Long,
    val code: String?,                 // INV-/GATE- ID
    val detail: JsonNode?,
    val adminPath: String,             // "/admin/cocktails/123" — UI가 바로 링크
    val status: String,
    val detectedAt: Instant,
)
```

`adminPath`를 서버가 만들어 준다 — 프론트가 `entityType` → 경로 매핑을 따로 들면 어긋난다.

### 자동 해소 (RED 21~23)

이슈 016의 배치가 담당한다. **이 이슈는 그 결과를 조회할 뿐**이다.
배치가 "위반이 사라진 open 태스크"를 `resolved`로 닫고 `resolved_by`를 시스템으로 둔다.

**하지 말 것**:
- 태스크 생성 — 이슈 016 (불변식) · Phase 1b (바 검증·인스타)
- 검증 태스크 UI — 이슈 045
- `verification_task` 테이블 — 이슈 016

## DoD

- [ ] RED 27항 전부 통과
- [ ] `TaskType` 에 **Phase 1b 값 2종 미리 정의** (RED 24 — `FR-ADMIN-004` 명시 항목)
- [ ] `adminPath` 를 서버가 제공 (RED 15)
- [ ] 자동 해소·재오픈이 배치와 정합 (RED 21~23)
- [ ] ⚖️ 2건(dismissed 제공·해소 감사) `GAPS.md` 등재
- [ ] 커밋: `feat(admin): 검증 태스크 큐 (FR-ADMIN-004, SPEC-06 §4.3)`
