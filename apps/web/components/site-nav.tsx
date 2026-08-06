"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useLastViewed } from "@/lib/use-last-viewed";

export function SiteNav() {
  const pathname = usePathname();
  const lastViewed = useLastViewed();

  const tabs = [
    { href: "/", label: "01 탐색 SEARCH", match: (p: string) => p === "/" },
    {
      href: `/cocktails/${lastViewed}`,
      label: "02 상세 DETAIL",
      match: (p: string) => p.startsWith("/cocktails/"),
    },
    { href: "/finder", label: "03 파인더 FINDER", match: (p: string) => p === "/finder" },
  ];

  return (
    <nav className="nav site-nav">
      <span className="nav-brand">
        K-COCKTAIL ARCHIVE<small>KR / EN 아카이브</small>
      </span>
      <div className="tabs">
        {tabs.map((t) => (
          <Link
            key={t.href}
            href={t.href}
            className="btn btn-secondary tab"
            aria-current={t.match(pathname) ? "page" : undefined}
          >
            {t.label}
          </Link>
        ))}
      </div>
    </nav>
  );
}
