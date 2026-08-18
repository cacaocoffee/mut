import {
  BASE_SPIRIT_LABELS,
  FLAVOR_KEY_LABELS,
  STYLE_KEY_LABELS,
  SWEETNESS,
  TECHNIQUES,
  getCocktail,
  type BaseSpirit,
  type Cocktail,
  type StyleKey,
  type SweetLevel,
  type Technique,
} from "@kca/domain";
import type { CocktailDetail } from "./api";

/**
 * 상세 화면이 보는 한 가지 모양 (ISSUE-038).
 *
 * 출처가 둘이다 — 공개 API 와 프로토타입 배열. 화면에서 갈래를 타면 두 벌이 되고,
 * **한쪽만 고치는 일이 반드시 생긴다.** 여기서 한 모양으로 맞춘다.
 *
 * 폴백은 API 주소가 없을 때만 쓴다 (`lib/api.ts` 참조). 주소를 넣으면 API 만 쓴다.
 */
export interface CocktailView {
  slug: string;
  nameKo: string;
  nameEn: string;
  summary: string;

  /** 향과 맛 서술. 발행 필수라 발행분에는 반드시 있다 (`GATE-COCKTAIL-01`). */
  tastingNote: string | null;
  /** 향 태그. 계약은 슬러그와 한국어를 함께 준다 (`TaxonRef`). */
  aromaTags: { slug: string; labelKo: string }[];

  base: { slug: string; labelKo: string };
  stylePrimary: { slug: string; labelKo: string };
  styles: { slug: string; labelKo: string }[];
  method: { slug: string; labelKo: string };

  abv: number | null;
  glassType: string;
  sweetness: { slug: string; ko: string; en: string };

  /**
   * 재료 줄. **미리 만든 문자열이 아니라 수치와 판정을 그대로 넘긴다** (ISSUE-043).
   *
   * 잔 수와 표기 단위는 화면에서 바뀌므로 여기서 문자열로 굳히면 환산할 것이 없다.
   * `isScalable` 은 서버 판정을 그대로 옮긴 값이다 — 프론트가 다시 정하지 않는다.
   */
  ingredients: {
    nameKo: string;
    nameEn: string;
    amount: number | null;
    unit: string | null;
    amountLabel: string | null;
    isScalable: boolean;
    isOptional: boolean;
    substitute: string | null;
  }[];
  steps: string[];

  story: { title: string | null; paragraphs: string[] } | null;
  origin: { year: string | null; place: string | null; creator: string | null } | null;

  /** 맛 프로필 5축. 계약에 없다 — `FR-COCKTAIL-023` 이 P1 이라 프로토타입에만 있다. */
  profile: number[] | null;

  actions: { targetType: string; targetSlug: string; sharePath: string };
}

/** 계약 응답 → 화면 모양. */
export function fromApi(detail: CocktailDetail): CocktailView {
  // 계약은 `{slug, labelKo}` 로 준다. 영문 표기는 계약에 없어 프론트 표에서 가져온다.
  const sweet = detail.spec.sweetness.slug as SweetLevel;
  const [, sweetEn] = SWEETNESS[sweet];

  return {
    slug: detail.slug,
    nameKo: detail.hero.nameKo,
    nameEn: detail.hero.nameEn,
    summary: detail.hero.summary,

    tastingNote: detail.tastingNote?.note ?? null,
    aromaTags: (detail.tastingNote?.aromaTags ?? []).map(taxon),

    base: taxon(detail.classification.base),
    stylePrimary: taxon(detail.classification.stylePrimary),
    styles: detail.classification.styles.map(taxon),
    method: taxon(detail.classification.method),

    abv: detail.spec.abv ?? null,
    glassType: detail.spec.glassType,
    sweetness: { slug: sweet, ko: detail.spec.sweetness.labelKo, en: sweetEn },

    ingredients: detail.ingredients.map((line) => ({
      nameKo: line.nameKo,
      nameEn: line.nameEn,
      // 계량한 것은 `amount`+`unit`, 아닌 것은 `amountLabel` 이다 (`"1조각"`).
      amount: line.amount ?? null,
      unit: line.unit ?? null,
      amountLabel: line.amountLabel ?? null,
      // 서버 판정을 그대로 옮긴다 (이슈 010 · 043 RED 6)
      isScalable: line.isScalable,
      isOptional: line.isOptional,
      // 대체는 재료 참조일 수도 안내 문구일 수도 있다 (`GATE-COCKTAIL-06` 이 둘 중 하나를 요구한다).
      substitute: line.substitute?.note ?? line.substitute?.nameKo ?? null,
    })),
    steps: detail.steps.map((s) => s.text),

    story: storyOf(detail.story),
    origin: detail.origin
      ? {
          year: detail.origin.year ?? null,
          place: detail.origin.place ?? null,
          creator: detail.origin.creator ?? null,
        }
      : null,

    // 계약이 주지 않는다. 레이더는 P1 이라 API 에 자리가 없다 (GAPS 등재 대상).
    profile: null,

    actions: {
      targetType: detail.actions.bookmarkTargetType,
      targetSlug: detail.actions.bookmarkTargetSlug,
      sharePath: detail.actions.sharePath,
    },
  };
}

