import type { Metadata } from "next";
import { SearchScreen } from "@/components/search-screen";
import { searchCorpus } from "@/lib/api";

/**
 * 탐색 · 필터 화면 (ISSUE-040 · SPEC-05 §4).
 *
 * ## 이 경로만 색인하지 않는다
 *
 * `NFR-S-02` · `R-F2.1-1` — 필터 결과는 색인 대상이 아니다. 조합마다 주소가 생기고
 * 대부분이 같은 항목의 다른 조각이라, 색인되면 중복 콘텐츠로 상세·카테고리까지 끌어내린다
 * (`PRIN-P06`).
 *
 * **전역 `robots` 로 붙이지 않는다.** 상세(이슈 038)와 카테고리(이슈 039)는 색인해야 한다 —
 * 전역으로 걸면 그쪽이 함께 사라지고, 그것이 배포 차단 조건이다.
 *
 * ## 코퍼스를 여기서 받는다
 *
 * 화면은 클라이언트 컴포넌트지만 코퍼스를 직접 받지 않는다. 서버에서 한 번 받아 넘기면
 * 브라우저가 다시 왕복하지 않고, 필터를 만질 때도 재요청이 없다 (SPEC-05 §4 · `NFR-P-02`).
 *
 * ## 쿼리스트링은 서버가 읽지 않는다
 *
 * 이 페이지는 미리 그려 둔다 — 코퍼스가 같으면 결과 화면도 같기 때문이다. 필터는
 * 브라우저에서 걸리므로 서버는 `searchParams` 를 받지 않는다. 받는 순간 이 경로가
 * **요청마다 렌더**로 바뀌고, 칩을 누를 때마다 서버를 왕복하게 된다.
 */
export const metadata: Metadata = {
  title: "칵테일 탐색",
  description: "기주 · 스타일 · 메이킹 · 당도 · 도수 · 맛/향 6개 축으로 교차 검색합니다.",
  robots: { index: false, follow: true },
};

/** 코퍼스는 발행분이라 자주 바뀌지 않는다. 10분은 홈과 같은 값이다 (SPEC-05 §4). */
export const revalidate = 600;

export default async function SearchPage() {
  const corpus = await searchCorpus();

  return <SearchScreen corpus={corpus} />;
}
