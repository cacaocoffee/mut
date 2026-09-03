import { test, expect } from "@playwright/test";
import { existsSync, readFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";

/**
 * ISSUE-039 — 카테고리 페이지 SSG (`FR-COCKTAIL-029`·`030`·`031` · `R-C-2` · `NFR-S-03`).
 *
 * ## 요체는 축 조합이 **0개**라는 것
 *
 * `PRIN-P06` — "카테고리와 필터는 다른 것이다. 이 구분이 무너지면 **URL 구조와 SEO가 함께
 * 무너진다.**" 당도나 도수를 카테고리로 올리면 `/cocktails/sweet/high-abv/gin/` 같은
 * 조합이 생기고, 중복 콘텐츠로 색인 페널티를 받는다.
 *
 * 막는 방법이 둘이다:
 * - **디렉터리 구조** — `base/[slug]` 아래에 다른 축이 없으면 만들 수가 없다
 * - **사이트맵 검사** — 그래도 생기면 여기 나타난다 (`NFR-S-03` 이 요구한 측정)
 */

const AXES = [
  { axis: "base", slug: "gin", labelKo: "진" },
  { axis: "style", slug: "highball", labelKo: "하이볼" },
  { axis: "method", slug: "stir", labelKo: "스터" },
];

const APP = join(process.cwd(), "app/cocktails");

// ── RED 1~6 : 3축 경로 (FR-COCKTAIL-029) ─────────────────────────────────

for (const { axis, slug, labelKo } of AXES) {
  test(`RED1,2,3 - ${axis} 카테고리가 생성된다`, async ({ page }) => {
    const res = await page.goto(`/cocktails/${axis}/${slug}`);

    expect(res?.status()).toBe(200);
    await expect(page.getByRole("heading", { level: 1 })).toContainText(labelKo);
    await expect(page.locator(".cocktail-card").first()).toBeVisible();
  });
}

/**
 * RED 4 — **3축 외의 카테고리 디렉터리가 없다** (`PRIN-P06`).
 *
 * 당도·도수·향맛은 필터다. 경로로 올리는 순간 조합이 생기고,
 * 그 조합이 `R-C-2` 가 막으려는 바로 그것이다.
 */
test("RED4 - 3축 외의 카테고리 라우트가 없다", () => {
  const dirs = readdirSync(APP).filter((n) => statSync(join(APP, n)).isDirectory());

  // `[slug]` 는 상세다 (이슈 038). `search` 는 필터 화면이고 `noindex` 라
  // 카테고리가 아니다 (이슈 040 · SPEC-05 §4) — 축을 더하지 않으므로 조합도 못 만든다.
  expect(dirs.sort()).toEqual(["[slug]", "base", "method", "search", "style"]);

  for (const forbidden of ["sweet", "abv", "flavor", "sweetness", "aroma"]) {
    expect(existsSync(join(APP, forbidden)), `${forbidden} 은 필터지 카테고리가 아니다`).toBe(false);
  }
});

/** RED 5 — 슬러그가 ADR-0002 확정값이다. 노출되면 URL 이라 못 바꾼다 (`PRIN-D02`). */
test("RED5 - 슬러그가 ADR-0002 확정값이다", async ({ page }) => {
  for (const slug of ["korean", "non-alcoholic", "agave"]) {
    const res = await page.goto(`/cocktails/base/${slug}`);
    expect(res?.status(), `${slug} 가 없다 — ADR-0002 확정값이다`).toBe(200);
  }

  // PRD 5.1 은 `soju` 였다. 막걸리·문배주를 소주로 부르는 것은 부정확해 `korean` 이 됐다.
  expect((await page.goto("/cocktails/base/soju"))?.status()).toBe(404);
});

/** RED 6 — 없는 슬러그는 404 다. 이슈 038 의 soft 404 를 여기서 되풀이하지 않는다. */
test("RED6 - 없는 슬러그는 404 다", async ({ page }) => {
  for (const path of ["/cocktails/base/no-such", "/cocktails/style/no-such", "/cocktails/method/no-such"]) {
    expect((await page.goto(path))?.status(), path).toBe(404);
  }
});

// ── RED 7~10 : 축 조합 0개 (R-C-2 · NFR-S-03) — 요체 ─────────────────────

/**
 * RED 7 — **중첩 동적 라우트가 없다.**
 *
 * `base/[slug]/style/[slug]` 같은 디렉터리가 없으면 조합 경로를 **만들 수가 없다.**
 * 규칙이 아니라 구조로 막는 것이 이 이슈의 요점이다.
 */
test("RED7 - 축 아래에 다른 축 디렉터리가 없다", () => {
  for (const axis of ["base", "style", "method"]) {
    const inside = readdirSync(join(APP, axis, "[slug]"));

    // `[slug]` 안에는 페이지 파일만 있다. 디렉터리가 있으면 그것이 조합의 시작이다.
    const dirs = inside.filter((n) => statSync(join(APP, axis, "[slug]", n)).isDirectory());
    expect(dirs, `${axis}/[slug] 아래에 디렉터리가 생겼다 — 조합 경로의 시작이다`).toEqual([]);
  }
});

/**
 * RED 8 — **사이트맵에 조합 경로가 0개다** (`NFR-S-03` 배포 차단).
 *
 * 디렉터리 구조가 막지만 그것만으로는 **측정**이 안 된다. 사이트맵의 모든 경로가
 * 허용 패턴 중 하나여야 한다는 규칙이 그 측정이다.
 */
test("RED8 - 사이트맵의 모든 경로가 단일 축이다", async ({ request }) => {
  const xml = await (await request.get("/sitemap.xml")).text();
  const paths = [...xml.matchAll(/<loc>([^<]+)<\/loc>/g)].map((m) => new URL(m[1]).pathname);

  expect(paths.length, "사이트맵이 비어 있으면 이 테스트는 아무것도 지키지 않는다").toBeGreaterThan(50);

  const combos = paths.filter((p) => (p.match(/\/(base|style|method)\//g) ?? []).length > 1);
  expect(combos, "NFR-S-03 — 축 조합 경로 0개").toEqual([]);

  // 허용 패턴 밖이 하나라도 있으면 실패한다.
  const patterns = [
    /^\/$/,
    /^\/finder$/,
    /^\/privacy$/,
    /^\/terms$/,
    /^\/cocktails\/[a-z0-9-]+$/,
    /^\/cocktails\/(base|style|method)\/[a-z0-9-]+$/,
    // 재료 사전은 색인한다 — 사람마다 달라지지 않고 "이 재료로 뭘 만드나" 는 실제 질의다.
    /^\/ingredients$/,
    /^\/ingredients\/[a-z0-9-]+$/,
    // 아티클 — 콘텐츠 유입이 존재 이유라 목록·상세 다 색인한다 (ADR-0010).
    /^\/articles$/,
    /^\/articles\/[a-z0-9-]+$/,
  ];
  const unexpected = paths.filter((p) => !patterns.some((re) => re.test(p)));
  expect(unexpected, "색인할 경로 모양이 늘었다 — sitemap.ts 의 ALLOWED_PATTERNS 를 함께 본다").toEqual([]);
});

/** RED 9 — 화면 어디에도 조합 링크가 없다. 상세(이슈 038)와 카테고리 양쪽을 본다. */
test("RED9 - 내부 링크에 조합 경로가 없다", async ({ page }) => {
  // `/` 대신 탐색 경로다 — 이슈 040 이 화면을 옮겼고 `/` 는 그리로 보내기만 한다.
  for (const path of ["/cocktails/negroni", "/cocktails/base/gin", "/cocktails/search"]) {
    await page.goto(path);

    const combos = await page.getByRole("link").evaluateAll((els) =>
      els
        .map((e) => (e as HTMLAnchorElement).getAttribute("href") ?? "")
        .filter((h) => (h.match(/\/(base|style|method)\//g) ?? []).length > 1),
    );
    expect(combos, `${path} 에 조합 링크가 있다`).toEqual([]);
  }
});

/**
 * RED 10 — **조합을 만들 수 있는 함수가 없다.**
 *
 * `cocktailsByAxis(axis, slug)` 가 축을 하나만 받는다. 둘을 받는 자리가 있으면
 * 언젠가 조합 경로가 생긴다 — 이슈 015 가 `RevalidatePaths` 에 쓴 것과 같은 장치다.
 */
test("RED10 - 조합 경로를 만드는 헬퍼가 없다", () => {
  const api = stripComments(readFileSync(join(process.cwd(), "lib/api.ts"), "utf8"));

  expect(api).toMatch(/cocktailsByAxis\(\s*axis: CategoryAxis,\s*slug: string,?\s*\)/);
  expect(api, "축을 둘 받는 자리를 만들지 않는다").not.toMatch(/axes:\s*CategoryAxis\[\]/);
});

// ── RED 11~14 : SSG (NFR-S-01) ───────────────────────────────────────────

test("RED11,12,13,14 - SSG + ISR 이고 요청 시 렌더하지 않는다", () => {
  for (const axis of ["base", "style", "method"]) {
    const source = stripComments(readFileSync(join(APP, axis, "[slug]/page.tsx"), "utf8"));

    expect(source, `${axis}: generateStaticParams 가 없다`).toMatch(
      /export async function generateStaticParams/,
    );
    expect(source, `${axis}: ISR 폴백이 없다`).toMatch(/export const revalidate\s*=\s*\d+/);
    expect(source, `${axis}: 요청 시 렌더로 샌다`).not.toContain("force-dynamic");
    expect(source, `${axis}: 미리 만든 것만 존재해야 한다`).toMatch(
      /export const dynamicParams\s*=\s*false/,
    );
  }

  // RED 12 — 실제로 정적 HTML 이 나왔는가.
  expect(builtHtml("gin.html"), "base/gin 정적 HTML 이 없다").not.toBeNull();
});

// ── RED 16~18 : 색인 (NFR-S-02) ──────────────────────────────────────────

test("RED16,18 - noindex 가 없고 canonical 이 있다", async ({ page }) => {
  await page.goto("/cocktails/base/gin");

  const robots = page.locator('meta[name="robots"]');
  if ((await robots.count()) > 0) {
    expect(await robots.getAttribute("content")).not.toContain("noindex");
  }

  await expect(page.locator('link[rel="canonical"]')).toHaveAttribute(
    "href",
    /\/cocktails\/base\/gin$/,
  );
});

// ── RED 19~22 : 소개 문구 (FR-COCKTAIL-031) ──────────────────────────────

/**
 * RED 21 — 문구가 없어도 페이지는 나온다.
 *
 * `NFR-S-07` 은 "발행 차단" 이라 하고 `FR-COCKTAIL-031` 은 P1 이다. 이슈 022 RED 17 과
 * **같은 판단**을 한다: 구조는 만들되 없어도 페이지는 나온다.
 *
 * 문구는 `category_intro` 에서 오고(이슈 022), API 주소가 없는 지금은 비어 있다.
 * 그래서 여기서 확인하는 것은 **자리가 있는가**다.
 */
test("RED19,21,22 - 소개 문구 자리가 있고 없어도 페이지가 나온다", async ({ page }) => {
  const res = await page.goto("/cocktails/base/gin");
  expect(res?.status()).toBe(200);

  const source = readFileSync(join(process.cwd(), "lib/category-page.tsx"), "utf8");
  expect(source, "문구를 렌더하는 자리가 없다").toContain("category-page__intro");
  expect(source, "문구가 없어도 렌더가 계속돼야 한다").toMatch(/view\.intro\s*(&&|\?\?)/);
});

// ── RED 23~26 : 사이트맵 (NFR-S-04) ──────────────────────────────────────

test("RED23,24,25 - 사이트맵에 상세·카테고리가 있고 필터가 없다", async ({ request }) => {
  const xml = await (await request.get("/sitemap.xml")).text();
  const paths = [...xml.matchAll(/<loc>([^<]+)<\/loc>/g)].map((m) => new URL(m[1]).pathname);

  expect(paths, "RED23 발행 칵테일").toContain("/cocktails/negroni");
  expect(paths, "RED24 카테고리").toContain("/cocktails/base/gin");

  // RED 25 — 필터 결과는 `noindex` 라 사이트맵에 없다 (`NFR-S-02`).
  expect(paths.filter((p) => p.includes("?")), "쿼리스트링 경로가 있다").toEqual([]);
});

// ── RED 28~31 : 목록 렌더 ────────────────────────────────────────────────

/**
 * RED 30 — `style` 카테고리는 **`stylePrimary` 기준**이다 (이슈 022 RED 14 와 정합).
 *
 * 복수 스타일을 다 세면 한 칵테일이 카테고리 여럿에 나오고, 대표가 무엇인지 흐려진다.
 */
test("RED28,30 - style 카테고리가 stylePrimary 기준이다", async ({ page }) => {
  await page.goto("/cocktails/style/spirit-forward");

  const names = await page.locator(".cocktail-card__ko").allTextContents();
  expect(names).toContain("네그로니"); // stylePrimary = spirit-forward
  expect(names.length).toBeGreaterThan(0);
});

/** #175 — 카드 전체가 링크라 제목·설명까지 밑줄로 보이던 것. 탐색 카드와 같은 모양이어야 한다. */
test("카드가 탐색 카드와 같고 글자에 밑줄이 없다 (#175)", async ({ page }) => {
  await page.goto("/cocktails/base/gin");
  const title = page.locator(".cocktail-card__ko").first();
  await expect(title).toBeVisible();
  const decoration = await title.evaluate((el) => getComputedStyle(el).textDecorationLine);
  expect(decoration).toBe("none");
});

/** RED 31 — 항목이 0건인 카테고리는 만들지 않는다. 목록만 있는 빈 페이지는 색인 가치가 없다. */
test("RED31 - 빈 카테고리 페이지가 없다", async ({ request }) => {
  const xml = await (await request.get("/sitemap.xml")).text();
  const categoryPaths = [...xml.matchAll(/<loc>([^<]+)<\/loc>/g)]
    .map((m) => new URL(m[1]).pathname)
    .filter((p) => /^\/cocktails\/(base|style|method)\//.test(p));

  // 코퍼스에 없는 스타일(`frozen` 등)은 사이트맵에 없어야 한다.
  expect(categoryPaths).not.toContain("/cocktails/style/frozen");
  expect(categoryPaths.length).toBeGreaterThan(10);
});

// ── RED 32 : 법적 (NFR-L-01) ─────────────────────────────────────────────

test("RED32 - 과음 경고가 하단에 있다", async ({ page }) => {
  await page.goto("/cocktails/base/gin");
  await expect(page.getByTestId("legal-notice")).toBeVisible();
});

// ── 헬퍼 ──────────────────────────────────────────────────────────────────

/** 규칙은 코드를 보는 것이지 설명을 보는 것이 아니다 (이슈 038 에서 주석에 걸렸다). */
function stripComments(source: string): string {
  return source.replace(/\/\*[\s\S]*?\*\//g, "").replace(/^\s*\/\/.*$/gm, "");
}

function builtHtml(name: string): string | null {
  const roots = [join(process.cwd(), ".next/server/app")];

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
