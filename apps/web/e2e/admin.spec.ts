import { test, expect } from "@playwright/test";
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";

/**
 * ISSUE-045 — 어드민 셸 (`FR-ADMIN-001`·`FR-ADMIN-006` · `PRIN-P02` · SPEC-05 §1·§4).
 *
 * ## 두 가지만 본다
 *
 * **못 들어오는 사람은 404** 이고, **노출 규칙 입력란은 존재하지 않는다.** 둘 다 원칙이
 * 코드에 남는 자리라 테스트가 지킨다 — 나머지(편집·승인 화면)는 이슈 047·048 이다.
 */

const ADMIN = "/admin";

/**
 * **만들면 안 되는 입력란** (`FR-ADMIN-006` · `PRIN-P02`).
 *
 * > 영업 편의로 조정할 수 있게 만들면 **반드시 조정된다.** 어드민에 수치 입력란을 두는
 * > 순간 그 수치는 올라간다.
 *
 * 이 목록을 늘리는 것은 자유지만 **줄이려면** SPEC-00 개정과 ADR 이 필요하다 (SPEC-00 §4).
 * 이름을 바꿔 우회하는 것도 같은 위반이라, 화면 글자에서도 찾는다.
 */
const FORBIDDEN_FIELDS = ["boostLimit", "homeSlotRatio", "sponsorLabelVisible"];

const FORBIDDEN_LABELS = ["부스팅", "슬롯 비율", "노출 가중치", "제휴 라벨 표시"];

// ── 접근 (SPEC-05 §4 · SCREENS-00 §3.4) ──────────────────────────────────

/**
 * RED 1·2 — 자격이 없으면 **404** 다.
 *
 * "권한 없음" 을 보여 주지 않는다 (SCREENS-00 §3.4) — 어드민이 있다는 사실 자체가 정보다.
 * 이 테스트는 로그인하지 않은 상태로 돈다.
 */
test("RED1,2 - 비로그인은 어드민을 볼 수 없다", async ({ page }) => {
  const res = await page.goto(ADMIN);

  expect(res?.status(), "권한 없는 사람에게 200 을 줬다").toBe(404);
  await expect(page.getByText("어드민 ADMIN")).toHaveCount(0);
});

