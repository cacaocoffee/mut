package kr.kcocktail.architecture

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.io.File
import kotlin.test.assertTrue

/**
 * ISSUE-000 RED 4·6·7 — 스캐폴딩 구조 검증.
 *
 * SPEC-05 §2 저장소 구조 · PRIN-T03 모듈 경계 · npm workspaces 독립성.
 * 컨텍스트 로드(RED 2)와 Testcontainers(RED 3)는 각각 별도 테스트다.
 */
class ScaffoldingTest {

    private val repoRoot = generateSequence(File(".").absoluteFile) { it.parentFile }
        .first { File(it, "package.json").exists() && File(it, "apps").isDirectory }

    private val kotlinRoot = File(repoRoot, "apps/api/src/main/kotlin/kr/kcocktail")

    /** SPEC-05 §2 — 9개 도메인 모듈 + 공용 커널 */
    private val modules = listOf(
        "cocktail", "ingredient", "bar", "partner",
        "content", "user", "stock", "search", "admin",
        "common",
    )

    /** CONVENTIONS §4 — 모듈 내부 구조. api 만 외부에서 참조 가능 */
    private val layers = listOf("api", "web", "domain", "repository", "internal")

    @Test
    fun `RED4 - 10개 도메인 패키지가 존재한다`() {
        assertAll(modules.map { m ->
            { assertTrue(File(kotlinRoot, m).isDirectory, "패키지 없음: kr.kcocktail.$m") }
        })
    }

    @Test
    fun `RED4 - 모듈마다 5개 하위 패키지가 있다`() {
        val missing = modules.flatMap { m ->
            layers.filterNot { File(kotlinRoot, "$m/$it").isDirectory }.map { "$m/$it" }
        }
        assertTrue(missing.isEmpty(), "하위 패키지 없음: $missing")
    }

    @Test
    fun `RED6 - npm workspaces 가 apps_api 를 포함하지 않는다`() {
        // SPEC-05 §2 — npm workspaces 는 apps/web 과 packages/* 만 관리한다.
        // apps/* 글롭이면 apps/api 를 삼킨다.
        val pkg = File(repoRoot, "package.json").readText()
        val ws = Regex(""""workspaces"\s*:\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL)
            .find(pkg)?.groupValues?.get(1).orEmpty()
        assertTrue("apps/web" in ws, "workspaces 에 apps/web 이 없다: $ws")
        assertTrue("apps/*" !in ws, "workspaces 의 apps/* 글롭이 apps/api 를 포함한다: $ws")
        assertTrue("apps/api" !in ws, "workspaces 가 apps/api 를 직접 포함한다: $ws")
    }

    @Test
    fun `RED7 - apps_web 이 apps_api 의 Gradle 산출물에 의존하지 않는다`() {
        // 프론트 빌드가 Gradle 없이 통과해야 한다 (SPEC-05 §1 — 각자의 파이프라인)
        val webPkg = File(repoRoot, "apps/web/package.json").readText()
        assertTrue("apps/api" !in webPkg, "apps/web 이 apps/api 를 참조한다")
        assertTrue("gradle" !in webPkg.lowercase(), "apps/web 스크립트가 gradle 을 호출한다")
    }

    @Test
    fun `Flyway 마이그레이션 디렉터리가 단일 위치다`() {
        // SPEC-06 §6 — 버전 순서를 Flyway 가 보장하려면 한 곳이어야 한다
        assertTrue(
            File(repoRoot, "apps/api/src/main/resources/db/migration").isDirectory,
            "db/migration 디렉터리 없음",
        )
    }
}
