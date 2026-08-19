package kr.mut.architecture

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
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

    private val kotlinRoot = File(repoRoot, "apps/api/src/main/kotlin/kr/mut")

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
            { assertTrue(File(kotlinRoot, m).isDirectory, "패키지 없음: kr.mut.$m") }
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

    // ── SPEC-06 §6 — 마이그레이션은 한 곳이다 (이슈 000-1) ─────────────────
    //
    // 원래 이 테스트는 이름과 달리 db/migration 이 **존재하는지**만 봤다.
    // 두 번째 디렉터리가 생겨도 초록이었다. §6 이 요구한 것은 단일성이다.
    //
    // 흩어지면 Flyway 가 버전 순서를 보장하지 못하고, 그때는 이미 여러 이슈의
    // 마이그레이션이 쌓인 뒤라 되돌리기 어렵다. 규칙이 필요해지기 직전이 지금이다.

    private val migrationDir = File(repoRoot, MIGRATION_PATH)

    @Test
    fun `db_migration 디렉터리가 저장소에 하나뿐이다`() {
        assertTrue(migrationDir.isDirectory, "$MIGRATION_PATH 없음")

        assertEquals(
            listOf(migrationDir.canonicalPath),
            findMigrationDirs(File(repoRoot, "apps/api")).map { it.canonicalPath }.sorted(),
            "마이그레이션 디렉터리가 둘 이상이다",
        )
    }

    @Test
    fun `마이그레이션 파일은 그 디렉터리 밖에 없다`() {
        val strays = File(repoRoot, "apps/api")
            .walkTopDown()
            .onEnter { it.name !in SKIP_DIRS }
            .filter { it.isFile && it.name.matches(MIGRATION_FILE) }
            .filter { it.parentFile.canonicalPath != migrationDir.canonicalPath }
            .map { it.relativeTo(repoRoot).path }
            .sorted()
            .toList()

        // 첫 건에서 멈추지 않는다 — 옮길 파일이 여럿이면 한 번에 보여야 한다.
        assertTrue(strays.isEmpty(), "$MIGRATION_PATH 밖의 마이그레이션 파일:\n  ${strays.joinToString("\n  ")}")
    }

    /**
     * 규칙이 실제로 잡는지.
     *
     * 위 두 테스트는 위반이 없으면 항상 통과한다 — **아무것도 안 해도 초록이다.**
     * 원래 구현이 정확히 그 상태였다. 임시 트리에 두 번째 디렉터리를 만들어 검출을 확인한다.
     */
    @Test
    fun `위반 픽스처가 실제로 검출된다`() {
        val fake = Files.createTempDirectory("migration-fixture").toFile()
        try {
            File(fake, MIGRATION_PATH.removePrefix("apps/api/")).mkdirs()
            File(fake, "src/main/resources/other/db/migration").mkdirs()

            assertEquals(
                2, findMigrationDirs(fake).size,
                "두 번째 db/migration 을 못 잡는다 — 규칙이 죽어 있다",
            )
        } finally {
            fake.deleteRecursively()
        }
    }

    /** `V001` 이 두 개면 Flyway 가 기동에 실패한다. 컨테이너를 띄우기 전에 파일명으로 잡는다. */
    @Test
    fun `버전 번호가 중복되지 않는다`() {
        val duplicates = migrationDir.listFiles().orEmpty()
            .mapNotNull { VERSIONED_FILE.find(it.name)?.groupValues?.get(1) }
            .groupingBy { it }.eachCount()
            .filterValues { it > 1 }
            .keys.sorted()

        assertTrue(duplicates.isEmpty(), "중복된 마이그레이션 버전: $duplicates")
    }

    private fun findMigrationDirs(root: File): List<File> = root.walkTopDown()
        .onEnter { it.name !in SKIP_DIRS }
        .filter { it.isDirectory && it.name == "migration" && it.parentFile?.name == "db" }
        .toList()

    private companion object {
        const val MIGRATION_PATH = "apps/api/src/main/resources/db/migration"

        /** 빌드 산출물에도 마이그레이션이 복사된다. 소스 트리만 본다. */
        val SKIP_DIRS = setOf("build", ".gradle", "node_modules", ".git")

        val MIGRATION_FILE = Regex("""^[VR].*\.sql$""")
        val VERSIONED_FILE = Regex("""^V(\d+(?:[._]\d+)*)__""")
    }
}
