---
id: ISSUE-014
title: slug 불변 + 상태 전이 + 감사 로그
domain: COCKTAIL
layer: api
wave: 3
status: TODO
depends_on: [ISSUE-013]
fr: [FR-COCKTAIL-014, FR-COCKTAIL-015]
r: []
inv: [INV-COCKTAIL-05]
nfr: [NFR-D-04, NFR-O-05]
migration: V014
owns:
  - apps/api/src/main/kotlin/kr/kcocktail/common/audit/**
  - apps/api/src/main/kotlin/kr/kcocktail/cocktail/lifecycle/**
  - apps/api/src/main/resources/db/migration/V014__*.sql
---

## 근거

**`PRIN-D02` — 슬러그는 불변이다**

> 카테고리 슬러그는 노출되는 순간 URL이고, 바꾸면 **리다이렉트 부채**가 된다.
> 새 슬러그를 추가하는 것은 자유지만 **기존 슬러그를 바꾸거나 재사용하지 않는다.**

**`FR-COCKTAIL-014`**: `slug`는 **최초 발행 이후** 변경할 수 없다. 어드민에서 **입력란을 비활성화**한다
**`INV-COCKTAIL-05`**: `slug`는 발행 후 불변

**`PRIN-T08` — 감사 가능성.** 아래는 누가·언제·무엇을 바꿨는지 이력을 남긴다. **되돌릴 수 있어야 하고, 다툼이 생겼을 때 근거가 돼야 한다.**

- **콘텐츠 발행 상태 전이** (`draft` → `published` → `archived`)
- 제휴 등급 변경 (`partner_tier`) — Phase 1b
- 큐레이션 리스트 발행 · 순위 변경 — Phase 2
- 바 정보 검증 (`hours_verified_at`) — Phase 1b

**SPEC-06 §3.8 `audit_log`**

| 컬럼 | 타입 |
|---|---|
| `entity_type` `entity_id` | |
| `action` | `VARCHAR(32)` — `publish`·`unpublish`·`tier_change`·`rank_change`·`verify` |
| `actor_user_id` | `BIGINT` |
| `before` `after` | `JSONB` |
| `at` | `TIMESTAMPTZ` |

`PRIN-T08`의 4종 대상을 전부 받는다. **SPEC-06 §5**: `audit_log(entity_type, entity_id, at)` B-tree

**SPEC-02 §8.1 상태 전이**

```
   draft ──발행 게이트 통과──▶ published ──▶ archived
     ▲                            │
     └────────────────────────────┘
```

- 전이는 **전부** 감사 로그를 남긴다
- `published → draft` 되돌리기가 가능하나 **이미 색인된 URL은 유지된다** (`PRIN-D02`)

**`NFR-D-04`**: 슬러그 변경 이력 **0건**. `audit_log` 감시 → 발견 시 **즉시 조사**
**`NFR-O-05`**: 감사 로그로 "누가 무엇을 언제" **재구성 가능**
**SPEC-08 §5.3**: 탈퇴해도 `audit_log.actor_user_id`는 **유지** — 콘텐츠 발행 이력은 법적 근거이자 신뢰 기록

## RED

### slug 불변 (`INV-COCKTAIL-05`, `PRIN-D02`)

1. `draft_상태에서는_slug를_바꿀_수_있다` — 아직 노출되지 않았다
2. `최초_발행_후_slug_변경이_거부된다` — 422 + `code=INV-COCKTAIL-05`
3. `published에서_draft로_되돌린_뒤에도_slug를_못_바꾼다` — "**최초 발행 이후**"
4. `archived_상태에서도_slug를_못_바꾼다`
5. `slug_변경_시도가_감사에_기록된다` — 거부돼도 시도는 남긴다 ⚖️ (`NFR-D-04` "즉시 조사"의 근거) + GAPS
6. `다른_칵테일이_쓰던_slug를_재사용할_수_없다` (`PRIN-D02` "재사용하지 않는다") — 논리적으로 삭제가 없으니 UNIQUE가 보장하나, **archived 것도 점유 유지**를 명시적으로 검증
7. `slug_변경_마이그레이션이_없다` (SPEC-06 §6) — 마이그레이션 파일 스캔

### 상태 전이 (SPEC-02 §8.1)

8. `전이_매트릭스_전수` — `draft→published`(게이트) · `published→draft` · `published→archived` · `archived→draft` 허용 / 그 외 거부
9. `draft에서_archived로_직행할_수_있는가` ⚖️ — 도식에 없다. **보수적으로 거부** + GAPS
10. `전이가_전부_감사에_기록된다`
11. `되돌려도_색인된_URL은_유지된다` — slug 불변으로 성립 (RED 3)

### 감사 로그 (`PRIN-T08`, SPEC-06 §3.8)

12. `action_5종이_정의돼_있다` — `publish`·`unpublish`·`tier_change`·`rank_change`·`verify`
13. `발행시_action_publish로_기록된다`
14. `회수시_action_unpublish로_기록된다`
15. `before와_after가_JSONB로_기록된다`
16. `actor_user_id가_기록된다`
17. `at이_기록된다`
18. `entity_type과_entity_id가_기록된다`
19. `감사_로그가_수정되지_않는다` ⚖️ — SPEC-06 §4.1의 `REVOKE DELETE` 목록에 `audit_log`는 **없다**. `PRIN-T08`의 취지상 append-only가 맞다. **보수적으로 UPDATE·DELETE 권한 회수** + GAPS 등재
20. `감사_조회_인덱스가_있다` — `(entity_type, entity_id, at)`
21. `탈퇴한_사용자의_actor_user_id가_유지된다` (SPEC-08 §5.3)
22. `Phase_1b_2용_action_값이_미리_정의돼_있다` — `tier_change`·`rank_change`·`verify`. 나중에 enum을 늘리면 클라이언트가 깨진다

### 재구성 (`NFR-O-05`)

23. `감사_로그로_특정_칵테일의_전체_이력을_시간순_조회할_수_있다`
24. `누가_언제_무엇을_바꿨는지_재구성된다` — before/after diff

### 트랜잭션

25. `감사_기록이_상태_전이와_같은_트랜잭션이다` — 전이가 롤백되면 감사도 없다
26. `감사_기록_실패가_전이를_막는다` — 감사 없는 발행은 없다 (`PRIN-T08`)

## GREEN

### `V014__audit_log.sql`

```sql
CREATE TABLE audit_log (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  entity_type VARCHAR(24) NOT NULL,
  entity_id BIGINT NOT NULL,
  action VARCHAR(32) NOT NULL CHECK (action IN
    ('publish','unpublish','archive','restore','tier_change','rank_change','verify','slug_change_attempt')),
  actor_user_id BIGINT REFERENCES "user",     -- 탈퇴해도 유지 (ON DELETE 없음)
  before JSONB, after JSONB,
  at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON audit_log (entity_type, entity_id, at);   -- SPEC-06 §5

REVOKE UPDATE, DELETE ON audit_log FROM kcocktail_app;    -- RED 19 (⚖️ 문서 이탈 — GAPS)
```

`action` CHECK에 SPEC-06 §3.8의 5종 + 전이에 필요한 `archive`·`restore` + `slug_change_attempt`(RED 5)를 넣는다. **SPEC-06 표를 넘어서는 값이므로 GAPS에 근거를 남긴다.**

`actor_user_id`에 `ON DELETE SET NULL`을 걸지 **않는다** — SPEC-08 §5.3이 "유지"라고 명시했다. 대신 사용자 삭제 시 FK 위반이 나므로, **탈퇴는 `user` 행 삭제가 아니라 별도 처리**가 필요하다.

> ⚠️ SPEC-08 §5.3은 "`user` 행 즉시 삭제"와 "`audit_log.actor_user_id` 유지"를 동시에 요구한다. **FK가 있으면 둘 다 못 한다.** 해법: `audit_log.actor_user_id`에 FK를 걸지 않는다(느슨한 참조). SPEC-06 §3.8도 FK를 명시하지 않았다. → **FK 없이** 간다.

### `common/audit`

```kotlin
enum class AuditAction { PUBLISH, UNPUBLISH, ARCHIVE, RESTORE,
                         TIER_CHANGE, RANK_CHANGE, VERIFY, SLUG_CHANGE_ATTEMPT }

interface AuditRecorder {          // common — 전 모듈이 쓴다
    fun record(entityType: String, entityId: Long, action: AuditAction,
               before: Any?, after: Any?)
}
```

`common`이 소유하되 도메인 모듈을 참조하지 않는다 (경계 테스트 RED 4).

### `cocktail/lifecycle`

```kotlin
object CocktailTransition {         // SPEC-02 §8.1 — 선언적
    private val ALLOWED = mapOf(
        DRAFT     to setOf(PUBLISHED),
        PUBLISHED to setOf(DRAFT, ARCHIVED),
        ARCHIVED  to setOf(DRAFT),
    )
}
```

`draft → archived` 는 없다 (RED 9, 보수적).

### slug 잠금 (RED 2·3)

`published_at IS NOT NULL` 이면 잠긴다. **`status`가 아니라 `published_at`을 기준으로** — 회수해도 잠겨 있어야 한다 (RED 3).

**하지 말 것**:
- 감사 조회 API — 이슈 029
- 재생성 훅 — 이슈 015
- 어드민 UI의 slug 입력란 비활성화 — 이슈 045 (서버가 이미 막으므로 UX)

## DoD

- [ ] RED 26항 전부 통과
- [ ] slug 잠금 기준이 `published_at` (RED 3 — "최초 발행 이후")
- [ ] `audit_log.actor_user_id`에 **FK 없음** (SPEC-08 §5.3 충돌 해소 — 근거 주석)
- [ ] `audit_log` UPDATE/DELETE 권한 회수 (RED 19)
- [ ] 전이가 감사와 **같은 트랜잭션** (RED 25·26)
- [ ] ⚖️ 4건(변경 시도 기록·`draft→archived`·audit 불변·action 확장) `GAPS.md` 등재
- [ ] 커밋: `feat(cocktail): slug 불변·상태 전이·감사 로그 (FR-COCKTAIL-014·015, PRIN-D02·T08)`
