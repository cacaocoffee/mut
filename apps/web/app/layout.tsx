import type { Metadata } from "next";
import { Archivo, Noto_Sans_KR, Noto_Serif_KR } from "next/font/google";
import { SiteNav } from "@/components/site-nav";
import { LegalNotice } from "@/components/legal/legal-notice";
import { SITE_NAME, SITE_URL } from "@/lib/site";
import "./globals.css";
import "@/components/legal/legal.css";

const archivo = Archivo({
  variable: "--font-archivo",
  subsets: ["latin"],
  weight: ["400", "600", "800"],
});

const notoSans = Noto_Sans_KR({
  variable: "--font-noto-sans",
  subsets: ["latin"],
  weight: ["400", "500", "700"],
});

const notoSerif = Noto_Serif_KR({
  variable: "--font-noto-serif",
  subsets: ["latin"],
  weight: ["400", "600"],
});

const DESCRIPTION =
  "기주 · 스타일 · 메이킹 · 당도 · 도수 · 맛/향 6개 축으로 교차 검색하는 칵테일 아카이브. 모든 수치는 표준 레시피 기준 실측값입니다.";

/**
 * 모든 공개 페이지가 물려받는 것 (ISSUE-044 · `NFR-S-06`).
 *
 * ## `metadataBase` 가 있어야 절대 주소가 된다
 *
 * OG 는 상대 경로를 못 따라온다 — 카카오톡도 구글도 그렇다. 여기 한 번 적어 두면 각
 * 페이지가 상대 경로로 써도 Next 가 절대 주소로 펴 준다 (RED 16).
 *
 * ## 여기서 한 번 깔면 페이지마다 빠뜨릴 일이 없다
 *
 * `NFR-S-06` 은 **모든 공개 페이지**에 OG 를 요구한다. 페이지마다 적으면 언젠가 하나를
 * 빠뜨리고, 빠뜨린 것은 공유해 보기 전까지 아무도 모른다. 개별 페이지는 제목·설명만 덮는다.
 */
export const metadata: Metadata = {
  metadataBase: new URL(SITE_URL),
  title: {
    default: "K-Cocktail Archive",
    template: "%s · K-Cocktail Archive",
  },
  description: DESCRIPTION,
  openGraph: {
    type: "website",
    siteName: SITE_NAME,
    locale: "ko_KR",
    title: SITE_NAME,
    description: DESCRIPTION,
    // 상세는 자기 카드를 만든다 (`opengraph-image.tsx`). 나머지는 이 한 장을 쓴다.
    images: [{ url: "/opengraph-image", width: 1200, height: 630 }],
  },
  // `R-F5-5` 는 카카오톡만 적었다. 트위터 카드는 같은 값을 한 줄로 재사용하는 것이라
  // 비용이 없고, 슬랙·디스코드가 이 태그를 먼저 본다 (RED 18 결정 — 추가).
  twitter: { card: "summary_large_image" },
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="ko"
      className={`${archivo.variable} ${notoSans.variable} ${notoSerif.variable}`}
    >
      <body>
        <div className="page">
          <SiteNav />
          {children}
          {/* NFR-L-01 — 모든 페이지 하단 고정. 배포 차단 조건이다.
              루트 레이아웃에 무조건 렌더한다: 페이지마다 붙이면 언젠가 빠뜨리고,
              빠뜨린 것을 알아채는 방법이 없다. 컴포넌트에 끌 수 있는 prop 도 없다. */}
          <LegalNotice />
        </div>
      </body>
    </html>
  );
}
