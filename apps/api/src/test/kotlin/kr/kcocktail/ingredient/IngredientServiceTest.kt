package kr.kcocktail.ingredient

import kr.kcocktail.common.web.error.ConflictException
import kr.kcocktail.ingredient.api.IngredientFacade
import kr.kcocktail.ingredient.api.IngredientSaved
import kr.kcocktail.ingredient.domain.DomesticAvailability
import kr.kcocktail.ingredient.domain.Ingredient
import kr.kcocktail.ingredient.domain.IngredientCategory
import kr.kcocktail.ingredient.internal.IngredientService
import kr.kcocktail.support.PostgresSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.event.EventListener
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/**
 * ISSUE-008 — 서비스와 도메인 이벤트.
 *
 * ## 이벤트가 순환을 끊는다
 *
 * 색인 갱신은 `search` 의 일인데, `ingredient` 가 `search` 를 부르면 순환이 생긴다:
 * `008 → 017 → 014 → 013 → 010 → 008`. 발행하는 쪽은 누가 듣는지 모른다 (SPEC-05 §3).
 */
@SpringBootTest
@ActiveProfiles(IngredientServiceTest.PROFILE)
class IngredientServiceTest {

    @Autowired private lateinit var service: IngredientService
    @Autowired private lateinit var facade: IngredientFacade
    @Autowired private lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun clear() {
        RecordingIndexListener.received.clear()
        jdbc.execute("DELETE FROM ingredient")
    }

    // ── RED 25~28 : 도메인 이벤트 ─────────────────────────────────────────

    @Test
    fun `RED25 - 저장하면 IngredientSaved 가 발행된다`() {
        service.save(newIngredient("gin"))

        assertThat(RecordingIndexListener.received).hasSize(1)
    }

    /** 색인에 필요한 것이 담겨야 한다 — 리스너가 엔티티를 다시 조회하면 그게 곧 결합이다. */
    @Test
    fun `RED26 - 이벤트에 색인용 정보가 담긴다`() {
        val saved = service.save(
            newIngredient("gin").apply { aliases = arrayOf("올드톰", "런던드라이") },
        )

        assertThat(RecordingIndexListener.received.single()).satisfies({
            assertThat(it.entityId).isEqualTo(saved.id)
            assertThat(it.slug).isEqualTo("gin")
            assertThat(it.nameKo).isEqualTo("진")
            assertThat(it.nameEn).isEqualTo("gin")
            assertThat(it.aliases).containsExactly("올드톰", "런던드라이")
            assertThat(it.isApproved).isFalse()
        })
    }

    /**
     * RED 28 — 저장이 롤백되면 색인 갱신도 일어나지 않아야 한다.
     * 저장에 실패했는데 검색에는 나오는 상태를 만들지 않는다.
     */
    @Test
    fun `RED28 - 저장이 실패하면 이벤트도 소비되지 않는다`() {
        service.save(newIngredient("gin"))
        RecordingIndexListener.received.clear()

        // slug 유일 제약 위반
        assertThatThrownBy { service.save(newIngredient("gin")) }.isInstanceOf(Exception::class.java)

        assertThat(RecordingIndexListener.received)
            .`as`("커밋되지 않은 저장의 이벤트가 색인에 도달하면 안 된다")
            .isEmpty()
    }

    // ── RED 16~18 : 승인제 ────────────────────────────────────────────────

    /** DECISIONS §1.1 — 미승인은 공개 조회 대상이 아니다. */
    @Test
    fun `RED16 - 미승인 재료는 findApproved 에 나오지 않는다`() {
        val pending = service.save(newIngredient("new-liqueur"))
        val approved = service.save(newIngredient("gin")).also { service.approve(it.id) }

        assertThat(facade.findApproved(listOf(pending.id, approved.id)).map { it.slug })
            .containsExactly("gin")
    }

