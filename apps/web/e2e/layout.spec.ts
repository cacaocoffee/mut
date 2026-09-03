import { test, expect, type Page } from "@playwright/test";

/**
 * 레이아웃·대비 회귀 (ISSUE-056 #80).
 *
 * 여기 있는 것은 전부 **한 번 실제로 깨졌던 것**이다. 취향으로 규칙을 추가하지 않는다.
 *   - 가로 넘침 · 두 줄 탭 레이블 → ISSUE-051 (#69)
 *   - 선택된 탭 비가시        → ISSUE-055 (#78)
 */

const WIDTHS = [320, 375, 414, 768];
const PAGES = [
  { path: "/", name: "탐색" },
  { path: "/cocktails/negroni", name: "상세" },
  { path: "/finder", name: "파인더" },
];

/**
 * 진짜 가로 넘침을 잰다.
 *
 * `html { overflow-x: clip }` 이 걸려 있으면 `scrollWidth` 가 뷰포트로 잘려서 넘침이 안 보인다.
 * 그게 이 프로젝트에서 320px 내비 파손이 오래 산 이유다 — 결함을 숨기는 규칙이 있었다.
 * 그래서 **재는 동안만 clip 을 풀고** 원래대로 되돌린다.
 */
async function overflowPx(page: Page): Promise<number> {
  return page.evaluate(() => {
    const html = document.documentElement;
    const body = document.body;
    const saved = [html.style.overflowX, body.style.overflowX] as const;
    html.style.overflowX = "visible";
    body.style.overflowX = "visible";
    // 강제 리플로
    void html.offsetWidth;
    const over = Math.max(0, html.scrollWidth - html.clientWidth);
    html.style.overflowX = saved[0];
    body.style.overflowX = saved[1];
    return over;
  });
}

/** 캐스케이드가 끝난 뒤의 실제 배경색을 찾는다 — 투명하면 위로 올라간다. */
async function contrastOf(page: Page, selector: string): Promise<number> {
  return page.evaluate((sel) => {
    const el = document.querySelector(sel);
    if (!el) throw new Error(`셀렉터를 찾지 못했다: ${sel}`);

    /**
     * 캔버스로 sRGB 로 환산한다.
     *
     * 숫자만 뽑아 R,G,B 로 읽으면 **틀린다.** 토큰이 `oklch()` 라 Chrome 이
     * `getComputedStyle` 에서 `lab(37.97 57.32 52.63)` 을 돌려주는데, 그걸 rgb 로 읽으면
     * 엉뚱한 값이 나온다 (실제로 이 테스트가 처음에 1.47:1 이라는 거짓 실패를 냈다).
     * 캔버스 2D 는 어떤 색 공간이든 sRGB 픽셀로 바꿔 주므로 색 공간에 무관해진다.
     */
    const ctx = document.createElement("canvas").getContext("2d")!;
    const parse = (c: string): [number, number, number, number] => {
      if (!c || c === "transparent") return [0, 0, 0, 0];
      ctx.clearRect(0, 0, 1, 1);
      ctx.fillStyle = "#000";
      ctx.fillStyle = c;
      ctx.fillRect(0, 0, 1, 1);
      const d = ctx.getImageData(0, 0, 1, 1).data;
      return [d[0], d[1], d[2], d[3] / 255];
    };

    const fg = parse(getComputedStyle(el).color);

    let bg: [number, number, number, number] = [255, 255, 255, 1];
    for (let node: Element | null = el; node; node = node.parentElement) {
      const c = parse(getComputedStyle(node).backgroundColor);
      if (c[3] > 0) {
        bg = c;
        break;
      }
    }

    const lin = (v: number) => {
      const s = v / 255;
      return s <= 0.04045 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
    };
    const lum = (c: number[]) => 0.2126 * lin(c[0]) + 0.7152 * lin(c[1]) + 0.0722 * lin(c[2]);

    const a = lum(fg);
    const b = lum(bg);
    const [hi, lo] = a > b ? [a, b] : [b, a];
    return (hi + 0.05) / (lo + 0.05);
  }, selector);
}

// ─────────────────────────────────────────────────────────────────────────────

