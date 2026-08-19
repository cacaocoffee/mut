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

// ── 검증 태스크 (FR-ADMIN-004) ───────────────────────────────────────────

없는화면이다("/admin/tasks");

/** RED 8 — 검증 태스크는 `editor` 도 처리한다. 역할로 가리는 곳이 없어야 한다. */
test("RED8 - 검증 태스크에는 역할 분기가 없다", () => {
  const page = read("app/admin/tasks/page.tsx");
  expect(page, "태스크 화면이 admin 전용이 됐다").not.toContain("requireAdminRole");
  expect(page, "역할로 화면을 가린다").not.toMatch(/role === "admin"/);
});

/**
 * RED 11 — 넘길 때는 사유가 필수다 (DECISIONS §1.11).
 *
 * 서버도 거부하지만, 사유 없이 눌리는 버튼을 두면 사람이 서버 오류로 그 규칙을 배우게 된다.
 */
test("RED11 - 사유 없이 넘길 수 없다", () => {
  expect(read("components/admin/task-resolve.tsx"), "사유가 비어도 넘김이 눌린다").toMatch(
    /disabled=\{busy \|\| reason\.trim\(\) === ""\}/,
  );
});

// ── 감사 로그 (FR-ADMIN-005 · SPEC-08 §2.2) ──────────────────────────────

없는화면이다("/admin/audit");

/**
 * RED 14·15·21 — 감사 로그는 `admin` 만이다.
 *
 * 메뉴에서 숨기는 것과 주소로 막는 것이 **둘 다** 있어야 한다. 숨김만 있으면 주소를
 * 치면 들어오고, 막기만 있으면 눌러서 404 를 보게 된다.
 */
test("RED14,15,21 - 감사 로그는 메뉴에서 숨고 주소로도 막힌다", () => {
  const nav = read("components/admin/admin-nav.tsx");
  expect(nav, "감사 로그 메뉴에 역할 조건이 없다").toMatch(
    /href: "\/admin\/audit"[^}]*adminOnly: true/,
  );
  expect(nav, "역할로 거르지 않는다").toMatch(/adminOnly \|\| role === "admin"/);

  expect(read("app/admin/audit/page.tsx"), "감사 화면이 역할을 확인하지 않는다").toMatch(
    /requireAdminRole\(\)/,
  );
  expect(read("lib/admin-session.ts"), "admin 이 아니면 404 가 아니다").toMatch(
    /role !== "admin"\) notFound\(\)/,
  );
});

/**
 * RED 19 — 감사 로그에는 **고치는 길이 없다** (`PRIN-T08`).
 *
 * API 에 경로가 없고 DB 권한도 없지만, 화면에 버튼이 생기면 누군가 그 경로를 만든다.
 * 쓰기 호출이 아예 없는지를 본다.
 */
test("RED19 - 감사 로그에 수정·삭제가 없다", () => {
  const page = read("app/admin/audit/page.tsx");

  expect(page, "감사 화면에서 쓰기를 부른다").not.toMatch(/method:\s*"(POST|PATCH|PUT|DELETE)"/);
  for (const word of ["수정", "삭제", "지우기"]) {
    expect(page, `감사 화면에 "${word}" 조작이 있다`).not.toMatch(
      new RegExp(`<button[^>]*>[^<]*${word}`),
    );
  }
});
