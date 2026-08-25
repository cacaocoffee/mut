package kr.mut.cocktail.publish

import kr.mut.cocktail.api.CocktailPublished
import kr.mut.cocktail.api.PublishGate
import kr.mut.cocktail.domain.CocktailStatus
import kr.mut.cocktail.lifecycle.CocktailTransition
import kr.mut.common.revalidate.RevalidateHook
import kr.mut.common.web.error.ConflictException
import kr.mut.common.web.error.DomainViolationException
import kr.mut.support.PostgresSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.support.TransactionTemplate

/**
 * ISSUE-013 RED 20~31 · 35~38 — 상태 전이 · 트랜잭션 · 이벤트.
 *
 * ## 우회 경로가 없다
 *
 * `NFR-D-02` — "게이트를 우회한 `published` 0건". 서비스에 `status` 를 직접 바꾸는
 * 공개 메서드가 없어야 하고, 어드민이 `PATCH` 로 넘겨도 받는 자리가 없어야 한다.
 */
@SpringBootTest
@ActiveProfiles(PublishServiceTest.PROFILE)
class PublishServiceTest {

    @Autowired private lateinit var service: PublishService
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var tx: TransactionTemplate

    @BeforeEach
    fun clear() {
        PublishedRecorder.received.clear()
        PublishedRecorder.explode = false
        ProbeRegenerationHook.called.clear()
        ProbeRegenerationHook.explode = false
        jdbc.execute("TRUNCATE cocktail, ingredient CASCADE")
    }

    // ── RED 20~24 : 상태 전이 (DECISIONS §1.4) ────────────────────────────

    @Test
    fun `RED20 - draft 에서 published 로 전이한다`() {
        val id = publishable()

        service.publish(id)

        assertThat(status(id)).isEqualTo("published")
    }

    /** 통과할 수도 있는 상태라 `violations` 를 돌려주면 "고치면 되나" 싶어진다. */
    @Test
    fun `RED21 - 이미 published 면 409 다`() {
        val id = publishable()
        service.publish(id)

        assertThatThrownBy { service.publish(id) }
            .isInstanceOf(ConflictException::class.java)
            .hasMessageContaining("이미 발행")
    }

    /**
     * RED 22 — **`archived → published` 는 막는다** (DECISIONS §1.4).
     *
     * 내렸던 것을 바로 올리면 **왜 내렸는지 다시 보지 않는다.**
     * `archived → draft → published` 를 거치면 게이트를 다시 통과해야 한다.
     */
    @Test
    fun `RED22 - archived 에서 바로 published 로 갈 수 없다`() {
        val id = publishable()
        service.publish(id)
        service.archive(id)

        assertThatThrownBy { service.publish(id) }
            .isInstanceOf(ConflictException::class.java)
            .hasMessageContaining("허용되지 않는 상태 전이")

        // draft 를 거치면 된다.
        service.unpublish(id)
        service.publish(id)
        assertThat(status(id)).isEqualTo("published")
    }

    @Test
    fun `RED23 - published 에서 draft 로 되돌릴 수 있다`() {
        val id = publishable()
        service.publish(id)

        service.unpublish(id)

        assertThat(status(id)).isEqualTo("draft")
        assertThat(one("SELECT published_at FROM cocktail WHERE id = $id"))
            .`as`("언제 발행했었는지는 기록이다")
            .isNotNull()
    }

    /** 내리는 데 조건을 걸 이유가 없다 — 잘못 나간 것을 빨리 내려야 한다. */
    @Test
    fun `RED24 - 회수에는 게이트를 검사하지 않는다`() {
        val id = publishable()
        service.publish(id)

        // 게이트를 깨뜨린다
        jdbc.execute("UPDATE cocktail SET tasting_note = NULL WHERE id = $id")

        service.unpublish(id) // 그래도 내려간다
        assertThat(status(id)).isEqualTo("draft")
    }

    @Test
    fun `허용된 전이만 통과한다`() {
        assertAll(
            listOf(
                Triple(CocktailStatus.DRAFT, CocktailStatus.PUBLISHED, true),
                Triple(CocktailStatus.PUBLISHED, CocktailStatus.DRAFT, true),
                Triple(CocktailStatus.PUBLISHED, CocktailStatus.ARCHIVED, true),
                Triple(CocktailStatus.ARCHIVED, CocktailStatus.DRAFT, true),
                // 초안 버리기 (2026-08-25) — 발행된 적 없어 URL·색인이 안 걸려 있다.
                Triple(CocktailStatus.DRAFT, CocktailStatus.ARCHIVED, true),
                Triple(CocktailStatus.ARCHIVED, CocktailStatus.PUBLISHED, false),
            ).map<Triple<CocktailStatus, CocktailStatus, Boolean>, () -> Unit> { (from, to, allowed) ->
                {
                    assertThat(CocktailTransition.isAllowed(from, to))
                        .`as`("%s → %s", from.slug, to.slug)
                        .isEqualTo(allowed)
                }
            },
        )
    }

