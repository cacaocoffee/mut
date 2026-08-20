/**
 * 분류 축의 **정본은 Kotlin 이다** (`PRIN-T02`).
 *
 * 이 파일은 프로토타입 시절 손으로 쓴 것이었다 — 기주가 한국어(`"진"`)이고 당도가
 * 숫자(`0`)였다. 이슈 037 이 그것을 계약 생성물로 바꿨다.
 *
 * ## 무엇이 어디에 있나
 *
 * | | 어디 | 왜 |
 * |---|---|---|
 * | 분류 축 5종 | `generated/api.ts` (계약) | 정본이 Kotlin 이다 |
 * | 한국어 이름 | `generated/labels.ts` (계약의 `x-labels`) | 같은 이유 |
 * | 화면 표시 문구 | `data.ts` | `"CITRUS 시트러스"` 처럼 꾸민 것. 계약의 관심사가 아니다 |
 * | 프론트 전용 타입 | 이 파일 | 필터 상태·파인더 단계처럼 서버가 모르는 것 |
 *
 * 표시 문구를 `Record<StyleKey, string>` 으로 두면 **축이 늘 때 빌드가 깨진다** —
 * 손으로 쓴 목록이 조용히 낡는 것을 그것이 막는다.
 */
import type { components } from "./generated/api";

/* ─────────────────  분류 축 — 계약이 정본 (PRIN-T02)  ───────────────── */

/**
 * 축 1 · 기주 (단일값 필수, `R-C-1`).
 *
 * 값이 **슬러그**다. 카테고리 URL `/cocktails/base/gin` 이 그대로 이 값이고,
 * 한번 노출되면 리다이렉트 없이 못 바꾼다 (`PRIN-D02`).
 *
 * 전통주가 `korean` 이다 — PRD 5.1 은 `soju` 였으나 막걸리·문배주를 소주로 부르는 것은
 * 부정확하다 (ADR-0002).
 */
export type BaseSpirit = components["schemas"]["BaseSpirit"];

/** 축 2 · 스타일 (복수, `stylePrimary` 필수). 레시피 **구조** 기준이다. */
export type StyleKey = components["schemas"]["StyleKey"];

/** 축 3 · 메이킹 방법. 실질 가치는 난이도 대신 쓰는 값이다 — `build` 만 켜면 도구 없이 만든다. */
export type Technique = components["schemas"]["Technique"];

/** 향 태그 1~3개 (`R-F1.2-1`). 카테고리가 아니라 필터 축이라 색인하지 않는다. */
export type FlavorKey = components["schemas"]["FlavorKey"];

/**
 * 당도 4단계.
 *
 * 프로토타입은 `0 | 1 | 2 | 3` 이었다. 숫자는 **의미가 순서에 숨어** 있어서,
 * 값을 하나 끼워 넣는 순간 저장된 데이터가 전부 뒤집힌다.
 */
export type SweetLevel = components["schemas"]["SweetLevel"];

/* ─────────────────  프론트 전용  ───────────────── */

/**
 * 도수 4구간.
 *
 * 연속 슬라이더를 쓰지 않는 이유는 `R-F2.1-2` 가 모든 필터 값에 결과 개수를 요구하는데
 * 슬라이더 눈금에는 개수를 붙일 수 없기 때문이다 (ADR-0003).
 *
 * **계약에 없다.** 도수는 서버가 숫자로 주고 구간 나누기는 화면의 판단이라
 * `abvBandOf()` 한 곳에서만 정의한다.
 */
export type AbvBand = "na" | "low" | "mid" | "high";

/**
 * 아티클의 주제 축 (ADR-0010).
 *
 * **계약에 없다.** SPEC-02 §6 의 `Article.type` 은 발행 형식 축
 * (`interview`·`guide`·`trend`·`photo_essay`)이고, 이 축은 지금 있는 글의 묶음이다.
 * 두 축의 관계는 `GET /articles` 계약을 만들 때 정한다 (GAPS G-49).
 */
export type ArticleCategory = "cocktail" | "bar" | "whisky";

/**
 * 아티클 본문 한 덩이. 문단 · 소제목 · 인용 · 사진 네 가지뿐이다 —
 * 블로그(SmartEditor)에서 이관하며 실제로 나온 종류가 이 넷이다.
 */
export type ArticleBlock =
  | { kind: "paragraph"; text: string }
  | { kind: "heading"; text: string }
  | { kind: "quote"; text: string }
  /** width·height 는 원본 픽셀 크기 — 화면이 자리를 미리 잡아 로딩 때 밀리지 않는다 (NFR-P-03) */
  | { kind: "figure"; src: string; width: number; height: number; caption?: string };

/**
 * 아티클 (`FR-CONTENT-001` 앞당김 · ADR-0010).
 *
 * **계약에 없다.** `article` 테이블(SPEC-06 §3.6)과 `GET /articles` 는 Phase 2 다.
 * 그때까지 본문은 `articles/` 아래 정적 데이터로 있고, 이 타입이 그 모양이다.
 */
export interface Article {
  slug: string;
  category: ArticleCategory;
  title: string;
  /** 카드와 메타 설명에 쓰는 한두 문장 요약 */
  dek: string;
  /** YYYY-MM-DD — 블로그 원문의 발행일 */
  publishedAt: string;
  /** 대표 사진. `/articles/{slug}/…` 아래 정적 파일 */
  hero: string;
  /** 블로그 원문 주소. 상세 하단에 출처로 표기한다 (ADR-0010 이관 규칙) */
  sourceUrl: string;
  /** 본문이 다루는 칵테일의 코퍼스 id — 상세의 "관련 칵테일" 링크가 된다 */
  relatedCocktailSlugs: string[];
  blocks: ArticleBlock[];
}

/* ─────────────────  레코드  ───────────────── */

/**
 * 레시피 한 줄. **재료 마스터가 아니다** — 그쪽은 `ingredients.ts` 의 [Ingredient] 다.
 *
 * 계약도 둘을 나눠 부른다 (`IngredientLine` · `IngredientItem`). 예전에는 이 타입 이름이
 * `Ingredient` 였는데, 마스터가 생기면서 같은 이름이 두 뜻을 갖게 됐다.
 */
export interface RecipeLine {
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

  /**
   * 향과 맛 서술. **발행 필수**다 (`GATE-COCKTAIL-01` · `R-F1.1-2`).
   *
   * `PRIN-P03` 이 이것을 필수로 만든 이유는 "직접 만들어 보고 쓴 내용" 이어야 해서다 —
   * 다른 사이트 설명을 옮기면 레시피 나열형 블로그와 구별되지 않는다.
   *
   * 없는 항목이 있다. 프로토타입의 `summary` 가 만드는 법을 적은 경우인데
   * (`"온도는 −3℃ 이하로 유지한다"`), 그것은 향·맛 서술이 아니라서 옮기지 않았다.
   * 그 항목들은 `draft` 로 남고 에디터가 채운다.
   */
  tastingNote?: string;

  ingredients: RecipeLine[];
  steps: string[];
  origin: Origin;
  story: Story;
  profile: Profile;
}
