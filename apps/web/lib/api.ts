import { PROTOTYPE_CORPUS, type SearchItem } from "@mut/domain";
import type { components } from "@mut/domain/generated/api";

/**
 * 공개 API 클라이언트 (ISSUE-038 · SPEC-07 §5).
 *
 * ## 빌드와 브라우저가 같은 엔드포인트를 쓴다
 *
 * SPEC-07 §5 — "별도의 내부 전용 조회 API를 두지 않는다. **두 벌이 되면 반드시 어긋난다.**"
 * SSG 빌드가 부르는 주소와 브라우저가 부르는 주소가 같다.
 *
 * ## 주소가 없으면 프로토타입 데이터로 빌드한다
 *
 * `MUT_API_URL` 이 비어 있으면 API 를 부르지 않고 `packages/domain` 의 배열을 쓴다.
 * 이 폴백이 없으면 **API 를 띄우지 않은 사람은 `npm run build` 를 못 한다** —
 * 화면 작업과 서버 작업이 서로를 막게 된다.
 *
 * 폴백은 **빌드 부트스트랩**이지 런타임 경로가 아니다. 주소를 넣으면 그 순간부터 API 만 쓴다.
 */
export type CocktailDetail = components["schemas"]["CocktailDetail"];
export type CocktailListItem = components["schemas"]["CocktailListItem"];
export type RelatedItem = components["schemas"]["RelatedItem"];
export type CategoryItem = components["schemas"]["CategoryItem"];
export type CategoriesResponse = components["schemas"]["CategoriesResponse"];

/** 카테고리 축 3종. **여기 없는 축은 카테고리가 아니다** (`PRIN-P06`). */
export const CATEGORY_AXES = ["base", "style", "method"] as const;
export type CategoryAxis = (typeof CATEGORY_AXES)[number];

const BASE = process.env.MUT_API_URL?.replace(/\/$/, "") ?? "";

export const usingApi = BASE.length > 0;

/**
 * 발행분 슬러그 전부.
 *
 * `generateStaticParams` 가 쓴다. 실패하면 **빌드를 멈추지 않고 빈 목록**을 준다 —
 * 여기서 예외를 던지면 API 가 잠깐 흔들릴 때 배포가 통째로 막힌다 (`NFR-R-01` 의 정신).
 */
