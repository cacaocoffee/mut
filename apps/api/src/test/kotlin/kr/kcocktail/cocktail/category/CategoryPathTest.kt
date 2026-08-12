package kr.kcocktail.cocktail.category

import com.tngtech.archunit.core.importer.ClassFileImporter
import kr.kcocktail.common.revalidate.RevalidatePaths
import kr.kcocktail.common.revalidate.RevalidateTarget
import kr.kcocktail.common.taxonomy.BaseSpirit
import kr.kcocktail.common.taxonomy.StyleKey
import kr.kcocktail.common.taxonomy.Technique
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * ISSUE-022 RED 4 · 6 · 7 · 8 · 9 · 19 — **구조로 고정되는 것들.**
 *
 * DB 없이 판정한다. `R-C-2`("축 조합 경로를 만들지 않는다")는 데이터가 아니라
 * **시그니처의 문제**라서, 응답을 들여다보는 것만으로는 지켜지지 않는다 —
 * 조합을 만드는 함수가 하나라도 있으면 언젠가 누가 부른다.
 */
class CategoryPathTest {

    // ── RED 4 : 축이 셋뿐이다 (PRIN-P06) ──────────────────────────────────

    /**
     * 당도 · 도수 · 향맛 필드가 **없는 것**이 `PRIN-P06` 의 물리적 구현이다.
     * 넷째 필드를 추가하려면 이 단언이 먼저 빨개진다.
     */
    @Test
    fun `RED4 - 3축 외의 카테고리가 없다`() {
        assertThat(CategoriesResponse::class.java.declaredFields.map { it.name })
            .containsExactlyInAnyOrder("base", "style", "method")

        assertThat(CategoryAxis.entries.map { it.slug })
            .containsExactly("base", "style", "method")

        assertThat(CategoryAxis.entries.map { it.slug })
            .`as`("당도·도수·향맛은 필터다 (PRIN-P06)")
            .doesNotContain("sweet", "sweetness", "abv", "flavor", "aroma")
    }

    // ── RED 6 : ADR-0002 확정 슬러그 ──────────────────────────────────────

    /** `korean`(not `soju`) · `agave` · `non-alcoholic` — 전수 대조다. */
    @Test
    fun `RED6 - slug 가 ADR-0002 확정값이다`() {
        assertThat(CategoryAxis.BASE.taxonomy.map { it.slug }).containsExactly(
            "gin", "vodka", "whisky", "rum", "agave",
            "brandy", "liqueur", "wine", "korean", "non-alcoholic",
        )
        assertThat(CategoryAxis.BASE.taxonomy.map { it.slug })
            .`as`("ADR-0002 §4 — 막걸리·문배주를 소주로 부르는 건 부정확하다")
            .doesNotContain("soju")

        assertThat(CategoryAxis.STYLE.taxonomy.map { it.slug }).containsExactly(
            "highball", "sour", "spirit-forward", "spritz", "tiki",
            "creamy", "hot", "frozen", "shot",
        )
        assertThat(CategoryAxis.METHOD.taxonomy.map { it.slug })
            .containsExactly("build", "shake", "stir", "blend", "etc")
    }

    /** 정본은 `common.taxonomy` 다 (`PRIN-T02`). 여기서 값을 다시 열거하지 않았는지. */
    @Test
    fun `축값의 정본은 taxonomy enum 이다`() {
        assertThat(CategoryAxis.BASE.taxonomy).isEqualTo(BaseSpirit.entries)
        assertThat(CategoryAxis.STYLE.taxonomy).isEqualTo(StyleKey.entries)
        assertThat(CategoryAxis.METHOD.taxonomy).isEqualTo(Technique.entries)
    }

    // ── RED 7 · 9 : 조합 경로가 없다 (FR-COCKTAIL-030 · R-C-2 · NFR-S-03) ─

