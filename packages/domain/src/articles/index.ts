import type { Article, ArticleCategory } from "../types";
import { ARTICLE as barBarns } from "./bar-barns";
import { ARTICLE as barYamazaki } from "./bar-yamazaki";
import { ARTICLE as bunnahabhain } from "./bunnahabhain-feis-ile-2024";
import { ARTICLE as espressoMartini } from "./espresso-martini";
import { ARTICLE as kilkerran } from "./kilkerran-8cs-batch10";
import { ARTICLE as milkAndHoney } from "./milk-and-honey-mokpo";
import { ARTICLE as negroni } from "./negroni";
import { ARTICLE as springbank } from "./springbank-distillery-tour";
import { ARTICLE as vesper } from "./vesper";

/**
 * 아티클 코퍼스 (`FR-CONTENT-001` 앞당김 · ADR-0010).
 *
 * 글 하나가 모듈 하나다 — 본문이 길어 한 파일에 모으면 찾기도 고치기도 어렵다.
 * 첫 9편은 운영자 블로그(Shaking Like Bartender)에서 이관했다. 출처는 각 글의
 * `sourceUrl` 에 있고 상세 하단에 표기된다.
 *
 * Phase 2 에 `article` 테이블과 `GET /articles` 가 생기면 이 배열이 시드가 된다 —
 * `data.ts` 의 칵테일 코퍼스와 같은 운명이다.
 */
export const ARTICLES: Article[] = [
  negroni,
  vesper,
  espressoMartini,
  milkAndHoney,
  barYamazaki,
  barBarns,
  springbank,
  kilkerran,
  bunnahabhain,
].sort((a, b) => (a.publishedAt < b.publishedAt ? 1 : -1));

/** 카테고리의 한국어 이름. 목록 화면의 카드 라벨이 쓴다. */
export const ARTICLE_CATEGORY_KO: Record<ArticleCategory, string> = {
  cocktail: "칵테일",
  bar: "바",
  whisky: "위스키",
};

export function articleBySlug(slug: string): Article | undefined {
  return ARTICLES.find((a) => a.slug === slug);
}

export function articlesByCategory(category: ArticleCategory): Article[] {
  return ARTICLES.filter((a) => a.category === category);
}
