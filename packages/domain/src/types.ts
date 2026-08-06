/**
 * 분류 축의 정본. PRD 5장 + 시안을 합쳐 ADR-0002에서 확정한 값이다.
 * enum은 PRD 기준으로 완전하게 두고(카테고리 URL이 되므로), 화면에는
 * 아카이브에 실제로 존재하는 값만 노출한다 — `basesInCorpus` 등을 쓸 것.
 */

/* ─────────────────  축 1 · 기주 (단일값, 필수)  ───────────────── */

export type BaseSpirit =
  | "진"
  | "보드카"
  | "위스키"
  | "럼"
  | "데킬라 · 메즈칼"
  | "브랜디"
  | "리큐르"
  | "와인 · 스파클링"
  | "전통주"
  | "무알콜";

/** 카테고리 URL `/cocktails/base/<slug>/`. 한번 노출되면 리다이렉트 없이 못 바꾼다. */
export const BASE_SLUGS: Record<BaseSpirit, string> = {
  진: "gin",
  보드카: "vodka",
  위스키: "whisky",
  럼: "rum",
  "데킬라 · 메즈칼": "agave",
  브랜디: "brandy",
  리큐르: "liqueur",
  "와인 · 스파클링": "wine",
  // PRD 5.1은 `soju`였으나 막걸리·문배주를 소주로 부르는 건 부정확하다 (ADR-0002).
  전통주: "korean",
  무알콜: "non-alcoholic",
};

/* ─────────────────  축 2 · 스타일 (복수, primary 필수)  ───────────────── */

/** 레시피 **구조** 기준. 시대 구분(클래식/모던)으로 잡으면 필터로 쓸모가 없다. */
export type StyleKey =
  | "highball"
  | "sour"
  | "spirit-forward"
  | "spritz"
  | "tiki"
  | "creamy"
  | "hot"
  | "frozen"
  | "shot";

/* ─────────────────  축 3 · 메이킹 방법 (단일값, 필수)  ───────────────── */

/** 실질 가치는 난이도 프록시다. Build만 켜면 도구 없이 오늘 만들 수 있는 것만 남는다. */
export type Technique = "Build" | "Shake" | "Stir" | "Blend" | "Etc";

/* ─────────────────  필터 축 (카테고리 아님 — 색인하지 않음)  ───────────────── */

/**
 * PRD 6.3의 9개와 시안의 7개를 합집합한 결과 (ADR-0002).
 * `sour`는 시안에서 왔다 — 위스키 사워처럼 시트러스 향이 주인공이 아닌 산미가 있어
 * `citrus`와 한 칸에 넣을 수 없다.
 */
export type FlavorKey =
  | "citrus"
  | "sour"
  | "fruity"
  | "floral"
  | "herbal"
  | "spicy"
  | "smoky"
  | "bitter"
  | "nutty"
  | "creamy";

/** 0 드라이 · 1 세미 드라이 · 2 세미 스위트 · 3 스위트 */
export type SweetLevel = 0 | 1 | 2 | 3;

/**
 * 도수 4구간. 연속 슬라이더를 쓰지 않는 이유는 `R-F2.1-2`가 모든 필터 값에
 * 결과 개수를 요구하는데 슬라이더 눈금에는 카운트를 붙일 수 없기 때문이다 (ADR-0003).
 * 탐색 필터와 취향 파인더가 이 정의를 공유한다.
 */
export type AbvBand = "na" | "low" | "mid" | "high";

/* ─────────────────  레코드  ───────────────── */

export interface Ingredient {
  ko: string;
  en: string;
  /** 1인분 기준 용량(ml). 계량하지 않는 재료는 생략하고 `amount`를 쓴다. */
  ml?: number;
  /** "1조각", "2 dash"처럼 배수 계산에서 제외되는 고정 표기 */
  amount?: string;
  /** 대체 재료 안내. 있으면 상세 화면에 "대체 가능" 버튼이 붙는다. */
  sub?: string;
}

export interface Origin {
  year: string;
  place: string;
  creator: string;
}

export interface Story {
  title: string;
  paragraphs: [string, string];
}

/** 단맛 · 산미 · 쓴맛 · 향 강도 · 알코올 — 각 0~5 */
export type Profile = [number, number, number, number, number];

export interface Cocktail {
  id: string;
  ko: string;
  en: string;

  /** 카테고리 3축 — 전부 필수 (PRD R-C-1). NULL을 허용하면 카테고리 페이지에 구멍이 난다. */
  base: BaseSpirit;
  styles: StyleKey[];
  /** 배리에이션 추천의 1순위 기준 (PRD R-C-3). `styles`에 반드시 포함돼야 한다. */
  stylePrimary: StyleKey;
  method: Technique;

  /** 표준 배합 기준 실측 도수(% vol) */
  abv: number;
  sweet: SweetLevel;
  /** 최소 1개, 최대 3개 (PRD R-F1.2-1) */
  flavors: FlavorKey[];

  glass: string;
  summary: string;
  ingredients: Ingredient[];
  steps: string[];
  origin: Origin;
  story: Story;
  profile: Profile;
}
