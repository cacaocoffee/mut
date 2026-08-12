"use client";

import { useCallback, useMemo } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import {
  ABV_BANDS,
  ABV_BAND_KEYS,
  BASES_IN_CORPUS,
  DEFAULT_FILTERS,
  FLAVORS_IN_CORPUS,
  FLAVOR_LABELS,
  STYLES_IN_CORPUS,
  STYLE_LABELS,
  SWEETNESS,
  facetCounts,
  filterCocktails,
  type AbvBand,
  type BaseSpirit,
  type Filters,
  type FlavorKey,
  type StyleKey,
} from "@kca/domain";
import { CocktailCard } from "./cocktail-card";

/** 필터는 URL 쿼리스트링에만 산다 — 공유 가능하되 색인 대상은 아니다 (PRD R-F2.1-1). */
function readFilters(params: URLSearchParams): Filters {
  const sweetRaw = params.get("sweet");
  const sweet = sweetRaw === null ? -1 : Number(sweetRaw);

  return {
    sweet: Number.isInteger(sweet) && sweet >= 0 && sweet <= 3 ? sweet : -1,
    bases: (params.get("base")?.split(",").filter(Boolean) ?? []).filter((b): b is BaseSpirit =>
      BASES_IN_CORPUS.includes(b as BaseSpirit)
    ),
    styles: (params.get("style")?.split(",").filter(Boolean) ?? []).filter((s): s is StyleKey =>
      STYLES_IN_CORPUS.includes(s as StyleKey)
    ),
    flavors: (params.get("flavor")?.split(",").filter(Boolean) ?? []).filter(
      (f): f is FlavorKey => FLAVORS_IN_CORPUS.includes(f as FlavorKey)
    ),
    abvBands: (params.get("abv")?.split(",").filter(Boolean) ?? []).filter((b): b is AbvBand =>
      ABV_BAND_KEYS.includes(b as AbvBand)
    ),
    query: params.get("q") ?? "",
  };
}

function toParams(f: Filters): string {
  const p = new URLSearchParams();
  if (f.sweet >= 0) p.set("sweet", String(f.sweet));
  if (f.bases.length) p.set("base", f.bases.join(","));
  if (f.styles.length) p.set("style", f.styles.join(","));
  if (f.flavors.length) p.set("flavor", f.flavors.join(","));
  if (f.abvBands.length) p.set("abv", f.abvBands.join(","));
  if (f.query.trim()) p.set("q", f.query);
  const s = p.toString();
  return s ? `/?${s}` : "/";
}

