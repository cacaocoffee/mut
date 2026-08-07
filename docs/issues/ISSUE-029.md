---
id: ISSUE-029
title: 감사 로그 조회
domain: ADMIN
layer: api
wave: 5
status: TODO
depends_on: [ISSUE-014, ISSUE-006]
fr: [FR-ADMIN-005]
r: []
inv: []
nfr: [NFR-O-05, NFR-D-04]
migration: —
owns:
  - apps/api/src/main/kotlin/kr/kcocktail/admin/audit/**
---

## 근거

**`FR-ADMIN-005`**: **콘텐츠 발행 · 제휴 등급 변경 · 큐레이션 순위 변경**의 감사 로그를 조회한다 (`PRIN-T08`)

**`PRIN-T08` — 감사 가능성.** 아래는 누가·언제·무엇을 바꿨는지 이력을 남긴다. **되돌릴 수 있어야 하고, 다툼이 생겼을 때 근거가 돼야 한다.**

- 콘텐츠 발행 상태 전이 (`draft` → `published` → `archived`)
- 제휴 등급 변경 (`partner_tier`) — **Phase 1b**
- 큐레이션 리스트 발행 · 순위 변경 — **Phase 2**
- 바 정보 검증 (`hours_verified_at`) — **Phase 1b**

**SPEC-07 §2.7**: `GET /admin/audit-logs` — 🔒 **`admin`**

**SPEC-08 §2**: 감사 로그 조회 = **`admin`만**. `editor` ―

> **SPEC-08 §2.2**: `editor`는 큐레이션 리스트를 만드는 사람이고 `partner_tier`는 매출과 직결된다. 한 사람이 둘 다 쥐면 `R-F3.3-3`의 감시자가 사라진다. **권한 분리 자체가 중립성 장치다.**
>
> → 감사 조회를 `editor`에게 주지 않는 것도 같은 이유다. **감시받는 사람이 감시 기록을 보면 안 된다.**

**SPEC-06 §3.8 `audit_log`** — 이슈 014가 만든 테이블
**SPEC-06 §5**: `audit_log(entity_type, entity_id, at)` B-tree — 이력 조회 경로

**SPEC-08 §5.3**: 탈퇴해도 `audit_log.actor_user_id`는 **유지** — 콘텐츠 발행 이력은 **법적 근거이자 신뢰 기록**. 이 예외는 개인정보 처리방침에 명시

**`NFR-O-05`**: 감사 로그로 **"누가 무엇을 언제" 재구성 가능** (수동 측정)
**`NFR-D-04`**: 슬러그 변경 이력 **0건** — `audit_log` 감시 → 발견 시 **즉시 조사**

## RED

### 권한 (SPEC-08 §2)

1. `admin만_조회_가능`
2. `editor는_403` — **감시받는 사람이 감시 기록을 보면 안 된다** (SPEC-08 §2.2)
3. `member는_403`
4. `partner_owner는_403`
5. `비로그인은_401`

### 조회 (`FR-ADMIN-005`)

6. `entity_type_필터가_동작한다`
7. `entity_id_필터가_동작한다`
8. `action_필터가_동작한다`
9. `actor_user_id_필터가_동작한다`
10. `기간_필터가_동작한다` — `from`·`to`
11. `필터_조합이_AND다`
12. `최신순_정렬이_기본이다`
13. `페이징된다`
14. `인덱스를_탄다` — `(entity_type, entity_id, at)` EXPLAIN

### 재구성 (`NFR-O-05`)

15. `특정_칵테일의_전체_이력을_시간순_조회한다`
16. `before와_after가_반환된다`
17. `누가_언제_무엇을_바꿨는지_재구성된다` — 발행 → 회수 → 재발행 시나리오
18. `actor_user_id로_행위자를_식별할_수_있다`
19. `탈퇴한_행위자도_id가_남아_있다` (SPEC-08 §5.3)
20. `탈퇴한_행위자의_표시가_구분된다` **결정** — 이름을 못 가져온다. **"탈퇴한 사용자" 표기**

### 감사 대상 (`PRIN-T08` 4종)

21. `발행_전이가_조회된다` — Phase 1a에 실재
22. `Phase_1b_2용_action이_필터에_미리_있다` — `tier_change`·`rank_change`·`verify`
23. `해당_action이_없어도_에러가_아니다` — 빈 결과

### slug 감시 (`NFR-D-04`)

24. `slug_변경_시도를_조회할_수_있다` — 이슈 014 RED 5의 `slug_change_attempt`
25. `slug_변경_이력이_0건임을_확인할_수_있다` — `NFR-D-04` 측정 수단

### 불변 (이슈 014 RED 19)

26. `감사_로그_수정_엔드포인트가_없다`
27. `감사_로그_삭제_엔드포인트가_없다`
28. `DB에서도_UPDATE_DELETE가_거부된다` — 이슈 014에서 회수한 권한 재확인

### 개인정보 (SPEC-08 §5.4 정신)

29. `응답에_불필요한_개인정보가_없다` **결정** — `before`/`after` JSONB에 뭐가 담기나. 콘텐츠 필드라 개인정보는 아니지만 **에디터 이름 정도**는 필요. **행위자 표시명만**

### 규약

30. `캐시_헤더가_없다`
31. `어드민_경로라_id를_쓴다`

## GREEN

### `admin/audit`

```kotlin
@GetMapping("/api/v1/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")          // editor 불가 — SPEC-08 §2·§2.2 (RED 2)
fun list(
    @RequestParam entityType: String?,
    @RequestParam entityId: Long?,
    @RequestParam action: String?,
    @RequestParam actorUserId: Long?,
    @RequestParam from: Instant?,
    @RequestParam to: Instant?,
    pageable: Pageable,
): PageResponse<AuditLogItem>
```

**`@PreAuthorize("hasRole('ADMIN')")`가 이 이슈의 핵심 한 줄이다.** `hasAnyRole('EDITOR','ADMIN')`으로 쓰면 SPEC-08 §2.2의 중립성 장치가 무너진다. 주석에 근거를 남긴다.

### 행위자 표시 (RED 18~20)

```kotlin
data class AuditLogItem(
    val id: Long,
    val entityType: String, val entityId: Long,
    val action: String,
    val actor: ActorRef?,          // null = 시스템 또는 탈퇴
    val before: JsonNode?, val after: JsonNode?,
    val at: Instant,
)
data class ActorRef(val userId: Long, val displayName: String?)   // 탈퇴 시 displayName null
```

`audit_log.actor_user_id`에 **FK가 없다**(이슈 014). 탈퇴한 사용자 ID는 남지만 조인이 안 된다 — `displayName`이 null이면 "탈퇴한 사용자"로 표시한다 (RED 20).

### 인덱스 활용 (RED 14)

`(entity_type, entity_id, at)` 인덱스는 **엔티티별 이력 조회**에 최적이다.
`actor_user_id`·`action` 단독 필터는 이 인덱스를 못 탄다.

**결정** **판단**: Phase 1a 규모(발행 500건 수준)에서는 풀스캔도 견딘다. **인덱스를 추가하지 않는다** — SPEC-06 §5에 없는 인덱스를 임의로 늘리지 않는다. 느려지면 GAPS 등재 후 추가.

### slug 감시 (RED 24·25 — `NFR-D-04`)

```
GET /admin/audit-logs?action=slug_change_attempt
```

`NFR-D-04`의 "0건" 측정이 이 쿼리 하나로 된다. **`NFR-D-04`는 배포 차단이 아니라 "즉시 조사"** 이므로 이슈 016의 배치가 이 쿼리를 돌려 태스크를 만든다 (이슈 016 RED 20).

**하지 말 것**:
- 감사 로그 UI — 이슈 045
- 감사 기록 — 이슈 014
- 추가 인덱스 — SPEC-06 §5 범위 밖

## DoD

- [ ] RED 31항 전부 통과
- [ ] **`admin`만 조회** (RED 1·2 — SPEC-08 §2.2 중립성 장치, 근거 주석)
- [ ] 탈퇴 행위자 처리 (RED 19·20 — SPEC-08 §5.3)
- [ ] 수정·삭제 엔드포인트 부재 (RED 26·27)
- [ ] `NFR-D-04` 측정 쿼리 동작 (RED 24·25)
- [ ] 미결은 [`DECISIONS.md`](DECISIONS.md) §1 확정분을 따른다 — **이슈에서 판단하지 않는다**
- [ ] 커밋: `feat(admin): 감사 로그 조회 (FR-ADMIN-005, PRIN-T08, SPEC-08 §2.2)`
