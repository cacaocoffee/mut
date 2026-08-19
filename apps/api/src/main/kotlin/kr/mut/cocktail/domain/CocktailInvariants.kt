package kr.mut.cocktail.domain

import kr.mut.common.taxonomy.FlavorKey
import kr.mut.common.taxonomy.StyleKey
import kr.mut.common.web.error.Violation
import kr.mut.common.web.error.ViolationCode

/**
 * SPEC-06 §4.3 이 **앱 강제**로 분류한 불변식 (ISSUE-009).
 *
 * ## 왜 DB 가 못 하나
 *
 * `INV-COCKTAIL-02`·`04` 는 **자식 행의 개수**에 대한 규칙이다.
 * `CHECK` 는 자기 행만 본다 — 다른 테이블의 행 수를 셀 수 없다.
 * 트리거로는 가능하지만, 그러면 규칙이 SQL 안에 숨어 발행 게이트가 재사용할 수 없다.
 *
 * ## 순수 함수여야 하는 이유
 *
 * 세 곳이 같은 규칙을 본다.
 *
 * | 어디 | 언제 |
 * |---|---|
 * | 저장 | 지금 |
 * | 발행 게이트 (이슈 013) | 발행 직전 |
 * | 배치 검증 (이슈 016) | 주기적으로 — `npm run check` 의 서버판 |
 *
 * 엔티티에 메서드로 두면 배치가 전부 로딩해야 하고, 게이트가 자기 판정을 또 쓴다.
 * **두 벌 구현 금지**가 INDEX 의 결합점에 명시돼 있다.
 *
 * ## 전부 모아서 돌려준다
 *
 * 첫 위반에서 멈추지 않는다 (`FR-ADMIN-003`). 에디터가 저장·실패를 반복하게 하지 않는다.
 */
object CocktailInvariants {

    /** `INV-COCKTAIL-04` · `R-F1.2-1` — 향 태그 개수 범위. */
    const val MIN_AROMA_TAGS = 1
    const val MAX_AROMA_TAGS = 3

    /**
     * 검사 대상. **엔티티가 아니라 값**이다 — 배치가 프로젝션만 읽어도 검사할 수 있어야 한다.
     */
    data class Subject(
        val slug: String,
        val styles: Set<StyleKey>,
        val stylePrimary: StyleKey,
        val aromaTags: Set<FlavorKey>,
    )

    fun check(subject: Subject): List<Violation> = buildList {
        addAll(checkStyles(subject))
        addAll(checkAromaTags(subject))
    }

    fun check(cocktail: Cocktail): List<Violation> = check(
        Subject(
            slug = cocktail.slug,
            styles = cocktail.styles,
            stylePrimary = cocktail.stylePrimary,
            aromaTags = cocktail.aromaTags,
        ),
    )

    /**
     * `INV-COCKTAIL-02` — `styles` 는 최소 1개 (`R-C-1`).
     *
     * `INV-COCKTAIL-03`(`style_primary ∈ styles`)도 여기서 본다. **DB 복합 FK 가 이미 막지만**,
     * 앱에서도 보는 이유는 에디터에게 어느 항목이 왜 막혔는지 알려 주기 위해서다 —
     * FK 위반은 `violations` 를 만들지 못한다 (`FR-ADMIN-003`).
     */
    private fun checkStyles(subject: Subject): List<Violation> = buildList {
        if (subject.styles.isEmpty()) {
            add(
                Violation.of(
                    ViolationCode.INV_COCKTAIL_02,
                    "스타일을 최소 하나 골라 주세요.",
                    "styles",
                ),
            )
            // 비어 있으면 03 도 자동으로 위반이지만 따로 알리지 않는다 —
            // 원인은 하나인데 메시지가 둘이면 무엇을 고쳐야 할지 흐려진다.
            return@buildList
        }

        if (subject.stylePrimary !in subject.styles) {
            add(
                Violation.of(
                    ViolationCode.INV_COCKTAIL_03,
                    "대표 스타일(${subject.stylePrimary.slug})이 선택한 스타일 목록에 없습니다.",
                    "stylePrimary",
                ),
            )
        }
    }

    /**
     * `INV-COCKTAIL-04` — 향 태그 1~3개 (`R-F1.2-1`).
     *
     * `FR-COCKTAIL-008` 이 "4개째는 UI 가 막는다"고 했지만 **서버도 막는다** (`PRIN-T05`).
     * UI 만 막으면 API 를 직접 부르는 경로가 남는다.
     */
    private fun checkAromaTags(subject: Subject): List<Violation> {
        val count = subject.aromaTags.size
        if (count in MIN_AROMA_TAGS..MAX_AROMA_TAGS) return emptyList()

        val detail =
            if (count == 0) "향과 맛 태그를 최소 하나 골라 주세요."
            else "향과 맛 태그는 최대 ${MAX_AROMA_TAGS}개입니다 (현재 ${count}개)."

        return listOf(Violation.of(ViolationCode.INV_COCKTAIL_04, detail, "aromaTags"))
    }
}
