import {
  ABV_BANDS,
  ABV_BAND_KEYS,
  BASES,
  COCKTAILS,
  FLAVOR_KEYS,
  STYLE_KEYS,
  TECHNIQUES,
  abvBandOf,
} from "./data";
import { SWEET_LEVELS, sweetRank } from "./data";
import type { components } from "./generated/api";
import type {
  AbvBand,
  BaseSpirit,
  Cocktail,
  FlavorKey,
  StyleKey,
  SweetLevel,
  Technique,
} from "./types";

/**
 * 필터가 보는 코퍼스 한 줄.
 *
 * 목록 API 의 `CocktailListItem` 과 프로토타입의 [Cocktail] 이 여기로 모인다 —
 * 필터 로직이 데이터 출처를 모르게 하려는 것이다 (SPEC-01 §6 "클라이언트 필터로 유지",
 * 이슈 040 GREEN "바뀌는 것은 데이터 출처뿐이다").
 *
 * 필드는 **필터와 카드가 쓰는 것만** 둔다. 상세 필드를 여기 얹으면 500종 응답이
 * 그만큼 커지고, 그것이 DECISIONS §3 이 실측 후로 미룬 항목이다.
 */
export interface SearchItem {
  slug: string;
  nameKo: string;
  nameEn: string;
  summary: string;
  base: BaseSpirit;
  /** `stylePrimary` 가 아니라 보유 스타일 전체다 — 필터가 맞추는 대상 (DECISIONS §1.11) */
  styles: StyleKey[];
  method: Technique;
  sweet: SweetLevel;
  flavors: FlavorKey[];
  /** 도수 미입력분은 `null` 이고 **어느 구간에도 들지 않는다** (`AbvBand` 와 같은 규칙) */
  abv: number | null;
  glass: string;
}

/** 프로토타입 레코드를 코퍼스 한 줄로 옮긴다. API 주소가 없을 때만 쓴다. */
export function searchItemOf(c: Cocktail): SearchItem {
  return {
    slug: c.id,
    nameKo: c.ko,
    nameEn: c.en,
    summary: c.summary,
    base: c.base,
    styles: c.styles,
    method: c.method,
    sweet: c.sweet,
    flavors: c.flavors,
    abv: c.abv,
    glass: c.glass,
  };
}

/** 빌드 부트스트랩용 코퍼스. `KC_API_URL` 이 있으면 쓰지 않는다. */
export const PROTOTYPE_CORPUS: SearchItem[] = COCKTAILS.map(searchItemOf);

export interface Filters {
  bases: BaseSpirit[];
  /** 복수 선택, OR 결합 (PRD 7.1 — 스타일은 복수 축) */
  styles: StyleKey[];
  /** 복수 선택, OR 결합 */
  methods: Technique[];
  /** `null` 이면 전체. 예전에는 `-1` 이었는데, 값이 문자열이 되면서 그 자리가 없어졌다 */
  sweet: SweetLevel | null;
  /** 복수 선택, OR 결합. 4구간 — 슬라이더를 쓰지 않는 이유는 ADR-0003 */
  abvBands: AbvBand[];
  /** 복수 선택, **AND** 결합 — 여섯 축 중 여기만 다르다 (SPEC-07 §3.1) */
  flavors: FlavorKey[];
  query: string;
}

export const DEFAULT_FILTERS: Filters = {
  bases: [],
  styles: [],
  methods: [],
  sweet: null,
  abvBands: [],
  flavors: [],
  query: "",
};

/** 모든 축을 AND로 교차한 뒤 도수 낮은 순으로 정렬한다. */
export function filterCocktails(corpus: SearchItem[], f: Filters): SearchItem[] {
  const q = f.query.trim().toLowerCase();
  return corpus
    .filter(
      (x) =>
        (f.sweet === null || x.sweet === f.sweet) &&
        (f.bases.length === 0 || f.bases.includes(x.base)) &&
        (f.styles.length === 0 || f.styles.some((s) => x.styles.includes(s))) &&
        (f.methods.length === 0 || f.methods.includes(x.method)) &&
        (f.flavors.length === 0 || f.flavors.every((k) => x.flavors.includes(k))) &&
        (f.abvBands.length === 0 || bandsOf(x).some((b) => f.abvBands.includes(b))) &&
        (!q || x.nameKo.toLowerCase().includes(q) || x.nameEn.toLowerCase().includes(q))
    )
    .sort((a, b) => (a.abv ?? 0) - (b.abv ?? 0));
}

