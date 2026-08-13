package kr.kcocktail.architecture

import kr.kcocktail.common.security.Role
import kr.kcocktail.common.security.authz.Action
import kr.kcocktail.common.security.authz.PermissionMatrix
import kr.kcocktail.support.PostgresSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import java.nio.file.Files
import java.nio.file.Path

/**
 * ISSUE-027 — **노출 규칙이 없다는 것을 고정한다** (`PRIN-P02` · `FR-ADMIN-006`).
 *
 * ## 이 파일은 기능을 검증하지 않는다. 부재를 검증한다
 *
 * `PRIN-P02` 는 큐레이션 중립성을 **하드 제약**으로 못박았다:
 *
 * > **영업 편의로 조정할 수 있게 만들면 반드시 조정된다.**
 * > 어드민에 수치 입력란을 두는 순간 **그 수치는 올라간다.**
 *
 * SPEC-08 §2.1 이 같은 말을 다르게 한다:
 *
 * > API 표면에도 DB 컬럼에도 존재하지 않는다. 바꾸려면 **코드를 고치고 배포해야 한다.
 * > 그게 의도다** — 영업 압박이 들어오는 순간 "어드민에서 잠깐만" 이 가능하면 반드시 그렇게 된다.
 *
 * SPEC-06 §4.4 가 이 파일의 존재 이유를 한 줄로 적었다:
 *
 * > `INV-PARTNER-01~04` 가 **"DB 에 없다"는 것이 곧 `PRIN-P02` 의 구현**이다.
 *
 * **부재는 코드로 표현할 수 없다.** 없는 컬럼과 없는 엔드포인트는 아무 파일에도 안 적힌다 —
 * 테스트로만 고정된다.
 *
 * ## 빨갛게 되면 기능 추가가 아니라 원칙 위반이다
 *
 * Phase 1a 에는 `partner` 도메인이 없어 지금은 통과하기 쉽다. 이 파일이 값을 하는 시점은
 * **1b 에서 `partner_contract` 를 만들 때**다 — 누군가 `boost_limit` 컬럼을 아무 생각 없이
 * 추가하는 순간 여기가 터진다. 그때 이 KDoc 을 읽고 왜 막혔는지 알 수 있어야 한다.
 *
 * **되돌리는 조건**: SPEC-00 `PRIN-P02` 개정 + ADR (SPEC-00 §4). 테스트를 지우는 것이 아니라
 * 원칙을 먼저 고친다. 순서가 반대면 원칙이 코드에 밀린다.
 */
@Tag("boundary")
@SpringBootTest
class ExposureRuleAbsenceTest {

    @Autowired private lateinit var jdbc: JdbcTemplate

    /**
     * 이름으로 못 박는다. 액추에이터가 `controllerEndpointHandlerMapping` 을 같은 타입으로
     * 하나 더 올려서 타입만으로는 모호하다.
     *
     * 액추에이터 쪽을 일부러 보지 않는다 — SPEC-07 §2.6 이 말하는 것은 **API 표면**이고,
     * `actuator` 하위는 운영 경로라 [FORBIDDEN_ROUTES] 의 `config` 같은 조각에 걸려
     * 거짓 양성만 낸다. 규칙이 헛돌면 다음 사람이 목록을 깎는다.
     */
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private lateinit var handlerMapping: RequestMappingHandlerMapping

    // ── RED 1~4 : DB 컬럼 부재 (SPEC-06 §3.4 · §4.4) ───────────────────────

    /**
     * RED 1~3 — `information_schema` 전수 스캔.
     *
     * 테이블을 열거하지 않고 스키마 전체를 훑는 이유: 목록을 적으면 **새 테이블이 목록에서
     * 빠진 채로 들어온다.** 1b 의 `partner_contract` 가 정확히 그 경우다.
     */
    @Test
    fun `RED1,2,3 - 노출 규칙 컬럼이 어느 테이블에도 없다`() {
        val violations = allColumns()
            .filter { (_, column) -> FORBIDDEN_COLUMNS.any { column.contains(it) } }
            .filterNot { (table, column) -> "$table.$column" in ALLOWED_COLUMNS }

        assertThat(violations)
            .`as`(
                "PRIN-P02 — 저장 가능하게 만들면 조정된다 (SPEC-06 §4.4). " +
                    "이 컬럼이 정말 필요하면 SPEC-00 을 먼저 고친다",
            )
            .isEmpty()
    }

