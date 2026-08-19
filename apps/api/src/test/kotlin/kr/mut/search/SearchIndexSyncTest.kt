package kr.mut.search

import kr.mut.cocktail.api.CocktailArchived
import kr.mut.cocktail.api.CocktailPublished
import kr.mut.cocktail.api.CocktailRenamed
import kr.mut.cocktail.api.CocktailUnpublished
import kr.mut.cocktail.lifecycle.CocktailFixture
import kr.mut.cocktail.lifecycle.CocktailLifecycleService
import kr.mut.cocktail.publish.PublishService
import kr.mut.ingredient.api.IngredientSaved
import kr.mut.ingredient.domain.DomesticAvailability
import kr.mut.ingredient.domain.Ingredient
import kr.mut.ingredient.domain.IngredientCategory
import kr.mut.ingredient.internal.IngredientService
import kr.mut.search.api.SearchDocumentDraft
import kr.mut.search.api.SearchEntityType
import kr.mut.search.api.SearchIndexSync
import kr.mut.search.index.JdbcSearchIndexSync
import kr.mut.search.index.SearchIndexListener
import kr.mut.support.PostgresSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.support.TransactionTemplate

/**
 * ISSUE-017 RED 10~23 · 27~28 — `search_document` 동기화 (SPEC-06 §3.8, SPEC-07 §3.4).
 *
 * ## 색인은 구독한다. 호출당하지 않는다
 *
 * `cocktail` · `ingredient` 는 `search` 를 모른다. 알면 순환이 생긴다
 * (`008 → 017 → 014 → 013 → 010 → 008`). 그래서 이 테스트는 **도메인의 정상 경로**
 * (`PublishService.publish` · `IngredientService.save`) 를 태우고 색인이 따라오는지 본다 —
 * 리스너를 직접 부르면 배선이 끊겨도 초록이다.
 *
 * ## 조회 API 는 여기서 만들지 않는다
 *
 * "매칭된다" 는 단언은 전부 SQL 로 한다 (RED 20 · 22 · 23). 검색 엔드포인트와
 * 자동완성은 이슈 024 다 — 여기서 만들면 그쪽이 두 벌을 상대한다.
 */
@SpringBootTest
@ActiveProfiles(SearchIndexSyncTest.PROFILE)
class SearchIndexSyncTest {

    @Autowired private lateinit var publish: PublishService
    @Autowired private lateinit var lifecycle: CocktailLifecycleService
    @Autowired private lateinit var ingredients: IngredientService
    @Autowired private lateinit var sync: SearchIndexSync
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var tx: TransactionTemplate

    private lateinit var cocktails: CocktailFixture

    @BeforeEach
    fun clear() {
        ExplodingIndexSync.explode = false
        jdbc.execute("TRUNCATE cocktail, ingredient CASCADE")
        jdbc.execute("TRUNCATE search_document")
        cocktails = CocktailFixture(jdbc, tx)
    }

    // ── RED 10~12 : 발행 · 회수 ───────────────────────────────────────────

    @Test
    fun `RED10 - 칵테일 저장시 search_document 가 동기화된다`() {
        val id = cocktails.cocktail(slug = "margarita", nameKo = "마르가리타", nameEn = "margarita")

        publish.publish(id)

        assertThat(doc("cocktail", id)).isNotNull().satisfies({
            assertThat(it!!["slug"]).isEqualTo("margarita")
            assertThat(it["name_ko"]).isEqualTo("마르가리타")
            assertThat(it["chosung"]).isEqualTo("ㅁㄹㄱㄹㅌ")
        })
    }

    @Test
    fun `RED11 - 발행시 is_published 가 true 가 된다`() {
        val id = cocktails.cocktail(slug = "margarita")

        publish.publish(id)

        assertThat(doc("cocktail", id)!!["is_published"]).isEqualTo(true)
    }

    /**
     * RED 12 — **행을 지우지 않는다.** `is_published` 를 내린다.
     *
     * 지우면 회수 상태에서 이름을 고쳤을 때 색인이 따라오지 않고, 다시 발행하는 순간
     * 낡은 이름이 그대로 검색에 뜬다. 공개 노출은 조회 쪽이 이 플래그로 거른다 (이슈 024).
     */
    @Test
    fun `RED12 - 회수시 is_published 가 false 가 된다`() {
        val id = cocktails.cocktail(slug = "margarita")
        publish.publish(id)

        publish.unpublish(id)

        assertThat(doc("cocktail", id)).isNotNull().satisfies({
            assertThat(it!!["is_published"]).isEqualTo(false)
        })
    }

