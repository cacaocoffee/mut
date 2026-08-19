import type { CocktailView } from "./cocktail-view";
import { formatQuantity } from "@mut/domain";
import { SITE_URL } from "./site";

/**
 * 구조화 데이터 (ISSUE-044 · `FR-COCKTAIL-026` · `R-F1.1-6` · `NFR-S-05`).
 *
 * ## 별점을 만들지 않는다
 *
 * `aggregateRating` 과 `review` 를 **넣지 않는다.** 리치 결과에 별이 뜨는 자리라 넣고 싶어지는
 * 지점이고, 그래서 `PRIN-P04` 가 먼저 금지했다 — "별점을 쌓지 않는다". 총점은 취향을 하나의
 * 수로 눌러 담고, 한번 쌓기 시작하면 낮은 점수를 가리려는 힘이 콘텐츠를 왜곡한다.
 * 없는 값을 지어내 채우는 것은 더 나쁘다 (구글 정책상 조작이다).
 *
 * ## 1잔 · ml 고정이다
 *
 * 화면에서 잔 수를 바꿔도 여기는 안 바뀐다 (이슈 043). 검색엔진이 읽는 것은 레시피 원본이지
 * 지금 보고 있는 배수가 아니다.
 */
export function recipeJsonLd(c: CocktailView) {
  return {
    "@context": "https://schema.org",
    "@type": "Recipe",
    name: `${c.nameKo} ${c.nameEn}`,
    description: c.tastingNote ?? c.summary,
    // 분류 3축을 그대로 쓴다 (RED 결정). `recipeCategory` 는 목록형 값이라
    // 스타일·메이킹까지 담을 수 있다 — 축이 곧 이 사이트의 분류다.
    recipeCategory: [c.stylePrimary.labelKo, c.method.labelKo].join(", "),
    recipeCuisine: c.base.labelKo,
    recipeYield: "1잔",
    keywords: [
      c.base.labelKo,
      c.stylePrimary.labelKo,
      c.method.labelKo,
      c.glassType,
      ...c.aromaTags.map((t) => t.labelKo),
    ].join(", "),
    image: [ogImageUrl(c.slug)],
    recipeIngredient: c.ingredients.map((i) => `${formatQuantity(i, 1, "ml")} ${i.nameKo}`.trim()),
    recipeInstructions: c.steps.map((text, i) => ({
      "@type": "HowToStep",
      position: i + 1,
      text,
    })),
  };
}

/**
 * 상세의 OG 이미지 주소.
 *
 * 히어로 사진이 없어 이름으로 그린다 (`opengraph-image.tsx` · GAPS G-36).
 * 절대 주소여야 한다 — 카카오톡·구글이 상대 경로를 못 따라온다 (`NFR-S-06`).
 */
export function ogImageUrl(slug: string): string {
  return `${SITE_URL}/cocktails/${slug}/opengraph-image`;
}
