---
id: ISSUE-000
title: apps/api Gradle 스캐폴딩 (Kotlin · Spring Boot 3.x)
domain: —
layer: infra
wave: 0
status: TODO
depends_on: []
fr: []
r: []
inv: []
nfr: []
migration: —
owns:
  - apps/api/build.gradle.kts
  - apps/api/settings.gradle.kts
  - apps/api/gradle/**
  - apps/api/gradlew*
  - apps/api/README.md
  - apps/api/src/main/kotlin/kr/kcocktail/KcocktailApplication.kt
  - apps/api/src/main/resources/application*.yml
  - apps/api/src/**/.gitkeep
  - .github/workflows/api.yml
---

> **소유 경로 주의**: 이 이슈는 **빌드 골격과 빈 패키지 디렉터리**만 만든다. `apps/api/**` 를 통째로 소유하지 않는다 — Wave 1 이후 이슈들이 각자의 패키지를 소유하기 때문이다 (CONVENTIONS §4). 패키지는 `.gitkeep` 으로만 만들고 그 안에 코드를 넣지 않는다.

## 근거

- **`PRIN-T01`** 확정 스택: **Kotlin + Spring Boot 3.x** (Spring Web · Data JPA · Security · Batch), **PostgreSQL 16**
- **SPEC-05 §2 저장소 구조**:
  ```
  apps/
    web/                Next.js — 공개 페이지 + 어드민 UI
    api/                Kotlin + Spring Boot (Gradle)
      src/main/kotlin/kr/kcocktail/
        cocktail/  ingredient/  bar/  partner/
        content/   user/        stock/  search/  admin/
        common/            공통 · 어댑터 · 감사
  ```
- **SPEC-05 §2**: `npm workspaces`는 `apps/web`과 `packages/*`만 관리한다. **`apps/api`는 Gradle이 독립적으로 관리**하며 같은 저장소에 나란히 둔다
- **`PRIN-T03`** 모듈러 모놀리스: 9개 도메인 모듈 경계를 코드로 지킨다
- **SPEC-05 §1**: 배포는 **프론트/백 분리** — 각자의 파이프라인

Phase 1a에 실제로 쓰는 모듈은 `cocktail` · `ingredient` · `search` · `user` · `admin` · `common` 6개다.
나머지 3개(`bar` · `partner` · `content` · `stock`)는 **패키지만 만들어 두고 비워 둔다** — Phase 1b·2에서 채운다.

## RED

1. `gradlew_build_성공` — Kotlin 컴파일 + Spring Boot 애플리케이션 플러그인
2. `애플리케이션_컨텍스트_로드_성공` — `@SpringBootTest` 1건
3. `Testcontainers_PostgreSQL16_기동` — 컨테이너 뜨고 연결됨
4. `9개_도메인_패키지가_존재한다` — `kr.kcocktail.{cocktail,ingredient,bar,partner,content,user,stock,search,admin}` + `common`
5. `gradlew_test_성공` — 각 모듈 스모크 테스트
6. `npm_workspaces가_apps_api를_포함하지_않는다` — 루트 `package.json`의 `workspaces` 배열에 `apps/api` 부재 (SPEC-05 §2)
7. `apps_web_빌드가_apps_api와_독립적이다` — `npm run build`가 Gradle 없이 통과

## GREEN

### 구조

```
apps/api/
├─ build.gradle.kts          Kotlin JVM 21, Spring Boot 3.x, 의존성
├─ settings.gradle.kts       rootProject.name = "kcocktail-api"
├─ gradle/wrapper/
├─ gradlew · gradlew.bat
└─ src/
   ├─ main/kotlin/kr/kcocktail/
   │   ├─ KcocktailApplication.kt
   │   ├─ common/
   │   ├─ cocktail/  ingredient/  search/  user/  admin/
   │   └─ bar/  partner/  content/  stock/        ← 빈 패키지 (Phase 1b·2)
   ├─ main/resources/
   │   ├─ application.yml · application-local.yml
   │   └─ db/migration/                            ← 이슈 002부터
   └─ test/kotlin/kr/kcocktail/
```

**단일 Gradle 프로젝트**로 간다. 모듈별 서브프로젝트로 쪼개지 않는다 — `PRIN-T03`이 요구하는 것은 **패키지 경계**이고, 그것은 이슈 001의 경계 테스트가 강제한다. 서브프로젝트로 쪼개면 빌드가 느려지고 1~2명 규모에 이득이 없다.

### 의존성

- Spring Boot: `web`, `data-jpa`, `security`, `validation`, `actuator`, `batch`
- PostgreSQL 드라이버, Flyway (`flyway-core` + `flyway-database-postgresql`)
- Kotlin: `jackson-module-kotlin`, `kotlin-reflect`
- 테스트: JUnit 5, `spring-boot-starter-test`, Testcontainers(`postgresql`, `junit-jupiter`), Kotest assertions 또는 AssertJ

### 각 도메인 패키지의 내부 구조

```
kr/kcocktail/<domain>/
├─ api/          ← 타 모듈에 공개하는 Facade 인터페이스 + DTO (여기만 외부 참조 가능)
├─ web/          ← REST 컨트롤러
├─ domain/       ← 엔티티 · 값 객체 · 도메인 서비스
├─ repository/   ← Spring Data (모듈 외부 참조 금지)
└─ internal/     ← 애플리케이션 서비스
```

빈 디렉터리라도 `.gitkeep`으로 만들어 둔다. 이슈 001의 경계 테스트가 이 구조를 강제한다.

### CI

`.github/workflows/api.yml` — `apps/api/**` 변경 시 `./gradlew check`. Docker 필요(Testcontainers).
`apps/web` CI와 **분리**한다 (SPEC-05 §1 — 각자의 파이프라인).

### ⚠️ G-07 — 호스팅 미정

배포 파이프라인은 만들지 않는다. **호스팅이 미정**이고(GAPS G-07 하단), SPEC-07 §1.2가 "프론트와 API가 같은 상위 도메인"이라는 제약을 걸어 뒀다. CI(빌드·테스트)까지만 하고 CD는 호스팅 확정 후.

**하지 말 것**: 테이블·엔티티·엔드포인트. 이 이슈는 골격만이다.

## DoD

- [ ] RED 7항 전부 통과
- [ ] 10개 패키지(9 도메인 + common) 각각에 5개 하위 디렉터리 존재
- [ ] `apps/api/README.md`에 로컬 실행 방법 (Docker 전제 명시)
- [ ] `npm run build`(웹)와 `./gradlew build`(API)가 서로 독립 (RED 6·7)
- [ ] CI 초록
- [ ] 커밋: `chore(api): Kotlin·Spring Boot 스캐폴딩 (PRIN-T01·T03, SPEC-05 §2)`
