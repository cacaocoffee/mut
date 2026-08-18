"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import type { components } from "@kca/domain/generated/api";
import { ENTITY_LABELS, SearchBox } from "@/components/search-box";
import { SEARCH_PATH } from "@/lib/routes";
import { track } from "@/lib/analytics/core";

type SearchResponse = components["schemas"]["SearchResponse"];

/**
 * 통합 검색 화면 (ISSUE-042 · `FR-SEARCH-006`·`007`·`008` · `R-F5-1`).
 *
 * ## 검색은 서버가 한다
 *
 * 초성·별칭·띄어쓰기 변형 매칭은 색인이 하는 일이다 (이슈 017·024). 화면은 **질의를 넘기고
 * 받은 것을 그린다.** 여기서 다시 거르면 서버와 다른 답을 내고, 그때 어느 쪽이 맞는지
 * 알 방법이 없다.
 *
 * ## 그룹 자리를 미리 잡아 둔다
 *
 * `R-F5-1` 이 타입별 그룹핑을 요구한다. Phase 1a 색인에는 칵테일·재료뿐이지만
 * (이슈 017) 바·아티클 자리를 지금 잡아 둔다 — 나중에 키가 생겨 렌더가 깨지지 않게.
 * 서버도 네 자리를 항상 채워 보낸다.
 */
const GROUP_ORDER = ["cocktail", "ingredient", "bar", "article"] as const;

/**
 * 상세 화면이 있는 타입만 링크가 된다.
 *
 * 재료 사전(`/ingredients/{slug}`)은 아직 없다 (SCREENS-01 01-D · 이슈 023 은 API 까지).
 * 없는 곳으로 보내면 404 를 눌러 보게 되므로, 화면이 생기기 전까지 글자로만 둔다.
 */
function hrefOf(entityType: string, slug: string): string | null {
  return entityType === "cocktail" ? `/cocktails/${slug}` : null;
}

type Answer =
  | { kind: "done"; result: SearchResponse }
  | { kind: "error"; status: number };

type State = { kind: "idle" } | { kind: "loading" } | Answer;

