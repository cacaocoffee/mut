/**
 * 사이트 절대 주소.
 *
 * OG·구조화 데이터·사이트맵이 **절대 주소**를 요구한다 (`NFR-S-06` · `NFR-S-04`) —
 * 카카오톡도 구글도 상대 경로를 따라오지 않는다. 세 곳이 각자 환경변수를 읽으면
 * 슬래시 하나 차이로 갈리므로 여기서 한 번만 정리한다.
 *
 * 호스팅이 미정이라(G-07) 기본값은 로컬이다. 배포에서 `KC_SITE_URL` 을 넣는다.
 */
export const SITE_URL = (process.env.KC_SITE_URL ?? "http://localhost:3000").replace(/\/$/, "");

/** 카드에 찍히는 이름. 브랜드 표기는 한 곳에서 온다. */
export const SITE_NAME = "K-Cocktail Archive";

/**
 * 페이지가 자기 OG 를 적을 때 쓰는 바탕.
 *
 * **Next 는 `openGraph` 를 통째로 덮는다** — 페이지가 제목 하나만 적어도 레이아웃에 깔아 둔
 * `siteName`·`locale` 이 함께 사라진다. 카카오톡 카드에서 사이트 이름이 빠지는 것이 그 결과라
 * 눈에 잘 띄지도 않는다. 여기를 거치면 빠질 자리가 없다 (`NFR-S-06`).
 */
export function openGraph(over: {
  title: string;
  description: string;
  url: string;
  type?: "website" | "article";
}) {
  return {
    siteName: SITE_NAME,
    locale: "ko_KR",
    type: over.type ?? "website",
    title: over.title,
    description: over.description,
    url: over.url,
  } as const;
}
