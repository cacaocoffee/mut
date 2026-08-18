import { test, expect, type Page } from "@playwright/test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

/**
 * ISSUE-041 — 취향 파인더 (`FR-SEARCH-004` · ADR-0003 · `NFR-A-04` · `NFR-P-02`).
 *
 * ## 요체는 **도수 구간이 한 곳에서 온다**는 것
 *
 * ADR-0003 — "탐색 필터와 취향 파인더가 이 정의를 공유한다." 따로 두면 반드시 어긋나고,
 * 어긋나면 파인더가 "가볍게" 로 추천한 것을 탐색의 "저 ~10%" 에서 못 찾는다.
 * 여기서는 **두 화면이 같은 어휘를 주소에 쓰는지**와 **같은 조건에 같은 수를 내는지**를 본다.
 *
 * ## 계측은 이 이슈가 아니다
 *
 * RED 15~18 의 `finder_step` 은 이슈 049(#51)가 소유한다 — 그 이슈가 `apps/web/lib/analytics`
 * 를 만들고 화면에 호출을 심으며, 기반인 이슈 035(#37)가 아직 열려 있다. 여기서 두 번째
 * 수집 경로를 만들지 않는다.
 */

const FINDER = "/finder";
const SEARCH = "/cocktails/search";

/** 지금 질문의 선택지. */
function options(page: Page) {
  return page.locator(".quiz-options button");
}

async function candidateCount(page: Page): Promise<number> {
  return Number(await page.locator(".quiz-count b").innerText());
}

/**
 * 화면이 브라우저에서 붙기를 기다린다.
 *
 * 미리 그린 HTML 위의 버튼은 스크립트가 붙기 전에는 눌러도 아무 일이 없다. 화면이
 * `data-ready` 로 그 시점을 알린다 — `networkidle` 은 이 앱에서 가라앉지 않아 못 쓴다.
 */
async function ready(page: Page) {
  await expect(page.locator("main[data-ready]")).toBeVisible();
}

function source(rel: string): string {
  return readFileSync(join(process.cwd(), rel), "utf8");
}

// ── RED 1~5 : 도수 구간 공유 (FR-SEARCH-004 — 요체) ───────────────────────

test("RED1 - 도수 질문이 4구간이다", async ({ page }) => {
  await page.goto(FINDER);

  await expect(page.getByRole("heading", { level: 2 })).toContainText("도수");
  await expect(options(page)).toHaveCount(4);
});

/**
 * RED 2·3 — **탐색 필터와 같은 구간 어휘를 쓴다.**
 *
 * 두 화면이 주소에 적는 값을 모아 비교한다. 탐색은 코퍼스에 없는 구간을 빼므로 부분집합이고,
 * 거기 없는 이름이 하나라도 나오면 정의가 갈린 것이다.
 */
test("RED2,3 - 구간 정의가 탐색 필터와 동일하다", async ({ page }) => {
  // 화면을 한 번만 열고 [이전]으로 되돌아오며 네 선택지를 다 눌러 본다.
  // 선택지마다 새로 열면 이 테스트 하나가 페이지를 여덟 번 그린다.
  await page.goto(FINDER);
  await ready(page);

  const bandInUrl = () => new URL(page.url()).searchParams.get("abv");

  const finderBands: string[] = [];
  for (let i = 0; i < 4; i++) {
    // [이전] 으로 돌아와도 앞 답은 주소에 남아 있다. 주소에 값이 **있는지**만 보면
    // 아직 안 바뀐 앞 값을 읽는다 — 값이 **바뀔 때까지** 기다린다.
    const before = bandInUrl();
    await options(page).nth(i).click();
    await expect.poll(bandInUrl).not.toBe(before);
    finderBands.push(bandInUrl()!);

    await page.getByRole("button", { name: /이전/ }).click();
    await expect(page.locator(".quiz-kicker")).toContainText("QUESTION 1 / 4");
  }

  await page.goto(SEARCH);
  await ready(page);

  const chips = page
    .locator(".filter-group")
    .filter({ hasText: "도수 ABV" })
    .locator("button[aria-pressed]");

  const searchBands: string[] = [];
  for (let i = 0; i < (await chips.count()); i++) {
    await chips.nth(i).click();
    await expect(page).toHaveURL(/abv=/);
    searchBands.push(new URL(page.url()).searchParams.get("abv")!);

    await chips.nth(i).click(); // 끄고 다음 칩으로 — 켠 채로 두면 두 구간이 섞인다
    await expect(page).not.toHaveURL(/abv=/);
  }

  expect(finderBands, "파인더가 4구간을 그대로 쓰지 않는다").toEqual(["na", "low", "mid", "high"]);
  for (const band of searchBands) {
    expect(finderBands, `탐색의 ${band} 구간이 파인더에 없다 — 정의가 갈렸다`).toContain(band);
  }
});

