import { defineConfig, devices } from "@playwright/test";

/**
 * 헤드리스 러너 — 정적 가드가 못 보는 **배치 결과**를 본다 (ISSUE-056 #80).
 *
 * `scripts/css-guard.mjs` 는 규칙의 존재만 본다. "`.tab` 에 white-space 가 있는가"는 알아도
 * "320px 에서 실제로 한 줄인가"는 모른다. 그 사이가 비어 있었고, ISSUE-055 의 탭 비가시
 * 결함이 정확히 거기서 나왔다.
 *
 * CI 워크플로는 여기서 만들지 않는다 — 이슈 046(#48) 소유다.
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? "list" : [["list"], ["html", { open: "never" }]],

  use: {
    baseURL: "http://127.0.0.1:3100",
    trace: "retain-on-failure",
  },

  // 폭은 각 테스트가 직접 정한다 — 이 파일은 기본 하나만 둔다.
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],

  /**
   * `next start` 로 **프로덕션 빌드**를 띄운다. `next dev` 가 아니다 —
   * 개발 서버는 CSS 를 다르게 주입해서 배치 검증의 대상으로 부적절하다.
   */
  webServer: {
    command: "npm run build && npm run start -- --port 3100",
    url: "http://127.0.0.1:3100",
    reuseExistingServer: !process.env.CI,
    timeout: 180_000,
  },
});