test("RED6 - 어드민은 색인 대상이 아니다", async ({ request }) => {
  const xml = await (await request.get("/sitemap.xml")).text();
  expect(xml, "사이트맵에 어드민이 있다").not.toContain("/admin");

  const source = readFileSync(join(process.cwd(), "app/admin/layout.tsx"), "utf8");
  expect(source, "robots 설정이 없다").toMatch(/robots:\s*\{\s*index:\s*false/);
});

/** RED 5 — 세션을 봐야 하므로 요청마다 그린다. 미리 그리면 남의 화면을 보여 준다. */
test("RED5 - 미리 그려 두지 않는다", () => {
  const source = readFileSync(join(process.cwd(), "app/admin/layout.tsx"), "utf8");
  expect(source).toMatch(/export const dynamic = "force-dynamic"/);
});

/** RED 7 — 세션은 `httpOnly` 라 서버가 쿠키를 옮기는 수밖에 없다 (SPEC-07 §1.2). */
test("RED7 - 쿠키를 API 로 전달한다", () => {
  for (const rel of ["middleware.ts", "lib/admin-session.ts"]) {
    const source = readFileSync(join(process.cwd(), rel), "utf8");
    expect(source, `${rel} 이 쿠키를 넘기지 않고 부른다`).toMatch(/headers:\s*\{\s*cookie\s*\}/);
  }
});

/**
 * 상태 코드가 **200 이 아니어야** 한다.
 *
 * 레이아웃·페이지에서 `notFound()` 를 불러도 응답은 `200` 인 채 본문만 바뀐다 —
 * 스트리밍이 시작된 뒤라 되돌릴 수 없다. 미들웨어가 렌더 전에 끝내는 이유이고,
 * 이 검사가 그것이 유지되는지를 본다.
 */
test("어드민 판정이 렌더 전에 끝난다", () => {
  const middleware = readFileSync(join(process.cwd(), "middleware.ts"), "utf8");
  expect(middleware, "어드민만 보지 않는다").toMatch(/matcher:\s*"\/admin/);
  expect(middleware, "404 를 상태로 주지 않는다").toMatch(/status:\s*404/);
});

// ── 노출 규칙 부재 (FR-ADMIN-006 · PRIN-P02) — 이슈 027 의 프론트판 ────────

/**
 * RED 10~15 — **입력란이 없다.**
 *
 * 백엔드(이슈 027)가 API 에 그 필드가 없음을 지키고, 여기서는 **화면에 그 입력란이
 * 생기지 않음**을 지킨다. 둘 중 하나만 있으면 다른 쪽으로 문이 열린다.
 */
test("RED10~15 - 노출 규칙 입력란이 어드민 어디에도 없다", () => {
  const root = join(process.cwd(), "app/admin");
  const components = join(process.cwd(), "components/admin");

  const files: string[] = [];
  const walk = (dir: string) => {
    for (const name of readdirSync(dir)) {
      const abs = join(dir, name);
      if (statSync(abs).isDirectory()) walk(abs);
      else if (/\.tsx?$/.test(name)) files.push(abs);
    }
  };
  walk(root);
  walk(components);

  expect(files.length, "어드민 파일을 못 찾았다 — 검사가 헛돈다").toBeGreaterThan(2);

  for (const file of files) {
    const source = readFileSync(file, "utf8");
    // 주석에서 "만들지 않는다" 고 적는 것은 허용한다. `<input>`·`name=` 이 문제다.
    const inputs = source.match(/<input[^>]*>|name=["'][^"']+["']/g) ?? [];

    for (const field of FORBIDDEN_FIELDS) {
      expect(
        inputs.join(" "),
        `${file}: ${field} 입력란이 생겼다 — PRIN-P02 는 하드 제약이다`,
      ).not.toContain(field);
    }
  }
});

test("RED12,13 - 화면에도 노출 규칙 조정 자리가 없다", async ({ page }) => {
  // 접근이 막혀 404 라 화면을 볼 수 없다. 그래서 **소스에서** 본다 —
  // 로그인한 어드민 화면은 이슈 047·048 이 붙을 때 함께 본다.
  const dashboard = readFileSync(join(process.cwd(), "app/admin/page.tsx"), "utf8");

  for (const label of FORBIDDEN_LABELS) {
    const context = dashboard.split("\n").filter((l) => l.includes(label));
    for (const line of context) {
      expect(
        /만들지 않는다|바꿀 수 없다|못 하는 것|없다/.test(line) || line.trim().startsWith("*"),
        `대시보드에 "${label}" 조정 자리가 생겼다`,
      ).toBe(true);
    }
  }

  // 어드민 밖(공개 화면)에도 그런 입력란이 없다
  await page.goto("/cocktails/search");
  for (const field of FORBIDDEN_FIELDS) {
    await expect(page.locator(`[name="${field}"]`)).toHaveCount(0);
  }
});

// ── 셸 (SPEC-05 §1 · CONVENTIONS §4) ─────────────────────────────────────

/** RED 20 — 같은 Next.js 앱의 라우트다. 나누면 토큰과 세션이 두 벌이 된다. */
test("RED20 - 별도 앱이 아니라 같은 앱의 라우트다", () => {
  const layout = readFileSync(join(process.cwd(), "app/admin/layout.tsx"), "utf8");
  expect(layout, "루트 레이아웃을 다시 만들고 있다").not.toMatch(/<html|<body/);
});

/** RED 18·19 — 어드민만의 색을 만들지 않는다. 토큰을 쓴다. */
test("RED18,19 - packages/ui 토큰을 쓰고 시안을 고치지 않았다", () => {
  const css = readFileSync(join(process.cwd(), "../../packages/ui/app.css"), "utf8");
  const adminBlock = css.slice(css.indexOf("── 어드민 (ISSUE-045)"));

  expect(adminBlock.length, "어드민 스타일이 없다").toBeGreaterThan(100);
  // 색을 직접 적지 않고 토큰으로만 쓴다
  expect(adminBlock, "hex 를 직접 적었다").not.toMatch(/#[0-9a-fA-F]{3,6}\b/);
});
