# apps/api — K-Cocktail Archive API

Kotlin + Spring Boot 3.x · PostgreSQL 16 (`PRIN-T01` · [SPEC-05 §1](../../docs/spec/SPEC-05_아키텍처.md))

**이 디렉터리는 Gradle 이 독립적으로 관리한다.** 루트의 npm workspaces 는 `apps/web` 과 `packages/*` 만 본다 (SPEC-05 §2).
프론트 빌드는 Gradle 없이, API 빌드는 npm 없이 돈다.

## 전제

| | 확인 |
|---|---|
| JDK 21 | `java -version` (`PRIN-T01`) |
| Docker | `docker ps` — **Testcontainers 가 쓴다** |

## 실행

```bash
./gradlew build          # 컴파일 + 테스트
./gradlew test           # 테스트만
./gradlew boundaryTest   # 모듈 경계만 (이슈 001 이 채운다)
./gradlew check          # test + boundaryTest
./gradlew bootRun        # 서버 (DB 필요)
```

## 구조 (SPEC-05 §2)

```
src/main/kotlin/kr/kcocktail/
├─ cocktail/ ingredient/ search/ user/ admin/     ← Phase 1a
├─ bar/ partner/ content/ stock/                  ← Phase 1b·2 (빈 패키지)
└─ common/                                        ← 공용 커널
```

각 모듈은 5개 하위 패키지를 갖는다 — **`api` 만 외부에서 참조 가능**하다 (`PRIN-T03`).

```
<module>/
├─ api/          타 모듈에 공개하는 Facade + DTO   ← 여기만 외부 참조 가능
├─ web/          REST 컨트롤러
├─ domain/       엔티티 · 도메인 서비스
├─ repository/   Spring Data (모듈 외부 참조 금지)
└─ internal/     애플리케이션 서비스
```

**단일 Gradle 프로젝트다.** 모듈별 서브프로젝트로 쪼개지 않는다 — `PRIN-T03` 이 요구하는 것은
패키지 경계이고 그것은 이슈 001 의 경계 테스트가 강제한다.

## 마이그레이션 (SPEC-06 §6)

`src/main/resources/db/migration/` **한 곳**이다. 흩으면 Flyway 가 버전 순서를 보장하지 못한다.

- `V<번호>__<설명>.sql` — **번호 = 이슈 번호** (CONVENTIONS §4)
- 적용된 마이그레이션을 **수정하지 않는다**
- `slug` 값을 바꾸는 마이그레이션을 **쓰지 않는다** (`PRIN-D02`)
- 시드는 `R__seed_*.sql` (repeatable)

## Docker 소켓 자동 탐색

`build.gradle.kts` 가 Testcontainers 용 소켓을 스스로 찾는다. **`DOCKER_HOST` 를 각자 설정할 필요가 없다.**

찾는 순서: Docker Desktop(mac) `docker.raw.sock` → Colima → Rancher Desktop → `/var/run/docker.sock`(리눅스·CI).
`DOCKER_HOST` 가 이미 설정돼 있으면 그것을 존중한다.

### macOS Docker Desktop 주의

`/var/run/docker.sock` 은 **CLI 소켓**으로 심볼릭 링크돼 있어 Engine API 요청에 `400` 을 반환한다.
`docker ps` 는 되는데 Testcontainers 만 "Could not find a valid Docker environment" 로 죽는 이유다.
실제 엔진은 `~/Library/Containers/com.docker.docker/Data/docker.raw.sock` 에 있고, 빌드가 이것을 찾는다.

비표준 소켓일 때는 **Ryuk(정리 컨테이너)를 끈다** — Ryuk 이 `/var/run/docker.sock` 을 바인드 마운트하려다 실패하기 때문이다.
컨테이너는 테스트가 정상 종료하면 그대로 정리된다. 리눅스·CI 에서는 Ryuk 을 켠 채로 둔다.

### 그래도 안 되면

`~/.testcontainers.properties` 에 `docker.client.strategy` 가 박혀 있는지 본다.
Testcontainers 가 과거에 써 둔 값이 낡으면 잘못된 소켓을 강제한다 — **그 줄을 지우면 자동 탐색으로 돌아간다.**

```bash
grep docker.client.strategy ~/.testcontainers.properties   # 있으면 그 줄 삭제
```

## 다음

[`docs/issues/INDEX.md`](../../docs/issues/INDEX.md) 에서 `status: TODO` 이고 의존이 충족된 이슈를 집는다.
착수 전 [`CONVENTIONS.md`](../../docs/issues/CONVENTIONS.md) 와 [`DECISIONS.md`](../../docs/issues/DECISIONS.md) 를 읽는다.