    /**
     * RED 3 — **`is_sponsored` 는 통과해야 한다.**
     *
     * 금지되는 것은 "사실의 저장"이 아니라 **"표시 여부의 제어"** 다.
     * `is_sponsored` 는 협찬을 받았다는 **사실**이고, `NFR-L-02` 는 그 사실이 있으면
     * 라벨이 **끌 수 없게** 붙기를 요구한다 — 저장하지 않으면 라벨을 붙일 근거가 없다.
     *
     * 반대로 `show_sponsor_label` 같은 컬럼은 **그 라벨을 끄는 스위치**다.
     * 공정위 심사지침상 의무를 데이터로 무력화하는 장치가 된다.
     *
     * 규칙이 과하게 넓지 않은지 여기서 확인한다 — 넓으면 지키는 사람이 우회한다.
     */
    @Test
    fun `RED3 - 사실을 저장하는 컬럼은 막지 않는다`() {
        val sponsored = allColumns().filter { (_, column) -> column == "is_sponsored" }

        assertAll(
            {
                assertThat(sponsored)
                    .`as`("NFR-L-02 의 근거가 되는 컬럼이라 있어야 한다")
                    .isNotEmpty()
            },
            {
                assertThat(sponsored)
                    .`as`("사실 저장은 금지 목록에 걸리지 않는다")
                    .noneMatch { (_, column) -> FORBIDDEN_COLUMNS.any { column.contains(it) } }
            },
        )
    }

    /**
     * RED 4 — 금지 목록이 코드 상수다. **늘릴 때 리뷰에 보인다.**
     *
     * 목록을 테스트 본문에 인라인으로 흩뿌리면 누가 조용히 한 줄 지워도 diff 에서 눈에 안 띈다.
     * 한곳에 모아 두면 그 파일이 바뀌는 것 자체가 신호다.
     *
     * 허용 목록도 **비어 있지 않은지** 함께 본다 — 예외를 적어 두지 않으면 다음 사람이
     * 규칙을 넓게 이해하고 `search_document.weight` 같은 정상 컬럼까지 피해 다닌다.
     */
    @Test
    fun `RED4 - 금지 목록과 허용 예외가 코드 상수다`() {
        assertAll(
            {
                assertThat(FORBIDDEN_COLUMNS)
                    .`as`("SPEC 이 든 네 가지 규칙을 전부 덮는다")
                    .contains("boost", "slot_ratio", "home_quota", "rank_weight", "show_sponsor", "exposure_")
            },
            {
                // 허용 예외는 실재하는 컬럼만 적는다. 죽은 예외가 쌓이면 목록을 아무도 안 읽는다.
                val existing = allColumns().map { (table, column) -> "$table.$column" }.toSet()
                assertThat(ALLOWED_COLUMNS)
                    .`as`("없는 컬럼을 예외로 적어 두면 규칙이 느슨해 보인다")
                    .allSatisfy { assertThat(existing).contains(it) }
            },
        )
    }

    // ── RED 5~7 : API 엔드포인트 부재 (SPEC-07 §2.6 · SPEC-08 §2) ──────────

