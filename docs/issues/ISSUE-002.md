---
id: ISSUE-002
title: Flyway 기반 + 공통 컬럼 규약 + pg_trgm
domain: —
layer: infra
wave: 0
status: TODO
depends_on: [ISSUE-000]
fr: []
r: []
inv: []
nfr: [NFR-D-03]
migration: V001
owns:
  - apps/api/src/main/resources/db/migration/V001__*.sql
  - apps/api/src/main/kotlin/kr/kcocktail/common/entity/**
  - apps/api/src/test/kotlin/kr/kcocktail/architecture/SchemaLintTest.kt
---

## 근거

**SPEC-06 §1.1 명명**
- 테이블 · 컬럼은 `snake_case` **단수형** (`cocktail`, `recipe_ingredient`) — 복수형이 아니다
- 조인 테이블은 `<주>_<종>` (`cocktail_style`)
- 불리언은 `is_` / `has_` 접두 (`is_signature`)
- 시각은 `_at` 접미 (`published_at`), 날짜는 `_on`

**SPEC-06 §1.2 공통 컬럼** — 모든 실체 테이블이 갖는다

| 컬럼 | 타입 |
|---|---|
| `id` | `BIGINT GENERATED ALWAYS AS IDENTITY` |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` — **트리거로 갱신** |

> **공개 식별자는 `slug`다.** URL에 `id`를 노출하지 않는다 — 슬러그가 SEO 자산이고 `PRIN-D02`가 불변을 요구한다.

**SPEC-06 §1.3** — enum은 Postgres 네이티브 `ENUM`이 아니라 **`VARCHAR` + `CHECK`**
- 값 추가는 쉽지만 삭제·순서 변경이 사실상 불가능하다. 분류 축은 앞으로 늘어난다
- 허용값의 **정본은 Kotlin enum**(`PRIN-T02`), `CHECK`는 DB 레벨 이중 방어

**SPEC-06 §1.4** — 배열은 **조인 테이블**로. 예외는 `aliases[]`(검색 전용, 무결성 대상 아님 → `TEXT[]` + GIN)

**SPEC-06 §5** — `pg_trgm` 확장이 필요하다. 초성 검색을 위해 **`CREATE EXTENSION pg_trgm`을 마이그레이션 첫 단계에** 둔다

**SPEC-06 §6 마이그레이션**
- **Flyway.** `V<번호>__<설명>.sql`, 앞으로만 간다. 적용된 마이그레이션을 수정하지 않는다
- **`slug` 값을 바꾸는 마이그레이션을 쓰지 않는다** (`PRIN-D02`)
- enum 값 추가는 `CHECK` 제약 교체로. **Kotlin enum과 같은 마이그레이션에** 넣는다
- 시드는 별도 `R__seed_*.sql`(repeatable)

**SPEC-06 §4.1** — `INV-BAR-03` 물리 삭제 금지: **앱 DB 역할에서 `DELETE` 권한을 회수한다.** `bar` · `cocktail` · `article` · `curation_list`에 `REVOKE DELETE`

## RED

### 확장·기반 (Testcontainers)

1. `pg_trgm_확장이_설치된다` — `SELECT * FROM pg_extension WHERE extname='pg_trgm'`
2. `Flyway가_V001을_적용한다`
3. `적용된_마이그레이션_체크섬이_바뀌면_기동이_실패한다` — SPEC-06 §6 "수정하지 않는다"의 강제

### 스키마 린트 (이후 모든 이슈의 상시 안전망)

4. `모든_실체테이블에_id_created_at_updated_at이_있다` — `information_schema` 조회. 위반 테이블명을 **전부 나열**
5. `id는_GENERATED_ALWAYS_AS_IDENTITY다`
6. `updated_at_갱신_트리거가_붙어_있다` — 행 수정 시 자동 갱신 확인
7. `테이블명은_snake_case_단수형이다` — 복수형(`cocktails`) 발견 시 실패 (SPEC-06 §1.1)
8. `불리언_컬럼은_is_또는_has_접두다`
9. `시각_컬럼은_at_접미_날짜는_on_접미다`
10. `Postgres_네이티브_ENUM_타입이_0개다` (SPEC-06 §1.3)
11. `시각_컬럼은_timestamptz다` — `timestamp without time zone` 발견 시 실패

### DELETE 권한 회수 (SPEC-06 §4.1)

12. `앱_역할에_DELETE_권한이_없는_테이블_목록이_정의된다` — `cocktail`·`bar`·`article`·`curation_list`. Phase 1a에는 `cocktail`만 존재하므로 **존재하는 것만** 검사하고 목록은 상수로 유지
13. `DELETE_시도가_DB에서_거부된다` — 앱 역할로 접속해 실패 확인

### 베이스 엔티티 (단위)

14. `BaseEntity_저장시_created_at과_updated_at이_채워진다`
15. `BaseEntity_수정시_updated_at만_갱신된다`

## GREEN

### `V001__baseline.sql`

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;   -- SPEC-06 §5 (초성 검색)

-- updated_at 자동 갱신 트리거 함수 (SPEC-06 §1.2)
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = now(); RETURN NEW; END;
$$ LANGUAGE plpgsql;
```

테이블은 만들지 않는다. 확장·함수·역할까지.

### DB 역할 분리

**마이그레이션 역할과 애플리케이션 역할을 나눈다.** 그래야 RED 13이 의미를 갖는다.

```
kcocktail_migrate   — DDL 권한. Flyway 전용
kcocktail_app       — DML만. 특정 테이블에 REVOKE DELETE
```

Testcontainers 테스트도 **애플리케이션 역할로 접속**해야 한다. 슈퍼유저로 붙으면 권한 테스트가 전부 통과해 버린다.

### `common/entity`

```kotlin
@MappedSuperclass
abstract class BaseEntity {
    @Id @GeneratedValue(strategy = IDENTITY) val id: Long = 0
    @Column(nullable = false, updatable = false) val createdAt: Instant = Instant.now()
    @Column(nullable = false) var updatedAt: Instant = Instant.now()
}
```

`updated_at`은 **DB 트리거가 정본**이다 (SPEC-06 §1.2 "트리거로 갱신"). JPA `@PreUpdate`는 벌크 업데이트를 놓친다.

### 스키마 린트

`SchemaLintTest`는 통합 테스트다(실제 스키마를 봐야 한다). CI의 `check`에 포함된다.
**위반을 전부 모아서 한 번에 보고한다** — 첫 위반에서 멈추면 세션이 고치고 돌리기를 반복한다.

### ⚠️ G-07 — `media_asset`

SPEC-06 §3.7의 `media_asset`은 만들지 않는다. **이미지 저장소 백엔드가 미정**(GAPS G-07 하단)이다. 다만 SPEC-06 §7이 "`storage_key`가 추상화 지점이라 백엔드가 정해져도 스키마는 바뀌지 않는다"고 했으므로, 이 테이블은 실제로 이미지를 다루는 이슈(045 어드민 UI)에서 만든다.

**하지 말 것**: 도메인 테이블. 이 이슈는 규약과 안전망까지.

## DoD

- [ ] RED 15항 전부 통과
- [ ] DB 역할 2종 분리, 테스트가 앱 역할로 접속 (RED 13)
- [ ] 스키마 린트가 `./gradlew check` 에 포함
- [ ] 린트가 위반을 **전부** 보고 (첫 실패에서 안 멈춤)
- [ ] `apps/api/README.md`에 로컬 DB 기동 방법
- [ ] 커밋: `feat(api): Flyway 기반·공통 컬럼 규약·pg_trgm (SPEC-06 §1·§5·§6)`
