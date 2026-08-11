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
    slug: String,

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

    /**
     * 공개 식별자 (SPEC-07 §1.1). **최초 발행 이후 불변**이다 —
     * 노출되는 순간 URL 이고, 바꾸면 리다이렉트 부채가 된다 (`PRIN-D02` · `INV-COCKTAIL-05`).
     *
     * 세터를 닫아 둔 것은 실수가 아니다. 바꾸는 길은 [changeSlug] 하나뿐이고,
     * 그 길이 [isSlugLocked] 를 본다 — 우회 경로를 두면 `NFR-D-04`("변경 이력 0건")를
     * 감시할 자리가 사라진다.
     */
    @Column(name = "slug", nullable = false, length = 120)
    var slug: String = slug
        private set

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

    // ── slug 잠금 (`INV-COCKTAIL-05` · `PRIN-D02`) ──────────────────────────

    /**
     * **`status` 가 아니라 `published_at` 을 본다.**
     *
     * 회수해서 `draft` 로 돌아가도 잠긴 채여야 한다 — `FR-COCKTAIL-014` 가
     * "**최초 발행 이후**" 라고 했고, 한 번 나간 URL 은 회수해도 남의 북마크와 색인에 남는다.
     * `status` 를 기준으로 삼으면 "회수 → 슬러그 변경 → 재발행" 으로 규칙을 빠져나간다.
     */
    val isSlugLocked: Boolean get() = publishedAt != null

    /**
     * @throws SlugLockedException 최초 발행 이후. 잡아서 감사에 남기는 것은 서비스 몫이다
     *   (`NFR-D-04` — 거부된 시도도 조사 근거가 된다)
     */
    fun changeSlug(newSlug: String) {
        if (isSlugLocked) throw SlugLockedException(slug, newSlug)
        slug = newSlug
    }

    /** 상태 전이 규칙과 감사 로그는 이슈 014 다. 여기서는 값을 옮기는 것까지. */
    fun markPublished(at: Instant) {
        statusSlug = CocktailStatus.PUBLISHED.slug
        publishedAt = at
    }

    /** 회수. `published_at` 은 지우지 않는다 — 언제 발행했었는지가 기록이다. */
    fun markDraft() {
        statusSlug = CocktailStatus.DRAFT.slug
    }

    fun markArchived() {
        statusSlug = CocktailStatus.ARCHIVED.slug
    }
}