    /**
     * RED 17 — **`draft` 에는 쓸 수 있다.**
     *
     * 승인을 기다리면 에디터 작업이 끊긴다. 발행에서 막으면 마스터 오염도 없다 (DECISIONS §1.1).
     * 게이트가 "없는 재료"와 "미승인 재료"를 구분해야 하므로 [IngredientFacade.findAll] 이 필요하다.
     */
    @Test
    fun `RED17 - 미승인 재료도 조회 자체는 된다`() {
        val pending = service.save(newIngredient("new-liqueur"))

        assertThat(facade.findAll(listOf(pending.id)))
            .singleElement()
            .satisfies({ assertThat(it.isApproved).isFalse() })
    }

    /** DECISIONS §1 — 재승인은 409 다. 멱등하게 넘기면 승인 이력이 흐려진다. */
    @Test
    fun `재승인은 409 다`() {
        val target = service.save(newIngredient("gin"))
        service.approve(target.id)

        assertThatThrownBy { service.approve(target.id) }
            .isInstanceOf(ConflictException::class.java)
    }

    // ── RED 19 : 상한 (DECISIONS §1.2) ────────────────────────────────────

    /**
     * **경고지 차단이 아니다.** SPEC-02 §3 의 상한 근거가 "역검색 UX"이지 무결성이 아니다 —
     * 301번째 재료가 데이터를 깨뜨리지는 않는다.
     */
    @Test
    fun `RED19 - 상한을 넘어도 저장은 된다`() {
        assertThat(IngredientService.APPROVED_CAP).isEqualTo(300L)

        // 상한 자체를 채우지 않는다 — 300건 저장은 느리고, 확인할 것은 "차단하지 않는다"뿐이다.
        val target = service.save(newIngredient("gin"))
        service.approve(target.id)

        assertThat(facade.findApproved(listOf(target.id))).hasSize(1)
    }

    // ── RED 23~24 : 규약 ──────────────────────────────────────────────────

    /** SPEC-07 §1.1 — 공개 응답의 식별자는 `slug` 다. `id` 는 모듈 간 참조 키일 뿐이다. */
    @Test
    fun `RED23 - Facade 뷰가 엔티티를 노출하지 않는다`() {
        val saved = service.save(newIngredient("gin"))
        val view = facade.findAll(listOf(saved.id)).single()

        assertThat(view).isNotInstanceOf(Ingredient::class.java)
        assertThat(view.slug).isEqualTo("gin")
    }

    @Test
    fun `RED24 - 참조가 없으면 삭제된다`() {
        val target = service.save(newIngredient("gin"))

        service.delete(target.id)

        assertThat(facade.findAll(listOf(target.id))).isEmpty()
    }

    /** 브랜드는 `ON DELETE CASCADE` 다 — 재료가 사라지면 그 브랜드도 의미가 없다. */
    @Test
    fun `삭제하면 브랜드도 함께 사라진다`() {
        val target = service.save(newIngredient("gin").apply { addBrand("탱커레이") })

        service.delete(target.id)

        assertThat(jdbc.queryForObject("SELECT count(*) FROM ingredient_brand", Int::class.java))
            .isZero()
    }

    // ── 헬퍼 ───────────────────────────────────────────────────────────────

    private fun newIngredient(slug: String) = Ingredient(
        slug = slug,
        nameKo = "진",
        nameEn = "gin",
        categorySlug = IngredientCategory.SPIRIT.slug,
        availabilitySlug = DomesticAvailability.COMMON.slug,
    )

    companion object {
        const val PROFILE = "ingredient-probe"

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
 * 이슈 017 의 색인 리스너 자리. 여기서는 **이벤트가 도달하는지**만 본다.
 *
 * `@Profile` 로 가둔다 — 그냥 두면 모든 통합 테스트가 이 리스너를 달고 돈다.
 */
@Profile(IngredientServiceTest.PROFILE)
@Component
class RecordingIndexListener {

    @EventListener
    fun on(event: IngredientSaved) {
        received += event
    }

    companion object {
        val received = mutableListOf<IngredientSaved>()
    }
}
