package kr.kcocktail.ingredient

import com.fasterxml.jackson.databind.ObjectMapper
import kr.kcocktail.common.web.ApiPaths
import kr.kcocktail.support.PostgresSupport
import org.junit.jupiter.api.BeforeAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import java.sql.Connection
import java.util.concurrent.atomic.AtomicInteger

/**
 * ISSUE-023 — 재료 사전 조회 API 의 공용 발판.
 *
 * ## 컨테이너를 공유한다
 *
 * `PostgresSupport` 의 PG16 하나를 테스트 전체가 쓴다. 다른 이슈의 테스트도 같은 DB 에
 * 행을 넣으므로 **"테이블에 몇 건"류의 단언을 하지 않는다.** 이 발판이 만드는 행은
 * 전부 [tag] 가 붙은 슬러그를 갖고, 단언은 그 슬러그를 기준으로 한다.
 *
 * ## 두 테스트가 같은 컨텍스트를 쓴다
 *
 * `@SpringBootTest` 는 설정 조합마다 컨텍스트를 따로 만들고 컨텍스트마다 커넥션 풀을 든다.
 * 설정을 이 상위 클래스 한 곳에 두면 하위 테스트들이 같은 컨텍스트를 재사용한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class IngredientApiSupport {

    @Autowired protected lateinit var mvc: MockMvc
    @Autowired protected lateinit var json: ObjectMapper

    // ── 응답 읽기 ──────────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    protected fun bodyOf(result: MvcResult): Map<String, Any> = json
        .readValue(result.response.getContentAsString(Charsets.UTF_8), Map::class.java)
        as Map<String, Any>

    @Suppress("UNCHECKED_CAST")
    protected fun itemsOf(result: MvcResult): List<Map<String, Any>> =
        bodyOf(result)["items"] as List<Map<String, Any>>

    @Suppress("UNCHECKED_CAST")
    protected fun pageOf(result: MvcResult): Map<String, Any> =
        bodyOf(result)["page"] as Map<String, Any>

    protected fun slugsOf(result: MvcResult): List<String> =
        itemsOf(result).map { it["slug"] as String }

    // ── 픽스처 ────────────────────────────────────────────────────────────

    /** 이 실행에서 만든 행만 골라내기 위한 접미사. */
    protected fun tag(name: String): String = "$name-${seq.incrementAndGet()}"

    protected fun insertIngredient(
        slug: String,
        category: String = "spirit",
        availability: String = "common",
        approved: Boolean = true,
        abv: String? = "40.0",
        description: String? = "테스트 재료 설명",
        substituteNote: String? = null,
        priceBand: String? = "mid",
        aliases: List<String> = emptyList(),
    ): Long = conn().use { c ->
        c.prepareStatement(
            """
            INSERT INTO ingredient
                (slug, name_ko, name_en, category, domestic_availability,
                 is_approved, abv, description, substitute_note, price_band, aliases)
            VALUES (?, ?, ?, ?, ?, ?, ?::numeric, ?, ?, ?, ?::text[])
            RETURNING id
            """.trimIndent(),
        ).use { st ->
            st.setString(1, slug)
            st.setString(2, "재료 $slug")
            st.setString(3, "ingredient $slug")
            st.setString(4, category)
            st.setString(5, availability)
            st.setBoolean(6, approved)
            st.setString(7, abv)
            st.setString(8, description)
            st.setString(9, substituteNote)
            st.setString(10, priceBand)
            st.setString(11, aliases.joinToString(",", "{", "}") { "\"$it\"" })
            st.executeQuery().use { rs -> rs.next(); rs.getLong(1) }
        }
    }

    /**
     * ⚠️ `isSponsored = true` 로 만든 행은 **반드시 [deleteBrand] 로 지운다.**
     *
     * `IngredientSchemaTest` 가 "1a 데이터에 켜진 브랜드가 0건"을 테이블 전체로 확인한다
     * (이슈 008 DoD · `NFR-L-05`). 공유 컨테이너라 남겨 두면 남의 테스트를 빨갛게 만든다.
     */
    protected fun insertBrand(
        ingredientId: Long,
        name: String,
        purchaseUrl: String? = null,
        isSponsored: Boolean = false,
    ): Long = conn().use { c ->
        c.prepareStatement(
            """
            INSERT INTO ingredient_brand (ingredient_id, name, purchase_url, is_sponsored)
            VALUES (?, ?, ?, ?) RETURNING id
            """.trimIndent(),
        ).use { st ->
            st.setLong(1, ingredientId)
            st.setString(2, name)
            st.setString(3, purchaseUrl)
            st.setBoolean(4, isSponsored)
            st.executeQuery().use { rs -> rs.next(); rs.getLong(1) }
        }
    }

    protected fun deleteBrand(id: Long) = exec("DELETE FROM ingredient_brand WHERE id = $id")

    protected fun insertCocktail(slug: String, status: String = "published"): Long = conn().use { c ->
        c.autoCommit = false
        val id = c.prepareStatement(
            """
            INSERT INTO cocktail (slug, name_ko, name_en, summary,
                base_spirit, style_primary, method, sweetness, glass_type, abv_calculated, status)
            VALUES (?, ?, ?, ?, 'gin', 'highball', 'build', 'dry', '하이볼 글라스', 12, ?)
            RETURNING id
            """.trimIndent(),
        ).use { st ->
            st.setString(1, slug)
            st.setString(2, "칵테일 $slug")
            st.setString(3, "cocktail $slug")
            st.setString(4, "요약 $slug")
            st.setString(5, status)
            st.executeQuery().use { rs -> rs.next(); rs.getLong(1) }
        }
        // style_primary ∈ styles 를 강제하는 복합 FK 는 DEFERRED 라 같은 트랜잭션에서 채운다.
        c.createStatement().use { it.execute("INSERT INTO cocktail_style VALUES ($id, 'highball')") }
        c.commit()
        id
    }

    protected fun insertRecipe(
        cocktailId: Long,
        versionType: String = "standard",
        barId: Long? = null,
    ): Long = conn().use { c ->
        c.createStatement().use { st ->
            st.executeQuery(
                """
                INSERT INTO recipe (cocktail_id, version_type, author_bar_id)
                VALUES ($cocktailId, '$versionType', ${barId ?: "NULL"})
                RETURNING id
                """.trimIndent(),
            ).use { rs -> rs.next(); rs.getLong(1) }
        }
    }

    protected fun insertRecipeIngredient(
        recipeId: Long,
        ingredientId: Long,
        position: Int = 1,
        isOptional: Boolean = false,
        substituteIngredientId: Long? = null,
    ) = exec(
        """
        INSERT INTO recipe_ingredient
            (recipe_id, ingredient_id, position, is_optional, substitute_ingredient_id)
        VALUES ($recipeId, $ingredientId, $position, $isOptional, ${substituteIngredientId ?: "NULL"})
        """.trimIndent(),
    )

    /** 응답에 나온 슬러그가 정말 승인된 것인지 DB 로 되짚는다. */
    protected fun approvedSlugs(slugs: Collection<String>): Set<String> =
        if (slugs.isEmpty()) emptySet()
        else rows(
            "SELECT slug FROM ingredient WHERE is_approved = true AND slug IN " +
                slugs.joinToString("', '", "('", "')"),
        ).toSet()

    protected fun conn(): Connection = PostgresSupport.migrateConnection()

    protected fun exec(sql: String) = conn().use { it.createStatement().use { st -> st.execute(sql) } }

    protected fun rows(sql: String): List<String> = conn().use { c ->
        c.createStatement().use { st ->
            st.executeQuery(sql).use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
        }
    }

    companion object {
        const val BASE = "${ApiPaths.BASE}/ingredients"

        private val seq = AtomicInteger(0)

        @JvmStatic
        @BeforeAll
        fun migrate() {
            PostgresSupport.flyway
        }

        @JvmStatic
        @DynamicPropertySource
        fun datasource(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { PostgresSupport.container.jdbcUrl }
            registry.add("spring.datasource.username") { PostgresSupport.container.username }
            registry.add("spring.datasource.password") { PostgresSupport.container.password }
            registry.add("spring.flyway.enabled") { true }
            registry.add("spring.flyway.user") { PostgresSupport.container.username }
            registry.add("spring.flyway.password") { PostgresSupport.container.password }
            registry.add("spring.jpa.hibernate.ddl-auto") { "none" }
        }
    }
}
