import type { Metadata } from "next";
import { UnifiedSearch } from "./unified-search";

/**
 * 통합 검색 (ISSUE-042 · `FR-SEARCH-008` · SPEC-07 §2.4).
 *
 * ## 색인하지 않는다
 *
 * 검색 결과는 필터와 같은 성격이라 색인 대상이 아니다 (`PRIN-P06` · `NFR-S-02`).
 * 상류 API 도 `X-Robots-Tag: noindex` 를 준다 — 프록시가 그대로 옮긴다.
 *
 * ## 화면이 검색을 하지 않는다
 *
 * 초성·별칭 매칭은 색인의 일이다 (이슈 017·024). 이 경로는 셸만 미리 그리고
 * 질의는 브라우저가 `/api/search` 로 보낸다.
 */
export const metadata: Metadata = {
  title: "통합 검색",
  description: "칵테일과 재료를 한 번에 찾습니다. 초성 · 별칭 · 영문명이 모두 이어집니다.",
  robots: { index: false, follow: true },
};

export default function SearchPage() {
  return <UnifiedSearch />;
}
