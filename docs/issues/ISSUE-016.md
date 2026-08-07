---
id: ISSUE-016
title: 불변식 배치 검증 (npm run check 서버판)
domain: ADMIN
layer: api
wave: 3
status: TODO
depends_on: [ISSUE-013]
fr: []
r: []
inv: [INV-COCKTAIL-02, INV-COCKTAIL-04, INV-INGREDIENT-01, INV-INGREDIENT-02]
nfr: [NFR-D-01, NFR-D-02, NFR-D-04]
migration: V016
owns:
  - apps/api/src/main/kotlin/kr/kcocktail/admin/verification/**
  - apps/api/src/main/resources/db/migration/V016__*.sql
---

## 근거

**SPEC-06 §4.3** — 앱이 강제하는 불변식 목록 끝에:

> **앱 강제 항목은 배치 검증으로 이중 확인한다.** 일 1회 전수 스캔해 위반 건을 관리자 태스크로 올린다 — **현재 `packages/domain/src/validate.ts`가 하는 일의 서버판이다.**

### 앱 강제 항목 (SPEC-06 §4.3 전수)

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
| `INV-INGREDIENT-02` 브랜드 광고성 표기 | 저장은 DB, 라벨 렌더링 강제는 앱 |
| `INV-CONTENT-02` 협찬 라벨 렌더링 강제 | 표현 계층 — **Phase 2** |

**`NFR-D-01`**: 발행분에 불변식 위반 **0건**. 측정 = **`npm run check` 상당의 서버 배치**. 실패 시 **배포 차단**

> `NFR-D-01`은 24종일 때는 눈으로 보이지만 **500종이 되면 이것 말고 확인할 방법이 없다.**

**`NFR-D-02`**: 발행 게이트를 **우회한 `published` 0건**. 일 1회 전수 스캔 → **즉시 회수**
**`NFR-D-04`**: 슬러그 변경 이력 0건. `audit_log` 감시 → **즉시 조사**

**`FR-ADMIN-004`**: 검증 태스크 큐를 제공한다 (큐 UI·API는 이슈 028)

**SPEC-05 §8 배치** — Spring Batch. **전부 멱등해야 한다** (`PRIN-T07`)

### 재사용 계약 (INDEX 결합점)

**이슈 013의 `PublishGate`를 그대로 쓴다.** 배치가 별도 규칙을 구현하면 `NFR-D-02`("게이트를 우회한 published")를 검출할 수 없다 — 두 규칙이 어긋나면 어느 쪽이 맞는지 알 수 없기 때문이다.

### 현재 프로토타입

`packages/domain/check.ts` + `validate.ts`가 `R-C-1`·`R-C-3`·`R-F1.2-1`을 강제한다. **이 배치가 그 서버판**이고, 프론트 쪽은 보조로 남는다 (`PRIN-T05`, CONVENTIONS §3.4).

## RED

### 전수 스캔 (`NFR-D-01`)

1. `발행분_전체를_스캔한다` — `status='published'`
2. `draft는_스캔_대상이_아니다` **결정** — `NFR-D-01`이 "발행분"이라 명시. 발행분만
3. `위반_0건이면_성공으로_종료한다`
4. `위반이_있으면_전부_보고한다` — 첫 위반에서 안 멈춤
5. `위반_건마다_엔티티_ID와_불변식_코드가_기록된다`

### 게이트 재사용 (`NFR-D-02`, INDEX 결합점)

6. `PublishGate_함수를_그대로_호출한다` — 별도 규칙 구현 부재 단언
7. `게이트를_우회해_published된_행을_검출한다` — DB 직접 UPDATE로 만든 위반 상태
8. `검출시_즉시_회수_대상으로_표시한다` (`NFR-D-02` "즉시 회수")
9. `자동_회수인가_수동인가` **결정** — `NFR-D-02`는 "즉시 회수"라 하지만 자동 회수는 위험하다(에디터 작업이 사라진다). **검증 태스크 + 알림, 자동 회수 안 함**

### 앱 강제 불변식 (SPEC-06 §4.3 전수)

10. `INV_COCKTAIL_02_스타일_0개_검출`
11. `INV_COCKTAIL_04_향태그_0개_또는_4개_이상_검출`
12. `GATE_01_tasting_note_빈_발행분_검출`
13. `GATE_02_3축_위반_검출`
14. `GATE_03_표준레시피_재료_또는_스텝_0개_검출`
15. `GATE_04_마스터_미참조_재료_검출`
16. `GATE_05_클래식인데_story_없음_검출`
17. `GATE_06_미유통_재료_대체재_없음_검출`
18. `INV_INGREDIENT_01_미유통_재료_substitute_note_없음_검출`
19. `INV_INGREDIENT_02_브랜드_광고성_미표기_검출`

### slug 감시 (`NFR-D-04`)

20. `audit_log에서_slug_변경을_검출한다` — action `slug_change_attempt` 또는 before/after diff
21. `slug_변경이_0건이면_통과`

### 배치 성질 (`PRIN-T07`, SPEC-05 §8)

22. `배치가_멱등하다` — 두 번 돌려도 태스크가 중복 생성되지 않음
23. `이미_보고된_위반은_중복_태스크를_만들지_않는다`
24. `해소된_위반의_태스크는_자동_종료된다`
25. `배치_실행_이력이_남는다` — 시작·종료·검사 건수·위반 건수
26. `배치_실패가_다음_실행을_막지_않는다`

### 배포 게이트 (`NFR-D-01` "실패 시 배포 차단")

27. `CI에서_실행_가능한_모드가_있다` — 배포 전 검증
28. `위반이_있으면_비정상_종료_코드를_반환한다`

## GREEN

### `V016__verification_task.sql`

```sql
CREATE TABLE verification_task (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  task_type VARCHAR(32) NOT NULL,        -- invariant_violation · hours_expired(1b) · instagram_signal(1b)
  entity_type VARCHAR(24) NOT NULL,
  entity_id BIGINT NOT NULL,
  code VARCHAR(40),                      -- INV-/GATE- ID
  detail JSONB,
  status VARCHAR(12) NOT NULL DEFAULT 'open' CHECK (status IN ('open','resolved','dismissed')),
  detected_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  resolved_at TIMESTAMPTZ, resolved_by BIGINT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  -- RED 22·23: 같은 위반이 중복 생성되지 않는다
  UNIQUE (task_type, entity_type, entity_id, code) DEFERRABLE INITIALLY IMMEDIATE
);
CREATE INDEX ON verification_task (status, detected_at DESC);

CREATE TABLE batch_run (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  batch_code VARCHAR(40) NOT NULL,
  started_at TIMESTAMPTZ NOT NULL, ended_at TIMESTAMPTZ,
  scanned_count INT, violation_count INT,
  status VARCHAR(12) NOT NULL, detail JSONB
);
```

유니크 제약이 멱등을 보장한다 (RED 22·23). 해소된 태스크는 `status='resolved'` 로 남기되, 같은 위반이 재발하면 **새 태스크가 아니라 재오픈**한다.

`verification_task` 는 이슈 028(검증 태스크 큐)이 조회한다. **Phase 1b의 `hours_verified_at` 만료도 같은 테이블**을 쓴다 — `task_type`으로 구분.

### `admin/verification`

```kotlin
@Component
class InvariantVerificationBatch(
    private val publishGate: PublishGate,      // 이슈 013 — 그대로 재사용 (RED 6)
) {
    fun run(): BatchResult {
        // published 전수 → PublishGate.check() → 위반을 verification_task 로
    }
}
```

**`PublishGate.check()`를 그대로 부른다.** 배치용 규칙을 새로 쓰지 않는다 — RED 6이 이것을 강제한다.

### Spring Batch를 쓸까

SPEC-05 §8이 "Spring Batch"라고 명시했다. 다만 Phase 1a의 배치는 이것 하나이고 500건 규모다. **Spring Batch의 Job/Step 메타 테이블이 부담**일 수 있다.

⚖️ **SPEC-05를 따라 Spring Batch를 쓴다** — 문서가 정본이고(`PRIN-S01` 상당), Phase 1b에 배치가 3종 더 늘어난다(SPEC-05 §8: 바 검증·파트너 집계·인스타 동기화·사이트맵). 지금 인프라를 세워 두는 편이 낫다.

### CI 모드 (RED 27·28)

```
./gradlew verifyInvariants   # 위반 있으면 exit 1
```

`NFR-D-01`의 "배포 차단"이 이것으로 구현된다. `npm run check`와 대칭이다.

**하지 말 것**:
- 검증 태스크 조회 API·UI — 이슈 028
- 자동 회수 — RED 9 (**결정** 안 함)
- Phase 1b 배치(바 검증·파트너 집계) — 테이블 구조만 공유

## DoD

- [ ] RED 28항 전부 통과
- [ ] **`PublishGate` 재사용** (RED 6 — 별도 규칙 구현 없음)
- [ ] SPEC-06 §4.3의 앱 강제 항목 **전수 검사** (RED 10~19)
- [ ] 배치 멱등 (RED 22 — `PRIN-T07`)
- [ ] `./gradlew verifyInvariants` 가 CI 배포 게이트 (RED 27·28 — `NFR-D-01`)
- [ ] ⚖️ 3건(draft 스캔 여부·자동 회수·Spring Batch 채택) `GAPS.md` 등재
- [ ] 커밋: `feat(admin): 불변식 배치 검증 (NFR-D-01·D-02·D-04, SPEC-06 §4.3)`