test("RED4 - 연속 슬라이더가 없다", async ({ page }) => {
  await page.goto(FINDER);

  await expect(page.locator('input[type="range"]')).toHaveCount(0);
  await options(page).first().click();
  expect(page.url(), "구간이 아니라 수치를 주소에 적고 있다").not.toMatch(/abvM(in|ax)|abv=\d/);
});

/**
 * RED 5·12 — **파인더 전용 도수 상수가 없다.**
 *
 * 화면은 질문 목록을 도메인에서 받아 그리기만 한다. 구간 이름을 화면 파일에 적는 순간
 * 정의가 두 곳이 되고, 한쪽만 고치는 날이 온다.
 */
test("RED5,12 - 구간 정의와 후보 계산이 한 곳에서 온다", () => {
  const screen = source("components/finder-screen.tsx");

  expect(screen, "질문을 화면이 새로 만들고 있다").toMatch(/QUESTIONS[,\s]/);
  expect(screen, "후보 계산이 화면에 복제됐다").toMatch(/quizCandidates|rankResults/);
  for (const band of ["na", "low", "mid", "high"]) {
    expect(screen, `구간 이름 "${band}" 가 화면 파일에 있다 — 정의는 ABV_BANDS 한 곳이다`)
      .not.toMatch(new RegExp(`["']${band}["']`));
  }
});

// ── RED 6~11 : 단계 진행 ─────────────────────────────────────────────────

test("RED6,7,10 - 4단계를 지나면 결과가 나온다", async ({ page }) => {
  await page.goto(FINDER);

  for (let step = 1; step <= 4; step++) {
    await expect(page.locator(".quiz-kicker")).toContainText(`QUESTION ${step} / 4`);
    expect(await candidateCount(page), "후보 수가 보이지 않는다").toBeGreaterThan(0);
    // 첫 질문만 마지막 선택지(가장 독한 구간)를 고른다 — 논알콜로 시작하면 남은 답이
    // 무엇이든 후보가 1종 이하라 "결과가 나오는가" 를 못 본다.
    await (step === 1 ? options(page).last() : options(page).first()).click();
  }

  await expect(page.getByRole("heading", { level: 4 })).toContainText("추천 결과");
  await expect(page.locator(".result-card").first()).toBeVisible();
});

test("RED8 - 이전 단계로 돌아갈 수 있다", async ({ page }) => {
  await page.goto(FINDER);
  await options(page).first().click();
  await expect(page.locator(".quiz-kicker")).toContainText("QUESTION 2 / 4");

  await page.getByRole("button", { name: /이전/ }).click();
  await expect(page.locator(".quiz-kicker")).toContainText("QUESTION 1 / 4");
  // 돌아왔을 때 앞서 고른 답이 눌린 채로 보여야 어디까지 왔는지 알 수 있다
  await expect(options(page).first()).toHaveAttribute("aria-pressed", "true");
});

