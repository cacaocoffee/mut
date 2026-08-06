import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // 워크스페이스 패키지는 빌드 산출물 없이 소스로 직접 소비한다.
  transpilePackages: ["@kca/domain", "@kca/ui"],
};

export default nextConfig;
