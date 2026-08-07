---
id: ISSUE-001
title: 모듈 경계 테스트
domain: —
layer: infra
wave: 0
status: TODO
depends_on: [ISSUE-000]
fr: []
r: []
inv: []
nfr: []
migration: —
owns:
  - apps/api/src/test/kotlin/kr/kcocktail/architecture/ModuleBoundaryTest.kt
---

> **소유 경로 주의**: 같은 `architecture/` 디렉터리에 다른 이슈의 테스트가 들어온다 — `SchemaLintTest.kt`(002) · `ExposureRuleAbsenceTest.kt`(027) · `LocationAbsenceTest.kt`(033). **디렉터리가 아니라 파일 단위**로 나눈다.

## 근거

- **`PRIN-T03`** 모듈러 모놀리스: Spring 단일 배포로 시작하되 **도메인 모듈 경계를 코드로 지킨다**
  - 모듈 간 호출은 **공개 인터페이스로만**. 다른 모듈의 리포지토리·엔티티를 직접 참조하지 않는다
  - 쪼갤 필요가 생기면 모듈 경계가 그대로 서비스 경계가 된다
- **SPEC-05 §3 모듈 규칙**:
  - 모듈 간 호출은 공개 인터페이스(`XxxFacade`)로만 한다
  - 다른 모듈의 `Repository`나 `@Entity`를 직접 참조하지 않는다
  - **순환 의존을 만들지 않는다.** `BAR`가 `COCKTAIL`을 참조하고 `COCKTAIL`이 "이 칵테일을 파는 바"를 보여줘야 하므로 양방향으로 보이지만, **조회는 `SEARCH`나 조회 전용 서비스가 담당해 순환을 끊는다**
  - 부수효과(알림·집계·검증 태스크 생성)는 **도메인 이벤트로 발행**하고 리스너가 처리한다

### SPEC-05 §3 의존 방향 (허용)

```
COCKTAIL ──uses──▶ INGREDIENT
COCKTAIL ◀──referenced by── BAR (시그니처 메뉴)
BAR ◀──extends── PARTNER
CONTENT ──references──▶ BAR · COCKTAIL
USER ──owns──▶ STOCK · Bookmark
SEARCH ──reads──▶ COCKTAIL · BAR · INGREDIENT · CONTENT
ADMIN ──governs──▶ 전부
```

**이 이슈를 Wave 0에 두는 이유**: 경계 테스트가 없으면 Wave 2부터 세 세션이 병렬로 서로의 엔티티를 참조하기 시작한다. 그때 발견하면 이미 세 브랜치를 되돌려야 한다.

## RED

각 규칙마다 **위반 픽스처를 만들어 규칙이 실제로 잡는지** 먼저 확인한다. 통과만 하는 경계 테스트는 오탐이 없는 게 아니라 아무것도 안 하는 것일 수 있다.

1. `모듈간_repository_직접참조_금지` — `cocktail` 패키지가 `ingredient.repository` 를 참조하면 위반 검출
2. `모듈간_entity_직접참조_금지` — `cocktail.domain` 이 `ingredient.domain.IngredientEntity` 를 참조하면 위반
3. `모듈간_참조는_api_패키지만` — `cocktail` → `ingredient.internal` 은 위반 / `ingredient.api` 는 허용
4. `common은_도메인모듈을_참조하지_않는다` — `common` → `cocktail` 참조 시 위반 (의존 단방향)
5. `web은_repository를_직접_호출하지_않는다` — 컨트롤러 → 리포지토리 직행 시 위반
6. `SPEC05_§3_의존방향표를_벗어난_참조_금지` — 위 표를 테스트 데이터로. 예: `INGREDIENT` → `COCKTAIL` 참조는 위반 (방향이 반대)
7. `순환의존_없음` — 슬라이스 사이클 검출
8. `SEARCH만_다중_도메인을_읽는다` — `SEARCH`는 4개 도메인 참조 허용, 다른 모듈은 불가
9. `위_8개_규칙이_현재_코드베이스에서_통과` (기준선)

## GREEN

### 도구 — Konsist 또는 ArchUnit

Kotlin이라 **Konsist**가 자연스럽지만 ArchUnit도 Kotlin 바이트코드에 동작한다. 팀 친숙도로 고른다. 어느 쪽이든 규칙은 위와 동일하다.

`apps/api/src/test/kotlin/kr/kcocktail/architecture/ModuleBoundaryTest.kt`

### Gradle 태스크 분리

```kotlin
tasks.register<Test>("boundaryTest") {
    useJUnitPlatform { includeTags("boundary") }
}
tasks.named("check") { dependsOn("boundaryTest") }
```

CI에서 `check`와 별개로도 돌려 실패 원인이 즉시 보이게 한다.

### 예외 등록 방식

경계 위반을 어쩔 수 없이 허용해야 하면 **테스트 파일 안에 사유 주석과 함께 명시적으로** 등록한다. 별도 억제 파일은 쓰지 않는다 — 억제가 쌓이는 것이 리뷰에서 보여야 한다.

```kotlin
// 예외: SEARCH 는 조회 전용이라 4개 도메인의 api 패키지를 읽는다 (SPEC-05 §3)
```

### 순환을 끊는 지점을 테스트로 고정

SPEC-05 §3이 "`BAR`↔`COCKTAIL`이 양방향으로 보이지만 조회는 `SEARCH`나 조회 전용 서비스가 담당해 순환을 끊는다"고 명시했다. Phase 1a에는 `BAR`가 없지만 **규칙은 지금 세워 둔다** — 1b에서 이 테스트가 설계를 강제한다 (RED 6·7).

**하지 말 것**: 규칙을 느슨하게 만들어 현재 코드를 통과시키기. 스캐폴딩 직후라 위반이 있을 리 없다.

## DoD

- [ ] RED 9항 전부 통과 (위반 픽스처가 실제로 검출되는 것 포함)
- [ ] `./gradlew boundaryTest` 가 CI에 포함
- [ ] 규칙 8개 각각에 근거(`PRIN-T03` / SPEC-05 §3 규칙)가 주석으로 달림
- [ ] 커밋: `test(api): 모듈 경계 강제 (PRIN-T03, SPEC-05 §3)`