test.describe("가로 넘침 (ISSUE-051)", () => {
  for (const w of WIDTHS) {
    for (const p of PAGES) {
      test(`${w}px · ${p.name} 은 가로로 넘치지 않는다`, async ({ page }) => {
        await page.setViewportSize({ width: w, height: 800 });
        await page.goto(p.path);
        await page.waitForLoadState("networkidle");

        const over = await overflowPx(page);
        expect(over, `${over}px 넘침 — overflow-x: clip 이 숨기고 있을 뿐이다`).toBe(0);
      });
    }
  }
});

test.describe("내비 탭 (ISSUE-051 · ISSUE-055)", () => {
  test("320px 에서 탭 레이블이 한 줄이다", async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 800 });
    await page.goto("/");
    await page.waitForLoadState("networkidle");

    // 탐색 · 파인더 · 아티클. 탭이 늘면 320px 에서 가장 먼저 무너지는 자리라 개수를
    // 여기 적어 둔다 — 늘릴 때 이 줄을 고치며 한 번 더 재게 된다.
    const tabs = page.locator(".tab");
    await expect(tabs).toHaveCount(3);

    for (let i = 0; i < 3; i++) {
      const lines = await tabs.nth(i).evaluate((el) => el.getClientRects().length);
      const text = await tabs.nth(i).innerText();
      expect(lines, `"${text.replace(/\n/g, " ")}" 이 ${lines}줄이다`).toBe(1);
    }
  });

  test("선택된 탭의 대비가 4.5:1 이상이다", async ({ page }) => {
    // 홈(`/`)에는 선택된 탭이 없다 — 홈은 어느 탭도 아니다 (ADR-0012). 탭이 선택되는
    // 화면에서 대비를 잰다. 탐색 화면에서 `01 탐색` 이 aria-current 다.
    await page.goto("/cocktails/search");
    await page.waitForLoadState("networkidle");

    const current = page.locator('.tab[aria-current="page"]');
    await expect(current).toHaveCount(1);

    const r = await contrastOf(page, '.tab[aria-current="page"]');
    // 되돌리면 1.00 이 나온다 — styles.css 의 .nav a[aria-current] 가 명시도로 이기던 자리.
    expect(r, `대비 ${r.toFixed(2)}:1 — 선택된 탭 글자가 안 읽힌다`).toBeGreaterThanOrEqual(4.5);
  });

  test("비선택 탭의 대비도 4.5:1 이상이다", async ({ page }) => {
    await page.goto("/");
    await page.waitForLoadState("networkidle");

    const r = await contrastOf(page, '.tab:not([aria-current="page"])');
    expect(r, `대비 ${r.toFixed(2)}:1`).toBeGreaterThanOrEqual(4.5);
  });

  test("탭 터치 타깃이 44px 이상이다 (WCAG 2.5.8)", async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 800 });
    await page.goto("/");
    await page.waitForLoadState("networkidle");

    const box = await page.locator(".tab").first().boundingBox();
    expect(box).not.toBeNull();
    expect(box!.height, `높이 ${box!.height}px`).toBeGreaterThanOrEqual(44);
  });
});

test.describe("카드 그리드 (ISSUE-051)", () => {
  test("카드가 트랙 밖으로 나가지 않는다", async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 900 });
    await page.goto("/");
    await page.waitForLoadState("networkidle");

    // 홈(`/`)에는 카드 그리드가 둘이다(최신 아티클 · 추천 칵테일, ADR-0012). 둘 다 본다.
    const grids = page.locator(".card-grid");
    await expect(grids.first()).toBeVisible();

    // 맨 `1fr` 이면 min-content 바닥 때문에 카드가 그리드보다 넓어진다.
    for (const grid of await grids.all()) {
      const spill = await grid.evaluate((g) => {
        const gr = g.getBoundingClientRect();
        return [...g.children]
          .map((c) => Math.round(c.getBoundingClientRect().right - gr.right))
          .filter((d) => d > 1);
      });
      expect(spill, `카드 ${spill.length}장이 그리드 밖으로 ${spill.join("·")}px 나갔다`).toEqual([]);
    }
  });
});

