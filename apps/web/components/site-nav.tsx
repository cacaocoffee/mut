"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useLastViewed } from "@/lib/use-last-viewed";
import { FINDER_PATH, SEARCH_PATH, UNIFIED_SEARCH_PATH } from "@/lib/routes";

export function SiteNav() {
  const pathname = usePathname();
  const lastViewed = useLastViewed();

  // 국문과 영문을 나눠 둔다 — 560px 아래에서 영문만 감춘다 (ISSUE-051 #69).
  // 레이블을 두 줄로 접는 대신 짧게 쓴다. `01`~`04` 가 화면 순서를 계속 드러낸다.
  const tabs = [
    { href: SEARCH_PATH, ko: "01 탐색", en: "SEARCH", match: (p: string) => p === SEARCH_PATH },
    {
      href: `/cocktails/${lastViewed}`,
      ko: "02 상세",
      en: "DETAIL",
      // 탐색도 `/cocktails/` 로 시작한다 (ISSUE-040) — 먼저 걸러 내지 않으면 둘 다 켜진다.
      match: (p: string) => p.startsWith("/cocktails/") && p !== SEARCH_PATH,
    },
    { href: FINDER_PATH, ko: "03 파인더", en: "FINDER", match: (p: string) => p === FINDER_PATH },
    {
      href: UNIFIED_SEARCH_PATH,
      ko: "04 검색",
      en: "FIND",
      match: (p: string) => p === UNIFIED_SEARCH_PATH,
    },
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
