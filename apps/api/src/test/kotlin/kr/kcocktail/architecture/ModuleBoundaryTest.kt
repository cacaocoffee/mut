package kr.kcocktail.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * ISSUE-001 — 모듈 경계 (`PRIN-T03`, SPEC-05 §3)
 *
 * `PRIN-T03` 은 단일 배포로 시작하되 **도메인 모듈 경계를 코드로 지킨다**고 했다.
 * 지키는 주체가 이 파일이다. 쪼갤 필요가 생기면 여기 규칙이 그대로 서비스 경계가 된다.
 *
 * ## 규칙을 두 번 돌린다
 *
 * 스캐폴딩 직후라 프로덕션에는 위반이 있을 리 없다. 그래서 통과만으로는
 * **규칙이 동작하는지 아무것도 증명하지 못한다** — 패턴에 오타가 나도 초록이다.
 * 그래서 같은 규칙을 픽스처에도 돌려 **실제로 잡는지**까지 단언한다.
 * 픽스처는 `architecture/fixture/` 에 있고 일부러 어긴다.
 *
 * 규칙을 `..` 없이 모듈·계층 좌표로 파싱하는 이유가 이것이다 —
 * 루트가 `kr.kcocktail` 이든 `kr.kcocktail.architecture.fixture` 든 똑같이 걸린다.
 *
 * ## 예외를 등록할 때
 *
 * 어쩔 수 없이 허용해야 하면 **이 파일 안에 사유 주석과 함께** 적는다.
 * 별도 억제 파일을 만들지 않는다 — 억제가 쌓이는 것이 리뷰에서 보여야 한다.
 */
@Tag("boundary")
class ModuleBoundaryTest {

    // ── RED 1~8 : 규칙 ─────────────────────────────────────────────────────

    /**
     * `PRIN-T03` — "다른 모듈의 리포지토리·엔티티를 직접 참조하지 않는다."
     * 리포지토리를 내주면 그 모듈의 쿼리 조건이 남의 코드에 박히고, 스키마를 못 바꾸게 된다.
     */
    @Test
    fun `RED1 - 모듈간 repository 직접참조 금지`() {
        val rule = { c: JavaClasses -> crossModuleRefs(c).failing { it.toLayer == REPOSITORY } }

        assertThat(rule(production())).isEmpty()
        assertThat(rule(fixture()))
            .`as`("규칙이 실제로 잡는지")
            .anySatisfy { assertThat(it).contains("CrossModuleViolationFixture", "ingredient.repository") }
    }

    /**
     * SPEC-05 §3 — 다른 모듈의 `@Entity` 직접 참조 금지.
     * 엔티티를 넘기면 영속성 컨텍스트가 모듈을 넘어가고 지연 로딩이 남의 트랜잭션에서 터진다.
     */
    @Test
    fun `RED2 - 모듈간 entity 직접참조 금지`() {
        val rule = { c: JavaClasses -> crossModuleRefs(c).failing { it.toLayer == DOMAIN } }

        assertThat(rule(production())).isEmpty()
        assertThat(rule(fixture()))
            .anySatisfy { assertThat(it).contains("CrossModuleViolationFixture", "ingredient.domain") }
    }

    /**
     * SPEC-05 §3 — "모듈 간 호출은 공개 인터페이스(`XxxFacade`)로만 한다."
     * RED 1·2 를 포함하는 상위 규칙이다. `internal`·`web` 도 함께 막는다.
     */
    @Test
    fun `RED3 - 모듈간 참조는 api 패키지만`() {
        val rule = { c: JavaClasses -> crossModuleRefs(c).failing { it.toLayer != API } }

        assertThat(rule(production())).isEmpty()
        assertThat(rule(fixture()))
            .anySatisfy { assertThat(it).contains("ingredient.internal") }

        // api 경유는 잡히지 않는다 — 규칙이 과하게 넓지 않은지 확인한다.
        assertThat(rule(fixture())).noneSatisfy { assertThat(it).contains("→ cocktail.api") }
    }

