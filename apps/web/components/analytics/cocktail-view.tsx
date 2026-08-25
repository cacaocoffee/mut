"use client";

import { useEffect } from "react";
import { previousPath, track } from "@/lib/analytics/core";
import { FINDER_PATH, SEARCH_PATH, UNIFIED_SEARCH_PATH } from "@/lib/routes";

/**
 * `cocktail_view` (ISSUE-035 · SPEC-10 §4.1).
 *
 * ## `entryPoint` 가 이 이벤트의 요체다
 *
 * > **`entryPoint` 가 `external` 인 비율이 곧 SEO 성과다.**
 *
 * 어디서 들어왔는지는 **직전 화면**이 말한다. 밖에서 왔으면 `external` 이고, 그 비율이
 * Phase 1a 에서 검증할 세 지표 중 하나다 (`PRIN-T04` — 유기 검색 50% 이상).
 *
 * ## 화면이 뜬 뒤에 한 번
 *
 * 상세는 미리 그려 두는 정적 페이지라 서버에서는 누가 보는지 모른다. 브라우저에서 붙은 뒤
 * 한 번 보낸다. 슬러그가 바뀔 때만 다시 보낸다 — 리렌더마다 세면 조회수가 부푼다.
 */
export function TrackCocktailView({ slug }: { slug: string }) {
  useEffect(() => {
    track("cocktail_view", { cocktailSlug: slug, entryPoint: entryPoint(slug) });
  }, [slug]);

  return null;
}

/** SPEC-10 §4.1 의 다섯 갈래. 서버가 이 다섯 밖의 값을 버린다. */
function entryPoint(slug: string): "search" | "category" | "related" | "finder" | "article" | "external" {
  // 화면 안에서 옮겨 왔으면 그 경로가 답이다. `document.referrer` 는 문서를 처음 열 때
  // 값에서 멈춰 있어, 링크를 눌러 다닌 것을 전부 "밖에서 왔다" 로 만든다 (`navigation.ts`).
  const inDocument = previousPath(`/cocktails/${slug}`);
  if (inDocument) return fromPath(inDocument);

  const referrer = document.referrer;
  if (!referrer) return "external"; // 주소창·북마크·앱에서 열었다

  let url: URL;
  try {
    url = new URL(referrer);
  } catch {
    return "external";
  }
  if (url.hostname !== window.location.hostname) return "external";

  return fromPath(url.pathname);
}

function fromPath(path: string): "search" | "category" | "related" | "finder" | "article" | "external" {
  if (path === SEARCH_PATH || path === UNIFIED_SEARCH_PATH) return "search";
  if (/^\/cocktails\/(base|style|method)\//.test(path)) return "category";
  if (path === FINDER_PATH) return "finder";
  // 아티클에서 넘어왔다 — 콘텐츠 → 칵테일 전환이 콘텐츠 가설의 성과다 (GAPS G-50)
  if (path.startsWith("/articles/")) return "article";
  // 다른 상세에서 왔다면 배리에이션 목록을 타고 온 것이다
  if (path.startsWith("/cocktails/")) return "related";

  // 같은 사이트의 다른 화면(약관 등). 다섯 갈래 밖이라 밖에서 온 것으로 세지 않는다.
  return "search";
}
