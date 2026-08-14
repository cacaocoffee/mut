import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import {
  BASE_SPIRIT_LABELS,
  COCKTAILS,
  FLAVOR_KEY_LABELS,
  STYLE_KEY_LABELS,
  SWEETNESS,
  TECHNIQUES,
  type BaseSpirit,
  type FlavorKey,
  type StyleKey,
  type SweetLevel,
  type Technique,
} from "@kca/domain";
import { CATEGORY_AXES, categories, cocktailsByAxis, usingApi, type CategoryAxis } from "./api";

/**
 * 카테고리 페이지 (ISSUE-039 · `FR-COCKTAIL-029`·`030`·`031` · `R-C-2`).
 *
 * ## 축이 셋이고 화면은 하나다
 *
 * `base` · `style` · `method` 가 같은 모양이라 여기 한 벌만 둔다.
 * 라우트 셋은 축 이름만 넘긴다.
 *
 * ## 축을 하나만 받는 것이 `R-C-2` 의 구현이다
 *
 * `axis` 가 하나뿐이라 **조합을 표현할 방법이 없다.** 두 개를 받는 자리가 있으면
 * 언젠가 `/cocktails/base/gin/style/sour` 가 생기고, 그 순간 색인 대상이 곱으로 늘면서
 * 대부분 결과가 0건인 페이지가 쌓인다 (`PRIN-P06` — "조합 폭발").
 *
 * 디렉터리 구조도 같은 말을 한다 — `base/[slug]` 아래에 `style/[slug]` 가 없다.
 */

/** 축마다 다른 것: 이름, 한국어 레이블 표, 프로토타입에서 그 축을 읽는 법. */
const AXIS = {
  base: {
    ko: "기주",
    en: "BASE",
    labelOf: (slug: string) => BASE_SPIRIT_LABELS[slug as BaseSpirit],
    matches: (c: (typeof COCKTAILS)[number], slug: string) => c.base === slug,
  },
  style: {
    ko: "스타일",
    en: "STYLE",
    labelOf: (slug: string) => STYLE_KEY_LABELS[slug as StyleKey],
    // 이슈 022 RED 14 와 정합 — `styles` 가 아니라 `stylePrimary` 기준이다.
    // 복수 스타일을 다 세면 한 칵테일이 카테고리 여럿에 나오고, 대표가 무엇인지 흐려진다.
    matches: (c: (typeof COCKTAILS)[number], slug: string) => c.stylePrimary === slug,
  },
  method: {
    ko: "메이킹",
    en: "METHOD",
    labelOf: (slug: string) => TECHNIQUES[slug as Technique]?.ko,
    matches: (c: (typeof COCKTAILS)[number], slug: string) => c.method === slug,
  },
} as const satisfies Record<CategoryAxis, unknown>;

export interface CategoryView {
  slug: string;
  labelKo: string;
  intro: string | null;
  items: { slug: string; nameKo: string; nameEn: string; abv: number | null; summary: string }[];
}

/** 축의 슬러그 목록. `generateStaticParams` 가 쓴다. */
export async function categorySlugs(axis: CategoryAxis): Promise<string[]> {
  if (usingApi) {
    const all = await categories();
    // API 가 빈 카테고리를 이미 뺐다 (이슈 022 RED 10).
    if (all) return all[axis].map((c) => c.slug);
  }

  // 폴백: 코퍼스에 실제로 있는 값만. 빈 페이지를 만들지 않는 규칙은 같다 (RED 31).
  const present = new Set(COCKTAILS.map((c) => axisValue(axis, c)));
  return [...present];
}

