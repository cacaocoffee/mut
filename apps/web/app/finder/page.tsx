import type { Metadata } from "next";
import { FinderScreen } from "@/components/finder-screen";
import { searchCorpus } from "@/lib/api";

/**
 * 취향 파인더 (ISSUE-041 · `FR-SEARCH-004`).
 *
 * ## 셸은 정적, 진행은 브라우저에서
 *
 * SPEC-05 §4 의 렌더링 표에 `/finder` 가 없다. 질문·답·후보 계산이 전부 클라이언트
 * 상호작용이라 **셸만 미리 그리고** 단계 전환은 서버를 부르지 않는다 (RED 25).
 * 코퍼스는 탐색 화면과 **같은 목록**을 여기서 한 번 받아 넘긴다 (RED 13).
 *
 * ## 색인은 한다 — 다만 답이 붙은 주소는 아니다
 *
 * 파인더 자체는 기능 소개가 있는 진입 화면이라 색인 대상이다 (사이트맵에도 있다 —
 * 이슈 039). 답이 붙은 주소(`?abv=high&…`)는 같은 화면의 다른 상태일 뿐이라
 * **canonical 을 답 없는 `/finder` 로 고정**한다. 그러지 않으면 조합마다 같은 내용의
 * 페이지가 색인되고, 그것이 `PRIN-P06` 이 막으려는 상황이다. `noindex` 를 붙이지 않은
 * 이유는 [GAPS G-33](../../../../docs/prd/GAPS.md) 에 적었다.
 */
export const metadata: Metadata = {
  title: "취향 파인더",
  description: "도수 · 당도 · 향 · 기주 4개 질문으로 오늘의 칵테일 3종을 좁힙니다.",
  alternates: { canonical: "/finder" },
};

/** 코퍼스는 발행분이라 자주 바뀌지 않는다. 탐색 화면과 같은 값이다. */
export const revalidate = 600;

export default async function FinderPage() {
  const corpus = await searchCorpus();

  return <FinderScreen corpus={corpus} />;
}
