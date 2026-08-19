package kr.mut.cocktail.api

import kr.mut.common.taxonomy.FlavorKey
import kr.mut.common.taxonomy.StyleKey
import kr.mut.common.web.error.Violation
import kr.mut.common.web.error.ViolationCode

/**
 * SPEC-02 §2.3 발행 게이트 6종. **경고가 아니라 차단이다** (`PRIN-P03` · `PRIN-T05`).
 *
 * ## 왜 `cocktail.api` 에 있나
 *
 * 이슈 016(불변식 배치 검증)이 `admin` 모듈에서 이것을 호출한다.
 * `cocktail/publish/…` 안에만 두면 **모듈 경계 테스트(001)가 016 을 막는다** (`PRIN-T03`).
 *
 * ## 순수 함수여야 하는 이유
 *
 * 발행 시점(여기)과 배치 검증(016)이 **같은 규칙**을 본다.
 * 두 벌 구현하면 반드시 어긋나고, `NFR-D-02`("게이트를 우회한 `published` 0건")가
 * 그 어긋남을 못 잡는다 — 검사하는 쪽이 틀렸으니까.
 *
 * ## 전부 모아서 돌려준다
 *
 * `require`·early return 을 쓰지 않는다. `FR-ADMIN-003` 이 "하나씩 고치게 하지 않는다"고 했고,
 * 6개가 한 번에 실패할 수 있다 — 에디터가 저장·실패를 여섯 번 반복하게 하지 않는다.
 */
object PublishGate {

    /**
     * @return 빈 리스트면 발행 가능. **게이트 번호순**이라 순서가 결정론적이다 (RED 19)
     */
    fun check(candidate: PublishCandidate): List<Violation> = buildList {
        addAll(gate01TastingNote(candidate))
        addAll(gate02Axes(candidate))
        addAll(gate03StandardRecipe(candidate))
        addAll(gate04IngredientMaster(candidate))
        addAll(gate05ClassicStory(candidate))
        addAll(gate06Substitute(candidate))
    }

    fun canPublish(candidate: PublishCandidate): Boolean = check(candidate).isEmpty()

    /**
     * `GATE-COCKTAIL-01` (`R-F1.1-2`) — 향과 맛 서술이 비면 발행 불가.
     *
     * `PRIN-P03` 의 핵심이다. 다른 사이트 설명을 옮기면 레시피 나열형 블로그와 구별되지 않고,
     * **그 순간 이 서비스의 존재 이유가 사라진다.**
     */
    private fun gate01TastingNote(c: PublishCandidate) = buildList {
        if (c.tastingNote.isNullOrBlank()) {
            add(
                Violation.of(
                    ViolationCode.GATE_COCKTAIL_01,
                    "향과 맛 서술은 발행 필수입니다. 직접 만들어 보고 쓴 내용이어야 합니다.",
                    "tastingNote",
                ),
            )
        }
    }

    /** `GATE-COCKTAIL-02` (`R-C-1`) — 분류 3축 불변식 전부 통과. */
    private fun gate02Axes(c: PublishCandidate) = buildList {
        if (c.styles.isEmpty() || c.stylePrimary !in c.styles ||
            c.aromaTags.size !in MIN_AROMA..MAX_AROMA
        ) {
            add(
                Violation.of(
                    ViolationCode.GATE_COCKTAIL_02,
                    "분류 3축이 완성되지 않았습니다. 스타일과 향·맛 태그를 확인해 주세요.",
                    "axes",
                ),
            )
        }
    }

    /** `GATE-COCKTAIL-03` — 표준 레시피가 재료 1개 이상 · 스텝 1개 이상. */
    private fun gate03StandardRecipe(c: PublishCandidate) = buildList {
        val recipe = c.standardRecipe
        if (recipe == null) {
            add(
                Violation.of(
                    ViolationCode.GATE_COCKTAIL_03,
                    "표준 레시피가 없습니다.",
                    "standardRecipe",
                ),
            )
            return@buildList
        }
        if (recipe.ingredients.isEmpty() || recipe.stepCount == 0) {
            add(
                Violation.of(
                    ViolationCode.GATE_COCKTAIL_03,
                    "표준 레시피에 재료와 만드는 순서가 각각 하나 이상 필요합니다.",
                    "standardRecipe",
                ),
            )
        }
    }

