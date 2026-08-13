package kr.kcocktail.ingredient.api

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 재료 마스터 설정 (ISSUE-026 RED 20).
 *
 * ```yaml
 * kcocktail:
 *   ingredient:
 *     approved-cap: 300
 * ```
 *
 * ## 왜 상수가 아니라 설정인가
 *
 * `FR-INGREDIENT-001` 의 "200~300개"는 **범위**다. SPEC-02 §3 이 그 근거로 든 것도
 * "무한정 늘리면 역검색 UX 가 무너진다"는 관찰이지 계산된 값이 아니다 —
 * 실제 운영에서 250 이 맞는지 350 이 맞는지는 카드 목록을 띄워 봐야 안다.
 *
 * 상수로 두면 그 조정에 **배포가 필요**하다. 경고 하나 바꾸자고 배포하지 않는다.
 *
 * ## 기본값을 두는 이유
 *
 * 설정을 빠뜨려도 기동해야 한다. 이 값은 [kr.kcocktail.common.revalidate.RevalidateProperties.secret]
 * 과 달리 **없으면 조용히 실패하는 종류가 아니다** — 넘어도 경고일 뿐이라
 * 기동을 막을 만한 사안이 아니다 (DECISIONS §1.2).
 */
@ConfigurationProperties(prefix = "kcocktail.ingredient")
data class IngredientProperties(
    /** `FR-INGREDIENT-001` "국내 유통 기준 200~300개". 넘으면 **경고**다, 차단이 아니다. */
    val approvedCap: Long = 300,
)