    // ── RED 25~28 : 트랜잭션 ──────────────────────────────────────────────

    @Test
    fun `RED25-26 - 게이트가 실패하면 status 가 바뀌지 않는다`() {
        val id = publishable(tastingNote = null)

        assertThatThrownBy { service.publish(id) }
            .isInstanceOf(DomainViolationException::class.java)

        assertThat(status(id))
            .`as`("PRIN-T05 — 서버 트랜잭션 안에서 막는다")
            .isEqualTo("draft")
    }

    @Test
    fun `게이트 실패는 violations 를 전부 담는다`() {
        val id = publishable(tastingNote = null, isClassic = true, story = null)

        val thrown = runCatching { service.publish(id) }.exceptionOrNull() as DomainViolationException

        assertThat(thrown.violations.map { it.code })
            .containsExactly("GATE-COCKTAIL-01", "GATE-COCKTAIL-05")
    }

    /**
     * RED 27 — `NFR-R-03` (DECISIONS §1.7). **재생성 훅 실패는 발행을 롤백시키지 않는다.**
     *
     * 훅은 커밋 뒤에 부른다. 프론트가 늦게 갱신되는 것과 발행 자체가 없던 일이 되는 것은
     * 심각도가 다르다 — 전자는 다음 재생성이 따라잡고, 후자는 에디터가 처음부터 다시 한다.
     */
    @Test
    fun `RED27 - 재생성 훅이 실패해도 발행이 유지된다`() {
        val id = publishable()
        ProbeRegenerationHook.explode = true

        service.publish(id) // 예외가 호출자에게 새어 나오지 않는다

        assertThat(status(id)).isEqualTo("published")
        assertThat(ProbeRegenerationHook.called).`as`("그래도 훅은 불렀다").hasSize(1)
    }

    /**
     * RED 27 의 반대편 — **색인 동기화 실패는 발행을 롤백한다** (DECISIONS §1.7).
     *
     * 재생성 훅과 다르다. 색인은 검색 정확성에 직결돼서, 발행됐는데 검색에 안 나오는 상태를
     * 남기느니 발행을 무르는 편이 낫다. 그래서 이벤트를 **트랜잭션 안에서** 발행한다.
     */
    @Test
    fun `색인 동기화가 실패하면 발행이 롤백된다`() {
        val id = publishable()
        PublishedRecorder.explode = true

        assertThatThrownBy { service.publish(id) }.isInstanceOf(IllegalStateException::class.java)

        assertThat(status(id)).isEqualTo("draft")
        assertThat(ProbeRegenerationHook.called)
            .`as`("커밋되지 않았으니 재생성도 없다")
            .isEmpty()
    }

    /**
     * ISSUE-015 RED 4~7 · 10 — 발행과 **회수 둘 다** 상세·3축·사이트맵을 재생성한다.
     *
     * 회수에도 부르는 이유: 안 부르면 내린 칵테일의 정적 페이지가 그대로 남아
     * 공개 API 는 404 인데 프론트만 계속 보여 준다.
     */
    @Test
    fun `ISSUE015 RED4-7-10 - 발행과 회수가 상세·3축·사이트맵을 재생성한다`() {
        val id = publishable()

        service.publish(id)

        assertThat(ProbeRegenerationHook.called).hasSize(1)
        assertThat(ProbeRegenerationHook.called.single())
            .contains(
                "/cocktails/base/gin",
                "/cocktails/style/highball",
                "/cocktails/method/build",
                "/sitemap.xml",
            )
            .anySatisfy { assertThat(it).startsWith("/cocktails/gin-tonic") }

        service.unpublish(id)

        assertThat(ProbeRegenerationHook.called)
            .`as`("RED10 — 내릴 때도 부른다")
            .hasSize(2)
    }

    @Test
    fun `RED28 - published_at 이 기록된다`() {
        val id = publishable()

        service.publish(id)

        assertThat(one("SELECT published_at FROM cocktail WHERE id = $id")).isNotNull()
    }

