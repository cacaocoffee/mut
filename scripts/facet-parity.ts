/**
 * 클라이언트 필터 · 패싯을 **서버 API 와 대조한다** (ISSUE-040 RED 19·20 · INDEX 결합점 019←040).
 *
 * ## 왜 필요한가
 *
 * SPEC-05 §4·§5 는 Phase 1 에서 **필터도 패싯도 클라이언트가 계산**하게 두고, 서버에도
 * 같은 계약(`GET /cocktails`·`GET /cocktails/facets`)을 두게 했다. 계산이 두 벌이면
 * 언젠가 갈라지고, 갈라진 순간 화면의 숫자가 거짓말을 한다 — `FR-SEARCH-002` 가 막으려는
 * 바로 그 상황이다. 두 구현에 **같은 쿼리스트링**을 주고 결과를 맞춰 본다.
 *
 * ## 어떻게 도는가
 *
 * ```
 * KC_API_URL=http://localhost:8080 npm run facet:parity
 * ```
 *
 * 주소가 없으면 **아무것도 확인하지 않았다고 말하고 종료 코드 0** 이다. API 를 띄우지 않은
 * 사람의 로컬 검증을 막지 않으려는 것이고(`lib/api.ts` 의 폴백과 같은 이유), CI 에서는
 * `--require-api` 로 그 관용을 끈다.
 */
import {
  facetCounts,
  filterCocktails,
  parseFilterQuery,
  type FacetCounts,
  type Filters,
  type SearchItem,
} from "@kca/domain";
import type { components } from "@kca/domain/generated/api";

type CocktailListItem = components["schemas"]["CocktailListItem"];

const BASE = (process.env.KC_API_URL ?? "").replace(/\/$/, "");
const REQUIRE_API = process.argv.includes("--require-api");

if (!BASE) {
  const message = "KC_API_URL 이 없다 — 서버 대조를 하지 않았다";
  if (REQUIRE_API) {
    console.error(`✗ ${message}. CI 는 API 없이 통과시키지 않는다.`);
    process.exit(1);
  }
  console.log(`· ${message}. 확인하려면 KC_API_URL 을 주고 다시 돌린다.`);
  process.exit(0);
}

async function getJson<T>(path: string): Promise<T> {
  const res = await fetch(`${BASE}${path}`);
  if (!res.ok) throw new Error(`GET ${path} → HTTP ${res.status} ${await res.text()}`);
  return (await res.json()) as T;
}

function toSearchItem(item: CocktailListItem): SearchItem {
  return {
    slug: item.slug,
    nameKo: item.nameKo,
    nameEn: item.nameEn,
    summary: item.summary,
    base: item.baseSpirit,
    styles: item.styles,
    method: item.method,
    sweet: item.sweetness,
    flavors: item.aromaTags,
    abv: item.abv ?? null,
    glass: item.glassType,
  };
}

/**
 * 대조할 필터 조합.
 *
 * 코퍼스에서 뽑아 만든다 — 값을 손으로 적으면 시드가 바뀔 때마다 400 이 나고,
 * 그 400 을 "대조 실패" 로 읽게 된다. 여섯 축과 OR·AND 를 모두 한 번씩 밟는다.
 */
function sampleQueries(corpus: SearchItem[]): string[] {
  const distinct = <T>(values: T[]) => [...new Set(values)];
  const bases = distinct(corpus.map((c) => c.base));
  const styles = distinct(corpus.flatMap((c) => c.styles));
  const methods = distinct(corpus.map((c) => c.method));
  const flavors = distinct(corpus.flatMap((c) => c.flavors));
  const name = corpus[0]?.nameKo.slice(0, 2) ?? "";

  return [
    "",
    `base=${bases[0]}`,
    `base=${bases.slice(0, 3).join(",")}`, // 같은 축 OR
    `style=${styles[0]}`,
    `style=${styles.slice(0, 2).join(",")}`,
    `method=${methods[0]}`,
    `sweet=dry`,
    `sweet=sweet`,
    `abv=low`,
    `abv=low,mid,high`,
    `flavor=${flavors[0]}`,
    `flavor=${flavors.slice(0, 2).join(",")}`, // 유일한 AND 축
    `flavor=${flavors.slice(0, 3).join(",")}`, // 대개 0건 — 그 0 이 서버와 같아야 한다
    `base=${bases[0]}&flavor=${flavors[0]}`, // 축 간 AND
    `base=${bases[0]}&style=${styles[0]}&abv=mid&sweet=dry`,
    `q=${encodeURIComponent(name)}`,
  ];
}

