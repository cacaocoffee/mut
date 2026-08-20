import { test, expect, type APIRequestContext } from "@playwright/test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

/**
 * ISSUE-044 — Schema.org `Recipe` + OG 태그
 * (`FR-COCKTAIL-026` · `FR-USER-005` · `R-F1.1-6`·`R-F5-5` · `NFR-S-05`·`S-06`).
 *
 * ## 크롤러가 보는 것을 본다
 *
 * 구조화 데이터와 OG 는 **브라우저가 아니라 크롤러**를 위한 것이라, 렌더된 DOM 이 아니라
 * 서버가 처음 보낸 HTML 을 그대로 받아서 읽는다 (RED 2). 자바스크립트가 붙은 뒤에 생기는
 * 태그는 카카오톡·구글이 못 본다.
 *
 * ## 여기서 재지 않는 것
 *
 * **리치 결과 테스트(RED 11)와 카카오톡 카드 확인(RED 19)은 수동이다** — 구글·카카오의
 * 도구를 사람이 돌린다 (SPEC-04 §9.2 릴리즈 체크리스트 · 이슈 050). 여기서는 그 도구가
 * 요구하는 필드가 다 있는지, 금지한 것이 없는지까지 본다.
 */

const DETAIL = "/cocktails/negroni";

/** 서버가 보낸 원본 HTML. 렌더 결과가 아니라 첫 응답이다. */
async function html(request: APIRequestContext, path: string): Promise<string> {
  const res = await request.get(path);
  expect(res.status(), `${path} 가 200 이 아니다`).toBe(200);
  return res.text();
}

function metaOf(source: string, property: string): string | null {
  const pattern = new RegExp(
    `<meta[^>]+(?:property|name)="${property}"[^>]*content="([^"]*)"|` +
      `<meta[^>]+content="([^"]*)"[^>]*(?:property|name)="${property}"`,
  );
  const m = source.match(pattern);
  return m ? (m[1] ?? m[2]) : null;
}

function jsonLdOf(source: string): Record<string, unknown> | null {
  const m = source.match(
    /<script[^>]+type="application\/ld\+json"[^>]*>([\s\S]*?)<\/script>/,
  );
  return m ? (JSON.parse(m[1]) as Record<string, unknown>) : null;
}

// ── RED 1~12 : Schema.org (FR-COCKTAIL-026 · NFR-S-05) ───────────────────

test("RED1,2,10 - 초기 HTML 에 유효한 Recipe JSON-LD 가 있다", async ({ request }) => {
  const source = await html(request, DETAIL);

  const ld = jsonLdOf(source); // 파싱이 실패하면 여기서 던진다 (RED 10)
  expect(ld, "구조화 데이터가 초기 HTML 에 없다").not.toBeNull();
  expect(ld!["@context"]).toBe("https://schema.org");
  expect(ld!["@type"]).toBe("Recipe");
});

test("RED3~7 - 리치 결과가 요구하는 필드가 있다", async ({ request }) => {
  const ld = jsonLdOf(await html(request, DETAIL))!;

  expect(ld.name, "RED3").toContain("네그로니");
  expect(String(ld.description).length, "RED4 — 설명이 비었다").toBeGreaterThan(10);

  const ingredients = ld.recipeIngredient as string[];
  expect(ingredients.length, "RED5").toBeGreaterThan(2);
  // 용량과 이름이 한 줄에 있어야 한다 — 구글이 그 모양을 읽는다
  expect(ingredients.join(" ")).toMatch(/\d+\s?ml/);
  expect(ingredients.join(" ")).toContain("진");

  const steps = ld.recipeInstructions as { "@type": string; text: string }[];
  expect(steps.length, "RED6").toBeGreaterThan(1);
  expect(steps[0]["@type"]).toBe("HowToStep");

  // RED 7 — 이미지는 절대 주소여야 한다. 상대 경로는 크롤러가 못 따라온다.
  const images = ld.image as string[];
  expect(images[0], "RED7").toMatch(/^https?:\/\/.+\/cocktails\/negroni\/opengraph-image$/);

  expect(ld.recipeYield).toBe("1잔");
});

/**
 * RED 8·9 — **별점을 쌓지 않는다** (`PRIN-P04`).
 *
 * 리치 결과에서 별이 뜨는 자리라 넣고 싶어지는 지점이고, 그래서 원칙이 먼저 금지했다.
 * 총점은 취향을 하나의 수로 눌러 담고, 없는 값을 지어내 채우는 것은 구글 정책상 조작이다.
 */
test("RED8,9 - aggregateRating 도 review 도 없다", async ({ request }) => {
  const source = await html(request, DETAIL);
  const ld = jsonLdOf(source)!;

  expect(ld.aggregateRating, "PRIN-P04 — 별점을 쌓지 않는다").toBeUndefined();
  expect(ld.review, "PRIN-P04 — 후기를 구조화 데이터에 넣지 않는다").toBeUndefined();
  expect(source).not.toContain("aggregateRating");
  expect(source).not.toContain('"@type":"Review"');
});

test("RED12 - 없는 칵테일에는 구조화 데이터가 없다", async ({ request }) => {
  const res = await request.get("/cocktails/no-such-cocktail");
  expect(res.status()).toBe(404);
  expect(jsonLdOf(await res.text()), "404 에 Recipe 가 붙었다").toBeNull();
});

// ── RED 13~21 : OG 태그 (FR-USER-005 · NFR-S-06) ─────────────────────────

