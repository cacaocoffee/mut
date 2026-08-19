import { test, expect } from "@playwright/test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

/**
 * ISSUE-048 — 재료 승인 · 검증 태스크 · 감사 로그 (`FR-ADMIN-004`·`005`·`007` · SPEC-08 §2·§2.2).
 *
 * ## 세 화면의 권한이 서로 다른 것이 요점이다
 *
 * | 화면 | `editor` | `admin` |
 * |---|---|---|
 * | 재료 승인 | — | ○ |
 * | 검증 태스크 | ○ | ○ |
 * | 감사 로그 | — | ○ |
 *
 * e2e 에 로그인 경로가 없어(세션은 API 가 발급한다) **역할별 화면은 소스로 확인한다.**
 * 지우면 검사가 깨지는 자리를 고른다 — 판정 호출, 메뉴의 역할 조건, 승인 버튼의 분기.
 * 서버가 정본이라 이 검사들이 통과해도 권한이 열리지는 않는다 (이슈 026·029 가 막는다).
 */
const read = (rel: string) => readFileSync(join(process.cwd(), rel), "utf8");

/** 못 들어오는 사람에게는 **없는 화면**이다 (RED 20 · SCREENS-00 §3.4). */
function 없는화면이다(path: string) {
  test(`RED20 - 비로그인은 ${path} 를 볼 수 없다`, async ({ page }) => {
    const res = await page.goto(path);

    expect(res?.status(), "권한 없는 사람에게 200 을 줬다").toBe(404);
    await expect(page.getByText("어드민 ADMIN")).toHaveCount(0);
  });
}

// ── 재료 승인 (FR-ADMIN-007) ─────────────────────────────────────────────

없는화면이다("/admin/ingredients");

/** RED 2·3 — 승인 버튼은 `admin` 에게만 그려진다. `editor` 는 목록은 보되 못 누른다. */
test("RED2,3 - 승인 버튼이 admin 에게만 있다", () => {
  const page = read("app/admin/ingredients/page.tsx");
  expect(page, "역할과 무관하게 승인 버튼을 그린다").toMatch(
    /role === "admin" \? \(\s*<IngredientApprove/,
  );
});

/** RED 5 — 상한 초과는 경고다. 승인을 막지 않는다 (DECISIONS §1.2 · SPEC-04 §9). */
test("RED5 - 재료 상한은 경고이고 승인을 막지 않는다", () => {
  const page = read("app/admin/ingredients/page.tsx");

  expect(page, "상한 경고가 없다").toMatch(/capacity\?\.warning/);
  // 경고가 차단으로 바뀌면 여기서 걸린다 — 승인 버튼은 상한과 무관하게 그려져야 한다
  expect(page, "경고가 승인을 막는다고 쓰여 있지 않다").toContain("승인을 막지는");
});