    /**
     * 의존은 단방향이다 — 모두가 `common` 을 참조하고 `common` 은 아무도 참조하지 않는다.
     * 되참조가 생기면 모듈을 서비스로 떼어낼 때 공용 커널이 따라 쪼개져야 한다.
     */
    @Test
    fun `RED4 - common 은 도메인 모듈을 참조하지 않는다`() {
        val rule = { c: JavaClasses ->
            crossModuleRefs(c).failing { it.fromModule == COMMON && it.toModule in DOMAIN_MODULES }
        }

        assertThat(rule(production())).isEmpty()
        assertThat(rule(fixture()))
            .anySatisfy { assertThat(it).contains("CommonToDomainFixture") }
    }

    /**
     * 계층 규칙 — 같은 모듈 안이라도 컨트롤러가 리포지토리를 직행하지 않는다.
     * 직행하면 `internal` 의 트랜잭션 경계를 건너뛰고, 조회 조건이 web 계층으로 샌다.
     */
    @Test
    fun `RED5 - web 은 repository 를 직접 호출하지 않는다`() {
        val rule = { c: JavaClasses ->
            allRefs(c).failing { it.fromLayer == WEB && it.toLayer == REPOSITORY }
        }

        assertThat(rule(production())).isEmpty()
        assertThat(rule(fixture()))
            .anySatisfy { assertThat(it).contains("WebToRepositoryFixture") }
    }

    /**
     * SPEC-05 §3 의존 방향표 그대로다. 표에 없는 화살표는 위반이다.
     *
     * ```
     * COCKTAIL ──uses──▶ INGREDIENT        BAR ◀──extends── PARTNER
     * COCKTAIL ◀──referenced by── BAR      CONTENT ──references──▶ BAR · COCKTAIL
     * USER ──owns──▶ STOCK                 SEARCH ──reads──▶ COCKTAIL · BAR · INGREDIENT · CONTENT
     * ADMIN ──governs──▶ 전부
     * ```
     *
     * Phase 1a 에 BAR·PARTNER·CONTENT·STOCK 은 없다. **그래도 지금 세워 둔다** —
     * 1b 에서 이 표가 설계를 강제한다.
     */
    @Test
    fun `RED6 - SPEC05 3 의존방향표를 벗어난 참조 금지`() {
        val rule = { c: JavaClasses ->
            crossModuleRefs(c).failing {
                // common 은 모두가 참조해도 되는 공용 커널이라 표 밖이다.
                it.toModule != COMMON && it.toModule !in ALLOWED_TARGETS.getValue(it.fromModule)
            }
        }

        assertThat(rule(production())).isEmpty()
        assertThat(rule(fixture()))
            .`as`("INGREDIENT → COCKTAIL 은 방향이 반대다")
            .anySatisfy { assertThat(it).contains("ReverseDirectionFixture", "→ cocktail") }
    }

    /**
     * SPEC-05 §3 — "순환 의존을 만들지 않는다."
     *
     * §3 은 `BAR ↔ COCKTAIL` 이 양방향으로 보이는 것을 인정하면서
     * **조회를 SEARCH 가 맡아 순환을 끊는다**고 했다. 그 설계를 테스트로 고정한다.
     */
    @Test
    fun `RED7 - 순환의존 없음`() {
        assertThat(cycleViolations(production(), PRODUCTION_ROOT)).isEmpty()
        assertThat(cycleViolations(fixture(), FIXTURE_ROOT))
            .`as`("cocktail → ingredient → cocktail 순환")
            .isNotEmpty()
    }

