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

// ── ISSUE-047 : 편집 화면 ─────────────────────────────────────────────────
//
// 어드민은 세션이 없으면 404 라 브라우저로 들어갈 수 없다 (API 가 OAuth 뿐이라 로컬에서
// 세션을 만들 수 없다). 그래서 **원칙이 코드에 남았는지**를 본다 — 화면 동작 확인은
// 세션을 만들 수 있게 되는 날 붙인다.

const FORM = "components/admin/cocktail-form.tsx";

/**
 * RED 1 — 게이트 실패를 **전부 한 번에** 보여 준다 (`FR-ADMIN-003`).
 *
 * 하나씩 고치게 하면 에디터가 발행까지 몇 번을 왕복해야 하는지 모른다. 서버가 `violations`
 * 를 통째로 주므로(SPEC-07 §1.4) 화면은 자르지 않고 전부 그린다.
 */
test("RED1,2,3,4 - 게이트 실패를 자르지 않고 전부 그린다", () => {
  const form = readFileSync(join(process.cwd(), FORM), "utf8");

  expect(form, "violations 를 그리지 않는다").toMatch(/violations\.map\(/);
  expect(form, "목록을 잘라 보여 준다 — 전부 한 번에가 요구다").not.toMatch(
    /violations[\s\S]{0,80}\.slice\(/,
  );
  // 필드·이유·코드 셋 다 (RED 2·3·4). 코드는 문구가 바뀌어도 남는 스펙 ID 다.
  for (const part of ["v.field", "v.message", "v.code"]) {
    expect(form, `${part} 를 보여 주지 않는다`).toContain(part);
  }
});

/** RED 7 · 15 — UI 는 보조다. 저장·발행 판정을 화면이 흉내 내지 않는다 (`PRIN-T05`). */
test("RED7,15 - 게이트를 화면이 복제하지 않는다", () => {
  const form = readFileSync(join(process.cwd(), FORM), "utf8");

  // 서버 게이트 ID 를 화면이 판정에 쓰면 두 벌이 된다. 받은 것을 그리기만 해야 한다.
  expect(form, "GATE- 규칙을 화면이 판정하고 있다").not.toMatch(/if\s*\([^)]*GATE-/);
  expect(form, "발행이 서버를 거치지 않는다").toMatch(/cocktails\/\$\{cocktail!\.id\}\/\$\{action\}/);
});

/** RED 8·9 — 대표 스타일 후보가 고른 스타일로 좁혀진다 (`FR-COCKTAIL-002`). */
test("RED8,9 - 대표 스타일은 고른 것 중에서만", () => {
  const form = readFileSync(join(process.cwd(), FORM), "utf8");

  expect(form, "후보를 전체 목록에서 고르게 한다").toMatch(
    /options=\{Object\.fromEntries\(\s*form\.styles\.map/,
  );
  // 목록에서 빠진 대표는 비운다 — 없는 값을 대표로 둘 수 없다
  expect(form).toMatch(/stylePrimary: next\.includes\(f\.stylePrimary\)/);
});

/** RED 10·11 — 향·맛 1~3개 (`FR-COCKTAIL-008`). */
test("RED10,11 - 향·맛은 1~3개다", () => {
  const form = readFileSync(join(process.cwd(), FORM), "utf8");

  expect(form).toMatch(/FLAVOR_MAX = 3/);
  expect(form, "4개째를 막지 않는다").toMatch(/disabled=\{!on && form\.aromaTags\.length >= FLAVOR_MAX\}/);
  expect(form, "0개인데 저장이 열려 있다").toMatch(/form\.aromaTags\.length > 0/);
});

/** RED 12·13 — 발행 뒤에는 주소가 굳는다 (`FR-COCKTAIL-014`). */
test("RED12,13 - 발행 뒤 slug 입력란이 잠긴다", () => {
  const form = readFileSync(join(process.cwd(), FORM), "utf8");

  expect(form).toMatch(/slugLocked = status !== "draft"/);
  expect(form, "잠그지 않는다").toMatch(/name="slug"[\s\S]{0,120}disabled=\{slugLocked\}/);
});

/** RED 19·20 — 갈 수 있는 곳만 보인다. `draft → archived` 직행은 없다 (DECISIONS §1.4). */
test("RED19,20 - 전이 버튼이 현재 상태에 맞다", () => {
  const form = readFileSync(join(process.cwd(), FORM), "utf8");

  expect(form).toMatch(/status === "draft" &&[\s\S]{0,200}publish/);
  expect(form).toMatch(/status === "published" &&[\s\S]{0,400}archive/);

  // draft 화면에 보관 버튼이 있으면 안 된다 — 초안을 보관하는 것은 지우는 것에 가깝다
  const draftBlock = form.slice(form.indexOf('status === "draft"'), form.indexOf('status === "published"'));
  expect(draftBlock, "초안에서 보관으로 직행하는 버튼이 있다").not.toContain("archive");
});

/**
 * RED 22 — **프리텍스트 재료 입력란을 만들지 않는다** (`PRIN-D01`).
 *
 * 있으면 언젠가 쓰이고, 그 순간 역검색과 바 연결이 무너진다. 재료는 마스터에서 고른다.
 */
test("RED22 - 재료를 손으로 적는 칸이 없다", () => {
  const form = readFileSync(join(process.cwd(), FORM), "utf8");

  for (const forbidden of ["ingredientName", "ingredientText", "customIngredient"]) {
    expect(form, `${forbidden} 입력란이 생겼다 — PRIN-D01`).not.toContain(forbidden);
  }
});

/** 프록시는 상태 코드를 그대로 옮긴다 — 화면이 422·409 로 분기한다 (SPEC-07 §1.4). */
test("어드민 프록시가 상태 코드를 그대로 넘긴다", () => {
  const proxy = readFileSync(join(process.cwd(), "app/api/admin/[...path]/route.ts"), "utf8");

  expect(proxy).toMatch(/status:\s*res\.status/);
  expect(proxy, "쿠키·CSRF 를 넘기지 않는다").toMatch(/"cookie", "x-csrf-token"/);
  expect(proxy, "본문 모양을 바꾸고 있다").not.toMatch(/JSON\.parse\(/);
});
