"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useLastViewed } from "@/lib/use-last-viewed";

export function SiteNav() {
  const pathname = usePathname();
  const lastViewed = useLastViewed();

  // 국문과 영문을 나눠 둔다 — 560px 아래에서 영문만 감춘다 (ISSUE-051 #69).
  // 레이블을 두 줄로 접는 대신 짧게 쓴다. `01`·`02`·`03` 이 화면 순서를 계속 드러낸다.
  const tabs = [
    { href: "/", ko: "01 탐색", en: "SEARCH", match: (p: string) => p === "/" },
    {
      href: `/cocktails/${lastViewed}`,
      ko: "02 상세",
      en: "DETAIL",
      match: (p: string) => p.startsWith("/cocktails/"),
    },
    { href: "/finder", ko: "03 파인더", en: "FINDER", match: (p: string) => p === "/finder" },
  ];

  return (
    <nav className="nav site-nav">
      <span className="nav-brand">
        K-COCKTAIL ARCHIVE<small>KR / EN 아카이브</small>
      </span>
      {/* btn-secondary 를 뺐다 — 테두리 상자였는데 이제 밑줄 인디케이터다 (ISSUE-055) */}
      <div className="tabs">
        {tabs.map((t) => (
          <Link
            key={t.href}
            href={t.href}
            className="btn tab"
            aria-current={t.match(pathname) ? "page" : undefined}
          >
            {t.ko} <span className="en">{t.en}</span>
          </Link>
        ))}
      </div>
    </nav>
  );
}
