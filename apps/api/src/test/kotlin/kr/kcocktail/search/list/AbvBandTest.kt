package kr.kcocktail.search.list

import kr.kcocktail.common.web.error.BadRequestException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.math.BigDecimal
import kotlin.reflect.full.memberProperties

/**
 * ISSUE-018 RED 15~21 — 도수 4구간 (`FR-SEARCH-003` · ADR-0003 §2).
 *
 * DB 없이 전수로 본다 (CONVENTIONS §3.2 — 계산은 단위 테스트). 경계가 결정론적인지가 요체다.
 */
class AbvBandTest {

    /** RED 15 — 구간 4종만 허용. 다섯 번째가 생기면 클라이언트 칩과 어긋난다. */
    @Test
    fun `RED15 - 구간_4종만_허용`() {
        assertThat(AbvBand.slugs).containsExactly("na", "low", "mid", "high")

        assertAll(
            { assertThat(AbvBand.ofSlugOrNull("na")).isEqualTo(AbvBand.NA) },
            { assertThat(AbvBand.ofSlugOrNull("medium")).isNull() },
            { assertThat(AbvBand.ofSlugOrNull("0-10")).isNull() },
        )
    }

    @Test
    fun `RED16 - na는_abv_0이다`() {
        assertThat(band(0)).isEqualTo(AbvBand.NA)
        assertThat(AbvBand.NA.matches(dec("0.0"))).isTrue()
        assertThat(AbvBand.NA.matches(dec("0.1"))).isFalse()
    }

    /** `(0, 10]` — 0 은 `na` 고 10 은 `low` 다. */
    @Test
    fun `RED17 - low는_0초과_10이하다`() {
        assertAll(
            { assertThat(band("0.1")).isEqualTo(AbvBand.LOW) },
            { assertThat(band(8)).isEqualTo(AbvBand.LOW) },
            { assertThat(band(10)).isEqualTo(AbvBand.LOW) },
            { assertThat(band("10.1")).isNotEqualTo(AbvBand.LOW) },
        )
    }

    @Test
    fun `RED18 - mid는_10초과_20이하다`() {
        assertAll(
            { assertThat(band("10.1")).isEqualTo(AbvBand.MID) },
            { assertThat(band(15)).isEqualTo(AbvBand.MID) },
            { assertThat(band(20)).isEqualTo(AbvBand.MID) },
            { assertThat(band("20.1")).isNotEqualTo(AbvBand.MID) },
        )
    }

    @Test
    fun `RED19 - high는_20초과다`() {
        assertAll(
            { assertThat(band("20.1")).isEqualTo(AbvBand.HIGH) },
            { assertThat(band(45)).isEqualTo(AbvBand.HIGH) },
            { assertThat(band(20)).isNotEqualTo(AbvBand.HIGH) },
        )
    }

    /**
     * RED 20 — **구간이 겹치지도, 구멍이 나지도 않는다.**
     *
     * `10.0` 이 `low` 인지 `mid` 인지 문서가 아니라 여기서 고정된다.
     * 0 부터 50 까지 0.1 간격으로 전수 확인한다 — 어느 값도 정확히 한 구간에만 든다.
     */
    @Test
    fun `RED20 - 구간_경계가_겹치지_않는다`() {
        val samples = (0..500).map { BigDecimal(it).divide(BigDecimal.TEN) }

        assertAll(
            samples.map { abv ->
                {
                    val hits = AbvBand.entries.filter { it.matches(abv) }
                    assertThat(hits).`as`("abv=$abv 가 든 구간").hasSize(1)
                }
            },
        )

        // 경계값은 위쪽이 닫힌 구간이다 — packages/domain 의 abvBandOf() 와 같은 정의다.
        assertThat(band(10)).isEqualTo(AbvBand.LOW)
        assertThat(band(20)).isEqualTo(AbvBand.MID)
    }

    /**
     * RED 21 — **연속값 파라미터를 받지 않는다** (`FR-SEARCH-003` · ADR-0003).
     *
     * 슬라이더가 되돌아오는 경로는 대개 "구간에 더해 min/max 도 받자" 다.
     * 필터 타입에 그 자리가 없어야 한다 — 있으면 `R-F2.1-2`(값별 카운트)가 무너진다.
     */
    @Test
    fun `RED21 - 연속값_파라미터를_받지_않는다`() {
        val fields = CocktailFilter::class.memberProperties.map { it.name }

        assertThat(fields).contains("abv")
        assertThat(fields).allSatisfy { name ->
            assertThat(name.lowercase()).doesNotContain("min").doesNotContain("max")
        }
    }

    // ── 파서 (RED 7 · 12 의 단위 층) ─────────────────────────────────────────

    @Test
    fun `모르는 구간 슬러그는 400 이다`() {
        assertThatThrownBy { CocktailFilterParser.parse(abv = "strong") }
            .isInstanceOf(BadRequestException::class.java)
            .hasMessageContaining("strong")
    }

    /** DECISIONS §1.11 — 당도는 단일값. 복수면 400 이고 첫 값을 쓰지 않는다. */
    @Test
    fun `RED12 - sweet는_단일값이다`() {
        assertThat(CocktailFilterParser.parse(sweet = "dry").sweet?.slug).isEqualTo("dry")

        assertThatThrownBy { CocktailFilterParser.parse(sweet = "dry,sweet") }
            .isInstanceOf(BadRequestException::class.java)
    }

    /** SPEC-07 §3.1 — 이 비대칭이 이 이슈의 요체다. 타입에서부터 갈린다. */
    @Test
    fun `flavor 만 AND 타입이다`() {
        val filter = CocktailFilterParser.parse(base = "gin,vodka", flavor = "citrus,herbal")

        assertThat(filter.base).hasSize(2)
        assertThat(filter.flavor.size).isEqualTo(2)
        assertThat(CocktailFilter::class.memberProperties.first { it.name == "flavor" }.returnType.toString())
            .`as`("AND 축은 Set 이 아니다 — SQL 에서 IN 으로 쓰는 실수를 타입이 막는다")
            .contains("AllOf")
    }

    private fun band(value: Int) = AbvBand.bandOf(BigDecimal(value))
    private fun band(value: String) = AbvBand.bandOf(BigDecimal(value))
    private fun dec(value: String) = BigDecimal(value)
}