    /**
     * RED 5·6·7 — 라우트 전수 스캔. **`admin` 경로에도 없다.**
     *
     * SPEC-08 §2 의 마지막 줄이 "노출 규칙 변경 = 모든 역할이 `—`" 다. `admin` 열도 `—` 라
     * 어드민 경로를 예외로 두지 않는다.
     *
     * RED 7 이 `/admin/settings` 류를 따로 보는 이유: **우회는 이름을 바꿔서 온다.**
     * "부스팅 한도" 라고 적힌 엔드포인트를 만드는 사람은 없고, "설정" 이라고 적는다.
     */
    @Test
    fun `RED5,6,7 - 노출 규칙을 조정하는 엔드포인트가 없다`() {
        val paths = allRoutePaths()

        val offending = paths.filter { path ->
            val lower = path.lowercase()
            FORBIDDEN_ROUTES.any { lower.contains(it) }
        }

        assertAll(
            {
                assertThat(offending)
                    .`as`("SPEC-07 §2.6 — API 표면에 존재하지 않는다")
                    .isEmpty()
            },
            {
                // 규칙이 실제로 도는지. 라우트를 하나도 못 읽으면 위 단언은 공짜로 통과한다.
                assertThat(paths).`as`("라우트를 읽지 못하면 이 테스트는 아무것도 지키지 않는다").isNotEmpty()
            },
        )
    }

    // ── RED 8~9 : 권한 액션 부재 (이슈 006 RED 16) ────────────────────────

    /**
     * RED 8·9 — 액션이 없으면 매트릭스에 행이 생길 수 없다.
     *
     * 이슈 006 의 `PermissionMatrixTest` RED 16 도 같은 것을 본다. 두 곳에서 보는 이유는,
     * **여기가 "노출 규칙 부재"를 찾는 사람이 열어 보는 파일**이기 때문이다 —
     * 원칙 하나를 지키는 단언이 권한 테스트 안에만 있으면 아무도 못 찾는다.
     */
    @Test
    fun `RED8,9 - 권한 액션과 매트릭스 행이 없다`() {
        val suspects = Action.entries.filter { action ->
            FORBIDDEN_ACTIONS.any { action.name.contains(it) }
        }

        assertAll(
            { assertThat(suspects).`as`("액션이 생기는 순간 그 규칙이 존재하게 된다").isEmpty() },
            {
                // 어떤 역할에게도 노출 규칙을 줄 수 없다 — 줄 액션 자체가 없으므로.
                //
                // Phase 1a 액션만 넣는다. `PermissionMatrix.evaluate` 는 1b·2 액션에
                // `require` 로 터지는데, 그것도 의도된 설계다 — 검증되지 않은 판정을
                // 믿게 하느니 부르는 쪽을 깨뜨린다.
                val evaluable = Action.entries.filter { it.isPhase1a }
                assertThat(Role.entries)
                    .allSatisfy { role ->
                        assertThat(evaluable.filter { PermissionMatrix.allows(setOf(role), it) })
                            .`as`("SPEC-08 §2 — %s 열에도 노출 규칙 행이 없다", role)
                            .noneMatch { action -> FORBIDDEN_ACTIONS.any { action.name.contains(it) } }
                    }
            },
        )
    }

    // ── RED 10~13 : 코드 상수 — Phase 1b ──────────────────────────────────

    /**
     * RED 10·11·13 — **Phase 1a 에서는 상수를 정의하지 않는다.**
     *
     * `partner` 도메인이 없어 상수를 쓸 곳이 없고, **쓰이지 않는 상수는 죽은 코드**다.
     * 죽은 상수는 지켜지지도 않는다 — 아무도 부르지 않으니 값이 틀려도 아무 일이 안 일어난다.
     *
     * 해제 시점: Phase 1b `partner` 도메인 (`EPICS-1B-PHASE2.md` — 1a 에서 넘긴 항목 표).
     * 값은 `INV-PARTNER-01` 이 **상위 3개 중 1개**, `INV-PARTNER-02` 가 **30%** 다.
     */
    @Test
    @Disabled("Phase 1b — partner 도메인이 생길 때 상수를 정의한다 (EPICS-1B-PHASE2.md)")
    fun `RED10,11,13 - 부스팅 한도와 홈 슬롯 비율이 코드 상수다`() = Unit

