/**
 * 재료 마스터 (`FR-INGREDIENT-001`·`006`).
 *
 * ## 왜 있어야 하나
 *
 * [SPEC-00 §7](../../../docs/spec/SPEC-00_개발원칙.md) 이 못박았다 — **"재료를 문자열로
 * 저장하면 재료 사전의 "이 재료로 만드는 것" 목록과 바 연결이 전부 불가능해진다."** `data.ts` 의 레시피 줄이
 * 정확히 그 상태다 (`{ ko: "진", en: "Dry Gin", ml: 30 }` — 슬러그도 ID 도 없다).
 * 여기가 그 이름들이 모이는 한 자리다.
 *
 * ## `data.ts` 를 건드리지 않는다
 *
 * 레시피 줄은 그대로 두고 **이름으로 찾아 온다** ([resolveIngredient]). 줄마다 슬러그를
 * 박으면 같은 사실이 두 곳에 적히고, 그 순간부터 둘이 어긋날 수 있다. 못 찾는 이름이
 * 생기면 `scripts/ingredient-guard.mjs` 가 `npm run check` 에서 막는다.
 *
 * ## 카테고리는 계약이 정한 7종이다
 *
 * `apps/api/.../IngredientCategory.kt` 와 같은 값·같은 기본값을 쓴다 (`PRIN-T02`).
 * **`garnish` 만 `countsForStock` 이 `false` 다** — 가니시는 필수 재료가 아니라는 구분이고, 빼지 않으면
 * 민트 잎 하나 없다고 모히토가 안 나온다.
 *
 * 7종 안에 술을 담을 칸이 `spirit` 하나뿐이라 베르무트 · 셰리 · 릴레 · 막걸리 · 맥주도
 * 거기 넣는다. 주정강화·발효주를 위한 칸은 계약에 없다 — 필요해지면 계약을 먼저 고친다.
 */
import { COCKTAILS } from "./data";

/** `FR-INGREDIENT-006` 이 정한 7종. 값은 계약의 `IngredientCategory` 슬러그 그대로다. */
export type IngredientCategory =
  | "spirit"
  | "liqueur"
  | "bitters"
  | "syrup"
  | "juice"
  | "garnish"
  | "mixer";

/** 화면에 낼 이름. 정렬 순서이기도 하다 — 술이 먼저, 꾸미는 것이 마지막. */
export const CATEGORY_LABELS: Record<IngredientCategory, string> = {
  spirit: "술",
  liqueur: "리큐르",
  bitters: "비터스",
  syrup: "시럽 · 당류",
  juice: "주스",
  mixer: "섞는 것",
  garnish: "가니시",
};

export const CATEGORY_ORDER = Object.keys(CATEGORY_LABELS) as IngredientCategory[];

/** 필수 재료로 세지 않는 카테고리. 계약의 `defaultCountsForStock` 과 같다. */
const NOT_COUNTED: ReadonlySet<IngredientCategory> = new Set<IngredientCategory>(["garnish"]);

export interface Ingredient {
  slug: string;
  nameKo: string;
  nameEn: string;
  category: IngredientCategory;
  /**
   * `data.ts` 가 같은 것을 다르게 적은 이름들.
   *
   * 표기 흔들림(`코앵트로`)과 표기 계열(`앙고스투라 비터` · `아로마틱 비터`)을 흡수한다.
   * **뜻이 다른 것을 여기 넣지 않는다** — 자메이카 럼과 화이트 럼은 다른 병이다.
   */
  aliases?: string[];
}

/**
 * 재료 목록.
 *
 * `nameEn` 은 `data.ts` 가 쓰는 표기를 따른다. 별칭은 그 줄에 실제로 나온 이름만 적는다 —
 * 안 쓰이는 별칭을 미리 넣으면 가드가 못 잡고 죽은 값이 쌓인다.
 */