    /**
     * 여러 도메인을 한자리에서 읽는 것은 **SEARCH 하나뿐**이다 (SPEC-05 §3 `SEARCH ──reads──▶` 4종).
     * ADMIN 은 거버넌스라 별도 예외다.
     *
     * RED 3 만으로는 못 잡는다 — `api` 경유라도 여러 도메인을 긁어모으기 시작하면
     * 조회 경로가 흩어지고 곧 순환이 따라온다. 표상 최대 팬아웃은 CONTENT 의 2다.
     */
    @Test
    fun `RED8 - SEARCH 만 다중 도메인을 읽는다`() {
        assertThat(fanOutViolations(production())).isEmpty()
        assertThat(fanOutViolations(fixture()))
            .anySatisfy { assertThat(it).contains("content", "3개") }
    }

    /** RED 9 — 기준선. 위 8개가 현재 코드베이스에서 전부 통과한다. */
    @Test
    fun `RED9 - 8개 규칙이 현재 코드베이스에서 통과한다`() {
        val classes = production()

        // 임포트 필터가 무너지면 위 8개가 전부 "위반 0건"으로 조용히 초록이 된다.
        // 기준선이 진짜 무언가를 보고 있는지부터 확인한다.
        assertThat(classes.map { it.name })
            .`as`("main 산출물을 실제로 읽었는가")
            .contains("kr.kcocktail.KcocktailApplication")
            .`as`("픽스처(test 산출물)가 섞여 들어오지 않았는가")
            .noneMatch { it.startsWith(FIXTURE_ROOT) }

        assertThat(
            mapOf(
                "1 모듈간 repository" to crossModuleRefs(classes).failing { it.toLayer == REPOSITORY },
                "2 모듈간 entity" to crossModuleRefs(classes).failing { it.toLayer == DOMAIN },
                "3 api 경유만" to crossModuleRefs(classes).failing { it.toLayer != API },
                "4 common 단방향" to crossModuleRefs(classes)
                    .failing { it.fromModule == COMMON && it.toModule in DOMAIN_MODULES },
                "5 web→repository" to allRefs(classes)
                    .failing { it.fromLayer == WEB && it.toLayer == REPOSITORY },
                "6 의존 방향표" to crossModuleRefs(classes).failing {
                    it.toModule != COMMON && it.toModule !in ALLOWED_TARGETS.getValue(it.fromModule)
                },
                "7 순환" to cycleViolations(classes, PRODUCTION_ROOT),
                "8 팬아웃" to fanOutViolations(classes),
            ).filterValues { it.isNotEmpty() },
        ).isEmpty()
    }
}

// ── 좌표 ────────────────────────────────────────────────────────────────────

private const val PRODUCTION_ROOT = "kr.kcocktail"
private const val FIXTURE_ROOT = "kr.kcocktail.architecture.fixture"

private const val API = "api"
private const val WEB = "web"
private const val DOMAIN = "domain"
private const val REPOSITORY = "repository"
private const val INTERNAL = "internal"
private const val COMMON = "common"

private val LAYERS = setOf(API, WEB, DOMAIN, REPOSITORY, INTERNAL)

/** SPEC-05 §2 의 9개 도메인 모듈. Phase 1a 에 쓰는 것은 5개뿐이지만 경계는 전부 세운다. */
private val DOMAIN_MODULES =
    setOf("cocktail", "ingredient", "bar", "partner", "content", "user", "stock", "search", "admin")

private val MODULES = DOMAIN_MODULES + COMMON

/**
 * SPEC-05 §3 의존 방향표. **여기 없는 화살표는 위반이다.**
 *
 * 새 방향이 필요하면 코드가 아니라 SPEC-05 §3 을 먼저 고친다 (SPEC-00 §4).
 * 규칙을 느슨하게 만들어 현재 코드를 통과시키지 않는다.
 */
private val ALLOWED_TARGETS: Map<String, Set<String>> = mapOf(
    "cocktail" to setOf("ingredient"),
    "ingredient" to emptySet(),
    "bar" to setOf("cocktail"),
    "partner" to setOf("bar"),
    "content" to setOf("bar", "cocktail"),
    "user" to setOf("stock"),
    "stock" to emptySet(),
    "search" to setOf("cocktail", "bar", "ingredient", "content"), // 조회 전용 (§3)
    "admin" to DOMAIN_MODULES,                                     // governs 전부
    COMMON to emptySet(),                                          // 공용 커널은 아무도 참조하지 않는다
)