test("RED13~18 - 상세에 OG 와 트위터 카드가 있다", async ({ request }) => {
  const source = await html(request, DETAIL);

  expect(metaOf(source, "og:title"), "RED13").toContain("네그로니");
  expect(metaOf(source, "og:description")?.length ?? 0, "RED14").toBeGreaterThan(10);
  expect(metaOf(source, "og:type"), "RED17").toBe("article");

  // RED 15·16 — 이미지와 주소는 **절대 주소**다. 카카오톡이 상대 경로를 못 따라온다.
  expect(metaOf(source, "og:image"), "RED15").toMatch(/^https?:\/\//);
  expect(metaOf(source, "og:url"), "RED16").toMatch(/^https?:\/\/.+\/cocktails\/negroni$/);

  // RED 18 — 카카오만 적혀 있지만 슬랙·디스코드가 이 태그를 먼저 본다
  expect(metaOf(source, "twitter:card"), "RED18").toBe("summary_large_image");
});

/**
 * RED 20 — **모든 공개 페이지**에 OG 가 있다 (`NFR-S-06`).
 *
 * 페이지마다 적으면 언젠가 하나를 빠뜨리고, 빠뜨린 것은 공유해 보기 전까지 아무도 모른다.
 * 루트 레이아웃이 깔고 개별 페이지가 제목만 덮는 구조라 여기서 전수로 확인한다.
 */
test("RED20 - 공개 페이지 전부에 OG 가 있다", async ({ request }) => {
  const paths = [
    "/cocktails/search",
    "/finder",
    "/search",
    "/cocktails/base/gin",
    "/cocktails/style/sour",
    "/cocktails/method/stir",
    "/cocktails/negroni",
    "/privacy",
    "/terms",
  ];

  for (const path of paths) {
    const source = await html(request, path);
    expect(metaOf(source, "og:title"), `${path} 에 og:title 이 없다`).not.toBeNull();
    expect(metaOf(source, "og:image"), `${path} 에 og:image 가 없다`).toMatch(/^https?:\/\//);
    expect(metaOf(source, "og:site_name"), `${path} 에 사이트 이름이 없다`).toBe(
      "MUT",
    );
  }
});

// ── RED 21~24 : OG 이미지 ────────────────────────────────────────────────

test("RED21,22 - 칵테일마다 1200×630 PNG 카드가 나온다", async ({ request }) => {
  const res = await request.get("/cocktails/negroni/opengraph-image");

  expect(res.status()).toBe(200);
  expect(res.headers()["content-type"]).toContain("image/png");

  const body = await res.body();
  // PNG 헤더의 IHDR 에서 가로·세로를 읽는다 (16~24바이트)
  expect(body.readUInt32BE(16), "가로").toBe(1200);
  expect(body.readUInt32BE(20), "세로").toBe(630);

  // 칵테일마다 다른 그림이어야 한다 — 같은 바이트면 이름이 안 들어간 것이다
  const other = await (await request.get("/cocktails/martini/opengraph-image")).body();
  expect(body.equals(other), "두 칵테일의 카드가 같다").toBe(false);
});

test("RED23 - 없는 슬러그에도 카드가 깨지지 않는다", async ({ request }) => {
  const res = await request.get("/cocktails/no-such-cocktail/opengraph-image");

  expect(res.status()).toBe(200);
  expect(res.headers()["content-type"]).toContain("image/png");
  expect((await res.body()).readUInt32BE(16)).toBe(1200);
});

test("RED20 - 상세가 아닌 화면도 기본 카드를 갖는다", async ({ request }) => {
  const res = await request.get("/opengraph-image");
  expect(res.status()).toBe(200);
  expect(res.headers()["content-type"]).toContain("image/png");
});

/** 카드 색이 팔레트 정본에서 왔는지 — Satori 가 CSS 변수를 못 읽어 값을 적어 둔 자리다. */
test("카드 색이 팔레트 정본과 같다", () => {
  const baseline = readFileSync(join(process.cwd(), "../../scripts/color-parity.mjs"), "utf8");
  const card = readFileSync(join(process.cwd(), "lib/og-card.tsx"), "utf8");

  // 매거진판 (ADR-0007). 팔레트를 바꾸면 BASELINE · og-card 와 함께 이 표를 고친다.
  for (const [token, hex] of [
    ["--color-bg", "#f6f3eb"],
    ["--color-text", "#241e18"],
    ["--color-accent", "#93293b"],
    ["--color-neutral-700", "#605e58"],
  ]) {
    expect(baseline, `${token} 이 팔레트 정본에서 바뀌었다`).toContain(`"${token}": "${hex}"`);
    expect(card, `카드가 ${token} 값을 안 쓴다`).toContain(hex);
  }
});

// ── RED 26·27 : 성능 ────────────────────────────────────────────────────

/**
 * RED 26·27 — 구조화 데이터와 카드가 화면 로딩을 막지 않는다.
 *
 * JSON-LD 는 HTML 안에 있어 요청이 늘지 않고, OG 이미지는 **화면이 부르지 않는다** —
 * 크롤러가 메타 태그를 보고 따로 가져간다. 사람이 여는 페이지에서 1200×630 PNG 를
 * 내려받으면 그만큼 느려진다.
 */
test("RED26,27 - 페이지가 OG 이미지를 내려받지 않는다", async ({ page }) => {
  const fetched: string[] = [];
  page.on("request", (r) => {
    if (r.url().includes("opengraph-image")) fetched.push(r.url());
  });

  await page.goto(DETAIL);
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();

  expect(fetched, "브라우저가 공유 카드를 받고 있다").toEqual([]);
  // 구조화 데이터는 인라인이라 요청이 없다
  await expect(page.locator('script[type="application/ld+json"]')).toHaveCount(1);
});
