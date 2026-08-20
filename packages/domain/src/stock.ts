/**
 * 내 술장 역검색 (`R-F2.2-1`·`2`·`5`).
 *
 * ## 코퍼스에 재료를 얹지 않는다
 *
 * 탐색·파인더가 쓰는 [SearchItem] 은 "필터와 카드가 쓰는 것만" 든다 (`search.ts`).
 * 상세 필드를 얹으면 500종 응답이 그만큼 커지고, 그것이 DECISIONS §3 이 실측 후로 미룬
 * 항목이다. 역검색이 필요한 것은 **슬러그 → 요구 재료** 뿐이라 따로 만든다.
 *
 * ## 가니시를 빼고 만든다
 *
 * `R-F2.2-5` — "가니시와 얼음, 물은 보유 재료 판정에서 제외한다. **이걸 요구하면 매칭이
 * 거의 안 된다.**" 실제로 레몬 필 · 오렌지 필 · 민트가 데이터 상위권이라, 빼지 않으면
 * 오렌지 껍질이 없어서 네그로니를 못 만든다는 답이 나온다.
 *
 * 빼는 판단은 [countsForStock] 한 곳에서만 한다 — 화면이 다시 거르지 않는다.
 */
import { COCKTAILS } from "./data";
import { countsForStock, resolveIngredient, type Requirement } from "./ingredients";

/** 칵테일 한 종이 요구하는 것. 바깥은 AND, 안쪽(`Requirement`)은 OR 다. */
export interface StockEntry {
  slug: string;
  needs: Requirement[];
}

/**
 * 칵테일 → 요구 재료.
 *
 * 가니시만으로 이뤄진 줄은 빠지므로 `needs` 가 빌 수 있다. 그런 칵테일은 아무것도
 * 없어도 만들 수 있는 것이 되는데, 코퍼스에는 그런 항목이 없다 (가드가 지킨다).
 */
export const STOCK_INDEX: StockEntry[] = COCKTAILS.map((c) => ({
  slug: c.id,
  needs: c.ingredients
    .map((line) => resolveIngredient(line.ko))
    .filter((req): req is Requirement => req !== null)
    // 택일 줄은 한쪽이라도 세는 재료면 남긴다 — `버번 또는 라이` 는 둘 다 술이다
    .map((req) => req.filter(countsForStock))
    .filter((req) => req.length > 0),
}));

/** 이 묶음을 가진 것으로 채울 수 있나. 안쪽은 OR 라 하나만 있으면 된다. */
function met(req: Requirement, have: ReadonlySet<string>): boolean {
  return req.some((slug) => have.has(slug));
}

/** 지금 만들 수 있는 것 (`R-F2.2-1`). 요구 재료를 **전부** 가진 칵테일이다. */
export function makeable(have: ReadonlySet<string>): string[] {
  return STOCK_INDEX.filter((e) => e.needs.length > 0 && e.needs.every((r) => met(r, have))).map(
    (e) => e.slug
  );
}

/** 딱 하나가 빈다. 무엇이 비는지까지 준다 — 그것을 말해 주는 것이 이 기능의 값이다. */
export interface OneAway {
  slug: string;
  /** 빠진 묶음. 여럿이면 그중 아무거나 하나만 있으면 된다 (`버번 또는 라이`) */
  missing: Requirement;
}

/**
 * 재료 **1개만 더 있으면** 만들 수 있는 것 (`R-F2.2-2`).
 *
 * PRD 가 "체류와 재방문의 핵심 동인" 이라고 적은 항목이다. 만들 수 있는 것만 보여 주면
 * 재료가 적은 사람에게는 빈 화면이고, 그 자리에서 나간다.
 *
 * 아무것도 안 가진 상태에서는 **재료가 하나뿐인 칵테일이 전부 걸린다.** 그것은 추천이
 * 아니라 목록이라, 고른 것이 없으면 빈 배열을 준다.
 */
export function oneAway(have: ReadonlySet<string>): OneAway[] {
  if (have.size === 0) return [];

  const out: OneAway[] = [];
  for (const entry of STOCK_INDEX) {
    if (entry.needs.length === 0) continue;

    const gaps = entry.needs.filter((r) => !met(r, have));
    if (gaps.length === 1) out.push({ slug: entry.slug, missing: gaps[0] });
  }
  return out;
}
