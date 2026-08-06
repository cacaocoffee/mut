import type { Metadata } from "next";
import { Archivo, Noto_Sans_KR, Noto_Serif_KR } from "next/font/google";
import { SiteNav } from "@/components/site-nav";
import "./globals.css";

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
          <footer className="legal">
            {/* PRD R-F1.1-8 · 12장 — 과음 경고는 모든 페이지 하단 고정 */}
            지나친 음주는 뇌졸중, 기억력 손상이나 치매를 유발합니다. 임신 중 음주는 기형아 출생
            위험을 높입니다. 만 19세 미만 청소년에게 판매하지 않습니다.
          </footer>
        </div>
      </body>
    </html>
  );
}
