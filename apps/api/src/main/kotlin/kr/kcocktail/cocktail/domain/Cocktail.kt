package kr.kcocktail.cocktail.domain

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import kr.kcocktail.common.entity.BaseEntity
import kr.kcocktail.common.taxonomy.BaseSpirit
import kr.kcocktail.common.taxonomy.FlavorKey
import kr.kcocktail.common.taxonomy.StyleKey
import kr.kcocktail.common.taxonomy.SweetLevel
import kr.kcocktail.common.taxonomy.Technique
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant

/**
 * 칵테일 (SPEC-06 §3.1).
 *
 * ## 분류 3축은 카테고리다 (`PRIN-P06`)
 *
 * 기주 · 스타일 · 메이킹 방법은 **모든 칵테일이 반드시 하나씩** 갖고 (`R-C-1`),
 * 경로가 되며 색인한다. 당도 · 도수 · 향은 필터라 없어도 되고 색인하지 않는다.
 *
 * ## enum 을 여기서 만들지 않는다
 *
 * `BaseSpirit` · `StyleKey` · `FlavorKey` · `Technique` · `SweetLevel` 의 정본은
 * [kr.kcocktail.common.taxonomy] 다 (`PRIN-T02`, ISSUE-004). 여기서 또 만들면
 * 계약과 도메인이 두 곳에 존재하게 되고, 그게 정확히 `PRIN-T02` 가 막으려는 상황이다.
 *
 * ## 불변식이 나뉘어 있다
 *
 * | | 어디서 | 무엇 |
 * |---|---|---|
 * | DB | `V009__cocktail.sql` | `01`(NOT NULL) · `03`(복합 FK) · `06`(CHECK) |
 * | 앱 | [CocktailInvariants] | `02`(자식 개수) · `04`(자식 개수) |
 *
 * 자식 행의 **개수**는 CHECK 로 표현할 수 없다 (SPEC-06 §4.3).
 * 앱 검증은 순수 함수로 빼서 발행 게이트(013)와 배치 검증(016)이 재사용한다.
 */
@Entity
@Table(name = "cocktail")
class Cocktail(
    @Column(name = "slug", nullable = false, length = 120, updatable = false)
    val slug: String,

    @Column(name = "name_ko", nullable = false, length = 120)
    var nameKo: String,

    @Column(name = "name_en", nullable = false, length = 120)
    var nameEn: String,

    @Column(name = "summary", nullable = false)
    var summary: String,

    @Column(name = "base_spirit", nullable = false, length = 24)
    private var baseSpiritSlug: String,

    @Column(name = "style_primary", nullable = false, length = 24)
    private var stylePrimarySlug: String,

    @Column(name = "method", nullable = false, length = 12)
    private var methodSlug: String,

    @Column(name = "sweetness", nullable = false, length = 12)
    private var sweetnessSlug: String,

    @Column(name = "glass_type", nullable = false, length = 40)
    var glassType: String,

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "aliases", nullable = false)
    var aliases: Array<String> = emptyArray(),

    @Column(name = "prep_time_min")
    var prepTimeMin: Short? = null,

    /** 발행 시 필수 (`GATE-COCKTAIL-01`). draft 에서는 비어 있어도 된다. */
    @Column(name = "tasting_note")
    var tastingNote: String? = null,

    /** 클래식이면 발행 시 필수 (`GATE-COCKTAIL-05`). */
    @Column(name = "story")
    var story: String? = null,

    @Column(name = "is_classic", nullable = false)
    var isClassic: Boolean = false,

    @Column(name = "origin_year", length = 80)
    var originYear: String? = null,

    @Column(name = "origin_place", length = 80)
    var originPlace: String? = null,

    @Column(name = "origin_creator", length = 80)
    var originCreator: String? = null,

    /** 이슈 011 이 채운다. 여기서는 컬럼만. */
    @Column(name = "abv_calculated", precision = 4, scale = 1)
    var abvCalculated: BigDecimal? = null,

    @Column(name = "abv_override", precision = 4, scale = 1)
    var abvOverride: BigDecimal? = null,
) : BaseEntity() {

    /** 생성 컬럼이다. DB 가 `COALESCE(abv_override, abv_calculated)` 로 채운다. */
    @Column(name = "abv", insertable = false, updatable = false)
    var abv: BigDecimal? = null
        protected set

    @Column(name = "status", nullable = false, length = 12)
    private var statusSlug: String = CocktailStatus.DRAFT.slug

    @Column(name = "published_at")
    var publishedAt: Instant? = null
        protected set

    @OneToMany(mappedBy = "cocktail", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    private val styleRows: MutableSet<CocktailStyle> = mutableSetOf()

    @OneToMany(mappedBy = "cocktail", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    private val aromaRows: MutableSet<CocktailAromaTag> = mutableSetOf()

    // ── 분류 3축 ────────────────────────────────────────────────────────────

    var baseSpirit: BaseSpirit
        get() = BaseSpirit.ofSlug(baseSpiritSlug)
        set(value) { baseSpiritSlug = value.slug }

    var stylePrimary: StyleKey
        get() = StyleKey.ofSlug(stylePrimarySlug)
        set(value) { stylePrimarySlug = value.slug }

    var method: Technique
        get() = Technique.ofSlug(methodSlug)
        set(value) { methodSlug = value.slug }

    var sweetness: SweetLevel
        get() = SweetLevel.ofSlug(sweetnessSlug)
        set(value) { sweetnessSlug = value.slug }

    val status: CocktailStatus get() = CocktailStatus.ofSlug(statusSlug)

    val styles: Set<StyleKey> get() = styleRows.map { it.style }.toSet()

    val aromaTags: Set<FlavorKey> get() = aromaRows.map { it.tag }.toSet()

    // ── 자식 축 ─────────────────────────────────────────────────────────────

    /**
     * `style_primary` 는 반드시 여기 포함돼야 한다 (`INV-COCKTAIL-03`).
     * **DB 의 복합 FK 가 강제**하므로 앱이 다시 검사하지 않는다 — 두 벌 검증은 어긋난다.
     */
    fun setStyles(values: Set<StyleKey>) {
        styleRows.removeIf { it.style !in values }
        values.filterNot { it in styles }.forEach { styleRows += CocktailStyle(this, it.slug) }
    }

    /** 1~3개 (`INV-COCKTAIL-04`). 개수 검증은 [CocktailInvariants] 다 — CHECK 로 못 쓴다. */
    fun setAromaTags(values: Set<FlavorKey>) {
        aromaRows.removeIf { it.tag !in values }
        values.filterNot { it in aromaTags }.forEach { aromaRows += CocktailAromaTag(this, it.slug) }
    }

    /** 상태 전이 규칙과 감사 로그는 이슈 014 다. 여기서는 값을 옮기는 것까지. */
    fun markPublished(at: Instant) {
        statusSlug = CocktailStatus.PUBLISHED.slug
        publishedAt = at
    }

    fun markArchived() {
        statusSlug = CocktailStatus.ARCHIVED.slug
    }
}