/** 프로토타입 배열 → 화면 모양. API 주소가 없을 때만 쓴다. */
export function fromPrototype(slug: string): CocktailView | null {
  const c = getCocktail(slug);
  if (!c) return null;

  const [sweetKo, sweetEn] = SWEETNESS[c.sweet];

  return {
    slug: c.id,
    nameKo: c.ko,
    nameEn: c.en,
    summary: c.summary,

    tastingNote: c.tastingNote ?? null,
    aromaTags: c.flavors.map((f) => ({ slug: f, labelKo: FLAVOR_KEY_LABELS[f] })),

    base: { slug: c.base, labelKo: BASE_SPIRIT_LABELS[c.base as BaseSpirit] },
    stylePrimary: { slug: c.stylePrimary, labelKo: STYLE_KEY_LABELS[c.stylePrimary as StyleKey] },
    styles: c.styles.map((s) => ({ slug: s, labelKo: STYLE_KEY_LABELS[s as StyleKey] })),
    method: { slug: c.method, labelKo: TECHNIQUES[c.method as Technique].ko },

    abv: c.abv,
    glassType: c.glass,
    sweetness: { slug: c.sweet, ko: sweetKo, en: sweetEn },

    ingredients: c.ingredients.map((i) => ({
      nameKo: i.ko,
      nameEn: i.en,
      amount: i.ml ?? null,
      unit: i.ml != null ? "ml" : null,
      // 프로토타입의 `amount` 가 계약의 `amountLabel` 이다 — `"1조각"` 처럼 배수에서 빠지는 표기.
      amountLabel: i.amount ?? null,
      // **여기만 프론트가 판정한다.** 서버가 없을 때 쓰는 폴백이라 서버와 같은 규칙을 적는다
      // (`amountLabel` 이 없고 `amount` 가 있으면 배수 대상 — `RecipeIngredient.isScalable`).
      isScalable: i.amount == null && i.ml != null,
      isOptional: false,
      substitute: i.sub ?? null,
    })),
    steps: c.steps,

    story: { title: c.story.title, paragraphs: [...c.story.paragraphs] },
    origin: c.origin,
    profile: c.profile,

    actions: {
      targetType: "cocktail",
      targetSlug: c.id,
      sharePath: `/cocktails/${c.id}`,
    },
  };
}

/** 프로토타입 목록. `generateStaticParams` 의 폴백이다. */
export function prototypeSlugs(cocktails: Cocktail[]): string[] {
  return cocktails.map((c) => c.id);
}

function taxon(ref: { slug: string; labelKo: string }) {
  return { slug: ref.slug, labelKo: ref.labelKo };
}

/**
 * 시드가 `## 제목` + 빈 줄 + 문단으로 직렬화했다 (이슈 036).
 * 제목이 없으면 전체를 문단으로 읽는다 — 어드민에서 손으로 쓴 것은 그 모양이 아닐 수 있다.
 */
function storyOf(story: string | null | undefined): CocktailView["story"] {
  if (!story?.trim()) return null;

  const lines = story.split("\n").map((l) => l.trim());
  const heading = lines[0]?.startsWith("## ") ? lines[0].slice(3).trim() : null;
  const body = (heading ? lines.slice(1) : lines).filter(Boolean);

  return { title: heading, paragraphs: body };
}