/**
 * 각 필터 값 옆에 붙일 실시간 결과 개수 (`R-F2.1-2` · `FR-SEARCH-002`).
 *
 * ## 응답 모양이 서버와 같다
 *
 * 반환 타입이 `GET /cocktails/facets` 의 계약 그대로다 (SPEC-05 §5 — "UI 계약은 두
 * 단계에서 동일하다. **계산 위치만 바뀐다**"). 화면은 이 모양만 알고, 데이터가 커져
 * 서버 계산으로 옮길 때 컴포넌트를 고치지 않는다. 계약이 바뀌면 여기서 빌드가 깨진다.
 *
 * ## 축마다 계산이 다르다
 *
 * | 축 | 계산 |
 * |---|---|
 * | 기주 · 스타일 · 메이킹 · 당도 · 도수 | **같은 축의 현재 선택을 무시**하고 그 값만 골랐을 때의 수 |
 * | 향·맛 | **현재 선택에 이 태그를 더했을 때**의 수 |
 *
 * 앞의 다섯은 OR 라 같은 축을 하나 더 고르면 결과가 늘어난다 — 자기 선택을 반영한
 * 카운트를 보여 주면 "보드카 0" 처럼 읽혀 다시 고를 수 없게 된다. 향·맛은 AND 라
 * 더할수록 줄어들고, 조합 불가능한 태그가 즉시 0 으로 떨어져야 한다 (`FR-SEARCH-009`).
 *
 * ## 0 과 부재를 구분한다
 *
 * - **0**: 코퍼스에 그 값이 있는데 현재 조합에서 0건 → 키가 있고 값이 0. 화면이 비활성 칩으로 그린다
 * - **부재**: 코퍼스에 아예 없는 값 → **키 자체가 없다.** 칩을 만들지 않는다 (ADR-0002 §5)
 *
 * 둘을 합치면 "영영 없는 값" 과 "지금 조합에서만 없는 값" 이 같아 보인다.
 */
export function facetCounts(corpus: SearchItem[], f: Filters): FacetCounts {
  const matched = (patch: Partial<Filters>) => filterCocktails(corpus, { ...f, ...patch });

  return {
    base: axis(BASES, corpus, matched({ bases: [] }), (x) => [x.base]),
    style: axis(STYLE_KEYS, corpus, matched({ styles: [] }), (x) => x.styles),
    method: axis(TECHNIQUE_KEYS, corpus, matched({ methods: [] }), (x) => [x.method]),
    sweet: axis(SWEET_LEVELS, corpus, matched({ sweet: null }), (x) => [x.sweet]),
    abv: axis(ABV_BAND_KEYS, corpus, matched({ abvBands: [] }), bandsOf),
    // 향·맛만 같은 축 선택을 유지한다 — 현재 결과 중 그 태그를 가진 것의 수다
    flavor: axis(FLAVOR_KEYS, corpus, matched({}), (x) => x.flavors),
  };
}

/** SPEC-07 §3.2 응답. 축별 `슬러그 → 개수` 맵이다. */
export type FacetCounts = components["schemas"]["FacetCounts"];

export const TECHNIQUE_KEYS = Object.keys(TECHNIQUES) as Technique[];

/**
 * 한 축의 카운트.
 *
 * @param order 열거 순서. 응답 키 순서가 흔들리면 서버 대조가 매번 깨진다
 * @param corpus 키 목록을 정한다 — 필터를 걸지 않은 전체
 * @param matched 값을 정한다 — 그 축의 선택만 뺀 결과
 */
function axis<T extends string>(
  order: readonly T[],
  corpus: SearchItem[],
  matched: SearchItem[],
  valuesOf: (item: SearchItem) => readonly T[]
): Record<string, number> {
  const present = new Set(corpus.flatMap((x) => valuesOf(x)));
  const counted = new Map<T, number>();
  for (const item of matched) {
    for (const v of valuesOf(item)) counted.set(v, (counted.get(v) ?? 0) + 1);
  }

  return Object.fromEntries(
    order.filter((v) => present.has(v)).map((v) => [v, counted.get(v) ?? 0])
  );
}