export function SearchScreen() {
  const router = useRouter();
  const searchParams = useSearchParams();

  const filters = useMemo(
    () => readFilters(new URLSearchParams(searchParams.toString())),
    [searchParams]
  );

  const apply = useCallback(
    (patch: Partial<Filters>) => {
      router.replace(toParams({ ...filters, ...patch }), { scroll: false });
    },
    [filters, router]
  );

  const results = useMemo(() => filterCocktails(filters), [filters]);
  const counts = useMemo(() => facetCounts(filters), [filters]);

  const summary = [
    filters.sweet < 0 ? "당도 전체" : SWEETNESS[filters.sweet][1],
    filters.bases.length ? filters.bases.join("/") : "기주 전체",
    filters.styles.length
      ? filters.styles.map((s) => STYLE_LABELS[s]).join("/")
      : "스타일 전체",
    filters.flavors.length ? `${filters.flavors.length}개 향` : "향 전체",
    filters.abvBands.length
      ? filters.abvBands.map((b) => ABV_BANDS.find((x) => x.key === b)!.ko).join("/")
      : "도수 전체",
  ].join(" · ");

  const toggleBase = (b: BaseSpirit) =>
    apply({
      bases: filters.bases.includes(b)
        ? filters.bases.filter((x) => x !== b)
        : [...filters.bases, b],
    });

  const toggleStyle = (s: StyleKey) =>
    apply({
      styles: filters.styles.includes(s)
        ? filters.styles.filter((x) => x !== s)
        : [...filters.styles, s],
    });

  const toggleFlavor = (f: FlavorKey) =>
    apply({
      flavors: filters.flavors.includes(f)
        ? filters.flavors.filter((x) => x !== f)
        : [...filters.flavors, f],
    });

  const toggleAbv = (b: AbvBand) =>
    apply({
      abvBands: filters.abvBands.includes(b)
        ? filters.abvBands.filter((x) => x !== b)
        : [...filters.abvBands, b],
    });

  return (
    <main className="shell">
      <header className="page-head">
        <div>
          <h1>
            칵테일 탐색<span className="sub">Browse {results.length} of 24 entries</span>
          </h1>
        </div>
        <p className="lede">
          당도 · 기주 · 맛/향 · 도수 4개 축으로 교차 검색합니다. 모든 수치는 표준 레시피 기준
          실측값입니다.
        </p>
      </header>

      <div className="search-layout">
        <aside className="filter-panel">
          <div className="rule-head">
            <h6 style={{ margin: 0 }}>필터 FILTERS</h6>
            <button
              type="button"
              className="btn btn-ghost"
              style={{ fontSize: 11 }}
              onClick={() => apply(DEFAULT_FILTERS)}
            >
              초기화 RESET
            </button>
          </div>

          <div className="filter-group">
            <div className="filter-label">당도 SWEETNESS</div>
            <div className="seg seg-stack">
              <label className="seg-opt">
                <input
                  type="radio"
                  name="sweetlvl"
                  checked={filters.sweet < 0}
                  onChange={() => apply({ sweet: -1 })}
                />
                <span className="ko">전체</span>
                <span className="en">All · {counts.sweetAll}</span>
              </label>
              {SWEETNESS.map(([ko, en], i) => (
                <label className="seg-opt" key={ko} title={en}>
                  <input
                    type="radio"
                    name="sweetlvl"
                    checked={filters.sweet === i}
                    onChange={() => apply({ sweet: i })}
                  />
                  <span className="ko">{ko}</span>
                  <span className="en">
                    {en} · {counts.sweet[i]}
                  </span>
                </label>
              ))}
            </div>
          </div>

          <div className="filter-group">
            <div className="filter-label">
              기주 BASE SPIRIT<span className="hint">복수 선택</span>
            </div>
            <div className="chip-row">
              {BASES_IN_CORPUS.map((b) => {
                const n = counts.bases[b];
                const on = filters.bases.includes(b);
                return (
                  <button
                    type="button"
                    key={b}
                    className="btn chip"
                    aria-pressed={on}
                    disabled={n === 0 && !on}
                    onClick={() => toggleBase(b)}
                  >
                    {b}
                    <span className="count">{n}</span>
                  </button>
                );
              })}
            </div>
          </div>

          <div className="filter-group">
            <div className="filter-label">
              스타일 STYLE<span className="hint">복수 선택</span>
            </div>
            <div className="chip-row">
              {STYLES_IN_CORPUS.map((s) => {
                const n = counts.styles[s];
                const on = filters.styles.includes(s);
                return (
                  <button
                    type="button"
                    key={s}
                    className="btn chip"
                    aria-pressed={on}
                    disabled={n === 0 && !on}
                    onClick={() => toggleStyle(s)}
                  >
                    {STYLE_LABELS[s]}
                    <span className="count">{n}</span>
                  </button>
                );
              })}
            </div>
          </div>

          <div className="filter-group">
            <div className="filter-label">맛 / 향 FLAVOR PROFILE</div>
            <div className="chip-row">
              {FLAVORS_IN_CORPUS.map((k) => {
                const n = counts.flavors[k];
                const on = filters.flavors.includes(k);
                return (
                  <button
                    type="button"
                    key={k}
                    className="chip-tag"
                    aria-pressed={on}
                    disabled={n === 0 && !on}
                    onClick={() => toggleFlavor(k)}
                  >
                    {FLAVOR_LABELS[k]}
                  </button>
                );
              })}
            </div>
          </div>

          <div className="filter-group">
            <div className="filter-label">
              도수 ABV<span className="hint">복수 선택</span>
            </div>
            <div className="chip-row">
              {ABV_BANDS.map((band) => {
                const n = counts.abvBands[band.key];
                const on = filters.abvBands.includes(band.key);
                return (
                  <button
                    type="button"
                    key={band.key}
                    className="btn chip"
                    aria-pressed={on}
                    disabled={n === 0 && !on}
                    onClick={() => toggleAbv(band.key)}
                  >
                    {band.ko}
                    <span className="count">{n}</span>
                  </button>
                );
              })}
            </div>
          </div>

          <div className="filter-group" style={{ borderBottom: 0 }}>
            <div className="filter-label">검색 KEYWORD</div>
            <input
              className="input"
              type="search"
              placeholder="네그로니 / Negroni"
              value={filters.query}
              aria-label="칵테일 이름 검색"
              onChange={(e) => apply({ query: e.target.value })}
            />
          </div>
        </aside>

        <section>
          <div className="results-head">
            <div className="results-count">
              <b>{results.length}</b>
              <span>개 결과 · {summary}</span>
            </div>
            <span style={{ fontSize: 11, color: "var(--color-neutral-700)" }}>
              정렬: 도수 낮은 순
            </span>
          </div>

          {results.length > 0 ? (
            <div className="card-grid">
              {results.map((c) => (
                <CocktailCard key={c.id} cocktail={c} />
              ))}
            </div>
          ) : (
            <div className="empty-state">
              <h3>조건에 맞는 항목이 없습니다</h3>
              <p>도수 구간을 넓히거나 맛/향 태그를 줄여보세요.</p>
            </div>
          )}
        </section>
      </div>
    </main>
  );
}
