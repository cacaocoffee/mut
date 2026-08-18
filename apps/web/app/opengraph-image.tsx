import { OG_SIZE, renderOgCard } from "@/lib/og-card";

/**
 * 기본 공유 카드 (ISSUE-044 · `NFR-S-06`).
 *
 * 상세는 자기 카드를 만들고(`cocktails/[slug]/opengraph-image.tsx`) 나머지 공개 화면 —
 * 탐색 · 파인더 · 통합 검색 · 카테고리 · 약관 — 은 이 한 장을 쓴다. 루트에 두면
 * **페이지마다 빠뜨릴 일이 없다.**
 */
export const size = OG_SIZE;
export const contentType = "image/png";
export const alt = "K-Cocktail Archive";

export default async function OpengraphImage() {
  return renderOgCard({
    nameKo: "칵테일 아카이브",
    nameEn: "K-Cocktail Archive",
    meta: "기주 · 스타일 · 메이킹 · 당도 · 도수 · 맛/향",
  });
}