/** RED 9 — 단계와 답이 주소에 남는다. 새로 열어도 같은 자리다. */
test("RED9 - 단계마다 주소가 바뀌고 공유하면 그 자리가 열린다", async ({ page }) => {
  await page.goto(FINDER);
  await options(page).first().click();
  await expect(page).toHaveURL(/step=1/);

  await options(page).first().click();
  await expect(page).toHaveURL(/step=2/);

  const shared = page.url();
  const before = await candidateCount(page);

  await page.goto(shared);
  await expect(page.locator(".quiz-kicker")).toContainText("QUESTION 3 / 4");
  await expect(page.locator(".quiz-count b")).toHaveText(String(before));
});

/**
 * RED 11 — 후보가 0이면 **그 자리에서** 알린다.
 *
 * 논알콜이면서 위스키인 것은 없다 — 어느 코퍼스에서도 그렇다. 남은 질문에 계속 답하게
 * 두면 마지막에 가서야 빈 화면을 본다.
 */
test("RED11 - 후보가 0이면 안내가 나온다", async ({ page }) => {
  await page.goto(`${FINDER}?abv=na&base=whisky&step=2`);

  expect(await candidateCount(page)).toBe(0);
  await expect(page.locator(".quiz-dead-end")).toBeVisible();
  await expect(page.locator(".quiz-dead-end")).toContainText("이전 단계");
});

// ── RED 13~14 : 탐색 화면과 같은 답 ──────────────────────────────────────

test("RED13 - 탐색 화면과 같은 코퍼스를 쓴다", async ({ page }) => {
  expect(source("app/finder/page.tsx"), "코퍼스를 따로 만들고 있다").toMatch(/searchCorpus/);

  await page.goto(FINDER);
  const all = await candidateCount(page);

  await page.goto(SEARCH);
  const searchAll = Number(await page.locator(".results-count b").innerText());
  expect(all, "두 화면이 다른 목록을 보고 있다").toBe(searchAll);
});

/**
 * RED 14 — **같은 조건이면 같은 수가 나온다.**
 *
 * 파인더의 "진 · 보드카" 는 탐색의 `base=gin,vodka` 와 같은 뜻이고, 도수·당도는 어휘까지
 * 같다. 두 화면이 다른 답을 내면 사용자는 어느 쪽을 믿을지 알 수 없다.
 */
test("RED14 - 파인더 후보가 같은 조건의 탐색 결과와 일치한다", async ({ page }) => {
  // 당도는 `dry` 만 1:1 이다 — 나머지 선택지는 파인더가 두 단계를 묶은 것이라 탐색의
  // 한 값으로 적을 수 없다. 기주 묶음은 탐색의 OR 로 편다.
  const BASES = [
    { finder: "clear", search: "gin,vodka" },
    { finder: "whisky", search: "whisky" },
    { finder: "warm", search: "rum,agave,korean" },
  ];

  let compared = 0;

  for (const band of ["low", "mid", "high"]) {
    for (const base of BASES) {
      await page.goto(`${FINDER}?abv=${band}&sweet=dry&base=${base.finder}&step=3`);
      const finderCount = await candidateCount(page);
      if (finderCount === 0) continue; // 0 끼리 맞은 것은 아무것도 지키지 않는다

      await page.goto(`${SEARCH}?abv=${band}&sweet=dry&base=${base.search}`);
      await expect(
        page.locator(".results-count b"),
        `abv=${band} · base=${base.finder} 에서 두 화면이 다른 답을 냈다`
      ).toHaveText(String(finderCount));
      compared += 1;
    }
  }

  expect(compared, "대조할 표본이 하나도 없다 — 코퍼스를 확인한다").toBeGreaterThan(0);
});

// ── RED 19~23 : 접근성 (NFR-A-04·A-05·A-08) ──────────────────────────────

