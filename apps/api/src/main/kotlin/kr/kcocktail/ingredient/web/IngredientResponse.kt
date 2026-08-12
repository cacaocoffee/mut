package kr.kcocktail.ingredient.web

import java.math.BigDecimal

/**
 * 재료 사전 응답 (ISSUE-023 · `FR-INGREDIENT-002`·`005`).
 *
 * ## 담지 않는 것
 *
 * 내부 `id` 를 내보내지 않는다 (SPEC-07 §5). 공개 식별자는 `slug` 다.
 */
data class IngredientItem(
    val slug: String,
    val nameKo: String,
    val nameEn: String,
    val category: String,
    val domesticAvailability: String,
    val abv: BigDecimal?,
)

/**
 * 상세. `FR-INGREDIENT-002` 가 요구한 **6개 항목**이 전부 여기 있다 —
 * 설명 · 도수 · 대표 브랜드 · 국내 유통 여부 · 가격대 · 이 재료를 쓰는 칵테일.
 *
 * 마지막 하나만 별도 엔드포인트다 (`/{slug}/cocktails`) — 페이징이 필요하고,
 * 상세에 통째로 실으면 재료 하나에 칵테일 수백 개가 딸려 나간다.
 *
 * ## `domesticAvailability` 가 이 서비스의 정체성이다
 *
 * `PRIN-P05` — "이 서비스가 해외 DB 의 번역판이 아닌 이유는 `domestic_availability` 하나다."
 */
data class IngredientDetail(
    val slug: String,
    val nameKo: String,
    val nameEn: String,

    /** `FR-INGREDIENT-005` — 검색 색인은 이슈 017 이고, 여기서는 노출이다 (`R-F2.1-3`). */
    val aliases: List<String>,

    val category: String,
    val abv: BigDecimal?,
    val description: String?,
    val domesticAvailability: String,

    /** 미유통이면 반드시 있다 (`INV-INGREDIENT-01`). */
    val substituteNote: String?,
    val priceBand: String?,
    val brands: List<BrandItem>,
)

/**
 * 대표 브랜드 (`R-F1.3-3`).
 *
 * ## `isSponsored` 가 nullable 이 아니다
 *
 * `NFR-L-02` 가 라벨을 **끌 수 없게** 요구한다 — 공정위 추천·보증 심사지침상 의무이고
 * 위반이 제재 대상이다. 옵셔널이면 프론트가 빠뜨리고, 빠뜨린 것과 광고가 아닌 것을
 * 구분할 수 없다. **판정은 서버가 내려서 항상 실어 보낸다.**
 *
 * 서버에 "라벨 숨김" 파라미터를 만들지 않는다. 만드는 순간 그것이 끄는 방법이 된다.
 */
data class BrandItem(
    val name: String,
    val purchaseUrl: String?,
    val isSponsored: Boolean,

    /**
     * **라벨을 붙여야 하는가.** `isSponsored` 와 따로 두는 이유가 있다.
     *
     * `isSponsored` 는 사실이고 이것은 **판정**이다. 지금은 둘이 같지만,
     * 규칙이 바뀌어도(예: 제휴 등급이 붙는 Phase 1b) 프론트는 이 필드만 보면 된다 —
     * 클라이언트가 `isSponsored` 로 직접 판단하면 규칙이 두 곳에 생기고,
     * 그때 라벨을 안 붙이는 클라이언트가 나온다. `NFR-L-02` 는 그것을 배포 차단 사유로 뒀다.
     */
    val requiresAdLabel: Boolean,
)

