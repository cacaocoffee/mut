package kr.kcocktail.search.list

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal

/**
 * 목록 한 줄 (SPEC-07 §1.5 `items[]`).
 *
 * ## 없는 것이 계약이다 (SPEC-07 §5 · DECISIONS §1.5)
 *
 * | 빠진 것 | 이유 |
 * |---|---|
 * | 내부 `id` | 공개 리소스의 식별자는 `slug` 뿐이다 |
 * | `status` | `published` 만 나오므로 무의미하다 |
 * | `abvCalculated` · `abvOverride` | **표시값 [abv] 하나만.** 계산인지 수동인지는 내부 사정이다 |
 * | `countsForStock` | Phase 2 역검색용. 지금 내보내면 쓸데없다 |
 *
 * ## 축 값은 슬러그다
 *
 * `PRIN-T02` — 계약의 어휘가 슬러그이고 한국어 레이블은 `x-labels` 확장에 있다
 * ([kr.kcocktail.common.openapi.TaxonomySchemaCustomizer]). 타입은 `String` 이지만
 * 스키마는 그 enum 을 가리킨다 — 프론트 생성 타입이 `string` 으로 뭉개지면 계약이 사라진다.
 *
 * ## 필드 수는 아직 줄이지 않았다
 *
 * DECISIONS §3 이 "목록 응답 필드 축소(500종 × 상세 필드)" 를 **응답 크기 실측 후**로 미뤘다.
 * Phase 1 프론트는 전체 목록을 받아 클라이언트에서 거르므로(SPEC-05 §4) 카드 렌더링에
 * 필요한 축이 전부 있어야 한다.
 */
data class CocktailListItem(
    val slug: String,
    val nameKo: String,
    val nameEn: String,
    val summary: String,

    @field:Schema(ref = "#/components/schemas/BaseSpirit")
    val baseSpirit: String,

    @field:Schema(ref = "#/components/schemas/StyleKey")
    val stylePrimary: String,

    /** 보유 스타일 전체. 필터가 맞추는 대상이 이쪽이다 (DECISIONS §1.11). */
    @field:ArraySchema(schema = Schema(ref = "#/components/schemas/StyleKey"))
    val styles: List<String>,

    @field:Schema(ref = "#/components/schemas/Technique")
    val method: String,

    @field:Schema(ref = "#/components/schemas/SweetLevel")
    val sweetness: String,

    @field:ArraySchema(schema = Schema(ref = "#/components/schemas/FlavorKey"))
    val aromaTags: List<String>,

    /** 표시값 하나뿐이다 (SPEC-07 §5). */
    val abv: BigDecimal?,

    val glassType: String,

    /**
     * 클래식 배지는 콘텐츠 성격이라 공개한다 (DECISIONS §1.5).
     *
     * `@JsonProperty` 를 붙인 이유는 Jackson 이 `isClassic` 을 `classic` 으로 깎기 때문이다.
     * SPEC-07 §3.3 의 응답 예시가 `isSignature` 라 접두사를 유지하는 것이 규약이다.
     */
    @get:JsonProperty("isClassic")
    val isClassic: Boolean,
)