    /** 보관도 공개 API 에서 404 다 (SPEC-07 §5). 회수와 같이 내린다. */
    @Test
    fun `보관시에도 is_published 가 false 가 된다`() {
        val id = cocktails.cocktail(slug = "margarita")
        publish.publish(id)

        publish.archive(id)

        assertThat(doc("cocktail", id)!!["is_published"]).isEqualTo(false)
    }

    // ── RED 13~14 : 이름 · 별칭 변경 ──────────────────────────────────────

    /** 발행 상태가 그대로여도 색인은 바뀌어야 한다 — 안 그러면 바뀐 이름으로 못 찾는다. */
    @Test
    fun `RED13 - 이름 변경시 색인이 갱신된다`() {
        val id = cocktails.cocktail(slug = "margarita", nameKo = "마가리타", nameEn = "margarita")
        publish.publish(id)

        lifecycle.rename(id, nameKo = "마르가리타", nameEn = "margarita", aliases = emptyList())

        assertThat(doc("cocktail", id)).isNotNull().satisfies({
            assertThat(it!!["name_ko"]).isEqualTo("마르가리타")
            assertThat(it["chosung"]).isEqualTo("ㅁㄹㄱㄹㅌ")
            assertThat(it["is_published"]).`as`("이름만 바뀌었다. 발행 상태는 그대로다").isEqualTo(true)
        })
    }

    /** 이슈 012 RED 16 연계 — 별칭은 검색어라 색인이 유일한 소비처다. */
    @Test
    fun `RED14 - 별칭 변경시 색인이 갱신된다`() {
        val id = cocktails.cocktail(slug = "old-fashioned", nameKo = "올드패션드", nameEn = "old fashioned")
        publish.publish(id)

        lifecycle.rename(
            id,
            nameKo = "올드패션드",
            nameEn = "old fashioned",
            aliases = listOf("올드 패션드", "올패"),
        )

        assertThat(aliases("cocktail", id)).containsExactly("올드 패션드", "올패")
        assertThat(doc("cocktail", id)!!["chosung"])
            .`as`("별칭도 초성으로 분해돼 들어간다. 이름과 겹치는 값은 한 번만 (`올드 패션드` → `올드패션드`)")
            .isEqualTo("ㅇㄷㅍㅅㄷ ㅇㅍ")
    }

    // ── RED 15 : 재료 ─────────────────────────────────────────────────────

    @Test
    fun `RED15 - 재료 저장시에도 동기화된다`() {
        val saved = ingredients.save(newIngredient("gin"))

        assertThat(doc("ingredient", saved.id)).isNotNull().satisfies({
            assertThat(it!!["slug"]).isEqualTo("gin")
            assertThat(it["name_ko"]).isEqualTo("진")
            assertThat(it["chosung"]).isEqualTo("ㅈ")
        })
    }

    /**
     * `IngredientSaved` 는 "미승인 재료는 색인하지 않는다" 고 적었다. 그것을 **행 삭제가 아니라
     * `is_published = false`** 로 실현한다 — 승인 대기 중의 이름 변경을 따라가야
     * 승인되는 순간 최신 이름으로 검색된다. 공개 노출은 어느 쪽이든 막힌다.
     */
    @Test
    fun `미승인 재료는 is_published 가 false 다`() {
        val saved = ingredients.save(newIngredient("gin"))
        assertThat(doc("ingredient", saved.id)!!["is_published"]).isEqualTo(false)

        ingredients.approve(saved.id)

        assertThat(doc("ingredient", saved.id)!!["is_published"]).isEqualTo(true)
    }

    // ── RED 17 : 멱등 ─────────────────────────────────────────────────────

    @Test
    fun `RED17 - 동기화가 멱등하다`() {
        val draft = draft(entityId = 42L, nameKo = "마르가리타")

        tx.executeWithoutResult { sync.index(draft) }
        tx.executeWithoutResult { sync.index(draft) }
        tx.executeWithoutResult { sync.index(draft) }

        assertThat(count("cocktail", 42L)).isEqualTo(1)
    }