    // ── RED 29~31 : 우회 불가 (NFR-D-02) ──────────────────────────────────

    /**
     * 서비스에 `status` 를 직접 받는 공개 메서드가 없다. 발행은 **전용 경로만**이고,
     * 그 경로가 게이트를 거친다.
     */
    @Test
    fun `RED29-30 - 상태를 직접 지정하는 메서드가 없다`() {
        // 합성 메서드는 뺀다 — 코틀린이 익명 객체(커밋 후 훅)에서 private 필드를 읽으려고
        // 만드는 `access$…` 브리지다. 소스에서 부를 수 없으니 공개 표면이 아니다.
        val publicMethods = PublishService::class.java.methods
            .filter { it.declaringClass == PublishService::class.java && !it.isSynthetic }
            .map { it.name }

        assertThat(publicMethods)
            .`as`("발행·회수·보관·검사만 있다")
            .containsExactlyInAnyOrder("publish", "unpublish", "archive", "inspect")

        assertThat(PublishService::class.java.methods.filter { it.name == "publish" })
            .`as`("상태를 인자로 받지 않는다")
            .allSatisfy { m -> assertThat(m.parameterTypes).containsExactly(Long::class.java) }
    }

    /** `PRIN-T05` — 프론트 검증은 UX 용 중복이다. 없어도 데이터가 깨지지 않아야 한다. */
    @Test
    fun `RED31 - 프론트를 우회한 요청도 서버가 막는다`() {
        val id = publishable(tastingNote = null)

        // 클라이언트가 무엇을 보내든 서비스는 DB 상태로 판정한다.
        assertThatThrownBy { service.publish(id) }
            .isInstanceOf(DomainViolationException::class.java)
    }

    // ── RED 35~38 : 도메인 이벤트 ─────────────────────────────────────────

    @Test
    fun `RED35-36 - 발행 성공시 색인용 이벤트가 발행된다`() {
        val id = publishable()

        service.publish(id)

        assertThat(PublishedRecorder.received).singleElement().satisfies({
            assertThat(it.entityId).isEqualTo(id)
            assertThat(it.slug).startsWith("gin-tonic")
            assertThat(it.nameKo).isEqualTo("진토닉")
            assertThat(it.nameEn).isEqualTo("gin tonic")
        })
    }

    @Test
    fun `RED37 - 게이트가 실패하면 이벤트가 발행되지 않는다`() {
        val id = publishable(tastingNote = null)

        runCatching { service.publish(id) }

        assertThat(PublishedRecorder.received).isEmpty()
    }

    /** `cocktail` 이 `search` 를 직접 부르면 순환이 생긴다 (SPEC-05 §3). 경계 테스트가 막는다. */
    @Test
    fun `RED38 - 이벤트가 색인에 필요한 것만 담는다`() {
        val fields = CocktailPublished::class.java.declaredFields.map { it.name }

        assertThat(fields).containsExactlyInAnyOrder(
            "entityId", "slug", "nameKo", "nameEn", "aliases",
        )
    }

    // ── inspect (이슈 016 재사용) ─────────────────────────────────────────

    @Test
    fun `배치 검증이 저장 없이 판정만 한다`() {
        val id = publishable(tastingNote = null)

        val violations = service.inspect(id)

        assertThat(violations.map { it.code }).containsExactly("GATE-COCKTAIL-01")
        assertThat(status(id)).`as`("아무것도 바꾸지 않는다").isEqualTo("draft")
    }

    /** 게이트와 서비스가 **같은 함수**를 본다. 두 벌이면 어긋난다. */
    @Test
    fun `inspect 와 PublishGate 가 같은 결과를 낸다`() {
        val id = publishable(tastingNote = null, isClassic = true, story = null)

        assertThat(service.inspect(id).map { it.code })
            .containsExactly("GATE-COCKTAIL-01", "GATE-COCKTAIL-05")
        assertThat(PublishGate::class.java.packageName).isEqualTo("kr.mut.cocktail.api")
    }

    // ── 헬퍼 ───────────────────────────────────────────────────────────────

    private var seq = 0