/** 도수 미입력분은 구간이 없다 — 서버의 `abv IS NULL` 과 같은 취급이다. */
function bandsOf(x: SearchItem): AbvBand[] {
  return x.abv === null ? [] : [abvBandOf(x.abv)];
}

/* ─────────────────────────  URL 계약  ───────────────────────── */

/**
 * 필터는 URL 쿼리스트링에만 산다 — 공유 가능하되 색인 대상은 아니다
 * (`FR-SEARCH-005` · `R-F2.1-1` · `NFR-S-02`).
 *
 * **파라미터 이름과 구분자가 `GET /cocktails` 와 같다** (SPEC-05 §4 — "데이터가 커지면
 * 서버 필터로 옮기되 URL 계약은 유지한다"). 같은 쿼리스트링을 서버에 그대로 넘길 수 있어야
 * 대조 테스트가 성립한다.
 *
 * 서버는 모르는 값에 400 을 주지만 여기서는 **버린다.** 주소는 사용자가 손으로 고칠 수
 * 있고, 오타 하나로 아무것도 못 보게 되는 것보다 그 축이 풀린 결과를 보여 주는 편이 낫다.
 */
export function parseFilterQuery(params: URLSearchParams): Filters {
  const list = <T extends string>(key: string, all: readonly T[]): T[] =>
    (params.get(key)?.split(",") ?? []).filter((v): v is T => all.includes(v as T));

  const sweet = params.get("sweet");

  return {
    bases: list("base", BASES),
    styles: list("style", STYLE_KEYS),
    methods: list("method", TECHNIQUE_KEYS),
    // 당도는 단일값이다 (DECISIONS §1.11). 서버는 복수에 400 이고 여기서는 첫 값만 본다.
    sweet: SWEET_LEVELS.includes(sweet as SweetLevel) ? (sweet as SweetLevel) : null,
    abvBands: list("abv", ABV_BAND_KEYS),
    flavors: list("flavor", FLAVOR_KEYS),
    query: params.get("q") ?? "",
  };
}

/** [parseFilterQuery] 의 역방향. 빈 축은 파라미터 자체를 쓰지 않는다 — 서버가 빈 값에 400 이다. */
export function toFilterQuery(f: Filters): URLSearchParams {
  const p = new URLSearchParams();
  if (f.bases.length) p.set("base", f.bases.join(","));
  if (f.styles.length) p.set("style", f.styles.join(","));
  if (f.methods.length) p.set("method", f.methods.join(","));
  if (f.sweet) p.set("sweet", f.sweet);
  if (f.abvBands.length) p.set("abv", f.abvBands.join(","));
  if (f.flavors.length) p.set("flavor", f.flavors.join(","));
  if (f.query.trim()) p.set("q", f.query.trim());
  return p;
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
      { ko: "단맛 절대 사절", en: "Dry", value: "dry" },
      { ko: "살짝 달콤", en: "Semi-Dry / Semi-Sweet", value: "semi_dry" },
      { ko: "달콤함 선호", en: "Sweet", value: "sweet" },
      { ko: "상관없음", en: "Any", value: "any" },
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
      // 다른 선택지는 여러 기주를 묶은 이름(`clear`·`warm`)이고 이것만 기주 슬러그 그대로다.
      { ko: "위스키", en: "Whiskey", value: "whisky" },
      { ko: "럼 · 데킬라 · 전통주", en: "Rum / Tequila / Korean", value: "warm" },
    ],
  },
];

export type Answers = Partial<Record<QuestionKey, number | string>>;

/**
 * 답변을 쿼리스트링에서 읽는다 (이슈 041 RED 9 — 단계마다 주소가 바뀐다).
 *
 * **도수만 탐색 필터와 같은 어휘다** — `abv=high` 가 양쪽에서 같은 구간을 뜻한다
 * (`FR-SEARCH-004` · ADR-0003). 당도·기주는 파인더가 **여러 값을 묶은 선택지**라
 * 이름이 겹쳐도 뜻이 다르다: 탐색의 `sweet=semi_dry` 는 그 한 단계지만 파인더의 것은
 * 가운데 두 단계를 덮는다. 두 화면의 주소를 서로 옮겨 붙이지 않는다 (SCREENS-03).
 *
 * 질문에 없는 값은 버린다 — 주소는 손으로 고칠 수 있고, 모르는 값에 화면이 멈추면 안 된다.
 */
