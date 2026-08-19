package kr.mut.search.list

import kr.mut.common.taxonomy.Slugged
import java.math.BigDecimal

/**
 * 도수 필터 **4구간** (`FR-SEARCH-003` · ADR-0003 §2).
 *
 * ## 연속 슬라이더가 아니다
 *
 * ADR-0003 이 시안의 `0~45` 연속 슬라이더를 버렸다. 이유는 취향이 아니라 스펙 정합성이다 —
 * `R-F2.1-2` 가 **모든 필터 값 옆에 결과 개수**를 요구하는데 45개 눈금에는 숫자를 달 수 없다.
 * 그래서 `abvMin`·`abvMax` 같은 연속값 파라미터를 **받지 않는다** (이슈 018 RED 21).
 *
 * ## 경계는 `(하한, 상한]` 이다
 *
 * ADR-0003 은 "구간 정의는 한 곳에만 둔다 — `abvBandOf()`" 라고만 했고 경계의 열림·닫힘을
 * 문장으로 적지 않았다. 정의는 프로토타입 [packages/domain/src/data.ts] 의 `abvBandOf()` 에 있다.
 *
 * ```ts
 * if (abv === 0) return "na";
 * if (abv <= 10) return "low";
 * if (abv <= 20) return "mid";
 * return "high";
 * ```
 *
 * **위쪽이 닫힌 구간**이다. `10.0` 은 `low`, `20.0` 은 `mid` 다 (RED 20 — 결정론적이어야 한다).
 * 서버가 다르게 자르면 클라이언트 필터(SPEC-05 §4)와 결과가 갈린다. 같은 정의를 옮긴 것이지
 * 여기서 새로 정한 것이 아니다.
 *
 * `abv` 가 `NULL` 인 행(도수 미입력 draft)은 **어느 구간에도 들지 않는다.**
 * 공개 목록은 발행분만 보므로 실무상 나타나지 않는다.
 */
enum class AbvBand(
    override val slug: String,
    override val labelKo: String,
    /** `abv` 컬럼에 그대로 거는 조건. 컬럼을 함수로 감싸지 않아야 인덱스를 탄다 (SPEC-06 §5). */
    val sqlPredicate: String,
) : Slugged {
    NA("na", "논알콜", "c.abv = 0"),
    LOW("low", "저 ~10%", "(c.abv > 0 AND c.abv <= 10)"),
    MID("mid", "중 10–20%", "(c.abv > 10 AND c.abv <= 20)"),
    HIGH("high", "고 20%~", "c.abv > 20"),
    ;

    fun matches(abv: BigDecimal?): Boolean = abv != null && bandOf(abv) == this

    companion object {
        val slugs: List<String> get() = entries.map { it.slug }

        fun ofSlugOrNull(slug: String): AbvBand? = entries.firstOrNull { it.slug == slug }

        /**
         * ADR-0003 이 "한 곳에만 둔다"고 한 그 한 곳이다.
         * 탐색 필터 · 패싯 카운트(019) · 파인더가 전부 이것을 쓴다.
         */
        fun bandOf(abv: BigDecimal): AbvBand = when {
            abv.signum() == 0 -> NA
            abv <= BigDecimal.TEN -> LOW
            abv <= BigDecimal("20") -> MID
            else -> HIGH
        }
    }
}