/**
 * 헤더의 워드마크 (MUT).
 *
 * 이름을 글자로 적지 않고 그림으로 세웠다 — 그림은 조용히 사라진다. 화면에는 빈칸만
 * 남고 아무도 못 알아챈다. **보이는지가 아니라 그려졌는지**를 본다.
 *
 * 예전 판(`next/image` + png)이 정확히 그렇게 실패했다. 요소는 자리를 차지하는데
 * DPR 2 이상에서 그림이 안 왔고, 그때 이 테스트는 DPR 1 로만 돌아 통과했다.
 * 인라인 SVG 로 바꾸면서 배율과 무관해졌지만, 검사는 그려진 폭을 직접 재는 쪽으로 남긴다.
 *
 * `aria-label` 은 이름 그 자체라 스크린리더가 읽을 것이 있는지도 함께 본다 (`NFR-A-01` 계열).
 */
/** #180 — 모바일에서 내려가면 로고 줄이 접히고 탭 줄만 남는다. 맨 위로 오면 다시 편다. */
test("모바일에서 스크롤하면 헤더가 탭 줄만 남긴다 (#180)", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/cocktails/search");
  await page.waitForLoadState("networkidle");

  const nav = page.locator(".site-nav");
  const tall = (await nav.boundingBox())!.height;
  expect(tall).toBeGreaterThan(100);

  await page.evaluate(() => window.scrollTo(0, 600));
  await expect.poll(async () => (await nav.boundingBox())!.height).toBeLessThan(90);
  await expect(page.locator(".tab").first()).toBeVisible();

  await page.evaluate(() => window.scrollTo(0, 0));
  await expect.poll(async () => (await nav.boundingBox())!.height).toBeGreaterThan(100);
});

test("데스크톱은 스크롤해도 헤더가 그대로다 (#180)", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto("/cocktails/search");
  const nav = page.locator(".site-nav");
  const before = (await nav.boundingBox())!.height;
  await page.evaluate(() => window.scrollTo(0, 600));
  await page.waitForTimeout(300);
  expect((await nav.boundingBox())!.height).toBe(before);
/** #179 — 통합 검색과 재료 사전은 탭이 아니라 아이콘·바닥글로 연다. 탭 셋은 그대로다. */
test("통합 검색 아이콘과 재료 사전 링크가 있다 (#179)", async ({ page }) => {
  await page.goto("/");
  await expect(page.locator(".tab")).toHaveCount(3);

  await page.getByRole("link", { name: "통합 검색" }).click();
  await expect(page).toHaveURL(/\/search$/);

  await page.getByTestId("legal-notice").getByRole("link", { name: "재료 사전" }).click();
  await expect(page).toHaveURL(/\/ingredients$/);
});

/** #183 — 로그인하면 내비엔 「내 저장」 하나, 로그아웃은 내 저장 화면 바닥에 있다. */
test("로그아웃은 내비가 아니라 내 저장 화면에 있다 (#183)", async ({ page }) => {
  await page.route("**/api/v1/me/profile", (route) =>
    route.fulfill({ json: { displayName: "테스터", roles: ["member"] } })
  );
  await page.route("**/api/v1/me/bookmarks", (route) => route.fulfill({ json: [] }));
  await page.goto("/saved");
  await expect(page.locator(".site-nav").getByRole("link", { name: "내 저장" })).toBeVisible();
  await expect(page.locator(".site-nav").getByRole("button", { name: "로그아웃" })).toHaveCount(0);
  await expect(page.locator("main").getByRole("button", { name: "로그아웃" })).toBeVisible();
});

test("헤더에 워드마크가 뜬다", async ({ page }) => {
  await page.goto("/cocktails/search");

  const mark = page.locator(".nav-brand svg");
  await expect(mark).toBeVisible();
  await expect(mark).toHaveAttribute("aria-label", "MUT");

  // **자리만 있고 안 그려지는 경우**를 잡는다. 예전 png 판이 정확히 그렇게 실패했다 —
  // 요소는 74×56 으로 자리를 차지하는데 그림이 없어 태그라인만 떠 있었다.
  const drawn = await mark.evaluate(
    (el) => (el.querySelector("path") as SVGGraphicsElement | null)?.getBBox().width ?? 0,
  );
  expect(drawn, "워드마크가 자리만 차지하고 그려지지 않았다").toBeGreaterThan(0);

  // `ㅅ` 획이 읽히는 최소 높이. 이 밑으로 내리면 `멋` 이 얼룩이 된다 (34px 이었다).
  const box = (await mark.boundingBox())!;
  expect(box.height, `워드마크가 ${box.height}px 다 — 48px 밑이면 획이 뭉갠다`).toBeGreaterThanOrEqual(48);
});