/** 표상 최대 팬아웃은 CONTENT 의 2다. SEARCH(4)·ADMIN(전부)만 예외로 둔다. */
private const val FAN_OUT_CAP = 2
private val FAN_OUT_EXEMPT = setOf("search", "admin")

// ── 그래프 ──────────────────────────────────────────────────────────────────

private data class Coordinate(val module: String, val layer: String)

private data class Ref(
    val fromClass: String, val fromModule: String, val fromLayer: String,
    val toClass: String, val toModule: String, val toLayer: String,
) {
    override fun toString() =
        "$fromModule.$fromLayer.$fromClass → $toModule.$toLayer.$toClass"
}

/**
 * 패키지 이름에서 `<module>.<layer>` 좌표를 찾는다.
 *
 * 루트에 상관없이 동작해야 한다 — 프로덕션은 `kr.kcocktail.cocktail.domain`,
 * 픽스처는 `kr.kcocktail.architecture.fixture.cocktail.domain` 이다.
 * `kcocktail` 은 세그먼트 전체가 일치하지 않으므로 `cocktail` 로 오인되지 않는다.
 */
private fun coordinateOf(packageName: String): Coordinate? {
    val segments = packageName.split('.')
    for (i in 0 until segments.size - 1) {
        if (segments[i] in MODULES && segments[i + 1] in LAYERS) {
            return Coordinate(segments[i], segments[i + 1])
        }
    }
    return null
}

private fun allRefs(classes: JavaClasses): List<Ref> = classes.flatMap { origin ->
    val from = coordinateOf(origin.packageName) ?: return@flatMap emptyList()
    origin.directDependenciesFromSelf.mapNotNull { dependency ->
        val target = dependency.targetClass
        val to = coordinateOf(target.packageName) ?: return@mapNotNull null
        if (target == origin) return@mapNotNull null
        Ref(origin.simpleName, from.module, from.layer, target.simpleName, to.module, to.layer)
    }
}

private fun crossModuleRefs(classes: JavaClasses): List<Ref> =
    allRefs(classes).filter { it.fromModule != it.toModule }

private fun List<Ref>.failing(violates: (Ref) -> Boolean): List<String> =
    filter(violates).map(Ref::toString).distinct().sorted()

private fun cycleViolations(classes: JavaClasses, root: String): List<String> =
    SlicesRuleDefinition.slices()
        .matching("$root.(*)..")
        .should().beFreeOfCycles()
        .allowEmptyShould(true)
        .evaluate(classes)
        .failureReport.details

private fun fanOutViolations(classes: JavaClasses): List<String> =
    crossModuleRefs(classes)
        .filter { it.toModule != COMMON && it.fromModule !in FAN_OUT_EXEMPT }
        .groupBy { it.fromModule }
        .mapNotNull { (module, refs) ->
            val targets = refs.map { it.toModule }.distinct().sorted()
            if (targets.size <= FAN_OUT_CAP) null
            else "$module 이 도메인 ${targets.size}개를 읽는다 (상한 $FAN_OUT_CAP): $targets"
        }
        .sorted()

// ── 대상 ────────────────────────────────────────────────────────────────────

/** main 산출물만. 픽스처(test 산출물)를 섞으면 프로덕션 검사가 항상 빨개진다. */
private fun production(): JavaClasses = ClassFileImporter()
    .withImportOption { location -> !location.contains("/test/") }
    .importPackages(PRODUCTION_ROOT)

/** 일부러 어긴 쪽. 규칙이 살아 있는지 여기서 확인한다. */
private fun fixture(): JavaClasses = ClassFileImporter().importPackages(FIXTURE_ROOT)
