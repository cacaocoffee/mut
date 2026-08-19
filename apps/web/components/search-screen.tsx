"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import {
  ABV_BANDS,
  BASE_SPIRIT_LABELS,
  DEFAULT_FILTERS,
  FLAVOR_LABELS,
  STYLE_LABELS,
  SWEETNESS,
  SWEET_LEVELS,
  TECHNIQUE_LABELS,
  facetCounts,
  filterCocktails,
  toFilterQuery,
  parseFilterQuery,
  type AbvBand,
  type BaseSpirit,
  type FlavorKey,
  type Filters,
  type SearchItem,
  type StyleKey,
  type Technique,
} from "@mut/domain";
import { SEARCH_PATH } from "@/lib/routes";
import { filterApply, type FilterAxis } from "@/lib/analytics/events";
import { CocktailCard } from "./cocktail-card";

const ABV_LABELS = Object.fromEntries(ABV_BANDS.map((b) => [b.key, b.ko])) as Record<
  AbvBand,
  string
>;

/**
 * 탐색 · 필터 화면 (ISSUE-040 · `FR-SEARCH-001`·`002`·`003`·`005`·`009`).
 *
 * ## 코퍼스를 통째로 받아 여기서 거른다
 *
 * SPEC-05 §4 — "Phase 1 규모에서는 전체 목록을 받아 클라이언트에서 거르는 편이 왕복 없이
 * 즉각적이다." 필터를 만질 때마다 서버를 부르지 않는다. 코퍼스는 페이지(서버 컴포넌트)가
 * **한 번** 받아 넘긴다.
 *
 * ## 패싯 카운트는 계약 모양으로 받는다
 *
 * `counts` 가 `GET /cocktails/facets` 의 응답 모양 그대로다 (SPEC-05 §5). 데이터가 커져
 * 서버 계산으로 옮길 때 이 컴포넌트는 그대로 두고 `facetCounts` 호출만 fetch 로 바꾼다.
 *
 * ## 색인하지 않는다
 *
 * 필터 결과는 색인 대상이 아니다 (`PRIN-P06` · `NFR-S-02`). `noindex` 는 라우트가 건다.
 */
