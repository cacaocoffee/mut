import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // 워크스페이스 패키지는 빌드 산출물 없이 소스로 직접 소비한다.
  transpilePackages: ["@mut/domain", "@mut/ui"],

  // `/` 리다이렉트를 지웠다 — 홈(app/page.tsx)이 생겼다 (ADR-0012). ISSUE-040 이
  // 예고한 그 홈이다("홈이 생기면 이 줄을 지운다"). 탐색은 그대로 /cocktails/search 다.
};

export default nextConfig;
