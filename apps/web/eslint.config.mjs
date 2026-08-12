import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  // Override default ignores of eslint-config-next.
  globalIgnores([
    // Default ignores of eslint-config-next:
    ".next/**",
    "out/**",
    "build/**",
    "next-env.d.ts",
    // Playwright 산출물 — 리포트는 번들된 서드파티 JS 라 린트 대상이 아니다 (ISSUE-056)
    "playwright-report/**",
    "test-results/**",
  ]),
]);

export default eslintConfig;
