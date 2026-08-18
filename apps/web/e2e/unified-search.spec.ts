import { test, expect, type Page } from "@playwright/test";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import type { components } from "@kca/domain/generated/api";

type SearchResponse = components["schemas"]["SearchResponse"];
type SearchHit = components["schemas"]["SearchHit"];

/**
 * ISSUE-042 — 통합 검색 화면 (`FR-SEARCH-006`·`007`·`008` · `R-F5-1` · `NFR-S-02`).
 *
 * ## 검색을 여기서 하지 않는다
 *
 * 초성·별칭·띄어쓰기 매칭은 색인의 일이고 이슈 017·024 가 이미 테스트한다. 화면이 지켜야
 * 할 것은 **질의를 그대로 넘기고, 받은 것을 그룹대로 그리고, 실패를 사람 말로 옮기는 것**이다.
 * 그래서 상류 응답을 흉내 내고(`page.route`) 화면의 행동만 본다 — API 를 띄우지 않아도 돌고,
 * 계약이 바뀌면 이 파일의 응답 타입에서 먼저 깨진다.
 *
 * ## 계측은 이 이슈가 아니다
 *
 * RED 15~17 의 `search_miss` 는 이슈 035(#37)가 소유한다 — 그 이슈가 수집 기반을 세우고
 * `cocktail_view` 와 함께 심으며, 의존에 이 이슈(042)가 적혀 있다. 여기서는 **서버가 준
 * `hadChosung` 을 화면이 다시 판정하지 않는다**는 것만 지킨다 (RED 17).
 */

const PATH = "/search";

function hit(entityType: string, slug: string, nameKo: string, nameEn?: string): SearchHit {
  return { entityType, slug, nameKo, nameEn, weight: entityType === "cocktail" ? 100 : 50 };
}

/** 서버가 네 자리를 항상 채워 보낸다 (`SearchResponse` KDoc). 흉내도 그렇게 낸다. */
function response(over: Partial<SearchResponse> = {}): SearchResponse {
  const cocktails = [hit("cocktail", "old-fashioned", "올드패션드", "Old Fashioned")];
  const ingredients = [hit("ingredient", "angostura", "앙고스투라 비터스", "Angostura")];

  return {
    query: "올드패션드",
    hadChosung: false,
    matchedCount: cocktails.length + ingredients.length,
    groups: {
      cocktail: { items: cocktails, count: cocktails.length },
      ingredient: { items: ingredients, count: ingredients.length },
      bar: { items: [], count: 0 },
      article: { items: [], count: 0 },
    },
    ...over,
  };
}

/** 상류를 흉내 낸다. 부른 질의를 모아 두어 "그대로 넘겼는가" 를 볼 수 있게 한다. */
async function mockSearch(
  page: Page,
  opts: { body?: SearchResponse; status?: number; suggest?: SearchHit[] } = {}
): Promise<{ searched: string[]; suggested: string[] }> {
  const searched: string[] = [];
  const suggested: string[] = [];

  await page.route(/\/api\/search\/suggest\?/, async (route) => {
    suggested.push(new URL(route.request().url()).searchParams.get("q") ?? "");
    await route.fulfill({ json: opts.suggest ?? [] });
  });

  await page.route(/\/api\/search\?/, async (route) => {
    searched.push(new URL(route.request().url()).searchParams.get("q") ?? "");
    const status = opts.status ?? 200;
    if (status !== 200) {
      await route.fulfill({ status, json: { error: "..." } });
      return;
    }
    await route.fulfill({ json: opts.body ?? response() });
  });

  return { searched, suggested };
}

async function search(page: Page, term: string) {
  await page.getByRole("combobox").fill(term);
  await page.getByRole("combobox").press("Enter");
}

// ── RED 1~7 : 질의를 그대로 넘긴다 (FR-SEARCH-006·007) ────────────────────

/**
 * RED 1~7 — 네 표기와 초성이 **같은 자리로** 간다.
 *
 * 어느 표기가 무엇에 매칭되는지는 서버가 정한다 (이슈 024 RED 7). 화면은 사용자가 친 것을
 * 손대지 않고 넘겨야 한다 — 여기서 공백을 지우거나 초성을 풀면 서버의 판정과 어긋난다.
 */
