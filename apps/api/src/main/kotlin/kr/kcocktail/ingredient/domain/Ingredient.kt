package kr.kcocktail.ingredient.domain

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import kr.kcocktail.common.entity.BaseEntity
import kr.kcocktail.common.web.error.DomainViolationException
import kr.kcocktail.common.web.error.Violation
import kr.kcocktail.common.web.error.ViolationCode
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal

/**
 * 재료 마스터 (SPEC-06 §3.2).
 *
 * `PRIN-D01` — 레시피는 이것을 **참조**한다. 문자열로 적으면 역검색과 바 연결이 불가능해진다.
 *
 * ## 불변식을 앱과 DB 양쪽에서 막는다
 *
 * `INV-INGREDIENT-01` 은 DB CHECK 로도 걸려 있다 (`ck_ingredient__substitute`).
 * 앱에서 막는 이유는 **에디터에게 어느 항목이 왜 막혔는지 알려 주기 위해서**다 —
 * DB 제약 위반은 `violations` 를 만들지 못한다 (`FR-ADMIN-003`).
 * DB 에도 거는 이유는 배치·마이그레이션이 앱을 거치지 않기 때문이다 (`PRIN-T05`).
 */
@Entity
@Table(name = "ingredient")
class Ingredient(
    @Column(name = "slug", nullable = false, length = 120, updatable = false)
    val slug: String,

    @Column(name = "name_ko", nullable = false, length = 120)
    var nameKo: String,

    @Column(name = "name_en", nullable = false, length = 120)
    var nameEn: String,

    @Column(name = "category", nullable = false, length = 16)
    private var categorySlug: String,

    @Column(name = "domestic_availability", nullable = false, length = 16)
    private var availabilitySlug: String,

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "aliases", nullable = false)
    var aliases: Array<String> = emptyArray(),

    @Column(name = "abv", precision = 4, scale = 1)
    var abv: BigDecimal? = null,

    @Column(name = "description")
    var description: String? = null,

    @Column(name = "substitute_note")
    var substituteNote: String? = null,

    @Column(name = "price_band", length = 12)
    var priceBand: String? = null,
) : BaseEntity() {

    /**
     * `FR-ADMIN-007` · DECISIONS §1.1 — 기본이 `false` 다.
     *
     * 미승인 재료도 `draft` 레시피에는 쓸 수 있다. 승인을 기다리면 에디터 작업이 끊기고,
     * 발행에서 막으면 마스터 오염(`PRIN-D01`)도 없다.
     */
    @Column(name = "is_approved", nullable = false)
    var isApproved: Boolean = false
        protected set

    @OneToMany(
        mappedBy = "ingredient",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY,
    )
    private val brandRows: MutableList<IngredientBrand> = mutableListOf()

    var category: IngredientCategory
        get() = IngredientCategory.ofSlug(categorySlug)
        set(value) { categorySlug = value.slug }

    var domesticAvailability: DomesticAvailability
        get() = DomesticAvailability.ofSlug(availabilitySlug)
        set(value) { availabilitySlug = value.slug }

    val brands: List<IngredientBrand> get() = brandRows.toList()

    /** 역검색에서 셀지의 기본값. 레시피가 뒤집을 수 있다 (이슈 010). */
    val defaultCountsForStock: Boolean get() = category.defaultCountsForStock

    /** `GATE-COCKTAIL-06` 이 이것을 본다 — 미유통 재료가 있으면 대체재를 요구한다. */
    val requiresSubstitute: Boolean get() = domesticAvailability.needsSubstitute

    init {
        validate()
    }

    /**
     * `INV-INGREDIENT-01` — 미유통이면 대체재 또는 자가제조 안내가 필수다.
     *
     * **공백만 있는 문자열은 없는 것으로 친다.** 있으나 마나 한 안내는 안내가 아니고,
     * `NOT NULL` 만 걸면 에디터가 스페이스 하나로 통과시킨다.
     */
    fun validate() {
        if (requiresSubstitute && substituteNote.isNullOrBlank()) {
            throw DomainViolationException(
                Violation.of(
                    ViolationCode.INV_INGREDIENT_01,
                    "국내에서 구하기 어려운 재료입니다. 대체재나 자가제조 안내를 적어 주세요.",
                    "substituteNote",
                ),
            )
        }
    }

    fun approve() {
        isApproved = true
    }

    fun addBrand(name: String, purchaseUrl: String? = null, isSponsored: Boolean = false) {
        brandRows += IngredientBrand(
            ingredient = this,
            name = name,
            purchaseUrl = purchaseUrl,
            isSponsored = isSponsored,
        )
    }
}