export const INGREDIENTS: Ingredient[] = [
  // ── 술 ──────────────────────────────────────────────────────────────────
  { slug: "gin", nameKo: "진", nameEn: "Dry Gin", category: "spirit" },
  { slug: "vodka", nameKo: "보드카", nameEn: "Vodka", category: "spirit" },
  { slug: "citron-vodka", nameKo: "시트론 보드카", nameEn: "Citron Vodka", category: "spirit" },
  // 종류를 안 적은 `럼` 은 화이트로 본다 — 보스턴 쿨러가 그 줄이고, 그 자리에 흔히 쓰는 것이다
  { slug: "white-rum", nameKo: "화이트 럼", nameEn: "White Rum", category: "spirit", aliases: ["럼"] },
  { slug: "dark-rum", nameKo: "다크 럼", nameEn: "Dark Rum", category: "spirit" },
  { slug: "jamaican-rum", nameKo: "자메이카 럼", nameEn: "Jamaican Rum", category: "spirit" },
  { slug: "rhum-agricole", nameKo: "아그리콜 럼", nameEn: "Rhum Agricole", category: "spirit" },
  { slug: "barbados-rum", nameKo: "바베이도스 럼", nameEn: "Barbados Rum", category: "spirit" },
  { slug: "blanco-tequila", nameKo: "블랑코 데킬라", nameEn: "Blanco Tequila", category: "spirit" },
  { slug: "bourbon", nameKo: "버번", nameEn: "Bourbon", category: "spirit", aliases: ["버번 위스키"] },
  { slug: "rye", nameKo: "라이 위스키", nameEn: "Rye Whiskey", category: "spirit" },
  // 블렌디드 스코치는 스카치의 한 갈래라 같은 병으로 본다. 아일라는 피트 향이 레시피의
  // 이유라 따로 둔다 (페니실린의 스모키가 그것이다)
  { slug: "scotch", nameKo: "스카치 위스키", nameEn: "Scotch Whisky", category: "spirit", aliases: ["블렌디드 스코치"] },
  { slug: "islay-single-malt", nameKo: "아일라 싱글몰트", nameEn: "Islay Single Malt", category: "spirit" },
  { slug: "cognac", nameKo: "꼬냑", nameEn: "Cognac", category: "spirit" },
  { slug: "calvados", nameKo: "칼바도스", nameEn: "Calvados", category: "spirit" },
  { slug: "absinthe", nameKo: "압생트", nameEn: "Absinthe", category: "spirit" },
  { slug: "soju", nameKo: "증류식 소주", nameEn: "Distilled Soju", category: "spirit" },
  { slug: "munbaeju", nameKo: "문배주 40도", nameEn: "Munbaeju 40%", category: "spirit" },
  { slug: "makgeolli", nameKo: "막걸리", nameEn: "Makgeolli", category: "spirit" },
  { slug: "beer", nameKo: "맥주", nameEn: "Lager Beer", category: "spirit" },
  { slug: "sweet-vermouth", nameKo: "스위트 베르무트", nameEn: "Sweet Vermouth", category: "spirit" },
  { slug: "dry-vermouth", nameKo: "드라이 베르무트", nameEn: "Dry Vermouth", category: "spirit" },
  { slug: "lillet-blanc", nameKo: "릴레 블랑", nameEn: "Lillet Blanc", category: "spirit" },
  { slug: "fino-sherry", nameKo: "피노 셰리", nameEn: "Fino Sherry", category: "spirit" },

  // ── 리큐르 ──────────────────────────────────────────────────────────────
  { slug: "campari", nameKo: "캄파리", nameEn: "Campari", category: "liqueur" },
  { slug: "suze", nameKo: "수즈", nameEn: "Suze", category: "liqueur" },
  // `쿠앵트로`·`코앵트로` 는 같은 병의 다른 표기다
  { slug: "cointreau", nameKo: "쿠앵트로", nameEn: "Cointreau", category: "liqueur", aliases: ["코앵트로"] },
  { slug: "orange-curacao", nameKo: "오렌지 큐라소", nameEn: "Orange Curaçao", category: "liqueur" },
  { slug: "blue-curacao", nameKo: "블루 큐라소", nameEn: "Blue Curaçao", category: "liqueur" },
  { slug: "benedictine", nameKo: "베네딕틴 돔", nameEn: "Bénédictine DOM", category: "liqueur" },
  { slug: "coffee-liqueur", nameKo: "커피 리큐어", nameEn: "Coffee Liqueur", category: "liqueur" },
  { slug: "banana-liqueur", nameKo: "바나나 리큐르", nameEn: "Banana Liqueur", category: "liqueur" },
  { slug: "green-tea-liqueur", nameKo: "녹차 리큐르", nameEn: "Green Tea Liqueur", category: "liqueur" },
  { slug: "lychee-liqueur", nameKo: "리치 리큐르", nameEn: "Lychee Liqueur", category: "liqueur" },
  { slug: "creme-de-cassis", nameKo: "크렘 드 카시스", nameEn: "Crème de Cassis", category: "liqueur" },
  { slug: "sloe-gin", nameKo: "슬로 진", nameEn: "Sloe Gin", category: "liqueur" },

  // ── 비터스 ──────────────────────────────────────────────────────────────
  {
    slug: "angostura-bitters",
    nameKo: "앙고스투라 비터스",
    nameEn: "Angostura Bitters",
    category: "bitters",
    aliases: ["앙고스투라 비터", "앙고스투라 아로마틱 비터"],
  },
  {
    slug: "orange-bitters",
    nameKo: "오렌지 비터스",
    nameEn: "Orange Bitters",
    category: "bitters",
    aliases: ["오렌지 비터"],
  },

  // ── 시럽 · 당류 ─────────────────────────────────────────────────────────
  // 리치 시럽(2:1)은 설탕 시럽을 졸이거나 설탕을 더 타면 된다. 설탕이 있으면 만들 수 있는
  // 것들이라 한 칸으로 둔다 — 병을 따로 사야 하는 것이 아니다
  {
    slug: "simple-syrup",
    nameKo: "설탕 시럽",
    nameEn: "Simple Syrup",
    category: "syrup",
    aliases: ["리치 시럽", "설탕"],
  },
  { slug: "honey-ginger-syrup", nameKo: "생강 꿀 시럽", nameEn: "Honey-Ginger Syrup", category: "syrup" },
  { slug: "orgeat", nameKo: "오르자 시럽", nameEn: "Orgeat", category: "syrup" },
  { slug: "jocheong-syrup", nameKo: "조청 시럽", nameEn: "Jocheong Syrup", category: "syrup" },
  { slug: "citrus-shrub-syrup", nameKo: "시트러스 슈럽 시럽", nameEn: "Citrus Shrub Syrup", category: "syrup" },

  // ── 주스 ────────────────────────────────────────────────────────────────
  { slug: "lemon-juice", nameKo: "레몬 주스", nameEn: "Lemon Juice", category: "juice" },
  { slug: "lime-juice", nameKo: "라임 주스", nameEn: "Lime Juice", category: "juice" },
  { slug: "grapefruit-juice", nameKo: "자몽 주스", nameEn: "Grapefruit Juice", category: "juice" },
  { slug: "tomato-juice", nameKo: "토마토 주스", nameEn: "Tomato Juice", category: "juice" },
  { slug: "cranberry-juice", nameKo: "크랜베리 주스", nameEn: "Cranberry Juice", category: "juice" },
  { slug: "pineapple-juice", nameKo: "파인애플 주스", nameEn: "Pineapple Juice", category: "juice" },

  // ── 섞는 것 ─────────────────────────────────────────────────────────────
  { slug: "soda-water", nameKo: "탄산수", nameEn: "Soda Water", category: "mixer", aliases: ["소다"] },
  { slug: "tonic-water", nameKo: "토닉워터", nameEn: "Tonic Water", category: "mixer" },
  { slug: "ginger-beer", nameKo: "진저비어", nameEn: "Ginger Beer", category: "mixer" },
  { slug: "grapefruit-soda", nameKo: "자몽 소다", nameEn: "Grapefruit Soda", category: "mixer" },
  { slug: "espresso", nameKo: "에스프레소", nameEn: "Espresso", category: "mixer" },
  { slug: "oolong-tea", nameKo: "우롱차", nameEn: "Oolong Tea", category: "mixer" },
  { slug: "milk", nameKo: "우유", nameEn: "Milk", category: "mixer" },
  { slug: "coconut-cream", nameKo: "코코넛 크림", nameEn: "Coconut Cream", category: "mixer" },
  { slug: "egg-white", nameKo: "달걀 흰자", nameEn: "Egg White", category: "mixer" },
  {
    slug: "worcestershire-tabasco",
    nameKo: "우스터소스 · 타바스코",
    nameEn: "Worcestershire · Tabasco",
    category: "mixer",
  },

  // ── 가니시 ── 여기부터는 필수 재료로 세지 않는다 ─────────────
  { slug: "lemon-peel", nameKo: "레몬 필", nameEn: "Lemon Peel", category: "garnish" },
  { slug: "orange-peel", nameKo: "오렌지 필", nameEn: "Orange Peel", category: "garnish" },
  { slug: "lemon-wheel", nameKo: "레몬 휠", nameEn: "Lemon Wheel", category: "garnish" },
  { slug: "orange-wheel", nameKo: "오렌지 휠", nameEn: "Orange Wheel", category: "garnish" },
  { slug: "lime-wedge", nameKo: "라임 웨지", nameEn: "Lime Wedge", category: "garnish" },
  // 카이피로스카의 통 라임은 잔에서 으깨는 것이라 주스에 가깝지만, 사 두는 물건으로는
  // 라임 웨지와 같은 것이다. 한 칸으로 둔다
  { slug: "lime", nameKo: "라임", nameEn: "Lime", category: "garnish" },
  { slug: "grapefruit-slice", nameKo: "자몽 조각", nameEn: "Grapefruit Slice", category: "garnish" },
  { slug: "maraschino-cherry", nameKo: "마라스키노 체리", nameEn: "Cherry", category: "garnish" },
  { slug: "mint", nameKo: "민트", nameEn: "Mint Leaves", category: "garnish" },
  { slug: "rosemary", nameKo: "로즈마리", nameEn: "Rosemary", category: "garnish" },
  { slug: "pear-peel", nameKo: "배 껍질", nameEn: "Pear Peel", category: "garnish" },
  { slug: "salt", nameKo: "소금", nameEn: "Salt Rim", category: "garnish" },
];