    // ── RED 18 : 실패는 발행을 롤백시킨다 (DECISIONS §1.7) ────────────────

    /**
     * 재생성 훅(`NFR-R-03`)과 **갈리는 지점**이다. 훅은 실패해도 발행이 남지만
     * 색인은 검색 정확성에 직결돼서, 발행됐는데 검색에 안 나오는 상태를 남기느니
     * 발행을 무른다. 그래서 리스너가 `@EventListener` 다 — 발행 트랜잭션 안에서 돈다.
     */
    @Test
    fun `RED18 - 동기화 실패가 발행을 롤백시킨다`() {
        val id = cocktails.cocktail(slug = "margarita")
        ExplodingIndexSync.explode = true

        assertThatThrownBy { publish.publish(id) }.isInstanceOf(IllegalStateException::class.java)

        assertThat(status(id)).isEqualTo("draft")
        assertThat(count("cocktail", id)).isZero()
    }

    // ── RED 19~23 : 별칭 색인 (`FR-SEARCH-006` · `R-F2.1-3`) ──────────────

    @Test
    fun `RED19 - aliases 가 색인에 복사된다`() {
        tx.executeWithoutResult {
            sync.index(draft(entityId = 1L, nameKo = "올드패션드", aliases = listOf("올드 패션드", "올패")))
        }

        assertThat(aliases("cocktail", 1L)).containsExactly("올드 패션드", "올패")
    }

    @Test
    fun `RED20 - name_ko 와 name_en 도 매칭 대상이다`() {
        tx.executeWithoutResult {
            sync.index(draft(entityId = 1L, nameKo = "올드패션드", nameEn = "Old Fashioned"))
        }

        assertThat(matches("name_ko ILIKE '%올드%'")).containsExactly(SLUG)
        assertThat(matches("name_en ILIKE '%fashioned%'")).containsExactly(SLUG)
    }

    /** 이슈 012 RED 14 의 처리 지점 — 저장은 관대하게, 색인은 엄격하게 (`AliasNormalizer`). */
    @Test
    fun `RED21 - 이름과 중복되는 별칭이 제거된다`() {
        tx.executeWithoutResult {
            sync.index(
                draft(
                    entityId = 1L,
                    nameKo = "올드패션드",
                    nameEn = "Old Fashioned",
                    aliases = listOf("올드패션드", " Old Fashioned ", "올패", "올패"),
                ),
            )
        }

        assertThat(aliases("cocktail", 1L))
            .`as`("이름과 겹치는 것 · 빈 것 · 중복을 걷어낸다")
            .containsExactly("올패")
    }

    /** DECISIONS §1.9 — "띄어쓰기 변형은 정규화 매칭 + 에디터 별칭 양쪽". */
    @Test
    fun `RED22 - 띄어쓰기 변형이 별칭으로 저장돼 있으면 매칭된다`() {
        tx.executeWithoutResult {
            sync.index(draft(entityId = 1L, nameKo = "올드패션드", aliases = listOf("올드 패션드")))
        }

        assertThat(matches("'올드 패션드' = ANY (aliases)")).containsExactly(SLUG)
        assertThat(matches("chosung LIKE '%ㅇㄷㅍㅅㄷ%'"))
            .`as`("띄어쓰기가 달라도 초성은 같은 값으로 모인다 (RED 4)")
            .containsExactly(SLUG)
    }

    @Test
    fun `RED23 - 축약형이 매칭된다`() {
        tx.executeWithoutResult {
            sync.index(draft(entityId = 1L, nameKo = "올드패션드", aliases = listOf("올패")))
        }

        assertThat(matches("'올패' = ANY (aliases)")).containsExactly(SLUG)
        assertThat(matches("chosung LIKE '%ㅇㅍ%'")).containsExactly(SLUG)
    }

    // ── RED 27~28 : 가중치 · 범위 ─────────────────────────────────────────

    /** 산정식은 미정이다 (SPEC-06 §7 · G-13). DECISIONS §1.9 — `entity_type` 별 고정값. */
    @Test
    fun `RED27 - weight 기본값이 있다`() {
        val cocktailId = cocktails.cocktail(slug = "margarita")
        publish.publish(cocktailId)
        val ingredientId = ingredients.save(newIngredient("gin")).id

        assertThat(weight("cocktail", cocktailId)).isEqualTo(100)
        assertThat(weight("ingredient", ingredientId)).isEqualTo(50)

        assertThat(SearchEntityType.entries.associate { it.slug to it.defaultWeight })
            .containsExactlyInAnyOrderEntriesOf(
                mapOf("cocktail" to 100, "bar" to 80, "ingredient" to 50, "article" to 30),
            )
    }