export function parseAnswers(params: URLSearchParams): Answers {
  const answers: Answers = {};

  for (const question of QUESTIONS) {
    const raw = params.get(question.key);
    if (raw && question.options.some((o) => String(o.value) === raw)) {
      answers[question.key] = raw;
    }
  }
  return answers;
}

/** [parseAnswers] 의 역방향. 질문 순서를 지켜 주소가 흔들리지 않게 한다. */
export function toAnswerQuery(answers: Answers, step: number): URLSearchParams {
  const p = new URLSearchParams();

  for (const question of QUESTIONS) {
    const v = answers[question.key];
    if (v !== undefined) p.set(question.key, String(v));
  }
  if (step > 0) p.set("step", String(step));
  return p;
}

/** 답변으로 후보군을 좁힌다. 도수·당도·기주는 하드 필터, 향은 점수에만 반영. */
export function quizCandidates(corpus: SearchItem[], answers: Answers): SearchItem[] {
  const abv = answers.abv as AbvBand | undefined;
  const sweet = answers.sweet as string | undefined;
  const base = answers.base as string | undefined;

  return corpus.filter((x) => {
    // 구간 정의는 `abvBandOf` 한 곳에서만 온다 (ADR-0003) — 탐색 필터가 쓰는 그 함수다.
    // 파인더 전용 상수를 두면 두 화면이 다른 답을 낸다 (`FR-SEARCH-004`).
    if (abv !== undefined && !bandsOf(x).includes(abv)) return false;

    // 파인더의 세 선택지는 당도 4단계를 **구간으로 묶은 것**이다 —
    // "살짝 달콤" 은 가운데 둘을 덮고, "달콤함 선호" 는 위 둘을 덮는다.
    if (sweet && sweet !== "any") {
      const rank = sweetRank(x.sweet);
      if (sweet === "dry" && rank !== 0) return false;
      if (sweet === "semi_dry" && (rank < 1 || rank > 2)) return false;
      if (sweet === "sweet" && rank < 2) return false;
    }

    if (base) {
      // 기주가 슬러그가 됐다 (이슈 037). `데킬라` 는 계약에서 `agave` 다 —
      // 예전 목록은 한국어 표기를 적어 두어 데킬라가 실제로는 안 걸리고 있었다.
      if (base === "clear" && !["gin", "vodka"].includes(x.base)) return false;
      if (base === "whisky" && x.base !== "whisky") return false;
      if (base === "warm" && !["rum", "agave", "korean"].includes(x.base)) return false;
    }
    return true;
  });
}

/** 62점에서 출발해 향·당도·도수 일치도를 더한 매칭 점수(최대 98). */
export function matchScore(x: SearchItem, answers: Answers): number {
  const flavor = answers.flavor as FlavorKey | undefined;
  const sweet = answers.sweet as string | undefined;
  const abv = answers.abv as AbvBand | undefined;
  const base = answers.base as string | undefined;

  let s = 62;
  if (flavor && x.flavors.includes(flavor)) s += 22;
  else if (flavor === "citrus" && x.flavors.includes("sour")) s += 14;
  else if (flavor === "bitter" && x.flavors.includes("smoky")) s += 10;

  if (sweet && sweet !== "any") {
    // `semi_dry` 는 가운데 둘을 덮으므로 그 사이(1.5)를 기준으로 잰다.
    const target = sweet === "semi_dry" ? 1.5 : sweetRank(sweet as SweetLevel);
    s += Math.max(0, 10 - Math.abs(sweetRank(x.sweet) - target) * 5);
  }
  // 구간은 이미 하드 필터라 가산점은 구간 **안에서의** 순위 조정용이다.
  // `high`는 위가 열려 있어 폭이 넓으므로 더 독한 쪽을 올린다.
  if (abv === "high" && (x.abv ?? 0) >= 28) s += 6;
  if (base && base !== "any") s += 4;

  return Math.min(98, Math.round(s));
}

export function rankResults(corpus: SearchItem[], answers: Answers, limit = 3) {
  return quizCandidates(corpus, answers)
    .map((cocktail) => ({ cocktail, match: matchScore(cocktail, answers) }))
    .sort((a, b) => b.match - a.match)
    .slice(0, limit);
}
