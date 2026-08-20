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

/** 가진 것이 하나라도 들어가는데 아직 멀은 것. */
export interface Partial {
  slug: string;
  /** 이미 채운 묶음 수 */
  matched: number;
  /** 아직 빈 묶음들. 여기 같은 재료가 자주 나오면 그것이 살 것이다 */
  missing: Requirement[];
}

/**
 * **가진 재료가 들어가는 칵테일** (`R-F2.2` 의 연장).
 *
 * ## 왜 따로 있나
 *
 * [makeable] 은 다 갖춘 것, [oneAway] 는 하나 남은 것이다. 캄파리 · 진 · 카시스만 있는
 * 사람에게는 둘 다 거의 비어 있고, 그러면 **뭘 사야 하는지 알 방법이 없다.**
 * 두 칸 이상 빈 것까지 펼쳐 두면 빠진 재료가 겹치는 것이 눈에 들어온다 — 여러 줄에
 * 스위트 베르무트가 반복되면 그것이 살 것이다.
 *
 * ## 앞의 두 묶음과 겹치지 않는다
 *
 * 빈 묶음이 **둘 이상**인 것만 준다. 0개는 [makeable], 1개는 [oneAway] 가 이미 말했고,
 * 같은 칵테일을 세 번 보여 주면 목록이 길어질 뿐 새로 아는 것이 없다.
 *
 * ## 가까운 것부터
 *
 * 빈 칸이 적은 순이다. 위쪽이 곧 "조금만 더 사면 되는 것" 이라 목록 자체가 답이 된다.
 * 같은 수면 이미 채운 것이 많은 쪽을 앞에 둔다 — 내 술장을 더 쓰는 잔이다.
 */
export function usingAny(have: ReadonlySet<string>): Partial[] {
  if (have.size === 0) return [];

  const out: Partial[] = [];
  for (const entry of STOCK_INDEX) {
    if (entry.needs.length === 0) continue;

    const missing = entry.needs.filter((r) => !met(r, have));
    const matched = entry.needs.length - missing.length;
    if (matched > 0 && missing.length >= 2) out.push({ slug: entry.slug, matched, missing });
  }

  return out.sort((a, b) => a.missing.length - b.missing.length || b.matched - a.matched);
}


/** 하나 사면 뭐가 열리나. */
export interface Buy {
  slug: string;
  /** 이것만 더 있으면 **바로 만들 수 있게 되는** 잔 수 */
  unlocks: number;
  /** 이것을 기다리는 잔 수 — 아직 멀어도 이 재료가 막고 있는 것 */
  blocks: number;
}

/**
 * **다음에 살 것** (`R-F2.2-2` 의 뒤집은 물음).
 *
 * 「하나만 더」와 「들어가는 것」은 *칵테일* 을 세로로 늘어놓는다. 여기는 그것을 **재료로**
 * 뒤집어 센다 — 캄파리 · 진 · 카시스를 담은 사람이 스무 줄을 눈으로 세어 "스위트 베르무트가
 * 자꾸 나오네" 를 알아내야 한다면, 그 계산은 화면이 해야 한다.
 *
 * `unlocks` 가 먼저다. 사자마자 만들 수 있게 되는 것이 지금 가장 확실한 값이고,
 * 같으면 `blocks` — 멀더라도 더 많은 잔이 기다리는 쪽을 앞에 둔다.
 *
 * 이미 가진 것은 셈에서 빠진다. 가니시는 애초에 `STOCK_INDEX` 에 없다 (`R-F2.2-5`).
 */
export function bestBuys(have: ReadonlySet<string>, limit = 5): Buy[] {
  if (have.size === 0) return [];

  const unlocks = new Map<string, number>();
  const blocks = new Map<string, number>();

  for (const entry of STOCK_INDEX) {
    if (entry.needs.length === 0) continue;

    const missing = entry.needs.filter((r) => !met(r, have));
    // 아무것도 안 겹치는 잔은 세지 않는다 — 내 술장과 무관한 재료까지 추천하게 된다
    if (missing.length === 0 || missing.length === entry.needs.length) continue;

    for (const req of missing) {
      for (const slug of req) {
        if (have.has(slug)) continue;
        blocks.set(slug, (blocks.get(slug) ?? 0) + 1);
        // 빈 묶음이 이 하나뿐이면 이걸 사는 순간 만들 수 있게 된다
        if (missing.length === 1) unlocks.set(slug, (unlocks.get(slug) ?? 0) + 1);
      }
    }
  }

  return [...blocks.keys()]
    .map((slug) => ({ slug, unlocks: unlocks.get(slug) ?? 0, blocks: blocks.get(slug) ?? 0 }))
    .sort((a, b) => b.unlocks - a.unlocks || b.blocks - a.blocks)
    .slice(0, limit);
}