export function SearchScreen({ corpus }: { corpus: SearchItem[] }) {
  const router = useRouter();

  /**
   * 지금 걸린 필터의 쿼리스트링.
   *
   * ## `useSearchParams()` 를 쓰지 않는다
   *
   * 이 경로는 미리 그려 두는 정적 페이지다 (SPEC-05 §4 — 필터는 클라이언트가 건다).
   * 미리 그리는 시점에는 쿼리스트링이 없으므로 훅은 **빈 값**을 주고, 브라우저가
   * `?base=gin` 으로 열어도 그대로 빈 값이었다 — 공유된 링크가 전체 목록을 보여 준다.
   * `FR-SEARCH-005` 가 바로 그 링크를 위해 있는 조항이라 그렇게 둘 수 없다.
   *
   * 그래서 **주소창을 직접 읽는다.** 붙는 순간 한 번 읽고, 뒤로·앞으로에 붙어 다시 읽는다.
   *
   * ## 클릭이 상태를 먼저 바꾼다
   *
   * `router.replace` 는 곧바로 끝나지 않는다. 주소가 정본이면 칩 둘을 빠르게 눌렀을 때
   * 두 번째 클릭이 첫 번째가 반영되기 전의 필터를 읽고 앞선 선택을 지운다. 그래서
   * [queryRef] 를 먼저 갱신하고 주소는 뒤따라 맞춘다 — 화면은 즉시 바뀌고(`NFR-P-02`),
   * 주소는 공유·새로고침을 위해 남는다.
   */
  const queryRef = useRef("");
  const [query, setQueryState] = useState("");
  /**
   * 브라우저에서 붙었는가.
   *
   * 미리 그린 HTML 은 필터가 걸리기 전 모습이고, 스크립트가 붙어야 칩이 눌린다.
   * 그 사이에 누른 클릭은 아무 일도 하지 않는다 — 사람은 다시 누르지만 테스트는
   * 못 누른 채로 기다리다 실패한다. 붙은 시점을 밖에서 볼 수 있게 표시한다.
   */
  const [ready, setReady] = useState(false);

  const setQuery = useCallback((q: string) => {
    queryRef.current = q;
    setQueryState(q);
  }, []);

  useEffect(() => {
    const read = () => {
      setQuery(window.location.search.replace(/^\?/, ""));
      setReady(true);
    };
    read();
    // `replace` 는 popstate 를 내지 않는다 — 아래 [apply] 가 직접 갱신한다.
    window.addEventListener("popstate", read);
    return () => window.removeEventListener("popstate", read);
  }, [setQuery]);

  const filters = useMemo(() => parseFilterQuery(new URLSearchParams(query)), [query]);

  const apply = useCallback(
    (patch: Partial<Filters>, touched?: { axis: FilterAxis; value: string }) => {
      const base = parseFilterQuery(new URLSearchParams(queryRef.current));
      const next = { ...base, ...patch };
      const q = toFilterQuery(next).toString();
      setQuery(q);

      // SPEC-10 §4.2 — 축이 여섯인데 실제로 뭘 쓰나. 결과 수는 **적용한 뒤**의 것이라
      // 여기서 바로 센다 (이슈 049). 초기화처럼 축이 특정되지 않는 조작은 세지 않는다.
      //
      // **주소를 바꾸기 전에** 센다. 뒤에 두면 라우터가 흔들릴 때(빠른 연속 조작으로
      // 이동이 겹칠 때) 그 조작이 통째로 안 세어진다 — 사용자는 분명히 눌렀는데 기록이 없다.
      if (touched) {
        filterApply({
          axis: touched.axis,
          value: touched.value,
          resultCount: filterCocktails(corpus, next).length,
          activeAxisCount: activeAxes(next),
        });
      }

      // `replace` 다 — 칩 하나에 히스토리 한 칸을 쓰면 뒤로가기가 화면을 못 벗어난다.
      // 주소는 그대로 바뀌므로 공유·새로고침은 같다 (`FR-SEARCH-005`).
      router.replace(q ? `${SEARCH_PATH}?${q}` : SEARCH_PATH, { scroll: false });
    },
    [corpus, router, setQuery]
  );

  const results = useMemo(() => filterCocktails(corpus, filters), [corpus, filters]);
  const counts = useMemo(() => facetCounts(corpus, filters), [corpus, filters]);
  // 당도 "전체" 칸의 개수. 축의 선택만 뺀 결과라 다른 축은 그대로 반영된다.
  const sweetAll = useMemo(
    () => filterCocktails(corpus, { ...filters, sweet: null }).length,
    [corpus, filters]
  );

  /**
   * 축 하나를 켜고 끈다.
   *
   * 지금 걸린 값을 **주소에서 다시 읽는다.** 화면이 그린 시점의 값(`filters`)으로 계산하면
   * 빠르게 두 개를 누를 때 두 번째 클릭이 첫 번째를 지운다 — 다시 그려지기 전이라
   * 그 값에는 첫 번째 선택이 없다.
   */
  const toggle = <T extends string>(key: keyof Filters, axis: FilterAxis, value: T) => {
    const live = parseFilterQuery(new URLSearchParams(queryRef.current));
    const current = live[key] as T[];
    const on = current.includes(value);

    apply(
      { [key]: on ? current.filter((x) => x !== value) : [...current, value] },
      // 끄는 것도 그 축을 만진 것이다. 값이 비면 해제다.
      { axis, value: on ? "" : value }
    );
  };

  const summary = [
    filters.sweet ? SWEETNESS[filters.sweet][0] : "당도 전체",
    filters.bases.length
      ? filters.bases.map((b) => BASE_SPIRIT_LABELS[b]).join("/")
      : "기주 전체",
    filters.styles.length ? filters.styles.map((s) => STYLE_LABELS[s]).join("/") : "스타일 전체",
    filters.methods.length
      ? filters.methods.map((m) => TECHNIQUE_LABELS[m]).join("/")
      : "메이킹 전체",
    filters.flavors.length ? `${filters.flavors.length}개 향` : "향 전체",
    filters.abvBands.length
      ? filters.abvBands.map((b) => ABV_LABELS[b]).join("/")
      : "도수 전체",
  ].join(" · ");

  return (
    <main className="shell" data-ready={ready || undefined}>
      <header className="page-head">
        <div>
          <h1>
            칵테일 탐색
            <span className="sub">
              Browse {results.length} of {corpus.length} entries
            </span>
          </h1>
        </div>
        <p className="lede">
          기주 · 스타일 · 메이킹 · 당도 · 도수 · 맛/향 6개 축으로 교차 검색합니다. 모든 수치는
          표준 레시피 기준 실측값입니다.
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

          {/* 당도만 단일값이다 (DECISIONS §1.11) — 라디오로 그 사실을 드러낸다 */}
          <div className="filter-group">
            <div className="filter-label">당도 SWEETNESS</div>
            <div className="seg seg-stack">
              <label className="seg-opt">
                <input
                  type="radio"
                  name="sweetlvl"
                  checked={filters.sweet === null}
                  onChange={() => apply({ sweet: null }, { axis: "sweet", value: "" })}
                />
                <span className="ko">전체</span>
                <span className="en">All · {sweetAll}</span>
              </label>
              {SWEET_LEVELS.filter((level) => level in counts.sweet).map((level) => {
                const [ko, en] = SWEETNESS[level];
                const n = counts.sweet[level];
                return (
                  <label className="seg-opt" key={level} title={en}>
                    <input
                      type="radio"
                      name="sweetlvl"
                      checked={filters.sweet === level}
                      disabled={n === 0 && filters.sweet !== level}
                      aria-label={`${ko} ${n}개`}
                      onChange={() => apply({ sweet: level }, { axis: "sweet", value: level })}
                    />
                    <span className="ko">{ko}</span>
                    <span className="en">
                      {en} · {n}
                    </span>
                  </label>
                );
              })}
            </div>
          </div>

          <FacetChips
            label="기주 BASE SPIRIT"
            counts={counts.base}
            selected={filters.bases}
            labelOf={(slug) => BASE_SPIRIT_LABELS[slug as BaseSpirit]}
            onToggle={(slug) => toggle("bases", "base", slug as BaseSpirit)}
          />

          <FacetChips
            label="스타일 STYLE"
            counts={counts.style}
            selected={filters.styles}
            labelOf={(slug) => STYLE_LABELS[slug as StyleKey]}
            onToggle={(slug) => toggle("styles", "style", slug as StyleKey)}
          />

          <FacetChips
            label="메이킹 METHOD"
            counts={counts.method}
            selected={filters.methods}
            labelOf={(slug) => TECHNIQUE_LABELS[slug as Technique]}
            onToggle={(slug) => toggle("methods", "method", slug as Technique)}
          />

          <FacetChips
            label="도수 ABV"
            counts={counts.abv}
            selected={filters.abvBands}
            labelOf={(slug) => ABV_LABELS[slug as AbvBand]}
            onToggle={(slug) => toggle("abvBands", "abv", slug as AbvBand)}
          />

          {/* 여섯 축 중 여기만 AND 다 — 고를수록 결과가 줄고, 불가능한 조합은 즉시 0 이다
              (`FR-SEARCH-009`). 힌트 문구가 그 차이를 적어 준다. */}
          <FacetChips
            label="맛 / 향 FLAVOR PROFILE"
            hint="전부 만족"
            variant="chip-tag"
            counts={counts.flavor}
            selected={filters.flavors}
            labelOf={(slug) => FLAVOR_LABELS[slug as FlavorKey]}
            onToggle={(slug) => toggle("flavors", "flavor", slug as FlavorKey)}
          />

          <div className="filter-group" style={{ borderBottom: 0 }}>
            <div className="filter-label">검색 KEYWORD</div>
            <input
              className="input"
              type="search"
              placeholder="네그로니 / Negroni"
              value={filters.query}
              aria-label="칵테일 이름 검색"
              onChange={(e) => apply({ query: e.target.value }, { axis: "query", value: e.target.value })}
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
                <CocktailCard key={c.slug} cocktail={c} />
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

/** 지금 걸려 있는 축의 수. 키워드도 한 축으로 센다 (SPEC-10 §4.2). */
function activeAxes(f: Filters): number {
  return [
    f.bases.length > 0,
    f.styles.length > 0,
    f.methods.length > 0,
    f.sweet !== null,
    f.abvBands.length > 0,
    f.flavors.length > 0,
    f.query.trim().length > 0,
  ].filter(Boolean).length;
}


/**
 * 한 축의 칩 줄.
 *
 * **`counts` 만 받는다** — 클라이언트 계산인지 서버 응답인지 모른다 (SPEC-05 §5).
 * 계산 위치가 바뀌어도 이 컴포넌트는 그대로다.
 *
 * 규칙 셋:
 * - **키가 곧 칩이다.** 코퍼스에 없는 값은 애초에 키가 없어 칩이 생기지 않는다 (ADR-0002 §5)
 * - **개수를 글자로 병기한다.** 0 이라는 숫자가 비활성의 근거라 색에 기대지 않는다 (`NFR-A-08`)
 * - `aria-label` 에 개수를 넣는다. 스크린리더는 옆의 작은 숫자를 이름으로 읽지 않는다 (`NFR-A-06`)
 */
function FacetChips({
  label,
  hint,
  variant = "chip",
  counts,
  selected,
  labelOf,
  onToggle,
}: {
  label: string;
  hint?: string;
  /** 맛·향만 다르게 보인다 — 여섯 축 중 유일하게 AND 라 눈으로도 구분되게 둔다 */
  variant?: "chip" | "chip-tag";
  counts: Record<string, number>;
  selected: string[];
  labelOf: (slug: string) => string;
  onToggle: (slug: string) => void;
}) {
  return (
    <div className="filter-group">
      <div className="filter-label">
        {label}
        <span className="hint">{hint ?? "복수 선택"}</span>
      </div>
      <div className="chip-row">
        {Object.entries(counts).map(([slug, n]) => {
          const on = selected.includes(slug);
          return (
            <button
              type="button"
              key={slug}
              className={variant === "chip" ? "btn chip" : "chip-tag"}
              aria-pressed={on}
              // 고른 값은 0 이 되어도 누를 수 있게 둔다 — 끄지 못하면 그 조합에서 못 나온다
              disabled={n === 0 && !on}
              aria-label={`${labelOf(slug)} ${n}개`}
              onClick={() => onToggle(slug)}
            >
              {labelOf(slug)}
              <span className="count" aria-hidden="true">
                {n}
              </span>
            </button>
          );
        })}
      </div>
    </div>
  );
}