export async function publishedSlugs(): Promise<string[]> {
  if (!usingApi) return [];

  try {
    const res = await fetch(`${BASE}/api/v1/cocktails?size=1000`, {
      // 빌드 시점 조회다. 캐시하면 재빌드가 낡은 목록을 본다.
      cache: "no-store",
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);

    const body = (await res.json()) as { items: CocktailListItem[] };
    return body.items.map((item) => item.slug);
  } catch (e) {
    console.warn(`[api] 목록 조회 실패 — 프로토타입 데이터로 빌드한다: ${describe(e)}`);
    return [];
  }
}

/**
 * 탐색 화면이 거를 코퍼스 전체 (ISSUE-040 · SPEC-05 §4).
 *
 * ## 한 번만 받는다
 *
 * "Phase 1 규모에서는 전체 목록을 받아 클라이언트에서 거르는 편이 왕복 없이 즉각적이다."
 * **필터마다 다시 부르지 않는다.** 페이지가 한 번 받아 화면에 넘긴다.
 *
 * `size=1000` 은 발행분 전부를 한 장에 담으려는 값이다. Phase 1 상한(500종)의 두 배라
 * 지금은 페이지가 나뉘지 않는다. **넘어가면 뒷부분이 응답에서 빠진다** — 화면은 그것을
 * 알아채지 못하므로 응답의 전체 개수와 받은 개수를 비교해 로그를 남긴다.
 * 그 로그가 뜨는 때가 SPEC-05 §4 가 말한 "데이터가 커지면 서버 필터로 옮긴다" 는 시점이다.
 *
 * 실패하면 프로토타입 코퍼스다 — 필터 화면이 빈 채로 뜨는 것보다 낫다.
 */
export async function searchCorpus(): Promise<SearchItem[]> {
  if (!usingApi) return PROTOTYPE_CORPUS;

  try {
    const res = await fetch(`${BASE}/api/v1/cocktails?size=1000`, {
      next: { revalidate: 600 },
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);

    const body = (await res.json()) as {
      items: CocktailListItem[];
      page: { totalElements: number };
    };
    if (body.page.totalElements > body.items.length) {
      console.warn(
        `[api] 발행분 ${body.page.totalElements}종 중 ${body.items.length}종만 받았다 —` +
          " 탐색 화면이 나머지를 걸러 내지 못한다. 서버 필터로 옮길 시점이다 (SPEC-05 §4).",
      );
    }
    return body.items.map(toSearchItem);
  } catch (e) {
    console.warn(`[api] 코퍼스 조회 실패 — 프로토타입으로 거른다: ${describe(e)}`);
    return PROTOTYPE_CORPUS;
  }
}

/**
 * 목록 한 줄 → 필터가 보는 한 줄.
 *
 * 이름만 바꾼다. 여기서 값을 손보기 시작하면 클라이언트 필터와 서버 필터가 갈라지고,
 * 그것이 이슈 040 의 대조 테스트가 잡으려는 상황이다.
 */
export function toSearchItem(item: CocktailListItem): SearchItem {
  return {
    slug: item.slug,
    nameKo: item.nameKo,
    nameEn: item.nameEn,
    summary: item.summary,
    base: item.baseSpirit,
    // `stylePrimary` 가 아니다 — 필터는 보유 스타일 전체와 맞춘다 (DECISIONS §1.11)
    styles: item.styles,
    method: item.method,
    sweet: item.sweetness,
    flavors: item.aromaTags,
    abv: item.abv ?? null,
    glass: item.glassType,
  };
}

/**
 * 상세.
 *
 * `null` 이면 없는 것으로 친다 — 화면이 404 를 낸다. `draft` 도 여기서 404 다
 * (이슈 020 RED 3) — 공개 API 가 발행분만 준다.
 */
export async function cocktailDetail(slug: string): Promise<CocktailDetail | null> {
  if (!usingApi) return null;

  try {
    const res = await fetch(`${BASE}/api/v1/cocktails/${encodeURIComponent(slug)}`, {
      // ISR 폴백. 주 경로는 발행 시 on-demand 재생성이다 (SPEC-05 §4).
      next: { revalidate: 3600 },
    });
    if (res.status === 404) return null;
    if (!res.ok) throw new Error(`HTTP ${res.status}`);

    return (await res.json()) as CocktailDetail;
  } catch (e) {
    console.warn(`[api] 상세 조회 실패 (${slug}) — 프로토타입 데이터로 그린다: ${describe(e)}`);
    return null;
  }
}

/** 예외 메시지만 남긴다. 스택은 빌드 로그를 덮고, 원인은 대개 첫 줄에 있다. */
function describe(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

/**
 * 배리에이션 (`FR-COCKTAIL-024` · 이슈 021).
 *
 * 상세 화면의 일부라 여기서 부른다. 실패하면 **빈 목록**이다 —
 * 추천이 없다고 레시피를 못 읽을 이유가 없다.
 */
export async function relatedCocktails(slug: string): Promise<RelatedItem[]> {
  if (!usingApi) return [];

  try {
    const res = await fetch(`${BASE}/api/v1/cocktails/${encodeURIComponent(slug)}/related`, {
      next: { revalidate: 3600 },
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);

    const body = (await res.json()) as { items: RelatedItem[] };
    return body.items;
  } catch (e) {
    console.warn(`[api] 배리에이션 조회 실패 (${slug}) — 비운다: ${describe(e)}`);
    return [];
  }
}

/**
 * 3축 카테고리 (`FR-COCKTAIL-029` · 이슈 022).
 *
 * 항목이 0건인 카테고리는 API 가 이미 뺀다 — 목록만 있고 아무것도 없는 페이지는
 * 색인 가치가 없다 (이슈 022 RED 10).
 */
export async function categories(): Promise<CategoriesResponse | null> {
  if (!usingApi) return null;

  try {
    const res = await fetch(`${BASE}/api/v1/categories`, { next: { revalidate: 3600 } });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);

    return (await res.json()) as CategoriesResponse;
  } catch (e) {
    console.warn(`[api] 카테고리 조회 실패 — 프로토타입으로 빌드한다: ${describe(e)}`);
    return null;
  }
}

/**
 * 한 축의 발행분 목록.
 *
 * **축 하나만 넘긴다.** 둘을 넘기는 자리를 만들지 않는 것이 `R-C-2` 의 구현이다 —
 * 조합을 만들 수 있는 함수가 있으면 언젠가 조합 경로가 생긴다.
 */
export async function cocktailsByAxis(
  axis: CategoryAxis,
  slug: string,
): Promise<CocktailListItem[]> {
  if (!usingApi) return [];

  try {
    const res = await fetch(
      `${BASE}/api/v1/cocktails?${axis}=${encodeURIComponent(slug)}&size=100`,
      { next: { revalidate: 3600 } },
    );
    if (!res.ok) throw new Error(`HTTP ${res.status}`);

    const body = (await res.json()) as { items: CocktailListItem[] };
    return body.items;
  } catch (e) {
    console.warn(`[api] ${axis}=${slug} 목록 조회 실패 — 비운다: ${describe(e)}`);
    return [];
  }
}