export async function categoryView(
  axis: CategoryAxis,
  slug: string,
): Promise<CategoryView | null> {
  const labelKo = AXIS[axis].labelOf(slug);
  if (!labelKo) return null; // ADR-0002 확정값 밖이다 (RED 5·6)

  if (usingApi) {
    const all = await categories();
    const found = all?.[axis].find((c) => c.slug === slug);
    if (!found) return null;

    const items = await cocktailsByAxis(axis, slug);
    return {
      slug,
      labelKo: found.labelKo,
      intro: found.intro ?? null,
      items: items.map((i) => ({
        slug: i.slug,
        nameKo: i.nameKo,
        nameEn: i.nameEn,
        abv: i.abv ?? null,
        summary: i.summary,
      })),
    };
  }

  const matched = COCKTAILS.filter((c) => AXIS[axis].matches(c, slug));
  if (matched.length === 0) return null; // RED 31 — 빈 카테고리는 만들지 않는다

  return {
    slug,
    labelKo,
    // 소개 문구는 `category_intro` 에서 온다 (이슈 022). 프로토타입에는 없다.
    intro: null,
    items: matched.map((c) => ({
      slug: c.id,
      nameKo: c.ko,
      nameEn: c.en,
      abv: c.abv,
      summary: c.summary,
    })),
  };
}

export function categoryMetadata(axis: CategoryAxis, view: CategoryView): Metadata {
  const { ko, en } = AXIS[axis];

  return {
    title: `${view.labelKo} ${en}`,
    // `FR-COCKTAIL-031` — 소개 문구가 있으면 그것이 설명이다. 목록만 있는 페이지는 색인 가치가 없다.
    description: view.intro ?? `${view.labelKo} ${ko} 칵테일 ${view.items.length}종.`,
    // `NFR-S-02` — 카테고리는 색인 대상이다. `noindex` 를 붙이지 않는다.
    alternates: { canonical: `/cocktails/${axis}/${view.slug}` },
  };
}

export function CategoryPage({ axis, view }: { axis: CategoryAxis; view: CategoryView }) {
  const { ko, en } = AXIS[axis];

  return (
    <main className="shell category-page">
      <Link href="/" className="btn btn-ghost" style={{ fontSize: 11, paddingLeft: 0 }}>
        ← 탐색으로 BACK TO SEARCH
      </Link>

      <h1>
        {view.labelKo} <span className="en">{en}</span>
      </h1>

      {/* `FR-COCKTAIL-031` · `NFR-S-07` — 카테고리마다 고유 문구.
          없어도 페이지는 나온다 (이슈 022 RED 17 과 같은 판단) — P1 이라 발행을 막지 않는다. */}
      {view.intro && <p className="summary category-page__intro">{view.intro}</p>}

      <p className="category-page__count">
        {ko} {view.labelKo} · {view.items.length}종
      </p>

      <div className="result-grid">
        {view.items.map((item) => (
          <Link key={item.slug} href={`/cocktails/${item.slug}`} className="card">
            <h3>{item.nameKo}</h3>
            <div className="en">{item.nameEn}</div>
            <p className="summary">{item.summary}</p>
            {item.abv != null && <div className="meta">{item.abv}%</div>}
          </Link>
        ))}
      </div>
    </main>
  );
}

/** 라우트 셋이 같은 것을 쓰게 한다. 세 곳에 적으면 한 곳만 고치는 일이 생긴다. */
export { notFound, CATEGORY_AXES };

function axisValue(axis: CategoryAxis, c: (typeof COCKTAILS)[number]): string {
  switch (axis) {
    case "base":
      return c.base;
    case "style":
      return c.stylePrimary;
    case "method":
      return c.method;
  }
}

/**
 * 카테고리가 **아닌** 축. 여기 있는 것들로 경로를 만들지 않는다 (`PRIN-P06`).
 *
 * 당도·도수·향맛은 필터다 — 카테고리로 올리면 `/cocktails/sweet/high-abv/gin/` 같은
 * 조합이 생기고 중복 콘텐츠로 색인 페널티를 받는다.
 *
 * 값을 여기 적어 두는 이유: **테스트가 이 목록으로 디렉터리 부재를 확인한다** (RED 4).
 */
export const FILTER_ONLY_AXES = ["sweet", "abv", "flavor"] as const;

/** 필터 축의 한국어 이름. 화면이 칩으로 보여 줄 때 쓴다 — 경로로는 쓰지 않는다. */
export const FILTER_AXIS_LABELS = {
  sweet: (slug: string) => SWEETNESS[slug as SweetLevel]?.[0],
  flavor: (slug: string) => FLAVOR_KEY_LABELS[slug as FlavorKey],
} as const;
