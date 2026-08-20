"use client";

import { useMemo } from "react";
import {
  CATEGORY_LABELS,
  CATEGORY_ORDER,
  STOCKABLE,
  getIngredient,
  bestBuys,
  makeable,
  oneAway,
  usingAny,
  type IngredientCategory,
  type SearchItem,
} from "@mut/domain";
import { useStock } from "@/lib/use-stock";
import { CocktailCard } from "./cocktail-card";
import { IngredientPicker } from "./ingredient-picker";

/** 처음 온 사람에게 권하는 것. 이 여섯이면 코퍼스에서 실제로 몇 잔이 열린다. */
const STARTERS = ["gin", "sweet-vermouth", "campari", "lemon-juice", "lime-juice", "simple-syrup"];

/**
 * 내 술장 (`R-F2.2-1`·`2`·`4`·`5`).
 *
 * ## 가니시는 목록에 없다
 *
 * 판정에서 빼는 것을 고르게 하면 (`R-F2.2-5`) "체크했는데 왜 결과가 그대로냐" 가 된다.
 * 빼는 판단은 도메인의 `countsForStock` 한 곳에서 하고, 화면은 `STOCKABLE` 을 그대로 편다.
 *
 * ## 「1개만 더」가 이 화면의 값이다
 *
 * 만들 수 있는 것만 보여 주면 재료가 적은 사람에게는 빈 화면이고, 그 자리에서 나간다.
 * PRD 가 `R-F2.2-2` 를 "체류와 재방문의 핵심 동인" 이라고 적은 이유다. 무엇이 빠졌는지까지
 * 말해 준다 — 그것을 말해 주지 않으면 목록이 아니라 놀림이다.
 *
 * ## 서버를 부르지 않는다
 *
 * 코퍼스는 페이지가 한 번 받아 넘기고, 계산은 전부 여기서 끝난다 (탐색·파인더와 같다).
 * 체크를 만질 때마다 왕복이 있으면 스무 개를 고르는 동안 스무 번을 기다린다.
 */