    /**
     * RED 12 — **환경변수로도 못 바꾼다.**
     *
     * 상수 정의(RED 10·11·13)는 1b 로 미뤘지만 **"설정에 없다"는 지금 확인할 수 있고,
     * 지금 확인해야 한다.** 상수보다 설정이 먼저 생기는 순서가 실제로 더 흔하다 —
     * 누군가 급할 때 `application.yml` 에 한 줄 넣는 것이 코드를 고치는 것보다 쉽다.
     *
     * 이슈는 이 항목도 `@Disabled` 로 묶었지만, 지금 검증 가능한 절반을 꺼 둘 이유가 없다.
     */
    @Test
    fun `RED12 - 설정 파일에 노출 규칙 키가 없다`() {
        val configs = listOf(
            Path.of("src/main/resources/application.yml"),
            Path.of("src/test/resources/application-test.yml"),
        ).filter { Files.exists(it) }

        assertAll(
            {
                // 잘라 내기가 헛돌면 빈 문자열만 훑게 된다 — 그러면 무엇을 넣어도 통과한다.
                assertThat(configs.map { ourNamespace(Files.readString(it)) })
                    .`as`("kcocktail 블록을 하나도 못 읽으면 이 테스트는 아무것도 지키지 않는다")
                    .anySatisfy { assertThat(it).isNotBlank() }
            },
        )

        assertAll(
            configs.map<Path, () -> Unit> { path ->
                {
                    val ours = ourNamespace(Files.readString(path)).lowercase()
                    assertThat(FORBIDDEN_CONFIG_KEYS)
                        .`as`("PRIN-P02 — %s 에 노출 규칙이 새어 들어왔다", path)
                        .noneMatch { ours.contains(it) }
                }
            },
        )
    }

