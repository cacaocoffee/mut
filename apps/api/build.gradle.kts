// PRIN-T01 확정 스택 — Kotlin + Spring Boot 3.x · PostgreSQL 16
// SPEC-05 §1·§2

plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.spring") version "2.0.21"
    kotlin("plugin.jpa") version "2.0.21"
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "kr.kcocktail"
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
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // SPEC-06 §6 — Flyway. 앞으로만 간다
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

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

// 이슈 001 이 채운다. 지금은 태그가 붙은 테스트가 없어 통과한다.
tasks.register<Test>("boundaryTest") {
    description = "모듈 경계 테스트 (PRIN-T03 · NFR-032 상당)"
    group = "verification"
    useJUnitPlatform { includeTags("boundary") }
}
tasks.named("check") { dependsOn("boundaryTest") }