export function MyBarScreen({ corpus }: { corpus: SearchItem[] }) {
  const { have, ready, toggle, clear } = useStock();

  const bySlug = useMemo(() => new Map(corpus.map((c) => [c.slug, c])), [corpus]);
  const can = useMemo(() => makeable(have).map((s) => bySlug.get(s)).filter(Boolean) as SearchItem[], [have, bySlug]);
  const near = useMemo(
    () =>
      oneAway(have)
        .map((x) => ({ item: bySlug.get(x.slug), missing: x.missing }))
        .filter((x): x is { item: SearchItem; missing: string[] } => Boolean(x.item)),
    [have, bySlug]
  );

  /** 아직 먼 것까지 펼친다 — 빠진 재료가 겹치는 것이 곧 살 것이다. */
  const partial = useMemo(
    () =>
      usingAny(have)
        .map((x) => ({ item: bySlug.get(x.slug), missing: x.missing }))
        .filter((x): x is { item: SearchItem; missing: string[][] } => Boolean(x.item)),
    [have, bySlug]
  );

  /** 그 겹침을 사람이 세지 않게 한다. */
  const buys = useMemo(() => bestBuys(have), [have]);

  const grouped = useMemo(() => {
    const map = new Map<IngredientCategory, typeof STOCKABLE>();
    for (const ing of STOCKABLE) {
      const list = map.get(ing.category) ?? [];
      list.push(ing);
      map.set(ing.category, list);
    }
    return CATEGORY_ORDER.filter((c) => map.has(c)).map((c) => [c, map.get(c)!] as const);
  }, []);

  return (
    <main className="shell" data-ready={ready || undefined}>
      <header className="page-head">
        <div>
          <h1>
            내 술장
            <span className="sub">
              {have.size}개 담음 · {can.length}잔 가능
            </span>
          </h1>
        </div>
      </header>

      <div className="search-layout">
        <aside className="filter-panel">
          <div className="rule-head">
            <h6 style={{ margin: 0 }}>가진 재료 MY SHELF</h6>
            <button
              type="button"
              className="btn btn-ghost"
              style={{ fontSize: 11 }}
              disabled={have.size === 0}
              onClick={clear}
            >
              비움 CLEAR
            </button>
          </div>

          {/* 59종을 훑는 대신 이름으로 찾는다. 아래 카테고리 목록은 그대로 둔다 —
              이름을 모르면 훑어야 하고, 훑다 보면 있는 줄도 몰랐던 것이 눈에 든다 */}
          <IngredientPicker have={have} onToggle={toggle} />

          {/* 가니시가 없는 이유를 적어 둔다 — 없으면 "왜 레몬 필이 없냐" 가 된다 */}
          <p className="stock-note">
            껍질 · 체리 · 민트 같은 가니시는 없어도 만들 수 있으므로 여기서 고르지 않습니다.
          </p>

          {grouped.map(([category, list]) => (
            <div className="filter-group" key={category}>
              <div className="filter-label">{CATEGORY_LABELS[category]}</div>
              <div className="chip-row">
                {list.map((ing) => (
                  <button
                    key={ing.slug}
                    type="button"
                    className="btn chip"
                    aria-pressed={have.has(ing.slug)}
                    onClick={() => toggle(ing.slug)}
                  >
                    {ing.nameKo}
                  </button>
                ))}
              </div>
            </div>
          ))}
        </aside>

        <section>
          {!ready ? null : have.size === 0 ? (
            <Empty onPick={toggle} />
          ) : (
            <>
              <Shelf
                title="지금 만들 수 있는 것"
                en="READY TO MAKE"
                count={can.length}
                empty="아직 없습니다. 아래 목록에서 하나만 더 담아 보세요."
              >
                <div className="card-grid">
                  {can.map((c) => (
                    <CocktailCard key={c.slug} cocktail={c} />
                  ))}
                </div>
              </Shelf>

              <Shelf
                title="재료 하나만 더 있으면"
                en="ONE AWAY"
                count={near.length}
                empty="담은 재료로 한 발짝 안에 드는 것이 없습니다."
              >
                <ul className="one-away">
                  {near.map(({ item, missing }) => (
                    <li key={item.slug} className="one-away__row">
                      <a className="one-away__name" href={`/cocktails/${item.slug}`}>
                        <b>{item.nameKo}</b>
                        <span className="en">{item.nameEn}</span>
                      </a>
                      <span className="one-away__need">
                        {/* 택일이면 둘 중 아무거나다 — `버번 또는 라이` 가 그 줄이다 */}
                        {missing.map((s) => getIngredient(s)?.nameKo ?? s).join(" 또는 ")}
                      </span>
                    </li>
                  ))}
                </ul>
              </Shelf>

              <Shelf
                title="가진 재료가 들어가는 것"
                en="WHAT ELSE USES THESE"
                count={partial.length}
                empty="담은 재료가 들어가는 다른 칵테일이 없습니다."
              >
                {/* 목록을 눈으로 세지 않게, 겹치는 재료를 먼저 말한다 */}
                {buys.length > 0 && (
                  <div className="buy-hint">
                    <span className="buy-hint__label">다음에 살 것</span>
                    <ul className="buy-hint__list">
                      {buys.map((b) => (
                        <li key={b.slug}>
                          <b>{getIngredient(b.slug)?.nameKo ?? b.slug}</b>
                          <span>
                            {b.unlocks > 0 ? `바로 ${b.unlocks}잔` : `${b.blocks}잔이 기다림`}
                          </span>
                        </li>
                      ))}
                    </ul>
                  </div>
                )}

                <ul className="one-away">
                  {partial.map(({ item, missing }) => (
                    <li key={item.slug} className="one-away__row">
                      <a className="one-away__name" href={`/cocktails/${item.slug}`}>
                        <b>{item.nameKo}</b>
                        <span className="en">{item.nameEn}</span>
                      </a>
                      <span className="one-away__need">
                        {missing
                          .map((req) => req.map((s) => getIngredient(s)?.nameKo ?? s).join(" 또는 "))
                          .join(" · ")}
                      </span>
                    </li>
                  ))}
                </ul>
              </Shelf>
            </>
          )}
        </section>
      </div>
    </main>
  );
}

/** 결과 한 묶음. 0건일 때도 자리를 지키고 다음 행동을 말한다. */
function Shelf({
  title,
  en,
  count,
  empty,
  children,
}: {
  title: string;
  en: string;
  count: number;
  empty: string;
  children: React.ReactNode;
}) {
  return (
    <section className="stock-shelf">
      <div className="results-head">
        <div className="results-count">
          <b>{count}</b>
          <span>
            {title} · {en}
          </span>
        </div>
      </div>
      {count > 0 ? children : <p className="stock-shelf__empty">{empty}</p>}
    </section>
  );
}

/**
 * 아무것도 안 담았을 때.
 *
 * 빈 화면 대신 무엇을 하는 곳인지와 **누를 것**을 준다. 자주 쓰는 여섯 개를 권하는 이유는
 * 첫 체크 하나가 결과를 바꾸는 것을 보여 주기 위해서다.
 */
function Empty({ onPick }: { onPick: (slug: string) => void }) {
  return (
    <div className="empty-state">
      <h3>가진 재료를 담아 보세요</h3>
      <p>담은 재료로 만들 수 있는 칵테일과, 하나만 더 있으면 되는 것을 함께 보여 줍니다.</p>
      <div className="chip-row" style={{ justifyContent: "center", marginTop: 18 }}>
        {STARTERS.map((slug) => (
          <button key={slug} type="button" className="btn chip" onClick={() => onPick(slug)}>
            {getIngredient(slug)?.nameKo ?? slug}
          </button>
        ))}
      </div>
    </div>
  );
}