/**
 * 레시피 줄 하나가 요구하는 것 — **이 중 아무거나** 있으면 된다.
 *
 * `"버번 또는 라이"` 처럼 택일로 적힌 줄이 있어 슬러그 하나로는 안 담긴다.
 * 보통은 한 칸짜리 배열이다.
 */
export type Requirement = string[];

/** 택일로 적힌 줄. 왼쪽 이름을 오른쪽 슬러그들 중 하나로 읽는다. */
const EITHER: Record<string, string[]> = {
  "버번 또는 라이": ["bourbon", "rye"],
};

const BY_NAME: Map<string, Requirement> = (() => {
  const map = new Map<string, Requirement>();
  for (const ing of INGREDIENTS) {
    map.set(ing.nameKo, [ing.slug]);
    for (const alias of ing.aliases ?? []) map.set(alias, [ing.slug]);
  }
  for (const [name, slugs] of Object.entries(EITHER)) map.set(name, slugs);
  return map;
})();

const BY_SLUG = new Map(INGREDIENTS.map((i) => [i.slug, i]));

export function getIngredient(slug: string): Ingredient | undefined {
  return BY_SLUG.get(slug);
}

/**
 * 레시피 줄의 한국어 이름을 마스터로 읽는다. 못 읽으면 `null` 이다.
 *
 * `null` 은 데이터가 어긋났다는 뜻이지 "없어도 되는 재료" 가 아니다 —
 * `scripts/ingredient-guard.mjs` 가 `npm run check` 에서 0건을 강제한다.
 */
export function resolveIngredient(nameKo: string): Requirement | null {
  return BY_NAME.get(nameKo) ?? null;
}

/** 필수 재료로 세는가. 마스터에 없으면 세지 않는다. */
export function countsForStock(slug: string): boolean {
  const ing = BY_SLUG.get(slug);
  return ing ? !NOT_COUNTED.has(ing.category) : false;
}

/** 체크리스트에 낼 재료. 가니시는 판정에 안 쓰므로 고르게 하지 않는다. */
export const STOCKABLE: Ingredient[] = INGREDIENTS.filter((i) => countsForStock(i.slug));

/** 이 재료를 쓰는 칵테일의 슬러그. 재료 상세가 쓴다 (`FR-INGREDIENT-002`). */
export function cocktailsUsing(slug: string): string[] {
  return COCKTAILS.filter((c) =>
    c.ingredients.some((line) => resolveIngredient(line.ko)?.includes(slug))
  ).map((c) => c.id);
}
