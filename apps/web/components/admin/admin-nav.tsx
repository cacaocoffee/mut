"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { AdminRole } from "@/lib/admin-session";

/**
 * 어드민 네비게이션 (ISSUE-045·048).
 *
 * ## 권한 없는 메뉴는 **아예 없다**
 *
 * SPEC-08 §2 — 눌러서 403 을 보게 하지 않는다 (RED 21). `editor` 에게 감사 로그 메뉴를
 * 보여 주고 막으면, 무엇이 기록되는지는 이미 알려 준 것이 된다 (SPEC-08 §2.2 —
 * 감시받는 사람이 감시 기록을 보면 안 된다).
 *
 * **숨김은 UX 이고 서버가 정본이다** — 이슈 026·029 가 `@PreAuthorize` 로 막는다.
 * 여기서 지워도 서버는 그대로 거부하고, 주소를 직접 치면 페이지가 404 를 준다
 * (`requireAdminRole`).
 *
 * ## 없는 화면은 링크하지 않는다
 *
 * 자리를 미리 링크로 만들면 눌렀을 때 아무 일도 안 일어나는 버튼이 남는다.
 */
const SECTIONS = [
  { href: "/admin", ko: "대시보드", en: "OVERVIEW", adminOnly: false },
  { href: "/admin/cocktails", ko: "칵테일", en: "COCKTAILS", adminOnly: false },
  // 목록은 `editor` 도 본다 — 승인 버튼만 `admin` 이다 (SPEC-08 §2).
  { href: "/admin/ingredients", ko: "재료", en: "INGREDIENTS", adminOnly: false },
  { href: "/admin/tasks", ko: "검증 태스크", en: "TASKS", adminOnly: false },
  { href: "/admin/audit", ko: "감사 로그", en: "AUDIT", adminOnly: true },
] as const;

export function AdminNav({ role }: { role: AdminRole }) {
  const pathname = usePathname();

  return (
    <nav className="admin__nav" aria-label="어드민 메뉴">
      {SECTIONS.filter((s) => !s.adminOnly || role === "admin").map((s) => (
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
