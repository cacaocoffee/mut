package kr.kcocktail.cocktail.web

import java.math.BigDecimal

/**
 * `GET /cocktails/{slug}` 응답 (ISSUE-020 · `FR-COCKTAIL-017`·`018`).
 *
 * ## 블록이 곧 계약이다
 *
 * `FR-COCKTAIL-017` 이 **8개를 필수 블록**으로 못박았다 —
 * 히어로 · 분류 · 스펙 · 재료 · 만드는 법 · 향과 맛 · 국내 구매 가이드 · 액션.
 * 평평한 필드 목록으로 두면 하나가 빠져도 아무도 모른다. 블록을 타입으로 세워
 * **빠진 블록이 컴파일과 테스트에서 보이게** 한다.
 *
 * ## 담지 않는 것 (SPEC-07 §5 · DECISIONS §1.5)
 *
 * | 무엇 | 왜 |
 * |---|---|
 * | 내부 `id` | 공개 식별자는 `slug` 다 (`PRIN-D02`) |
 * | `abvCalculated` · `abvOverride` | 표시값 [Spec.abv] 하나만. 넣으면 프론트가 언젠가 쓴다 |
 * | `status` | `published` 만 나오므로 무의미하다 |
 * | `countsForStock` | Phase 2 역검색용. 지금 내보내면 쓸데없다 |
 *
 * ## 여기 없는 것
 *
 * 배리에이션(이슈 021) · 마실 수 있는 바(`FR-COCKTAIL-025`, Phase 1b) ·
 * Schema.org·OG(이슈 044) · 잔 수 환산(이슈 043)은 이 응답의 몫이 아니다.
 */
data class CocktailDetail(
    /** 공개 식별자. **내부 `id` 를 노출하지 않는다** (SPEC-07 §1.1). */
    val slug: String,

    /** DECISIONS §1.5 — 클래식 배지는 콘텐츠 성격이라 내보낸다. */
    val isClassic: Boolean,

    // ── 필수 블록 8종 (FR-COCKTAIL-017) ────────────────────────────────────
    val hero: Hero,
    val classification: Classification,
    val spec: Spec,
    val ingredients: List<IngredientLine>,
    val steps: List<Step>,
    val tastingNote: TastingNote,
    val purchaseGuide: List<PurchaseGuideItem>,
    val actions: Actions,

    // ── 선택 블록 (PRD 6.1) ────────────────────────────────────────────────
    /** `R-F1.1-3` — 선택이지만 클래식이면 발행 필수다 (`GATE-COCKTAIL-05`). */
    val story: String?,
    val origin: Origin?,
)

/**
 * 분류 축 하나. **`labelKo` 를 응답이 준다** (DECISIONS §1.10) —
 * 분류 축의 정본이 Kotlin 이라(`PRIN-T02`) 프론트가 매핑 표를 따로 들면 반드시 어긋난다.
 *
 * `slug` 가 그대로 카테고리 URL 의 마지막 조각이다 (`FR-COCKTAIL-018`).
 */
data class TaxonRef(val slug: String, val labelKo: String)

/** 히어로 — 이름 · 요약 · 대표 이미지 (PRD 6.1). */
data class Hero(
    val nameKo: String,
    val nameEn: String,
    val summary: String,
    /**
     * **Phase 1a 에서는 항상 `null` 이다.** 이미지 저장소가 미정이라
     * (`G-07` · DECISIONS §2 `D-6`) `media_asset` 자체가 없다. 이슈 044·045 가 채운다.
     *
     * 그래도 필드를 지금 두는 이유는 블록의 모양이 계약이기 때문이다 —
     * 나중에 넣으면 프론트가 히어로를 두 번 만든다.
     */
    val imageUrl: String?,
)

/**
 * 분류 3축 (`R-C-1`). **각 축이 카테고리 페이지 링크가 된다** (`FR-COCKTAIL-018`).
 *
 * [styles] 는 표시용이다. **링크는 [stylePrimary] 하나만** 만든다 (`R-C-2`) —
 * 전부 링크하면 같은 칵테일이 여러 카테고리의 정본처럼 보이고 색인이 갈린다.
 */
data class Classification(
    val base: TaxonRef,
    val stylePrimary: TaxonRef,
    val styles: List<TaxonRef>,
    val method: TaxonRef,
)

/** 스펙 스트립 — 도수 · 당도 · 잔 (SCREENS-01 §01-B). */
data class Spec(
    /**
     * **표시값 하나다** (SPEC-07 §5). 계산인지 수동 오버라이드인지는 내부 사정이라
     * 공개 응답에서 구분하지 않는다. DB 의 생성 컬럼이 이미 하나로 합쳐 준다.
     */
    val abv: BigDecimal?,
    val sweetness: TaxonRef,
    val glassType: String,
    val prepTimeMin: Short?,
)

/**
 * 재료 한 줄. `PRIN-D01` — 재료는 참조지 문자열이 아니라서 `slug` 가 나간다.
 */