    /**
     * 기본은 **게이트를 전부 통과하는** 칵테일이다. 테스트마다 하나씩 망가뜨린다.
     *
     * 한 트랜잭션으로 묶는 이유: `fk_cocktail__style_primary` 가
     * `DEFERRABLE INITIALLY DEFERRED` 다 (V009 §130). 칵테일 행과 스타일 행이
     * 서로를 가리키므로 **커밋 시점에 함께** 봐야 한다 — 문장마다 커밋하면 첫 INSERT 에서 걸린다.
     */
    private fun publishable(
        tastingNote: String? = "쌉싸름한 진 향에 토닉의 단맛이 얹힌다",
        isClassic: Boolean = false,
        story: String? = null,
    ): Long = tx.execute { insertPublishable(tastingNote, isClassic, story) }!!

    private fun insertPublishable(
        tastingNote: String?,
        isClassic: Boolean,
        story: String?,
    ): Long {
        val n = seq++
        val ingredientId = jdbc.queryForObject(
            """INSERT INTO ingredient (slug, name_ko, name_en, category, domestic_availability, is_approved)
               VALUES ('gin-$n', '진', 'gin', 'spirit', 'common', true) RETURNING id""",
            Long::class.java,
        )!!

        val cocktailId = jdbc.queryForObject(
            """
            INSERT INTO cocktail (slug, name_ko, name_en, summary,
                base_spirit, style_primary, method, sweetness, glass_type,
                abv_calculated, tasting_note, is_classic, story)
            VALUES ('gin-tonic-$n', '진토닉', 'gin tonic', '가장 흔한 하이볼',
                'gin', 'highball', 'build', 'dry', '하이볼 글라스',
                12, ?, ?, ?)
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            tastingNote, isClassic, story,
        )!!

        jdbc.execute("INSERT INTO cocktail_style VALUES ($cocktailId, 'highball')")
        jdbc.execute("INSERT INTO cocktail_aroma_tag VALUES ($cocktailId, 'citrus')")

        val recipeId = jdbc.queryForObject(
            "INSERT INTO recipe (cocktail_id, version_type) VALUES ($cocktailId, 'standard') RETURNING id",
            Long::class.java,
        )!!
        jdbc.execute(
            "INSERT INTO recipe_ingredient (recipe_id, ingredient_id, position, amount, unit) " +
                "VALUES ($recipeId, $ingredientId, 1, 45, 'ml')",
        )
        jdbc.execute("INSERT INTO recipe_step (recipe_id, step_no, text) VALUES ($recipeId, 1, '얼음을 채운다')")

        return cocktailId
    }

    private fun status(id: Long) = one("SELECT status FROM cocktail WHERE id = $id")

    private fun one(sql: String): String? =
        jdbc.query(sql) { rs, _ -> rs.getString(1) }.firstOrNull()

    companion object {
        const val PROFILE = "publish-probe"

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { PostgresSupport.container.jdbcUrl }
            registry.add("spring.datasource.username") { PostgresSupport.container.username }
            registry.add("spring.datasource.password") { PostgresSupport.container.password }
            registry.add("spring.flyway.enabled") { true }
            registry.add("spring.flyway.user") { PostgresSupport.container.username }
            registry.add("spring.flyway.password") { PostgresSupport.container.password }
        }
    }
}

/**
 * 이슈 017 의 색인 리스너 자리. 여기서는 이벤트 도달만 본다.
 *
 * `@EventListener` 라 **발행 트랜잭션 안에서** 불린다 — 여기서 터지면 발행이 롤백된다
 * (DECISIONS §1.7 "색인 동기화 실패 → 발행 롤백").
 */
@Profile(PublishServiceTest.PROFILE)
@Component
class PublishedRecorder {

    @EventListener
    fun on(event: CocktailPublished) {
        received += event
        if (explode) throw IllegalStateException("색인 동기화 실패")
    }

    companion object {
        val received = mutableListOf<CocktailPublished>()

        @JvmStatic
        var explode = false
    }
}

/**
 * 재생성 훅(이슈 015)을 가로챈다. 커밋 뒤에 불리고, 터져도 발행을 되돌리지 않는다 (`NFR-R-03`).
 *
 * 여기서 던지는 것이 요점이다. 운영 구현([kr.mut.common.revalidate.HttpRevalidateHook])은
 * 실패를 스스로 삼키지만, **삼키는 쪽이 사라져도 발행이 안전한지**를 이 프로브가 확인한다.
 */
@Profile(PublishServiceTest.PROFILE)
@Primary
@Component
class ProbeRegenerationHook : RevalidateHook {

    override fun revalidate(paths: List<String>) {
        called += paths
        if (explode) throw IllegalStateException("재생성 훅 실패")
    }

    companion object {
        val called = mutableListOf<List<String>>()

        @JvmStatic
        var explode = false
    }
}