test("RED1~7 - 한글·띄어쓰기·영문·별칭·초성을 그대로 넘긴다", async ({ page }) => {
  const calls = await mockSearch(page);
  await page.goto(PATH);

  const terms = ["올드패션드", "올드 패션드", "Old Fashioned", "올패", "ㅁㄹㄱㄹㅌ", "ㅁㄹㄱ"];
  for (const term of terms) {
    await search(page, term);
    await expect(page.locator(".search-group").first()).toBeVisible();
  }

  expect(calls.searched).toEqual(terms);
});

/** RED 8 — 초성으로도 된다는 것을 화면이 말한다. 아는 사람만 쓰는 기능은 없는 기능이다. */
test("RED8 - 초성 입력이 안내된다", async ({ page }) => {
  await mockSearch(page);
  await page.goto(PATH);

  const box = page.getByRole("combobox");
  expect(await box.getAttribute("placeholder")).toMatch(/초성/);
  expect(await box.getAttribute("aria-label")).toMatch(/초성/);
});

// ── RED 9~13 : 타입별 그룹핑 (FR-SEARCH-008 · R-F5-1) ─────────────────────

test("RED9,10,11,13 - 타입별로 묶이고 건수가 붙는다", async ({ page }) => {
  await mockSearch(page);
  await page.goto(PATH);
  await search(page, "올드패션드");

  const groups = page.locator(".search-group");
  await expect(groups).toHaveCount(2); // Phase 1a 색인은 칵테일·재료뿐 (이슈 017)

  // RED 13 — 순서가 결정론적이다. 칵테일이 먼저다 (weight).
  await expect(groups.nth(0).getByRole("heading")).toContainText("칵테일");
  await expect(groups.nth(1).getByRole("heading")).toContainText("재료");

  // RED 11 — 그룹마다 건수
  await expect(groups.nth(0).getByRole("heading")).toContainText("1건");
  await expect(groups.nth(1).getByRole("heading")).toContainText("1건");
});

/**
 * RED 12 — 빈 그룹 처리가 일관된다.
 *
 * 서버는 `bar`·`article` 자리를 항상 채워 보낸다 (Phase 1b·2 라 늘 비어 있다).
 * 화면은 **0건 그룹을 그리지 않는다** — "바 0건" 을 네 줄 늘어놓는 것은 결과가 아니다.
 */
test("RED12 - 빈 그룹은 그리지 않는다", async ({ page }) => {
  await mockSearch(page);
  await page.goto(PATH);
  await search(page, "올드패션드");

  await expect(page.locator(".search-group")).toHaveCount(2);
  await expect(page.getByText("바", { exact: true })).toHaveCount(0);
  await expect(page.getByText("아티클", { exact: true })).toHaveCount(0);
});