/** 다른 값만 줄로 뽑는다. 맵 두 개를 통째로 찍으면 어디가 다른지 사람이 못 찾는다. */
function diffAxis(axisName: string, mine: Record<string, number>, theirs: Record<string, number>) {
  const keys = [...new Set([...Object.keys(mine), ...Object.keys(theirs)])];
  return keys
    .filter((k) => mine[k] !== theirs[k])
    .map((k) => `      ${axisName}.${k}: 클라이언트 ${mine[k] ?? "없음"} ≠ 서버 ${theirs[k] ?? "없음"}`);
}

async function main() {
  const corpus = (
    await getJson<{ items: CocktailListItem[] }>("/api/v1/cocktails?size=1000")
  ).items.map(toSearchItem);

  if (corpus.length === 0) {
    console.error("✗ 코퍼스가 비었다 — 대조가 아무것도 지키지 않는다. 시드를 확인한다.");
    process.exit(1);
  }

  const queries = sampleQueries(corpus);
  let failed = 0;

  for (const query of queries) {
    const filters: Filters = parseFilterQuery(new URLSearchParams(query));
    const problems: string[] = [];

    // ── 패싯 (RED 19) ────────────────────────────────────────────────────
    const mine = facetCounts(corpus, filters);
    const theirs = await getJson<FacetCounts>(`/api/v1/cocktails/facets?${query}`);

    for (const axis of Object.keys(mine) as (keyof FacetCounts)[]) {
      problems.push(...diffAxis(axis, mine[axis], theirs[axis]));
    }

    // ── 목록 (RED 11) ────────────────────────────────────────────────────
    // 카운트만 맞고 **어떤 항목인지** 갈리는 경우가 있다. 슬러그 집합까지 본다.
    const mineList = filterCocktails(corpus, filters).map((c) => c.slug);
    const theirsList = (
      await getJson<{ items: CocktailListItem[] }>(`/api/v1/cocktails?size=1000&${query}`)
    ).items.map((c) => c.slug);

    const onlyMine = mineList.filter((s) => !theirsList.includes(s));
    const onlyTheirs = theirsList.filter((s) => !mineList.includes(s));
    if (onlyMine.length) problems.push(`      목록: 클라이언트에만 ${onlyMine.join(", ")}`);
    if (onlyTheirs.length) problems.push(`      목록: 서버에만 ${onlyTheirs.join(", ")}`);

    const label = query || "(필터 없음)";
    if (problems.length) {
      failed += 1;
      console.error(`  ✗ ?${label}`);
      problems.forEach((p) => console.error(p));
    } else {
      console.log(`  ✓ ?${label}`);
    }
  }

  if (failed > 0) {
    console.error(
      `\n✗ ${failed}건이 갈렸다. 두 계산이 어긋나면 화면의 개수가 거짓말을 한다 —` +
        " `packages/domain/src/search.ts` 와 `apps/api` 의 `FacetSql`·`CocktailListSql` 을 함께 본다.",
    );
    process.exit(1);
  }

  console.log(
    `\n✓ 칵테일 ${corpus.length}종 · ${queries.length}개 조합에서 클라이언트와 서버가 같다.`,
  );
}

main().catch((e) => {
  console.error(`✗ 대조를 마치지 못했다: ${e instanceof Error ? e.message : String(e)}`);
  process.exit(1);
});
