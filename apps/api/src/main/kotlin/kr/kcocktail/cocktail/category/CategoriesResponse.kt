package kr.kcocktail.cocktail.category

/**
 * `GET /api/v1/categories` 응답 (SPEC-07 §2.1).
 *
 * ## 필드가 셋뿐인 것이 `PRIN-P06` 의 구현이다
 *
 * 당도 · 도수 · 향맛 필드가 **없다.** 그것들은 카테고리가 아니라 필터이고,
 * 카테고리로 올리면 조합 폭발과 중복 콘텐츠 페널티가 따라온다.
 * 넷째 필드를 추가하려면 이 타입을 고쳐야 하고, 그때 이 주석을 읽게 된다.
 *
 * 소비자는 Next.js 의 `generateStaticParams` 다 — **어떤 카테고리 경로를 정적 생성할지**를
 * 이 응답이 결정한다. 그래서 기본값은 "코퍼스에 발행분이 있는 값만" 이다 (DECISIONS §1.11).
 */
data class CategoriesResponse(
    val base: List<CategoryItem>,
    val style: List<CategoryItem>,
    val method: List<CategoryItem>,
)

/**
 * 카테고리 한 칸.
 *
 * @param slug ADR-0002 확정 슬러그. 노출되는 순간 URL 이다 (`PRIN-D02`)
 * @param labelKo 한국어 표시 레이블. 정본이 Kotlin 이라 API 가 내보낸다 (DECISIONS §1.10)
 * @param count **발행분** 기준 건수. `draft` · `archived` 는 세지 않는다
 * @param intro 고유 소개 문구 (`FR-COCKTAIL-031` · `NFR-S-07`). 없으면 `null` —
 *   D-1 이 `NFR-S-07` 의 "발행 차단" 을 **경고로** 확정했다 (DECISIONS §2)
 */
data class CategoryItem(
    val slug: String,
    val labelKo: String,
    val count: Int,
    val intro: String?,
)