/** 그룹 자리 넷이 화면에 적혀 있다 — 나중에 키가 생겨도 렌더가 깨지지 않는다 (RED 10). */
test("RED10 - 바·아티클 자리가 잡혀 있다", () => {
  const source = readFileSync(join(process.cwd(), "app/search/unified-search.tsx"), "utf8");
  expect(source).toMatch(/GROUP_ORDER\s*=\s*\[\s*"cocktail",\s*"ingredient",\s*"bar",\s*"article"/);
});

/** 상세가 있는 타입만 링크다. 없는 화면으로 보내면 404 를 눌러 보게 된다. */
test("결과에서 칵테일 상세로 간다", async ({ page }) => {
  await mockSearch(page);
  await page.goto(PATH);
  await search(page, "올드패션드");

  await expect(page.locator(".search-group").first().getByRole("link")).toHaveAttribute(
    "href",
    "/cocktails/old-fashioned"
  );
  // 재료 사전은 아직 화면이 없다 (SCREENS-01 01-D)
  await expect(page.locator(".search-group").nth(1).getByRole("link")).toHaveCount(0);
});

// ── RED 14~18 : 결과 0건 (SPEC-10 §4.3) ──────────────────────────────────

test("RED14,18 - 0건 안내가 다음 행동을 준다", async ({ page }) => {
  await mockSearch(page, {
    body: response({ query: "없는칵테일", matchedCount: 0, groups: {} }),
  });
  await page.goto(PATH);
  await search(page, "없는칵테일");

  const empty = page.locator(".search-status__empty");
  await expect(empty).toContainText("없는칵테일");
  await expect(empty).toContainText("결과가 없습니다");

  // 검색이 실패한 자리에서 끝내지 않는다 — 탐색으로 가는 길을 준다
  await expect(empty.getByRole("link")).toHaveAttribute("href", "/cocktails/search");
  await expect(page.locator(".search-group")).toHaveCount(0);
});

/**
 * RED 17 — `hadChosung` 을 **화면이 판정하지 않는다.**
 *
 * SPEC-10 §4.3 이 0건의 원인을 둘로 나눈다 — 콘텐츠가 없거나 초성 색인이 고장났거나.
 * 프론트가 따로 판정하면 서버와 다른 답을 낼 수 있고, 그 순간 구분이 무의미해진다.
 * 이벤트를 쏘는 것은 이슈 035 이지만, **판정 코드를 여기 들이지 않는 것**은 지금 지킨다.
 */
test("RED17 - 초성 판정을 화면이 다시 하지 않는다", () => {
  for (const rel of ["app/search/unified-search.tsx", "components/search-box.tsx"]) {
    const source = readFileSync(join(process.cwd(), rel), "utf8");
    expect(source, `${rel} 에 초성 판정이 있다 — 서버 응답을 쓴다`).not.toMatch(/ㄱ-ㅎ/);
    expect(source, `${rel} 가 hadChosung 을 만들고 있다`).not.toMatch(/hadChosung\s*[:=]/);
  }
});

// ── RED 19~23 : 자동완성 ─────────────────────────────────────────────────

test("RED19,21 - 입력 중 제안이 나오고 개수 상한이 있다", async ({ page }) => {
  const many = Array.from({ length: 20 }, (_, i) => hit("cocktail", `c${i}`, `칵테일 ${i}`));
  await mockSearch(page, { suggest: many });
  await page.goto(PATH);

  await page.getByRole("combobox").fill("칵");
  const list = page.getByRole("listbox");
  await expect(list).toBeVisible();
  // 서버도 8 로 자르지만 화면도 잘라야 계약이 늘어도 드롭다운이 길어지지 않는다
  await expect(list.getByRole("option")).toHaveCount(8);
});

/**
 * RED 20 — 디바운스.
 *
 * `/search/suggest` 는 60 req/min 이다 (SPEC-08 §6). 글자마다 부르면 한 낱말에 한도를 쓴다.
 * 다섯 글자를 이어 치면 **호출은 한 번**이어야 한다.
 */
test("RED20 - 제안이 디바운스된다", async ({ page }) => {
  const calls = await mockSearch(page, { suggest: [hit("cocktail", "negroni", "네그로니")] });
  await page.goto(PATH);

  const box = page.getByRole("combobox");
  for (const ch of ["네", "네그", "네그로", "네그로니"]) await box.fill(ch);

  await expect(page.getByRole("listbox")).toBeVisible();
  expect(calls.suggested, `${calls.suggested.length}번 불렀다`).toEqual(["네그로니"]);
});

test("RED22,23 - 키보드로 제안을 고르면 그 결과로 간다", async ({ page }) => {
  const calls = await mockSearch(page, {
    suggest: [hit("cocktail", "negroni", "네그로니"), hit("cocktail", "martini", "마티니")],
  });
  await page.goto(PATH);

  const box = page.getByRole("combobox");
  await box.fill("네");
  await expect(page.getByRole("listbox")).toBeVisible();

  await box.press("ArrowDown");
  await box.press("ArrowDown");
  // 고른 자리가 보조기기에 전달된다 (`aria-activedescendant`)
  const activeId = await box.getAttribute("aria-activedescendant");
  expect(activeId).not.toBeNull();
  await expect(page.locator(`#${activeId}`)).toHaveAttribute("aria-selected", "true");

  await box.press("Enter");
  await expect(box).toHaveValue("마티니");
  expect(calls.searched).toEqual(["마티니"]);
  await expect(page).toHaveURL(/q=/);
});

test("Escape 로 제안을 닫는다", async ({ page }) => {
  await mockSearch(page, { suggest: [hit("cocktail", "negroni", "네그로니")] });
  await page.goto(PATH);

  await page.getByRole("combobox").fill("네");
  await expect(page.getByRole("listbox")).toBeVisible();

  await page.getByRole("combobox").press("Escape");
  await expect(page.getByRole("listbox")).toHaveCount(0);
});

// ── RED 24~25 : 레이트 리밋 (SPEC-08 §6) ─────────────────────────────────

test("RED24,25 - 429 를 받으면 안내가 나오고 입력은 계속된다", async ({ page }) => {
  await mockSearch(page, { status: 429 });
  await page.goto(PATH);
  await search(page, "네그로니");

  const error = page.locator(".search-status__error");
  await expect(error).toContainText("잠시 후");
  await expect(error).toHaveAttribute("role", "alert");

  // 화면이 죽지 않는다 — 입력은 그대로 쓸 수 있다
  await expect(page.getByRole("combobox")).toBeEditable();
  await expect(page.locator(".search-group")).toHaveCount(0);
});

test("자동완성이 429 를 받아도 검색은 된다", async ({ page }) => {
  const suggested: string[] = [];
  await page.route(/\/api\/search\/suggest\?/, async (route) => {
    suggested.push("x");
    await route.fulfill({ status: 429, json: { error: "..." } });
  });
  await page.route(/\/api\/search\?/, async (route) => route.fulfill({ json: response() }));

  await page.goto(PATH);
  await search(page, "올드패션드");

  await expect(page.getByRole("listbox")).toHaveCount(0);
  await expect(page.locator(".search-group").first()).toBeVisible();
});

// ── RED 26 : 색인 ────────────────────────────────────────────────────────

test("RED26 - 검색 결과에 noindex 가 있다", async ({ page }) => {
  await mockSearch(page);
  await page.goto(PATH);

  await expect(page.locator('meta[name="robots"]')).toHaveAttribute("content", /noindex/);
});

test("RED26 - 사이트맵에 검색 경로가 없다", async ({ request }) => {
  const xml = await (await request.get("/sitemap.xml")).text();
  expect(xml).not.toMatch(/<loc>[^<]*\/search<\/loc>/);
});

// ── RED 27~30 : 접근성 ───────────────────────────────────────────────────

test("RED27,28 - 입력에 라벨이 있고 결과 수가 읽힌다", async ({ page }) => {
  await mockSearch(page);
  await page.goto(PATH);

  const box = page.getByRole("combobox");
  await expect(box).toHaveAttribute("aria-label", /검색/);
  // 라벨 요소도 함께 둔다 — 눈으로 보는 사람에게도 무엇을 넣는 칸인지 말한다
  await expect(page.locator(`label[for="${await box.getAttribute("id")}"]`)).toBeVisible();

  const live = page.locator(".search-status[aria-live]");
  await expect(live).toHaveAttribute("aria-live", "polite");

  await search(page, "올드패션드");
  await expect(live).toContainText("2건");
});

test("RED29,30 - 키보드로 전체 흐름이 되고 아웃라인이 있다", async ({ page }) => {
  await mockSearch(page, { suggest: [hit("cocktail", "negroni", "네그로니")] });
  await page.goto(PATH);

  const box = page.getByRole("combobox");
  await expect
    .poll(async () => {
      await box.focus();
      return box.evaluate((el) => el === document.activeElement);
    })
    .toBe(true);

  const outline = await box.evaluate((el) => getComputedStyle(el).outlineWidth);
  expect(outline, "focus-visible 아웃라인이 없다 (NFR-A-05)").not.toBe("0px");

  await page.keyboard.type("올드패션드");
  await page.keyboard.press("Enter");
  await expect(page.locator(".search-group").first()).toBeVisible();

  // 결과의 링크까지 탭으로 닿는다
  await page.keyboard.press("Tab");
  await expect(page.locator(".search-group").first().getByRole("link")).toBeFocused();
});

// ── RED 31 : 법적 (NFR-L-01) ─────────────────────────────────────────────

test("RED31 - 과음 경고가 하단에 있다", async ({ page }) => {
  await mockSearch(page);
  await page.goto(PATH);
  await expect(page.getByTestId("legal-notice")).toBeVisible();
});
