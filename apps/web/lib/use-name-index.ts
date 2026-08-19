"use client";

import { useEffect, useRef, useState } from "react";
import type { components } from "@mut/domain/generated/api";

type SearchResponse = components["schemas"]["SearchResponse"];

/** 입력이 멈추길 기다리는 시간. 한 글자마다 부르면 초성 입력 한 번에 세 번 나간다. */
const SETTLE_MS = 200;

export interface NameIndex {
  /**
   * 색인이 고른 칵테일 슬러그. `null` 이면 **색인을 쓰지 않는다** — 검색어가 없거나,
   * 색인을 못 불렀거나, 아직 답이 오기 전이다. 화면은 그때 이름 부분일치로 거른다.
   */
  slugs: Set<string> | null;
  /** 답을 기다리는 중. 결과를 지우지 말고 "찾는 중"만 알린다. */
  pending: boolean;
}

/**
 * 이름 검색을 **서버 색인**에 맡긴다 (ISSUE-042 · `FR-SEARCH-006`·`007`).
 *
 * ## 왜 화면이 직접 거르지 않나
 *
 * 초성 · 별칭 · 띄어쓰기 변형 매칭은 색인이 하는 일이다 (이슈 017 · 024). `ㄴㄱㄹㄴ` 이
 * `네그로니` 로 이어지는 것을 클라이언트에서 흉내 내면 서버와 다른 답을 내고, 그때
 * 어느 쪽이 맞는지 알 방법이 없다.
 *
 * ## 못 부르면 조용히 이름 매칭으로 떨어진다
 *
 * 색인은 `MUT_API_URL` 이 있어야 돈다. 없으면 프록시가 503 을 준다
 * (`app/api/search/proxy.ts`) — 프로토타입 데이터로 도는 지금이 그 상태다.
 * 그때 `slugs` 는 `null` 이고, 화면은 `filterCocktails` 의 이름 부분일치를 그대로 쓴다.
 * **검색이 안 되는 것이 아니라 덜 되는 것이다** — 초성과 별칭만 빠진다.
 *
 * 429(너무 잦음)도 같게 다룬다. 한도에 걸렸다고 탐색 화면이 멈추면 안 된다.
 *
 * ## 칵테일만 가져온다
 *
 * 응답에는 재료 · 바 · 아티클 그룹도 있다 (`R-F5-1`). 탐색은 칵테일 그리드라 그것들을
 * 담을 자리가 없고, 재료 상세 화면도 아직 없다 (이슈 023 은 API 까지). 타입별로 묶어
 * 보여 주는 것은 `/search` 화면이 계속 맡는다.
 */
export function useNameIndex(term: string): NameIndex {
  const q = term.trim();
  const [state, setState] = useState<{ q: string; slugs: Set<string> | null }>({
    q: "",
    slugs: null,
  });

  /** 늦게 온 응답이 뒤의 결과를 덮지 않게 한다. */
  const latest = useRef(0);

  useEffect(() => {
    // 검색어가 없으면 부를 것이 없다. 상태를 비우지 않아도 되는 이유는 아래 반환값이
    // `state.q === q` 로 거르기 때문이다 — 지난 검색어의 답은 그냥 안 쓰인다.
    if (!q) return;

    const ticket = ++latest.current;
    const timer = setTimeout(async () => {
      try {
        const res = await fetch(`/api/search?q=${encodeURIComponent(q)}`);
        if (ticket !== latest.current) return;

        if (!res.ok) {
          // 503(주소 없음) · 429(너무 잦음) · 502(상류 실패) 전부 같다 — 이름 매칭으로 둔다
          setState({ q, slugs: null });
          return;
        }

        const body = (await res.json()) as SearchResponse;
        if (ticket !== latest.current) return;

        const cocktails = body.groups?.cocktail?.items ?? [];
        setState({ q, slugs: new Set(cocktails.map((hit) => hit.slug)) });
      } catch {
        if (ticket === latest.current) setState({ q, slugs: null });
      }
    }, SETTLE_MS);

    return () => clearTimeout(timer);
  }, [q]);

  // 지금 검색어의 답이 아직 없으면 기다리는 중이다.
  return { slugs: state.q === q ? state.slugs : null, pending: Boolean(q) && state.q !== q };
}