test("RED19,20 - 키보드로 전 단계를 진행할 수 있다", async ({ page }) => {
  await page.goto(FINDER);

  for (let step = 1; step <= 4; step++) {
    const first = options(page).first();
    await expect
      .poll(async () => {
        await first.focus();
        return first.evaluate((el) => el === document.activeElement);
      })
      .toBe(true);

    const outline = await first.evaluate((el) => getComputedStyle(el).outlineWidth);
    expect(outline, "focus-visible 아웃라인이 없다 (NFR-A-05)").not.toBe("0px");

    await page.keyboard.press("Enter");
  }

  await expect(page.getByRole("heading", { level: 4 })).toContainText("추천 결과");
});

/** RED 21 — 고른 것을 색만으로 말하지 않는다 (`NFR-A-08`). */
test("RED21 - 선택 상태가 색만으로 표현되지 않는다", async ({ page }) => {
  await page.goto(FINDER);
  await options(page).first().click();
  await page.getByRole("button", { name: /이전/ }).click();

  const picked = options(page).first();
  await expect(picked).toHaveAttribute("aria-pressed", "true");
  await expect(picked.locator(".quiz-option__mark"), "글자로 된 표시가 없다").toBeVisible();
});

/** RED 22·23 — 단계와 후보 수가 읽힌다. */
test("RED22,23 - 단계 진행과 후보 수가 스크린리더에 안내된다", async ({ page }) => {
  await page.goto(FINDER);

  // Next 가 라우터 알림용 live 영역을 하나 더 깔아 둔다 — 우리 것만 집는다.
  const live = page.locator(".quiz-count[aria-live]");
  await expect(live).toHaveAttribute("aria-live", "polite");
  await expect(live).toContainText("현재 후보");
  await expect(live).toContainText("질문 1 / 4");

  await options(page).first().click();
  await expect(live).toContainText("질문 2 / 4");
});

// ── RED 24~25 : 성능 ────────────────────────────────────────────────────

/**
 * RED 25 — 단계 전환에 **서버 왕복이 없다.**
 *
 * 코퍼스는 이미 브라우저에 있다 (SPEC-05 §4). 단계마다 서버를 부르면 파인더를 클라이언트에
 * 둔 이유가 사라지고 `NFR-P-02`(INP ≤ 200ms)도 지키기 어려워진다.
 */
test("RED24,25 - 단계 전환이 서버를 부르지 않고 즉시 끝난다", async ({ page }) => {
  await page.goto(FINDER);

  const calls: string[] = [];
  page.on("request", (r) => {
    if (/\/api\/v1\/|_rsc=/.test(r.url())) calls.push(r.url());
  });

  const started = Date.now();
  await options(page).first().click();
  await expect(page.locator(".quiz-kicker")).toContainText("QUESTION 2 / 4");
  const elapsed = Date.now() - started;

  expect(calls, "단계를 넘길 때 서버를 부르고 있다").toEqual([]);
  expect(elapsed, `단계 전환에 ${elapsed}ms 걸렸다`).toBeLessThan(1000);
});

// ── RED 26 : 법적 (NFR-L-01) ────────────────────────────────────────────

test("RED26 - 과음 경고가 하단에 있다", async ({ page }) => {
  await page.goto(FINDER);
  await expect(page.getByTestId("legal-notice")).toBeVisible();
});

// ── 색인 ─────────────────────────────────────────────────────────────────

/**
 * 답이 붙은 주소는 같은 화면의 다른 상태다. canonical 을 답 없는 `/finder` 로 고정해
 * 조합마다 색인되는 것을 막는다 (`PRIN-P06` · GAPS G-33).
 */
test("답이 붙어도 canonical 은 /finder 다", async ({ page }) => {
  await page.goto(`${FINDER}?abv=mid&step=1`);

  await expect(page.locator('link[rel="canonical"]')).toHaveAttribute("href", /\/finder$/);
  const robots = page.locator('meta[name="robots"]');
  if (await robots.count()) {
    expect(await robots.getAttribute("content")).not.toContain("noindex");
  }
});
