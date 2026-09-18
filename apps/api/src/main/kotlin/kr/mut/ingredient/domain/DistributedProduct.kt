package kr.mut.ingredient.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import kr.mut.common.entity.BaseEntity
import org.hibernate.annotations.Immutable
import java.time.LocalDate

/**
 * 식약처에 신고된 술 한 건 (#194 · `V029__distributed_product.sql`).
 *
 * **앱은 읽기만 한다.** 채우는 것은 `scripts/fetch-distributed-products.ts` 가 만든 시드다.
 * 그래서 `@Immutable` 이고 세터가 없다. `raw` 컬럼은 여기 매핑하지 않는다 — 화면이 쓰지 않는다.
 *
 * 이 데이터가 말하는 것은 "신고가 있었다" 까지다. 재고 소진·판매 중단은 모른다.
 */
@Entity
@Immutable
@Table(name = "distributed_product")
class DistributedProduct(
    @Column(name = "source", nullable = false, length = 16)
    val source: String,

    @Column(name = "source_key", nullable = false, length = 160)
    val sourceKey: String,

    @Column(name = "name_ko", nullable = false)
    val nameKo: String,

    @Column(name = "name_en")
    val nameEn: String? = null,

    /** 수입이면 수입업체, 국산이면 제조업소. */
    @Column(name = "importer_or_maker")
    val importerOrMaker: String? = null,

    @Column(name = "manufacturer")
    val manufacturer: String? = null,

    @Column(name = "origin_country", length = 80)
    val originCountry: String? = null,

    @Column(name = "food_type", nullable = false, length = 40)
    val foodType: String,

    @Column(name = "last_reported_on")
    val lastReportedOn: LocalDate? = null,
) : BaseEntity()