    /** `/cocktails/<축>/<슬러그>` — 마디가 셋이다. 넷이면 조합 경로다. */
    @Test
    fun `RED7 - 조합 경로가 만들어지지 않는다`() {
        val paths = CategoryAxis.entries.flatMap { axis ->
            axis.taxonomy.map { CategoryPaths.categoryPath(axis, it.slug) }
        }

        assertThat(paths).isNotEmpty()
        assertThat(paths).allSatisfy { path ->
            assertThat(path).matches("^/cocktails/(base|style|method)/[a-z0-9-]+$")
            assertThat(path.trim('/').split("/")).hasSize(3)
        }
        assertThat(paths).contains("/cocktails/base/gin", "/cocktails/style/sour", "/cocktails/method/build")
    }

    /**
     * RED 8 — **조합을 만들 시그니처 자체가 없다.**
     *
     * 축을 둘 이상 받는 함수가 하나라도 생기면 `/cocktails/base/gin/style/sour` 가 한 줄이면 나온다.
     * 응답을 검사하는 RED 7 은 그 함수가 아직 안 불렸을 뿐인 상태를 통과시킨다.
     */
    @Test
    fun `RED8 - 축을 둘 이상 받는 경로 조립 함수가 없다`() {
        val production = ClassFileImporter()
            .withImportOption { !it.contains("/test/") }
            .importPackages("kr.kcocktail")

        assertThat(production.map { it.name })
            .`as`("main 산출물을 실제로 읽었는가")
            .contains(CategoryPaths::class.java.name)

        val twoAxisSignatures = production
            .flatMap { it.methods }
            .filter { m -> m.rawParameterTypes.count { it.name == CategoryAxis::class.java.name } > 1 }
            .map { it.fullName }

        assertThat(twoAxisSignatures)
            .`as`("축 2개를 받는 자리가 있으면 R-C-2 를 어길 길이 열린다")
            .isEmpty()

        // 오버로드도 두지 않는다 — 경로를 만드는 길이 하나여야 규율이 성립한다.
        val builders = CategoryPaths::class.java.declaredMethods.filterNot { it.isSynthetic }
        assertThat(builders).singleElement().satisfies({
            assertThat(it.name).isEqualTo("categoryPath")
            assertThat(it.parameterTypes).containsExactly(CategoryAxis::class.java, String::class.java)
        })
    }

    /**
     * RED 9 — `NFR-S-03` "축 조합 경로가 0개".
     *
     * 사이트맵 생성은 프론트 몫이지만(이슈 039·044), **재료가 되는 경로 집합**은 서버가 낸다.
     * 재생성 훅(이슈 015)이 만드는 경로도 같은 규율 아래 있어야 한다.
     */
    @Test
    fun `RED9 - 사이트맵 재료에 조합 경로가 0개다`() {
        val revalidate = RevalidatePaths.forCocktail(
            RevalidateTarget("gin-tonic", "gin", "highball", "build"),
        )

        assertThat(revalidate.filter { it.startsWith("/cocktails/base/") || it.startsWith("/cocktails/style/") || it.startsWith("/cocktails/method/") })
            .allSatisfy { assertThat(it.trim('/').split("/")).hasSize(3) }

        assertThat(revalidate)
            .`as`("축을 이어 붙인 경로")
            .noneMatch { it.matches(Regex("^/cocktails/(base|style|method)/[^/]+/(base|style|method)/.*")) }
    }

    /** RED 19 — `NFR-S-04`. 3축 경로가 사이트맵 재료에 전부 들어간다. */
    @Test
    fun `RED19 - 3축 경로가 사이트맵 재료에 포함된다`() {
        val revalidate = RevalidatePaths.forCocktail(
            RevalidateTarget("gin-tonic", "gin", "highball", "build"),
        )

        assertThat(revalidate).contains(
            CategoryPaths.categoryPath(CategoryAxis.BASE, "gin"),
            CategoryPaths.categoryPath(CategoryAxis.STYLE, "highball"),
            CategoryPaths.categoryPath(CategoryAxis.METHOD, "build"),
            RevalidatePaths.SITEMAP,
        )
    }
}