data class IngredientLine(
    val slug: String,
    val nameKo: String,
    val nameEn: String,
    val amount: BigDecimal?,
    val unit: String?,
    /** `1조각` 처럼 **배수 계산에서 제외**하는 표기 (`FR-COCKTAIL-019`). */
    val amountLabel: String?,
    val role: String?,
    val isOptional: Boolean,
    /**
     * 잔 수를 바꿀 때 이 줄을 곱하는가 (`FR-COCKTAIL-019`).
     *
     * **환산 자체는 FE(이슈 043)가 한다.** 판정만 서버가 내리는 이유는 어드민 미리보기와
     * 화면이 같은 규칙을 써야 하기 때문이다 — 두 곳이 다르면 에디터가 본 것과 사용자가
     * 보는 것이 달라진다.
     */
    val isScalable: Boolean,
    /** `FR-COCKTAIL-021` — 없으면 `null`. 대체 불가와 대체 안내 없음을 구분하지 않는다. */
    val substitute: Substitute?,
)

data class Substitute(
    /** 대체 재료를 지정하지 않고 안내만 적을 수 있다 (`INV-INGREDIENT-01`). */
    val slug: String?,
    val nameKo: String?,
    val note: String?,
)

/** 만드는 법. 번호가 곧 순서다 (PK 가 `(recipe_id, step_no)`). */
data class Step(
    val stepNo: Short,
    val text: String,
    /** 기법 용어 툴팁 키 (`FR-COCKTAIL-022`, P1). 표현은 FE 다. */
    val techniqueRef: String?,
)

/**
 * 향과 맛 (`R-F1.1-2`). **서술이 필수**라서 발행된 칵테일에는 반드시 있다
 * (`GATE-COCKTAIL-01`).
 *
 * 필터 태그를 같은 블록에 싣는다 (PRD 6.3) — 서술을 읽지 않고도 걸러낼 수 있어야 한다.
 */
data class TastingNote(
    val note: String,
    val aromaTags: List<TaxonRef>,
)

/**
 * 국내 구매 가이드 (PRD 6.1 · `PRIN-P05`).
 *
 * > 이 서비스가 해외 DB 의 번역판이 아닌 이유는 이 축 하나다.
 *
 * 재료 마스터에서 온다 — `IngredientFacade` 경유다 (`PRIN-T03`).
 */
data class PurchaseGuideItem(
    val slug: String,
    val nameKo: String,
    val availability: TaxonRef,
    /** 미유통이면 반드시 있다 (`INV-INGREDIENT-01`). */
    val substituteNote: String?,
    val priceBand: String?,
    val brands: List<Brand>,
)

/**
 * `INV-INGREDIENT-02` · `FR-INGREDIENT-004` — 광고성 여부를 **서버가 판정해서** 내보낸다.
 * 클라이언트에 맡기면 라벨을 붙이지 않는 클라이언트가 생긴다.
 */
data class Brand(
    val name: String,
    val purchaseUrl: String?,
    val isSponsored: Boolean,
)

/**
 * 액션 블록 (PRD 6.1) — 저장 · 공유.
 *
 * ## 왜 서버가 주는가
 *
 * 북마크는 `POST /me/bookmarks` 가 `{targetType, targetSlug}` 를 받는다 (SPEC-07 §2.5).
 * 그 두 값을 프론트가 문자열로 조립하면 대상 타입이 코드 두 곳에 존재하게 된다.
 *
 * ## 내 술장 대조는 여기 없다
 *
 * `/me/stock` 은 **Phase 2** 다 (SPEC-07 §2.9). 자리만 잡아 두지 않는다 —
 * 응답에 있으면 프론트가 버튼을 만들고, 누르면 없는 API 를 부른다.
 */
data class Actions(
    /** `POST /me/bookmarks` 의 `targetType`. */
    val bookmarkTargetType: String,
    val bookmarkTargetSlug: String,
    /**
     * 공유·`canonical` 용 **경로**다. 절대 URL 이 아니다 —
     * 도메인이 아직 정해지지 않았다 (`G-07`). 호스트는 프론트가 붙인다.
     */
    val sharePath: String,
)

/** 기록 (PRD 6.1 "관련 이야기"). 셋 다 선택이라 블록 자체가 `null` 일 수 있다. */
data class Origin(
    val year: String?,
    val place: String?,
    val creator: String?,
)

/**
 * `GET /cocktails/{slug}/recipes` 응답 (SPEC-07 §2.1 · `FR-COCKTAIL-003`).
 *
 * 페이징하지 않는다 — 칵테일당 **표준 1개 + 바 시그니처 n개**이고 n 이 작다
 * (`INV-COCKTAIL-07` · `PRIN-D03`). SPEC-07 §3.3 의 `{items:[…]}` 와 같은 모양이다.
 */
data class RecipeVersions(val items: List<RecipeVersion>)

data class RecipeVersion(
    val versionType: String,
    /** SPEC-02 §2.6 — **기본 노출은 `standard`** 다. 상세가 보여주는 것이 이것이다. */
    val isDefault: Boolean,
    val servingCount: Short,
    val note: String?,
    val ingredients: List<IngredientLine>,
    val steps: List<Step>,
)