    /**
     * `GATE-COCKTAIL-04` (`R-F1.1-1`) — 모든 재료가 마스터 참조.
     *
     * FK 가 이미 존재를 보장하지만 게이트가 **승인 여부**를 재확인한다 —
     * 미승인 재료는 `draft` 에는 쓸 수 있고 발행에서 막힌다 (DECISIONS §1.1).
     */
    private fun gate04IngredientMaster(c: PublishCandidate) = buildList {
        val unapproved = c.ingredients.filterNot { it.isApproved }
        if (unapproved.isNotEmpty()) {
            add(
                Violation.of(
                    ViolationCode.GATE_COCKTAIL_04,
                    "승인되지 않은 재료가 있습니다: ${unapproved.joinToString { it.slug }}",
                    "ingredients",
                ),
            )
        }
    }

    /** `GATE-COCKTAIL-05` (`R-F1.1-3`) — 클래식이면 `story` 필수. */
    private fun gate05ClassicStory(c: PublishCandidate) = buildList {
        if (c.isClassic && c.story.isNullOrBlank()) {
            add(
                Violation.of(
                    ViolationCode.GATE_COCKTAIL_05,
                    "클래식으로 분류된 항목은 관련 이야기가 필요합니다.",
                    "story",
                ),
            )
        }
    }

    /**
     * `GATE-COCKTAIL-06` (`R-F1.3-2`) — 국내 미유통 재료가 있으면 대체재 명시.
     *
     * **대체 재료 지정 또는 안내 문구, 둘 중 하나면 충족**한다. 둘 다 "대신 이렇게 하세요"를
     * 전하고, 자가제조처럼 대체 재료가 없는 경우도 있다.
     *
     * 재료 마스터의 `substitute_note`(`INV-INGREDIENT-01`)와 다른 층이다 —
     * 그쪽은 "이 재료의 일반적 대안", 여기는 "이 레시피에서 무엇으로 바꿀지"다.
     */
    private fun gate06Substitute(c: PublishCandidate) = buildList {
        val missing = c.ingredients
            .filter { it.requiresSubstitute }
            .filterNot { it.hasRecipeSubstitute }

        if (missing.isNotEmpty()) {
            add(
                Violation.of(
                    ViolationCode.GATE_COCKTAIL_06,
                    "국내에서 구하기 어려운 재료에 대체 안내가 없습니다: " +
                        missing.joinToString { it.slug },
                    "ingredients",
                ),
            )
        }
    }

    private const val MIN_AROMA = 1
    private const val MAX_AROMA = 3
}

/**
 * 게이트가 보는 스냅샷. **엔티티가 아니다** —
 * 배치 검증(016)이 프로젝션만 읽어도 검사할 수 있어야 한다.
 */
data class PublishCandidate(
    val slug: String,
    val tastingNote: String?,
    val isClassic: Boolean,
    val story: String?,
    val styles: Set<StyleKey>,
    val stylePrimary: StyleKey,
    val aromaTags: Set<FlavorKey>,
    val standardRecipe: RecipeSnapshot?,
    val ingredients: List<IngredientSnapshot>,
)

data class RecipeSnapshot(
    val ingredients: List<IngredientSnapshot>,
    val stepCount: Int,
)

data class IngredientSnapshot(
    val id: Long,
    val slug: String,
    val isApproved: Boolean,
    /** 재료 마스터가 `import_only`·`unavailable` 인가 (`IngredientFacade.requiresSubstitute`). */
    val requiresSubstitute: Boolean,
    /** 이 레시피가 대체 재료나 안내를 달았는가. */
    val hasRecipeSubstitute: Boolean,
)

/**
 * 발행 성공 시 (SPEC-05 §3 — 부수효과는 도메인 이벤트로).
 *
 * 이슈 017 이 구독해 색인을 갱신한다. `cocktail` 이 `search` 를 직접 부르면 순환이 생긴다.
 */
data class CocktailPublished(
    val entityId: Long,
    val slug: String,
    val nameKo: String,
    val nameEn: String,
    val aliases: List<String>,
)
