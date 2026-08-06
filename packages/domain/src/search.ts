import {
  ABV_BANDS,
  ABV_BAND_KEYS,
  BASES_IN_CORPUS,
  COCKTAILS,
  FLAVORS_IN_CORPUS,
  STYLES_IN_CORPUS,
  abvBandOf,
} from "./data";
import type { AbvBand, BaseSpirit, Cocktail, FlavorKey, StyleKey } from "./types";

export interface Filters {
  /** -1 = 전체 */
  sweet: number;
  bases: BaseSpirit[];
  /** 복수 선택, OR 결합 (PRD 7.1 — 스타일은 복수 축) */
  styles: StyleKey[];
  flavors: FlavorKey[];
  /** 복수 선택, OR 결합. 4구간 — 슬라이더를 쓰지 않는 이유는 ADR-0003 */
  abvBands: AbvBand[];
  query: string;
}

export const DEFAULT_FILTERS: Filters = {
  sweet: -1,
  bases: [],
  styles: [],
  flavors: [],
  abvBands: [],
  query: "",
};

/** 모든 축을 AND로 교차한 뒤 도수 낮은 순으로 정렬한다. */
export function filterCocktails(f: Filters): Cocktail[] {
  const q = f.query.trim().toLowerCase();
  return COCKTAILS.filter(
    (x) =>
      (f.sweet < 0 || x.sweet === f.sweet) &&
      (f.bases.length === 0 || f.bases.includes(x.base)) &&
      (f.styles.length === 0 || f.styles.some((s) => x.styles.includes(s))) &&
      (f.flavors.length === 0 || f.flavors.every((k) => x.flavors.includes(k))) &&
      (f.abvBands.length === 0 || f.abvBands.includes(abvBandOf(x.abv))) &&
      (!q || x.ko.toLowerCase().includes(q) || x.en.toLowerCase().includes(q))
  ).sort((a, b) => a.abv - b.abv);
}

/**
 * 각 필터 값 옆에 붙일 실시간 결과 개수 (PRD R-F2.1-2).
 * 0건인 값은 호출부에서 비활성 처리한다.
 *
 * - 당도·기주는 같은 축의 현재 선택을 무시하고 "그 값만 골랐을 때"의 개수를 센다.
 * - 맛/향은 AND 조건이라 "현재 선택에 이 태그를 더했을 때"의 개수를 센다.
 *   조합이 불가능한 태그가 곧바로 0으로 떨어져야 하기 때문이다.
 */
export function facetCounts(f: Filters) {
  const count = (patch: Partial<Filters>) => filterCocktails({ ...f, ...patch }).length;

  return {
    sweet: [0, 1, 2, 3].map((level) => count({ sweet: level })),
    sweetAll: count({ sweet: -1 }),
    bases: Object.fromEntries(
      BASES_IN_CORPUS.map((b) => [b, count({ bases: [b] })])
    ) as Record<BaseSpirit, number>,
    styles: Object.fromEntries(
      STYLES_IN_CORPUS.map((s) => [s, count({ styles: [s] })])
    ) as Record<StyleKey, number>,
    abvBands: Object.fromEntries(
      ABV_BAND_KEYS.map((b) => [b, count({ abvBands: [b] })])
    ) as Record<AbvBand, number>,
    flavors: Object.fromEntries(
      FLAVORS_IN_CORPUS.map((k) => [
        k,
        count({ flavors: f.flavors.includes(k) ? f.flavors : [...f.flavors, k] }),
      ])
    ) as Record<FlavorKey, number>,
  };
}

/** 재료 용량을 인분 배수와 단위에 맞춰 표기한다. */
export function formatAmount(ml: number, servings: number, unit: "ml" | "oz"): string {
  const total = ml * servings;
  if (unit === "ml") return `${Math.round(total * 10) / 10} ml`;
  const oz = total / 30;
  return `${(Math.round(oz * 100) / 100).toFixed(2).replace(/0$/, "").replace(/\.$/, "")} oz`;
}

/* ─────────────────────────  취향 파인더  ───────────────────────── */

export type QuestionKey = "abv" | "sweet" | "flavor" | "base";

export interface QuizOption {
  ko: string;
  en: string;
  value: number | string;
}

export interface Question {
  key: QuestionKey;
  title: string;
  hint: string;
  options: QuizOption[];
}

