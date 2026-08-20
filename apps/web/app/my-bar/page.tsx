import type { Metadata } from "next";
import { MyBarScreen } from "@/components/my-bar-screen";
import { searchCorpus } from "@/lib/api";

/**
 * 내 술장 (`R-F2.2-1`·`2`·`4`·`5` · SPEC-01 `STOCK`).
 *
 * ## 셸은 정적, 판정은 브라우저에서
 *
 * 파인더와 같은 모양이다 — 가진 재료가 `localStorage` 에 있어 서버는 누가 무엇을
 * 담았는지 모른다. 코퍼스만 한 번 넘기고 역검색은 전부 브라우저가 한다.
 *
 * ## 색인하지 않는다
 *
 * 담은 것에 따라 내용이 통째로 달라지는 화면이라 탐색 필터와 같은 성격이다
 * (`PRIN-P06` · `NFR-S-02`). 색인되면 남의 술장이 검색 결과에 뜨는 셈이 된다.
 *
 * ## `/me/*` 아래가 아니다
 *
 * `R-F2.2-4` 가 "비로그인 상태에서도 로컬 저장으로 체험" 을 요구한다. 인증이 붙은 뒤
 * 서버 동기화(`GET`·`PUT /me/stock`)를 얹을 때도 화면 주소는 그대로 둔다 —
 * 로그인 여부로 주소가 바뀌면 보내 둔 링크가 깨진다.
 */
export const metadata: Metadata = {
  title: "내 술장",
  description: "가진 재료를 담으면 지금 만들 수 있는 칵테일과 하나만 더 있으면 되는 것을 보여 줍니다.",
  robots: { index: false, follow: true },
};

/** 코퍼스는 발행분이라 자주 바뀌지 않는다. 탐색·파인더와 같은 값이다. */
export const revalidate = 600;

export default async function MyBarPage() {
  const corpus = await searchCorpus();

  return <MyBarScreen corpus={corpus} />;
}
