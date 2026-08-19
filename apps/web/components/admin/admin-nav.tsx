"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

/**
 * 어드민 네비게이션 (ISSUE-045·048).
 *
 * ## 없는 화면은 링크하지 않는다
 *
 * 자리를 미리 링크로 만들면 눌렀을 때 아무 일도 안 일어나는 버튼이 남는다.
 */
const SECTIONS = [
  { href: "/admin", ko: "대시보드", en: "OVERVIEW" },
  { href: "/admin/cocktails", ko: "칵테일", en: "COCKTAILS" },
  // 목록은 `editor` 도 본다 — 승인 버튼만 `admin` 이다 (SPEC-08 §2).
  { href: "/admin/ingredients", ko: "재료", en: "INGREDIENTS" },
  { href: "/admin/tasks", ko: "검증 태스크", en: "TASKS" },
] as const;

export function AdminNav() {
  const pathname = usePathname();

  return (
    <nav className="admin__nav" aria-label="어드민 메뉴">
      {SECTIONS.map((s) => (
        <Link
          key={s.href}
          href={s.href}
          className="admin__nav-item"
          // 지금 어디인지 (RED 17). 색이 아니라 상태로 알린다.
          aria-current={
            pathname === s.href || (s.href !== "/admin" && pathname.startsWith(s.href))
              ? "page"
              : undefined
          }
        >
          {s.ko} <span className="en">{s.en}</span>
        </Link>
      ))}
    </nav>
  );
}
