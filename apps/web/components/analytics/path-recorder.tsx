"use client";

import { useEffect } from "react";
import { usePathname } from "next/navigation";
import { recordVisit } from "@/lib/analytics/core";

/**
 * 지나온 화면을 적어 둔다 (ISSUE-035 · SPEC-10 §4.1).
 *
 * 레이아웃에 있어야 **모든 화면**을 지난다. 화면마다 붙이면 하나를 빠뜨리고, 빠뜨린 화면에서
 * 넘어간 조회는 전부 "밖에서 왔다" 로 세어진다.
 */
export function PathRecorder() {
  const pathname = usePathname();

  useEffect(() => {
    recordVisit(pathname);
  }, [pathname]);

  return null;
}
