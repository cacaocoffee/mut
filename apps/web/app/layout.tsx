import type { Metadata } from "next";
import { Archivo, Noto_Sans_KR, Noto_Serif_KR } from "next/font/google";
import { SiteNav } from "@/components/site-nav";
import { LegalNotice } from "@/components/legal/legal-notice";
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

export const metadata: Metadata = {
  title: {
    default: "K-Cocktail Archive",
    template: "%s · K-Cocktail Archive",
  },
  description:
    "당도 · 기주 · 맛/향 · 도수 4개 축으로 교차 검색하는 칵테일 아카이브. 모든 수치는 표준 레시피 기준 실측값입니다.",
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