export const QUESTIONS: Question[] = [
  {
    key: "abv",
    title: "오늘 원하는 도수는 어느 정도인가요?",
    hint: "잔당 알코올 총량 기준으로 후보를 좁힙니다.",
    // 탐색 필터와 **같은 구간 정의**를 쓴다 (ADR-0003). 라벨만 구어체다.
    options: ABV_BANDS.map((b) => ({ ko: b.colloquial, en: b.en, value: b.key })),
  },
  {
    key: "sweet",
    title: "단맛은 얼마나 들어가길 원하시나요?",
    hint: "당도는 4단계로 색인되어 있습니다.",
    options: [
      { ko: "단맛 절대 사절", en: "Dry", value: 0 },
      { ko: "살짝 달콤", en: "Semi-Dry / Semi-Sweet", value: 1 },
      { ko: "달콤함 선호", en: "Sweet", value: 3 },
      { ko: "상관없음", en: "Any", value: -1 },
    ],
  },
  {
    key: "flavor",
    title: "어떤 향에 끌리시나요?",
    hint: "맛/향 태그와 교차 대조합니다.",
    options: [
      { ko: "스모키", en: "Smoky / Peaty", value: "smoky" },
      { ko: "시트러스", en: "Citrus / Sour", value: "citrus" },
      { ko: "허브 · 풀", en: "Herbal", value: "herbal" },
      { ko: "커피 · 견과 · 쓴맛", en: "Bitter / Roasted", value: "bitter" },
    ],
  },
  {
    key: "base",
    title: "선호하는 기주가 있나요?",
    hint: "마지막 축입니다. 상관없음을 고르면 전 항목에서 추천합니다.",
    options: [
      { ko: "상관없음", en: "Any base", value: "any" },
      { ko: "진 · 보드카", en: "Gin / Vodka", value: "clear" },
      { ko: "위스키", en: "Whiskey", value: "위스키" },
      { ko: "럼 · 데킬라 · 전통주", en: "Rum / Tequila / Korean", value: "warm" },
    ],
  },
];

export type Answers = Partial<Record<QuestionKey, number | string>>;

/** 답변으로 후보군을 좁힌다. 도수·당도·기주는 하드 필터, 향은 점수에만 반영. */
export function quizCandidates(answers: Answers): Cocktail[] {
  const abv = answers.abv as AbvBand | undefined;
  const sweet = answers.sweet as number | undefined;
  const base = answers.base as string | undefined;

  return COCKTAILS.filter((x) => {
    if (abv !== undefined && abvBandOf(x.abv) !== abv) return false;
    if (sweet !== undefined && sweet >= 0) {
      if (sweet === 0 && x.sweet !== 0) return false;
      if (sweet === 1 && (x.sweet < 1 || x.sweet > 2)) return false;
      if (sweet === 3 && x.sweet < 2) return false;
    }
    if (base) {
      if (base === "clear" && !["진", "보드카"].includes(x.base)) return false;
      if (base === "위스키" && x.base !== "위스키") return false;
      if (base === "warm" && !["럼", "데킬라", "전통주"].includes(x.base)) return false;
    }
    return true;
  });
}

/** 62점에서 출발해 향·당도·도수 일치도를 더한 매칭 점수(최대 98). */
export function matchScore(x: Cocktail, answers: Answers): number {
  const flavor = answers.flavor as FlavorKey | undefined;
  const sweet = answers.sweet as number | undefined;
  const abv = answers.abv as AbvBand | undefined;
  const base = answers.base as string | undefined;

  let s = 62;
  if (flavor && x.flavors.includes(flavor)) s += 22;
  else if (flavor === "citrus" && x.flavors.includes("sour")) s += 14;
  else if (flavor === "bitter" && x.flavors.includes("smoky")) s += 10;

  if (sweet !== undefined && sweet >= 0) {
    const target = sweet === 1 ? 1.5 : sweet;
    s += Math.max(0, 10 - Math.abs(x.sweet - target) * 5);
  }
  // 구간은 이미 하드 필터라 가산점은 구간 **안에서의** 순위 조정용이다.
  // `high`는 위가 열려 있어 폭이 넓으므로 더 독한 쪽을 올린다.
  if (abv === "high" && x.abv >= 28) s += 6;
  if (base && base !== "any") s += 4;

  return Math.min(98, Math.round(s));
}

export function rankResults(answers: Answers, limit = 3) {
  return quizCandidates(answers)
    .map((cocktail) => ({ cocktail, match: matchScore(cocktail, answers) }))
    .sort((a, b) => b.match - a.match)
    .slice(0, limit);
}