export function UnifiedSearch() {
  const router = useRouter();

  const [query, setQuery] = useState("");
  /** 응답을 **질의와 함께** 들고 있는다 — 지금 질의의 답이 아직 없으면 그것이 곧 "부르는 중"이다. */
  const [answered, setAnswered] = useState<{ q: string; answer: Answer } | null>(null);

  // 주소창을 직접 읽는다 — 미리 그려 두는 경로라 `useSearchParams()` 는 빈 값을 준다
  // (탐색 화면과 같은 이유, ISSUE-040).
  useEffect(() => {
    const read = () => setQuery(new URLSearchParams(window.location.search).get("q") ?? "");
    read();
    window.addEventListener("popstate", read);
    return () => window.removeEventListener("popstate", read);
  }, []);

  const term = query.trim();
  const state: State = !term
    ? { kind: "idle" }
    : answered?.q === term
      ? answered.answer
      : { kind: "loading" };

  useEffect(() => {
    if (!term) return;

    let live = true;
    (async () => {
      try {
        const res = await fetch(`/api/search?q=${encodeURIComponent(term)}`);
        if (!live) return;

        // 429·400·503 을 상태 그대로 들고 온다 — 화면이 원인에 따라 다르게 말한다 (RED 24)
        const answer: Answer = res.ok
          ? { kind: "done", result: (await res.json()) as SearchResponse }
          : { kind: "error", status: res.status };
        setAnswered({ q: term, answer });

        // SPEC-10 §4.3 — **0건이 곧 수요가 확인된 콘텐츠 후보다.**
        // `hadChosung` 은 **서버가 판정한 값을 그대로** 옮긴다 (이슈 024) — 여기서 다시
        // 판정하면 "콘텐츠가 없다" 와 "초성 색인이 고장났다" 를 나눌 수 없게 된다.
        if (answer.kind === "done" && answer.result.matchedCount === 0) {
          track("search_miss", {
            query: answer.result.query,
            matchedCount: 0,
            hadChosung: answer.result.hadChosung,
          });
        }
      } catch {
        if (live) setAnswered({ q: term, answer: { kind: "error", status: 0 } });
      }
    })();

    // 앞선 질의의 응답이 늦게 도착해 뒤의 결과를 덮지 않게 한다.
    return () => {
      live = false;
    };
  }, [term]);

  const submit = useCallback(
    (next: string) => {
      const submitted = next.trim();
      setQuery(submitted);
      router.replace(submitted ? `/search?q=${encodeURIComponent(submitted)}` : "/search", {
        scroll: false,
      });
    },
    [router]
  );

  return (
    <main className="shell">
      <header className="page-head">
        <div>
          <h1>
            통합 검색<span className="sub">Cocktails · Ingredients</span>
          </h1>
        </div>
        <p className="lede">
          칵테일과 재료를 한 번에 찾습니다. 띄어쓰기 · 영문명 · 별칭 · 초성이 모두 같은 결과로
          이어집니다.
        </p>
      </header>

      <SearchBox initialQuery={query} onSubmit={submit} />

      {/* 결과 수는 화면이 바뀌어도 소리로는 안 바뀐다 — 읽어 준다 (RED 28) */}
      <div className="search-status" aria-live="polite">
        <Status state={state} query={query} />
      </div>

      {state.kind === "done" && state.result.matchedCount > 0 && (
        <div className="search-groups">
          {GROUP_ORDER.map((type) => {
            const group = state.result.groups[type];
            // 빈 그룹은 그리지 않는다. 자리는 잡되 "칵테일 0건" 을 늘어놓지 않는다 (RED 12)
            if (!group || group.count === 0) return null;

            return (
              <section key={type} className="search-group">
                <div className="rule-head">
                  <h4 style={{ margin: 0 }}>
                    {ENTITY_LABELS[type] ?? type}
                    <span className="search-group__count">{group.count}건</span>
                  </h4>
                </div>
                <ul className="search-group__list">
                  {group.items.map((hit) => {
                    const href = hrefOf(hit.entityType, hit.slug);
                    const body = (
                      <>
                        <span className="ko">{hit.nameKo}</span>
                        {hit.nameEn && <span className="en">{hit.nameEn}</span>}
                      </>
                    );
                    return (
                      <li key={`${hit.entityType}:${hit.slug}`} className="search-group__item">
                        {href ? (
                          <Link href={href}>{body}</Link>
                        ) : (
                          // 갈 곳이 없는 타입은 글자로만 둔다 — 없는 화면으로 보내지 않는다
                          <span className="search-group__plain">{body}</span>
                        )}
                      </li>
                    );
                  })}
                </ul>
              </section>
            );
          })}
        </div>
      )}
    </main>
  );
}

/**
 * 상태별 안내.
 *
 * 0건 안내가 **다음 행동을 준다** (RED 18). 검색이 실패한 자리에서 끝내면 사용자는 나가고,
 * 그 이탈은 콘텐츠가 없어서가 아니라 길이 없어서다.
 */
function Status({ state, query }: { state: State; query: string }) {
  if (state.kind === "idle") {
    return <p className="search-status__hint">찾을 이름을 입력하세요. 초성도 됩니다.</p>;
  }
  if (state.kind === "loading") {
    return <p className="search-status__hint">검색 중…</p>;
  }
  if (state.kind === "error") {
    // 429 는 잠시 뒤에 다시 하면 되고, 나머지는 지금 안 되는 것이다 (RED 24)
    return (
      <p className="search-status__error" role="alert">
        {state.status === 429
          ? "검색 요청이 너무 잦습니다. 잠시 후 다시 시도해 주세요."
          : "검색을 쓸 수 없습니다. 잠시 후 다시 시도해 주세요."}
      </p>
    );
  }

  const { matchedCount } = state.result;
  if (matchedCount === 0) {
    return (
      <div className="search-status__empty">
        <p>
          <b>{query}</b> 에 대한 결과가 없습니다.
        </p>
        <p>
          이름의 일부만 넣거나 초성으로 다시 찾아보세요.{" "}
          <Link href={SEARCH_PATH}>기주 · 도수로 탐색하기 →</Link>
        </p>
      </div>
    );
  }

  return (
    <p className="search-status__count">
      <b>{matchedCount}</b>건을 찾았습니다.
    </p>
  );
}