    /**
     * `kcocktail:` 블록만 잘라 낸다.
     *
     * 파일 전체를 훑으면 `management.endpoints.web.exposure`(액추에이터 엔드포인트 노출)가
     * `exposure` 조각에 걸린다. **거짓 양성 하나가 목록 전체를 깎게 만든다** —
     * 다음 사람은 규칙을 고치는 대신 `exposure` 를 지우고, 그러면 진짜가 통과한다.
     *
     * 우리 설정만 보는 것이 옳기도 하다. 스프링이 자기 네임스페이스에 무엇을 두든
     * `PRIN-P02` 가 막는 것은 **우리가 우리 노출 규칙을 설정으로 빼는 것**이다.
     */
    private fun ourNamespace(yaml: String): String {
        val lines = yaml.lines()
        val start = lines.indexOfFirst { it.startsWith("kcocktail:") }
        if (start < 0) return ""

        val rest = lines.drop(start + 1)
        // 다음 최상위 키(들여쓰기 0, 주석·빈 줄 아님)까지가 우리 블록이다.
        val end = rest.indexOfFirst { it.isNotBlank() && !it.startsWith(" ") && !it.startsWith("#") }

        return (if (end < 0) rest else rest.take(end))
            // **주석을 걷어낸다.** 규칙이 보는 것은 설정 키이지 산문이 아니다.
            // 이 파일 이름(`ExposureRuleAbsenceTest`)을 주석에 적었다가 `exposure` 에 걸렸다 —
            // 주석에 걸리는 규칙은 다음 사람이 **주석을 지워서** 통과시킨다. 근거가 사라지는 쪽이 더 나쁘다.
            // YAML 규칙대로 **줄 처음이거나 공백 뒤의** `#` 부터가 주석이다.
            // 그냥 `substringBefore('#')` 로 자르면 값 안의 `#`(URL 프래그먼트 등)까지 날아간다.
            .map { it.replace(Regex("(^|\\s)#.*$"), "") }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    // ── RED 14~16 : 라벨 강제 (NFR-L-02 · INV-PARTNER-04) ─────────────────

    /**
     * RED 14·15·16 — **라벨을 끄는 입력이 어디에도 없다.**
     *
     * 이슈 023 의 `IngredientDictionaryApiTest.RED20` 이 **런타임**으로 같은 것을 본다 —
     * 파라미터를 실제로 보내 보고 라벨이 그대로인지 확인한다. 여기서는 **구조**로 본다:
     * 그런 이름의 `@RequestParam` · `@RequestHeader` 가 **선언조차 되어 있지 않은지**.
     *
     * 둘이 필요한 이유가 있다. 런타임 테스트는 **자기가 아는 이름만** 시도한다 —
     * 목록에 없는 새 파라미터가 생기면 조용히 통과한다. 구조 스캔은 반대로,
     * 이름이 무엇이든 선언된 것 전부를 훑는다.
     *
     * `NFR-L-02` 는 배포 차단 조건이고 **공정위 심사지침상 의무**다. 하나라도 통하면
     * "끌 수 없게" 가 무너진다.
     */
    @Test
    fun `RED14,15,16 - 라벨을 억제하는 파라미터나 헤더가 없다`() {
        val inputs = handlerMapping.handlerMethods.values.flatMap { handler ->
            handler.methodParameters.mapNotNull { parameter ->
                val param = parameter.getParameterAnnotation(RequestParam::class.java)
                val header = parameter.getParameterAnnotation(RequestHeader::class.java)
                when {
                    param != null -> param.name.ifBlank { param.value }.ifBlank { parameter.parameterName }
                    header != null -> header.name.ifBlank { header.value }.ifBlank { parameter.parameterName }
                    else -> null
                }?.let { handler.method.declaringClass.simpleName + "." + handler.method.name + "(" + it + ")" to it }
            }
        }

        val offending = inputs.filter { (_, name) ->
            val lower = name.lowercase()
            FORBIDDEN_LABEL_INPUTS.any { lower.contains(it) }
        }

        assertThat(offending.map { it.first })
            .`as`("NFR-L-02 — 라벨은 끌 수 없다. 공정위 심사지침상 의무다")
            .isEmpty()
    }

    // ── 규칙이 실제로 잡는가 ──────────────────────────────────────────────

    /**
     * **부재 테스트는 통과하기 쉽다.** 규칙이 헛돌아도 초록이고, 그 사실이 몇 년 뒤
     * 정말 필요한 순간에 드러난다 — 1b 에서 `partner_contract` 가 들어올 때다.
     *
     * 그래서 목록이 실제로 무는지 합성 입력으로 확인한다. 진짜 위반을 만들 수는 없다 —
     * 만드는 순간 그것이 `PRIN-P02` 위반이다. `ModuleBoundaryTest` 가 fixture 패키지로
     * 하는 일을, 여기서는 컬럼·경로 이름이 문자열이라 값으로 한다.
     */
    @Test
    fun `금지 목록이 실제 위반을 잡는다`() {
        val fakeColumns = listOf(
            "partner_contract" to "boost_limit",      // R-F4.2-2
            "partner_contract" to "home_slot_ratio",  // R-F4.2-4
            "search_document" to "rank_weight_bonus", // R-F3.3-3
            "ingredient_brand" to "show_sponsor_label", // NFR-L-02
        )
        val fakeRoutes = listOf(
            "/api/v1/admin/exposure-rules",
            "/api/v1/admin/settings",
            "/api/v1/admin/partners/{id}/boost",
        )
        val fakeInputs = listOf("hideAdLabel", "showSponsor", "sponsored")

        assertAll(
            {
                assertThat(fakeColumns)
                    .`as`("컬럼 규칙이 헛돈다")
                    .allSatisfy { (_, column) ->
                        assertThat(FORBIDDEN_COLUMNS).anyMatch { column.contains(it) }
                    }
            },
            {
                assertThat(fakeRoutes)
                    .`as`("라우트 규칙이 헛돈다")
                    .allSatisfy { path ->
                        assertThat(FORBIDDEN_ROUTES).anyMatch { path.lowercase().contains(it) }
                    }
            },
            {
                assertThat(fakeInputs)
                    .`as`("라벨 억제 규칙이 헛돈다")
                    .allSatisfy { name ->
                        assertThat(FORBIDDEN_LABEL_INPUTS).anyMatch { name.lowercase().contains(it) }
                    }
            },
            {
                // 반대쪽도 본다. 규칙이 과하게 넓으면 정상 컬럼이 걸리고, 그때 사람은
                // 규칙을 고치는 대신 목록을 깎는다.
                listOf("is_sponsored", "weight", "name_ko", "created_at").forEach { column ->
                    assertThat(FORBIDDEN_COLUMNS)
                        .`as`("정상 컬럼 %s 가 걸린다 — 규칙이 너무 넓다", column)
                        .noneMatch { column.contains(it) }
                }
            },
        )
    }

    // ── RED 17 : 문서 동기화 ──────────────────────────────────────────────

    /**
     * RED 17 — **왜 없는지 읽어서 알 수 있어야 한다.**
     *
     * 부재 테스트의 가장 흔한 죽음은 "이게 왜 막혀 있지" 하고 지우는 것이다.
     * 금지 목록 옆에 근거가 없으면 다음 사람에게는 그냥 방해물로 보인다.
     *
     * 이 테스트가 자기 소스를 읽는 것이 이상해 보일 수 있는데, 검증 대상이 **주석**이라
     * 다른 방법이 없다. 근거를 지우면 빨갛게 되는 것이 목적이다.
     */
    @Test
    fun `RED17 - 금지 목록에 SPEC 인용이 붙어 있다`() {
        val source = Path.of(SOURCE_PATH)
        assertThat(Files.exists(source)).`as`("테스트가 자기 소스를 찾지 못했다: %s", SOURCE_PATH).isTrue()

        val text = Files.readString(source)

        assertAll(
            REQUIRED_CITATIONS.map<String, () -> Unit> { citation ->
                { assertThat(text).`as`("근거 %s 가 사라졌다", citation).contains(citation) }
            } + listOf {
                assertThat(text)
                    .`as`("is_sponsored(허용) 와 표시 제어(금지) 의 구분이 남아 있어야 한다")
                    .contains("is_sponsored")
                    .contains("show_sponsor_label")
            },
        )
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────

    /** `(table, column)` 전수. Flyway 가 만든 것과 Hibernate 가 만든 것을 구분하지 않는다. */
    private fun allColumns(): List<Pair<String, String>> =
        jdbc.queryForList(
            """
            SELECT table_name, column_name
              FROM information_schema.columns
             WHERE table_schema = 'public'
               AND table_name <> 'flyway_schema_history'
            """.trimIndent(),
        ).map { it["table_name"] as String to (it["column_name"] as String).lowercase() }

    private fun allRoutePaths(): List<String> =
        handlerMapping.handlerMethods.keys.flatMap { info ->
            info.pathPatternsCondition?.patternValues
                ?: info.patternsCondition?.patterns
                ?: emptySet()
        }

    companion object {
        private const val SOURCE_PATH =
            "src/test/kotlin/kr/kcocktail/architecture/ExposureRuleAbsenceTest.kt"

        /**
         * **금지 컬럼명 조각** (RED 4).
         *
         * | 조각 | 무엇을 막는가 | 근거 |
         * |---|---|---|
         * | `boost` | 정렬 상위 3개 중 파트너 부스팅 한도 | `R-F4.2-2` · `INV-PARTNER-01` |
         * | `slot_ratio` · `home_quota` | 홈 파트너 슬롯 비율 | `R-F4.2-4` · `INV-PARTNER-02` |
         * | `rank_weight` | 제휴 여부가 순위에 영향 | `R-F3.3-3` · `INV-PARTNER-03` |
         * | `show_sponsor` · `sponsor_label_visible` | 제휴 라벨 표시 제어 | `NFR-L-02` · `INV-PARTNER-04` |
         * | `exposure_` | 이름을 바꿔 오는 우회 | SPEC-06 §3.4 |
         *
         * SPEC-06 §3.4 — "노출 규칙에 해당하는 컬럼을 만들지 않는다. **저장 가능하게 만들면 조정된다.**"
         *
         * 조각으로 두는 이유: `boost_limit` · `partner_boost` · `boosted_until` 을 다 적을 수 없다.
         * 넓게 잡고 예외를 [ALLOWED_COLUMNS] 에 근거와 함께 적는 편이, 좁게 잡고 새는 것보다 낫다.
         */
        private val FORBIDDEN_COLUMNS = listOf(
            "boost",
            "slot_ratio",
            "home_quota",
            "rank_weight",
            "show_sponsor",
            "sponsor_label_visible",
            "exposure_",
            "promote",
            "featured_rank",
        )

        /**
         * 금지 조각에 걸리지만 **성격이 다른** 컬럼. `table.column` 으로 정확히 적는다.
         *
         * 지금은 비어 있다. `search_document.weight` 는 검색 관련도 가중치라 조각과 겹치지 않지만
         * (`rank_weight` 이지 `weight` 가 아니다) 성격을 여기 남겨 둔다 —
         * **1b 에서 "가중치니까 파트너 부스팅도 여기 넣자"가 나올 자리**다.
         * 그 컬럼의 정본은 `SearchEntityType.defaultWeight` 이고 `entity_type` 만 본다.
         * 제휴 여부를 섞으면 `R-F3.3-3`(제휴가 순위에 영향 없음) 위반이다.
         */
        private val ALLOWED_COLUMNS = emptySet<String>()

        /** 라우트 경로 조각 (RED 5·6·7). `/admin/settings` 류 우회를 포함한다. */
        private val FORBIDDEN_ROUTES = listOf(
            "boost", "slot", "quota", "exposure", "ranking-rule", "rank-weight",
            "promotion", "settings", "config", "policy",
        )

        /** 권한 액션 이름 조각 (RED 8·9). 이슈 006 `PermissionMatrixTest` RED 16 과 같은 목록이다. */
        private val FORBIDDEN_ACTIONS = listOf(
            "BOOST", "SLOT", "TIER_LIMIT", "EXPOSURE", "RANK", "PROMOTE", "FEATURE",
        )

        /** 설정 키 조각 (RED 12). **환경변수로도 못 바꾼다** — SPEC-08 §2.1. */
        private val FORBIDDEN_CONFIG_KEYS = listOf(
            "boost", "slot-ratio", "slot_ratio", "home-quota", "home_quota",
            "rank-weight", "exposure",
        )

        /** 라벨 억제 입력 이름 조각 (RED 14·15·16). */
        private val FORBIDDEN_LABEL_INPUTS = listOf(
            "adlabel", "ad-label", "ad_label",
            "hidesponsor", "hide-sponsor", "hide_sponsor",
            "showsponsor", "show-sponsor", "show_sponsor",
            "sponsored", "disclosure",
        )

        /** RED 17 — 이 문구들이 사라지면 다음 사람이 "왜 막혔는지" 알 수 없다. */
        private val REQUIRED_CITATIONS = listOf(
            "PRIN-P02", "FR-ADMIN-006",
            "SPEC-06 §4.4", "SPEC-07 §2.6", "SPEC-08 §2.1",
            "NFR-L-02", "R-F4.2-2", "R-F4.2-4", "R-F3.3-3",
            "INV-PARTNER-01", "INV-PARTNER-02", "INV-PARTNER-03", "INV-PARTNER-04",
        )

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { PostgresSupport.container.jdbcUrl }
            registry.add("spring.datasource.username") { PostgresSupport.container.username }
            registry.add("spring.datasource.password") { PostgresSupport.container.password }
            registry.add("spring.flyway.enabled") { true }
            registry.add("spring.flyway.user") { PostgresSupport.container.username }
            registry.add("spring.flyway.password") { PostgresSupport.container.password }
            registry.add("kcocktail.verification.scheduled") { false }
        }
    }
}