    /** `bar` 는 Phase 1b, `article` 은 Phase 2 다. 지금 색인하면 빈 결과를 그리게 된다. */
    @Test
    fun `RED28 - Phase 1a 는 cocktail 과 ingredient 만 색인한다`() {
        cocktails.cocktail(slug = "margarita").also { publish.publish(it) }
        ingredients.save(newIngredient("gin"))

        assertThat(jdbc.queryForList("SELECT DISTINCT entity_type FROM search_document", String::class.java))
            .containsExactlyInAnyOrder("cocktail", "ingredient")

        val subscribed = SearchIndexListener::class.java.declaredMethods
            .filter { it.name == "on" }
            .flatMap { it.parameterTypes.toList() }
            .toSet()

        assertThat(subscribed).containsExactlyInAnyOrder(
            CocktailPublished::class.java,
            CocktailUnpublished::class.java,
            CocktailArchived::class.java,
            CocktailRenamed::class.java,
            IngredientSaved::class.java,
        )
    }

    // ── 헬퍼 ───────────────────────────────────────────────────────────────

    private fun draft(
        entityId: Long,
        nameKo: String,
        nameEn: String? = "margarita",
        aliases: List<String> = emptyList(),
    ) = SearchDocumentDraft(
        type = SearchEntityType.COCKTAIL,
        entityId = entityId,
        slug = SLUG,
        nameKo = nameKo,
        nameEn = nameEn,
        aliases = aliases,
        isPublished = true,
    )

    private fun newIngredient(slug: String) = Ingredient(
        slug = slug,
        nameKo = "진",
        nameEn = "gin",
        categorySlug = IngredientCategory.SPIRIT.slug,
        availabilitySlug = DomesticAvailability.COMMON.slug,
    )

    private fun doc(type: String, entityId: Long): Map<String, Any?>? =
        jdbc.queryForList(
            "SELECT * FROM search_document WHERE entity_type = ? AND entity_id = ?",
            type, entityId,
        ).firstOrNull()

    private fun aliases(type: String, entityId: Long): List<String> =
        jdbc.query(
            "SELECT aliases FROM search_document WHERE entity_type = ? AND entity_id = ?",
            { rs, _ -> @Suppress("UNCHECKED_CAST") (rs.getArray(1).array as Array<String>).toList() },
            type, entityId,
        ).firstOrNull().orEmpty()

    private fun weight(type: String, entityId: Long): Int =
        jdbc.queryForObject(
            "SELECT weight FROM search_document WHERE entity_type = ? AND entity_id = ?",
            Int::class.java, type, entityId,
        )!!

    private fun matches(predicate: String): List<String> =
        jdbc.queryForList("SELECT slug FROM search_document WHERE $predicate", String::class.java)

    private fun count(type: String, entityId: Long): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM search_document WHERE entity_type = ? AND entity_id = ?",
            Int::class.java, type, entityId,
        )!!

    private fun status(id: Long): String? =
        jdbc.queryForList("SELECT status FROM cocktail WHERE id = ?", String::class.java, id).firstOrNull()

    companion object {
        const val PROFILE = "search-index-probe"
        private const val SLUG = "old-fashioned"

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
 * RED 18 — 색인이 터지는 상황을 만든다.
 *
 * 평소에는 진짜 구현에 그대로 넘긴다. 스텁으로 대체하면 나머지 RED 가 전부
 * "테스트용 구현" 을 검사하게 된다.
 */
@Profile(SearchIndexSyncTest.PROFILE)
@Primary
@Component
class ExplodingIndexSync(private val real: JdbcSearchIndexSync) : SearchIndexSync {

    override fun index(draft: SearchDocumentDraft) {
        if (explode) throw IllegalStateException("색인 동기화 실패")
        real.index(draft)
    }

    override fun setPublished(type: SearchEntityType, entityId: Long, isPublished: Boolean) {
        if (explode) throw IllegalStateException("색인 동기화 실패")
        real.setPublished(type, entityId, isPublished)
    }

    companion object {
        @JvmStatic
        var explode = false
    }
}
