import { test, expect } from "@playwright/test";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { existsSync } from "node:fs";
import { join } from "node:path";

/**
 * ISSUE-038 — 칵테일 상세 SSG+ISR (`PRIN-T04` · `NFR-S-01` · `FR-COCKTAIL-017`·`018`·`027`).
 *
 * ## SSG 를 고른 이유의 절반은 `NFR-R-01` 이다
 *
 * "API 가 죽어도 정적 페이지는 살아 있다." 빌드 시점에 HTML 이 만들어져 있어야 그 말이
 * 성립하므로, **브라우저가 아니라 빌드 산출물을 직접 읽어** 확인한다 —
 * 브라우저로 보면 JS 가 이미 돌아 있어 정적인지 아닌지 구별되지 않는다.
 */

const SLUG = "negroni";
const DETAIL = `/cocktails/${SLUG}`;

// ── RED 1~4 : 렌더링 전략 (NFR-S-01 · SPEC-05 §4) ─────────────────────────

/**
 * RED 1·2 — **정적 HTML 이 실제로 만들어졌다.**
 *
 * `generateStaticParams` 가 돌지 않으면 페이지가 요청 때 렌더된다. 그것이
 * `PRIN-T04` 가 막으려는 상태다 — 크롤러가 볼 것이 없고 TTFB 가 늘어난다.
 */
test("RED1,2 - 상세가 SSG 로 생성된다", () => {
  const html = builtHtml(`${SLUG}.html`);

  expect(html, ".next 산출물에 정적 HTML 이 없다 — SSG 가 돌지 않았다").not.toBeNull();
  expect(html!).toContain("네그로니");
});

/**
 * RED 3·4 — ISR 설정이 있고 **요청 시 렌더로 새지 않는다.**
 *
 * `dynamic = "force-dynamic"` 한 줄이면 SSG 가 통째로 무효가 되는데,
 * 빌드는 성공하고 아무도 모른다. 소스를 읽어 그 줄이 없음을 고정한다.
 */
test("RED3,4 - ISR 이 설정되고 force-dynamic 이 없다", () => {
  // **주석을 걷어내고 본다.** 처음에는 원문을 훑었다가 "force-dynamic 을 쓰지 않는다" 라고
  // 적어 둔 내 주석에 걸렸다 — 규칙이 산문에 걸리면 다음 사람은 주석을 지워서 통과시킨다.
  const source = stripComments(readFileSync(pageSource(), "utf8"));

  expect(source, "ISR 폴백이 없다").toMatch(/export const revalidate\s*=\s*\d+/);
  expect(source, "PRIN-T04 — 요청 시 렌더하지 않는다").not.toContain("force-dynamic");
  expect(source, "미리 만든 것만 존재한다 — 없으면 soft 404 가 된다").toMatch(
    /export const dynamicParams\s*=\s*false/,
  );
  expect(source).toMatch(/export async function generateStaticParams/);
});

// ── RED 7~10 : 경로 (SPEC-07 §1.1) ────────────────────────────────────────

/** RED 7·8 — 공개 식별자는 `slug` 다 (`PRIN-D02`). `[id]` 는 없어졌다. */
test("RED7,8 - 경로가 slug 기반이고 옛 경로가 없다", async ({ page }) => {
  expect(existsSync(join(process.cwd(), "app/cocktails/[slug]/page.tsx"))).toBe(true);
  expect(existsSync(join(process.cwd(), "app/cocktails/[id]"))).toBe(false);

  expect((await page.goto(DETAIL))?.status()).toBe(200);
});

/** RED 9·10 — 없는 것도 `draft` 도 404 다. 공개 API 가 발행분만 주므로 같은 결과다. */
test("RED9,10 - 없는 slug 는 404 다", async ({ page }) => {
  expect((await page.goto("/cocktails/no-such-cocktail"))?.status()).toBe(404);
});

// ── RED 11~13 : 필수 블록 (FR-COCKTAIL-017) ───────────────────────────────

/**
 * RED 11·12·13 — 블록이 **초기 HTML 에** 있다.
 *
 * 크롤러가 JS 를 돌리지 않고도 본문을 봐야 한다 (`PRIN-T04`).
 * 빌드 산출물을 읽는 이유가 그것이다.
 */
