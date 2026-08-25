"use client";

import { useEffect } from "react";
import { previousPath, track } from "@/lib/analytics/core";
import { ARTICLES_PATH } from "@/lib/routes";
import { SEARCH_PATH, UNIFIED_SEARCH_PATH } from "@/lib/routes";

/**
 * `article_view` (ADR-0010 · SPEC-10 §4.1 의 아티클판).
 *
 * ## `entryPoint = external` 비율이 콘텐츠·SEO 가설의 성과다
 *
 * 내 술장을 폐기하며 KPI 가 공중에 떴다 (GAPS G-50). 그 대체 지표의 절반이 이것이다 —
 * 아티클이 유기 검색으로 얼마나 읽히나. 나머지 절반(아티클 → 칵테일 전환)은
 * `cocktail_view` 의 `entryPoint = article` 이 잡는다.
 *
 * cocktail_view 와 같은 규약이다: 상세는 정적 페이지라 브라우저에서 붙은 뒤 한 번,
 * 슬러그가 바뀔 때만 다시 보낸다.
 */
export function TrackArticleView({ slug }: { slug: string }) {
  useEffect(() => {
    track("article_view", { articleSlug: slug, entryPoint: entryPoint(slug) });
  }, [slug]);

  return null;
}

/** 어디서 아티클로 들어왔나. 서버는 값을 검증하지 않으므로 아티클에 맞는 갈래로 둔다. */
function entryPoint(slug: string): "list" | "search" | "related" | "external" {
  const inDocument = previousPath(`${ARTICLES_PATH}/${slug}`);
  if (inDocument) return fromPath(inDocument);

  const referrer = document.referrer;
  if (!referrer) return "external"; // 주소창·북마크·앱·검색 결과 클릭

  let url: URL;
  try {
    url = new URL(referrer);
  } catch {
    return "external";
  }
  if (url.hostname !== window.location.hostname) return "external";

  return fromPath(url.pathname);
}

function fromPath(path: string): "list" | "search" | "related" | "external" {
  if (path === ARTICLES_PATH) return "list";
  if (path === SEARCH_PATH || path === UNIFIED_SEARCH_PATH) return "search";
  // 다른 아티클이나 칵테일 상세에서 왔으면 이어 읽기다
  if (path.startsWith(`${ARTICLES_PATH}/`) || path.startsWith("/cocktails/")) return "related";
  return "list";
}
