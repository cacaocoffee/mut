// PRIN-T01 확정 스택 — Kotlin + Spring Boot 3.x · PostgreSQL 16
// SPEC-05 §1·§2

plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.spring") version "2.0.21"
    kotlin("plugin.jpa") version "2.0.21"
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "kr.mut"
version = "0.1.0"

kotlin {
    jvmToolchain(21) // PRIN-T01
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

repositories { mavenCentral() }

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-batch") // SPEC-05 §8
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // PRIN-T02 — OpenAPI 가 계약의 정본이다. UI 는 쓰지 않는다 (타입만 뽑는다)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-api:2.6.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // SPEC-08 §9 — "Phase 1 은 DB 세션으로 충분." Redis 를 지금 들이지 않는다 (ISSUE-005)
    implementation("org.springframework.session:spring-session-jdbc")

    // ISSUE-030 — 애플 로그인만 JWT 를 요구한다 (SPEC-08 §4.2).
    //   · client_secret 이 고정 문자열이 아니라 ES256 서명 JWT 다
    //   · 프로필이 userinfo 가 아니라 id_token 클레임에 온다 — **서명을 검증해야** 한다
    // 직접 파싱하고 검증을 건너뛰면 누구나 아무 sub 로 로그인할 수 있다.
    // 스프링이 버전을 관리하는 것으로 고른다 (nimbus-jose-jwt 를 직접 핀하지 않는다).
    implementation("org.springframework.security:spring-security-oauth2-jose")

    // SPEC-06 §6 — Flyway. 앞으로만 간다
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // ADR-0011 이미지 업로드 — Google Cloud Storage (ADR-0007 의 R2 대신 같은 GCP 프로젝트를
    // 쓴다. 계정을 하나 더 만들지 않는다 — Cloud Run 서비스 계정이 ADC 로 붙는다).
    implementation(platform("com.google.cloud:libraries-bom:26.50.0"))
    implementation("com.google.cloud:google-cloud-storage")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
    testImplementation("org.testcontainers:postgresql:1.20.4")
    // ISSUE-001 — 모듈 경계 (PRIN-T03, SPEC-05 §3)
    testImplementation("com.tngtech.archunit:archunit:1.3.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

/**
 * Testcontainers 가 쓸 Docker 엔진 소켓을 찾는다.
 *
 * macOS 의 Docker Desktop 은 `/var/run/docker.sock` 을 CLI 소켓으로 심볼릭 링크하는데,
 * 그 소켓은 Engine API 요청에 400 을 반환한다 — Testcontainers 가 "유효한 Docker 환경 없음"으로 죽는다.
 * 실제 엔진은 `~/Library/Containers/com.docker.docker/Data/docker.raw.sock` 에 있다.
 *
 * CI(리눅스)에서는 `/var/run/docker.sock` 이 정상이라 이 탐색이 그대로 통과한다.
 * 개발자가 각자 DOCKER_HOST 를 설정하게 하지 않는 것이 목적이다.
 */
fun detectDockerHost(): String? {
    System.getenv("DOCKER_HOST")?.takeIf { it.isNotBlank() }?.let { return null } // 이미 설정됨 — 존중
    val home = System.getProperty("user.home")
    return listOf(
        "$home/Library/Containers/com.docker.docker/Data/docker.raw.sock", // Docker Desktop (mac)
        "$home/.colima/default/docker.sock",                               // Colima
        "$home/.rd/docker.sock",                                           // Rancher Desktop
        "/var/run/docker.sock",                                            // Linux · CI
    ).firstOrNull { File(it).exists() }?.let { "unix://$it" }
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging { events("passed", "skipped", "failed") }

    detectDockerHost()?.let { host ->
        environment("DOCKER_HOST", host)

        // 사용자 홈의 ~/.testcontainers.properties 가 UnixSocketClientProviderStrategy 를 강제하면
        // /var/run/docker.sock(= Docker Desktop 의 CLI 소켓)만 보고 실패한다.
        // Testcontainers 는 이 env 로 그 설정을 덮는다.
        environment("TESTCONTAINERS_DOCKER_CLIENT_STRATEGY",
            "org.testcontainers.dockerclient.EnvironmentAndSystemPropertyClientProviderStrategy")

        // 위 전략은 API 버전을 협상하지 않고 docker-java 기본값(1.32)을 쓰는데
        // 최근 엔진은 1.40 미만을 거부한다.
        val apiVersion = System.getenv("DOCKER_API_VERSION") ?: "1.43"
        environment("DOCKER_API_VERSION", apiVersion)
        systemProperty("api.version", apiVersion)

        // Ryuk(정리 컨테이너)는 /var/run/docker.sock 을 바인드 마운트한다.
        // 소켓이 비표준 경로(Docker Desktop raw.sock 등)면 마운트에 실패해 컨테이너가 안 뜬다.
        // 표준 경로일 때(리눅스·CI)는 Ryuk 를 켠 채로 둔다 — 크래시 시 컨테이너 정리가 그쪽 몫이다.
        if (host != "unix:///var/run/docker.sock") {
            environment("TESTCONTAINERS_RYUK_DISABLED", "true")
        }
    }
}

/**
 * PRIN-T02 — OpenAPI 스펙을 커밋 파일로 갱신한다 (ISSUE-004).
 *
 * springdoc 의 Gradle 플러그인은 앱을 실제로 기동해 /v3/api-docs 를 긁는다.
 * 그러려면 빌드가 DB 를 붙잡아야 하고 포트·기동 시간·헬스체크가 전부 실패 지점이 된다.
 * 이미 있는 Testcontainers 위에서 MockMvc 로 같은 문서를 뽑는 편이 단순하다.
 *
 * 드리프트 판정 자체는 `check` 안에 있다 — CI 스텝에만 두면 로컬에서는
 * 아무도 모른 채 낡은 생성물을 커밋한다.
 */
tasks.register<Test>("generateOpenApiDocs") {
    description = "openapi.json 을 현재 코드로 갱신한다 (PRIN-T02)"
    group = "documentation"
    useJUnitPlatform { includeTags("contract") }
    systemProperty("openapi.write", "true")
    outputs.upToDateWhen { false } // 항상 다시 뽑는다
}

/**
 * `NFR-D-01` 배포 게이트 (ISSUE-016). `npm run check` 의 서버판이다.
 *
 * 발행분을 전수 스캔해 불변식 위반이 있으면 **비정상 종료**한다 — CI 가 그 코드로 배포를 막는다.
 * 웹 서버를 띄우지 않는 이유: 배포 전에 한 번 돌고 죽는 것이라 포트도 필터도 필요 없다.
 *
 * DB 를 붙잡으므로 `check` 에 매달지 않는다. 로컬에서 `./gradlew verifyInvariants` 로 부르고,
 * CI 는 배포 직전 스텝에서 실제 DB 를 가리켜 돌린다.
 */
/**
 * 검색 색인 되살리기 (이슈 053 · G-34).
 *
 * 시드가 SQL 로 들어오면 발행 이벤트가 없어 색인이 비어 있다. 운영에서도 색인이 깨졌을 때
 * 되살릴 길이 이것뿐이라 태스크로 둔다.
 */
tasks.register<JavaExec>("reindexSearch") {
    description = "search_document 를 전수 재작성 (G-34)"
    group = "application"
    mainClass.set("kr.mut.MutApplicationKt")
    classpath = sourceSets["main"].runtimeClasspath
    args(
        "--mut.search.reindex-cli=true",
        "--mut.verification.scheduled=false",
        "--spring.main.web-application-type=none",
    )
}

tasks.register<JavaExec>("verifyInvariants") {
    description = "발행분 불변식 전수 검증 — 위반이 있으면 exit 1 (NFR-D-01)"
    group = "verification"
    mainClass.set("kr.mut.MutApplicationKt")
    classpath = sourceSets["main"].runtimeClasspath
    args(
        "--mut.verification.cli=true",
        "--mut.verification.scheduled=false",
        "--spring.main.web-application-type=none",
    )
}

// 이슈 001 이 채운다. 지금은 태그가 붙은 테스트가 없어 통과한다.
tasks.register<Test>("boundaryTest") {
    description = "모듈 경계 테스트 (PRIN-T03 · NFR-032 상당)"
    group = "verification"
    useJUnitPlatform { includeTags("boundary") }
}
tasks.named("check") { dependsOn("boundaryTest") }