test("RED11,12,13 - 필수 블록이 초기 HTML 에 있다", () => {
  const html = builtHtml(`${SLUG}.html`)!;

  const blocks = [
    ["히어로", "네그로니"],
    ["스펙", "GLASSWARE"],
    ["재료", "재료 INGREDIENTS"],
    ["만드는 법", "제조 순서 METHOD"],
    ["향과 맛", "향과 맛 TASTING"],
    ["기록", "기록 ORIGIN"],
    ["액션", "저장 SAVE"],
  ];

  for (const [name, needle] of blocks) {
    expect(html, `${name} 블록이 초기 HTML 에 없다`).toContain(needle);
  }
});

// ── RED 14~17 : 카테고리 링크 (FR-COCKTAIL-018 · R-C-2) ───────────────────

/**
 * RED 14·15·16 — 3축 각각이 카테고리로 간다.
 *
 * RED 17 이 짝이다 — **축 조합 링크를 만들지 않는다** (`NFR-S-03`).
 * `/cocktails/base/gin/style/sour` 같은 경로가 생기면 색인 대상이 곱으로 늘고,
 * 그중 대부분은 결과가 0건이다.
 */
test("RED14,15,16,17 - 3축이 링크되고 축 조합 링크가 없다", async ({ page }) => {
  await page.goto(DETAIL);

  const taxa = page.getByRole("navigation", { name: "분류" });
  // `evaluateAll` 은 기다리지 않는다 — 링크가 그려진 것을 먼저 확인한다.
  await expect(taxa.getByRole("link").first()).toBeVisible();

  const hrefs = await taxa.getByRole("link").evaluateAll((els) =>
    els.map((e) => (e as HTMLAnchorElement).getAttribute("href") ?? ""),
  );

  expect(hrefs).toContain("/cocktails/base/gin");
  expect(hrefs.some((h) => h.startsWith("/cocktails/style/"))).toBe(true);
  expect(hrefs.some((h) => h.startsWith("/cocktails/method/"))).toBe(true);

  // 축이 둘 이상 겹친 경로가 없다.
  const combos = await page.getByRole("link").evaluateAll((els) =>
    els
      .map((e) => (e as HTMLAnchorElement).getAttribute("href") ?? "")
      .filter((h) => (h.match(/\/(base|style|method)\//g) ?? []).length > 1),
  );
  expect(combos, "R-C-2 · NFR-S-03 — 축 조합은 색인 대상이 아니다").toEqual([]);
});

// ── RED 18~24 : 재생성 훅 (SPEC-07 §4) ────────────────────────────────────

/**
 * RED 19·20 — **시크릿이 틀리면 401.**
 *
 * 시크릿이 설정되지 않은 환경에서는 503 이다 — 열어 두면 누구나 재생성을 부를 수 있어
 * 그쪽이 더 나쁘다. 둘 다 "통과하지 않는다" 는 같다.
 */
test("RED18,19,20 - 재생성 훅이 시크릿을 검증한다", async ({ request }) => {
  const wrong = await request.post("/api/revalidate", {
    // HTTP 헤더 값은 ASCII 여야 한다. 한글을 넣으면 클라이언트가 먼저 거부한다.
    headers: { "X-Revalidate-Secret": "wrong-secret" },
    data: { paths: [DETAIL] },
  });

  expect([401, 503]).toContain(wrong.status());
});

/** RED 21 — `paths` 가 배열이 아니면 400. 조용히 통과시키면 재생성이 안 된 줄 모른다. */
test("RED21 - paths 가 배열이 아니면 400 이다", async ({ request }) => {
  const secret = process.env.REVALIDATE_SECRET;
  test.skip(!secret, "REVALIDATE_SECRET 이 없으면 인증을 통과할 수 없다");

  const res = await request.post("/api/revalidate", {
    headers: { "X-Revalidate-Secret": secret! },
    data: { paths: "문자열" },
  });

  expect(res.status()).toBe(400);
});

/**
 * RED 24 — **시크릿이 클라이언트 번들에 없다.**
 *
 * `NEXT_PUBLIC_` 접두를 붙이면 번들에 그대로 실려 나간다. 소스에 그 이름이
 * 없다는 것을 고정한다 — 실제 값은 환경마다 다르니 이름으로 본다.
 */
test("RED24 - 시크릿이 공개 환경변수가 아니다", () => {
  const source = readFileSync(join(process.cwd(), "app/api/revalidate/route.ts"), "utf8");

  expect(source).toContain("process.env.REVALIDATE_SECRET");
  expect(source, "NEXT_PUBLIC_ 을 붙이면 번들에 들어간다").not.toContain(
    "NEXT_PUBLIC_REVALIDATE",
  );
});

// ── RED 25~27 : API 장애 격리 (NFR-R-01) ──────────────────────────────────

/**
 * RED 25·26 — **API 없이도 페이지가 서빙된다.**
 *
 * 이 테스트가 도는 동안 API 는 떠 있지 않다. 그런데도 상세가 200 이면
 * 런타임에 API 를 부르지 않는다는 뜻이다 — 그것이 `NFR-R-01` 이 요구하는 상태다.
 */
test("RED25,26 - API 없이 정적 페이지가 서빙된다", async ({ page }) => {
  const failed: string[] = [];
  page.on("requestfailed", (r) => failed.push(r.url()));

  const res = await page.goto(DETAIL);

  expect(res?.status()).toBe(200);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("네그로니");
  expect(failed.filter((u) => u.includes("/api/v1/")), "런타임에 API 를 부르지 않는다").toEqual([]);
});

// ── RED 30~31 : 색인 ──────────────────────────────────────────────────────

test("RED30,31 - noindex 가 없고 canonical 이 있다", async ({ page }) => {
  await page.goto(DETAIL);

  // 없는 요소에 `getAttribute` 를 걸면 나타날 때까지 기다리다 시간이 끝난다. 개수를 먼저 본다.
  const robots = page.locator('meta[name="robots"]');
  if ((await robots.count()) > 0) {
    expect(await robots.getAttribute("content")).not.toContain("noindex");
  }

  await expect(page.locator('link[rel="canonical"]')).toHaveAttribute(
    "href",
    new RegExp(`${DETAIL}$`),
  );
});

// ── RED 32~35 : 액션 블록 (FR-COCKTAIL-027) ───────────────────────────────

/**
 * RED 32·34·35 — 저장과 공유가 있고, **내 술장 대조는 없다.**
 *
 * 대조는 P2 다 (`FR-COCKTAIL-027` 의 괄호). 자리만 만들어 두면 눌렀을 때
 * 아무 일도 안 일어나는 버튼이 남는다.
 */
test("RED32,34,35 - 저장·공유가 있고 술장 대조는 없다", async ({ page }) => {
  await page.goto(DETAIL);

  await expect(page.getByRole("button", { name: /저장/ })).toBeVisible();
  await expect(page.getByRole("button", { name: /공유/ })).toBeVisible();
  await expect(page.getByText(/내 술장|재료 대조/)).toHaveCount(0);
});

/** RED 33 — 비로그인 저장은 막지 않고 **유도한다** (`R-F2.2-4` 의 정신). */
test("RED33 - 비로그인 저장이 로그인을 유도한다", async ({ page }) => {
  await page.route("**/api/v1/me/bookmarks", (route) => route.fulfill({ status: 401 }));
  await page.goto(DETAIL);

  await page.getByRole("button", { name: /저장/ }).click();

  await expect(page.getByRole("status")).toContainText("로그인");
});

// ── RED 38 : 법적 고지 (NFR-L-01) ─────────────────────────────────────────

test("RED38 - 과음 경고가 하단에 있다", async ({ page }) => {
  await page.goto(DETAIL);
  await expect(page.getByTestId("legal-notice")).toBeVisible();
});

// ── 헬퍼 ──────────────────────────────────────────────────────────────────

/** 블록·줄 주석을 지운다. 규칙은 코드를 보는 것이지 설명을 보는 것이 아니다. */
function stripComments(source: string): string {
  return source.replace(/\/\*[\s\S]*?\*\//g, "").replace(/^\s*\/\/.*$/gm, "");
}

function pageSource(): string {
  return join(process.cwd(), "app/cocktails/[slug]/page.tsx");
}

/**
 * 빌드 산출물에서 HTML 을 찾는다.
 *
 * 못 찾으면 `null` 이고 부르는 쪽이 실패시킨다 — Next 버전마다 경로가 달라
 * 하나로 못박으면 업그레이드 때 **조용히** 통과해 버린다.
 */
function builtHtml(name: string): string | null {
  const roots = [join(process.cwd(), ".next/server/app"), join(process.cwd(), ".next/server/pages")];

  for (const root of roots) {
    const found = find(root, name);
    if (found) return readFileSync(found, "utf8");
  }
  return null;
}

function find(dir: string, name: string): string | null {
  try {
    if (!statSync(dir).isDirectory()) return null;
  } catch {
    return null;
  }

  const direct = join(dir, name);
  try {
    if (statSync(direct).isFile()) return direct;
  } catch {
    /* 계속 찾는다 */
  }

  for (const entry of readdirSync(dir)) {
    const child = join(dir, entry);
    try {
      if (statSync(child).isDirectory()) {
        const found = find(child, name);
        if (found) return found;
      }
    } catch {
      /* 무시 */
    }
  }
  return null;
}
