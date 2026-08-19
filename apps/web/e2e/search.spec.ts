import { test, expect, type Locator, type Page } from "@playwright/test";
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

/**
 * ISSUE-040 — 탐색 · 필터 화면 + 패싯 연동
 * (`FR-SEARCH-001`·`002`·`003`·`005`·`009` · `R-F2.1-1`·`2` · `NFR-S-02`·`A-06`·`P-01`).
 *
 * ## 요체는 **모든 필터 값 옆의 개수**다
 *
 * `FR-SEARCH-002` 는 P0 이고 PRD 가 "초기부터 넣지 않으면 나중에 UI 를 다시 짜야 한다" 고
 * 못박았다. 값마다 숫자가 붙고, 0 인 값은 비활성이며, 다른 축을 고르면 즉시 갱신된다.
 *
 * ## 데이터를 적어 두지 않는다
 *
 * 코퍼스가 둘이다 — `MUT_API_URL` 이 있으면 발행분(41종)이고 없으면 프로토타입(24종)이다.
 * "진이 7개" 같은 기대값을 적으면 둘 중 하나에서 반드시 깨진다. 그래서 **화면이 말하는
 * 숫자끼리의 관계**를 본다: 합이 맞는가, 줄어드는가, 0 이 비활성인가.
 *
 * ## 여기서 재지 않는 것
 *
 * 클라이언트 패싯이 서버 `/cocktails/facets` 와 같은지는 API 가 떠 있어야 알 수 있다 —
 * `scripts/facet-parity.ts` 가 CI(`contract.yml` 의 `facet-parity` 잡)에서 본다 (RED 19·20).
 */

const SEARCH = "/cocktails/search";

/** 한 축의 칩 전부. `.filter-group` 은 라벨과 칩을 함께 담는다. */
function axisChips(page: Page, label: string): Locator {
  return page.locator(".filter-group").filter({ hasText: label }).locator("button[aria-pressed]");
}

function chip(page: Page, label: string, name: string): Locator {
  return axisChips(page, label).filter({ hasText: name });
}

/** 칩이 병기한 개수. `aria-label` 이 "진 7개" 라 거기서 읽는다 (RED 31 과 같은 출처). */
async function countOf(chip: Locator): Promise<number> {
  const label = (await chip.getAttribute("aria-label")) ?? "";
  const n = label.match(/(\d+)개$/);
  expect(n, `개수가 aria-label 에 없다: "${label}"`).not.toBeNull();
  return Number(n![1]);
}

async function resultCount(page: Page): Promise<number> {
  return Number(await page.locator(".results-count b").innerText());
}

/**
 * 결과 수가 이 값이 되기를 기다린다.
 *
 * 클릭은 주소를 바꾸고 화면은 그 다음 렌더에서 따라온다. 클릭 직후에 읽으면 **이전 숫자**를
 * 보게 되고, 그것은 화면이 틀린 것이 아니라 너무 일찍 본 것이다.
 */
async function expectResults(page: Page, n: number, why?: string) {
  await expect(page.locator(".results-count b"), why).toHaveText(String(n));
}

/** 이름이 아니라 **첫 칩**을 집는다 — 코퍼스가 둘이라 어떤 값이 있는지 미리 못 적는다. */
async function firstChipName(page: Page, label: string): Promise<string> {
  const first = axisChips(page, label).first();
  return ((await first.getAttribute("aria-label")) ?? "").replace(/\s\d+개$/, "");
}

/**
 * 당도 한 칸.
 *
 * 라디오 입력은 `opacity: 0` 으로 감춰져 있고 눈에 보이는 것은 라벨이다 —
 * 사용자가 누르는 것도 라벨이라 테스트도 그것을 누른다.
 */
function sweetOption(page: Page, name: RegExp): Locator {
  return page.locator("label.seg-opt").filter({ hasText: name });
}

/** 당도 칸 중 지금 고를 수 있는 것. "전체" 는 세지 않는다. */
async function enabledSweet(page: Page): Promise<Locator> {
  const options = page.locator("label.seg-opt");
  for (let i = 1; i < (await options.count()); i++) {
    if (await options.nth(i).locator("input").isEnabled()) return options.nth(i);
  }
  throw new Error("고를 수 있는 당도가 없다 — 코퍼스를 확인한다");
}

/** 0 건이라 비활성인 칩은 누를 수 없다. 무엇을 고를지는 코퍼스가 정한다. */
async function enabledChip(chips: Locator, from = 0): Promise<Locator> {
  for (let i = from; i < (await chips.count()); i++) {
    if (await chips.nth(i).isEnabled()) return chips.nth(i);
  }
  throw new Error("고를 수 있는 칩이 없다 — 코퍼스를 확인한다");
}

const AXES: { label: string; param: string }[] = [
  { label: "기주 BASE SPIRIT", param: "base" },
  { label: "스타일 STYLE", param: "style" },
  { label: "메이킹 METHOD", param: "method" },
  { label: "도수 ABV", param: "abv" },
  { label: "맛 / 향 FLAVOR PROFILE", param: "flavor" },
];

// ── RED 1~7 : 6축 필터 (FR-SEARCH-001) ───────────────────────────────────

for (const { label, param } of AXES) {
  test(`RED1~6 - ${label} 필터가 동작한다`, async ({ page }) => {
    await page.goto(SEARCH);
    const all = await resultCount(page);

    const name = await firstChipName(page, label);
    const target = chip(page, label, name);
    const expected = await countOf(target);

    await target.click();
    await expect(page).toHaveURL(new RegExp(`[?&]${param}=`));
    await expectResults(page, expected, `${label} 칩의 개수와 결과 수가 다르다`);
    expect(expected).toBeLessThanOrEqual(all);
    await expect(target).toHaveAttribute("aria-pressed", "true");
  });
}

/** 당도만 단일값이라 라디오다 (DECISIONS §1.11) — 칩이 아니라 여기서 따로 본다. */
test("RED4 - 당도 필터가 동작한다", async ({ page }) => {
  await page.goto(SEARCH);

  const dry = sweetOption(page, /^드라이/);
  const label = (await dry.locator("input").getAttribute("aria-label")) ?? "";
  const expected = Number(label.match(/(\d+)개/)![1]);

  await dry.click();
  await expect(page).toHaveURL(/[?&]sweet=dry/);
  await expectResults(page, expected);
  await expect(dry.locator("input")).toBeChecked();
});

/**
 * RED 7 — **보유 재료 축이 없다** (P2).
 *
 * `FR-SEARCH-001` 이 6축을 셌고 보유 재료는 그 안에 없다. 역검색(내 술장)은 **자기 화면**을
 * 갖는다 (`R-F2.2-*`) — 코퍼스에 재료를 얹지 않는 것과 같은 결정이다 (SPEC-07 §5).
 */
test("RED7 - 보유 재료 축이 없다", async ({ page }) => {
  await page.goto(SEARCH);

  // 칩 축 5개 + 당도(라디오) 하나. 일곱 번째가 늘면 축이 늘어난 것이다.
  // (검색어 칸은 패널 밖 `.search-field` 로 나갔다 — 축이 아니라 축 전체에 걸리는 것이라서다.)
  const labels = await page.locator(".filter-label").allInnerTexts();
  expect(labels).toHaveLength(AXES.length + 1);
  expect(labels.join(" ")).not.toMatch(/재료|보유|STOCK|INGREDIENT/i);
});

// ── RED 8~10 : OR / AND (SPEC-07 §3.1 — 서버와 같은 규칙) ─────────────────

/**
 * RED 8 — 기주 복수 선택은 **OR** 다.
 *
 * 칵테일 하나에 기주가 하나뿐이라(`R-C-1`) 두 값을 고른 결과는 각각의 합이다.
 * 합보다 작으면 AND 로 걸린 것이고, 그것이 이 축에서 가장 흔한 실수다.
 */
test("RED8 - 기주 복수 선택이 OR 다", async ({ page }) => {
  await page.goto(SEARCH);
  const chips = axisChips(page, "기주 BASE SPIRIT");
  const [a, b] = [chips.nth(0), chips.nth(1)];
  const [na, nb] = [await countOf(a), await countOf(b)];

  await a.click();
  await b.click();

  await expectResults(page, na + nb, "OR 이 아니다 — 합과 다르다");
});

/**
 * RED 9 — 향·맛 복수 선택은 **AND** 다. 여섯 축 중 유일한 예외 (SPEC-07 §3.1).
 *
 * 하나 고른 뒤 다른 태그의 칩이 말하는 개수가 곧 "둘 다 가진 것" 의 수다 —
 * 그 숫자와 실제 결과가 같아야 한다 (RED 16 과 같은 규칙의 다른 면).
 */
test("RED9,16 - 향·맛 복수 선택이 AND 다", async ({ page }) => {
  await page.goto(SEARCH);
  const flavors = axisChips(page, "맛 / 향 FLAVOR PROFILE");

  await flavors.nth(0).click();
  const alone = await resultCount(page);

  const second = await enabledChip(flavors, 1);
  const both = await countOf(second); // 현재 선택에 이 태그를 더했을 때의 수
  await second.click();

  await expectResults(page, both);
  expect(both, "AND 인데 결과가 늘었다").toBeLessThanOrEqual(alone);
});

/** RED 10 — 축이 다르면 AND 다. 두 축을 걸면 각각보다 넓어질 수 없다. */
test("RED10 - 다른 축 간에는 AND 다", async ({ page }) => {
  await page.goto(SEARCH);

  const base = axisChips(page, "기주 BASE SPIRIT").first();
  const baseOnly = await countOf(base);
  await base.click();

  const style = await enabledChip(axisChips(page, "스타일 STYLE"));
  await style.click();

  await expect.poll(async () => resultCount(page)).toBeLessThanOrEqual(baseOnly);
});

// ── RED 12~18 : 패싯 카운트 (FR-SEARCH-002 — 요체) ────────────────────────

test("RED12 - 모든 필터 값 옆에 개수가 표시된다", async ({ page }) => {
  await page.goto(SEARCH);

  for (const { label } of AXES) {
    const chips = axisChips(page, label);
    const n = await chips.count();
    expect(n, `${label} 에 칩이 없다`).toBeGreaterThan(0);

    for (let i = 0; i < n; i++) {
      // 눈으로도 보여야 한다 — 색만으로 상태를 말하지 않는 근거가 이 숫자다 (NFR-A-08)
      await expect(chips.nth(i).locator(".count")).toHaveText(/^\d+$/);
      await countOf(chips.nth(i)); // aria-label 에도 있다 (NFR-A-06)
    }
  }

  const radios = page.getByRole("radio");
  for (let i = 1; i < (await radios.count()); i++) {
    expect(await radios.nth(i).getAttribute("aria-label")).toMatch(/\d+개$/);
  }
});

/**
 * RED 13·17 — 0 건인 값이 **비활성**이고, 조합 불가능한 값은 **즉시** 0 이 된다
 * (`FR-SEARCH-009`).
 *
 * 향·맛을 여럿 고르면 AND 라 조합이 빠르게 불가능해진다. 그때 0 이 된 칩이 눌리면
 * 사용자는 결과 0 건으로 들어가고, 거기서 무엇을 풀어야 할지 알 수 없다.
 */
test("RED13,17 - 0 건인 값이 비활성 처리된다", async ({ page }) => {
  await page.goto(SEARCH);
  const flavors = axisChips(page, "맛 / 향 FLAVOR PROFILE");

  await flavors.nth(0).click();
  await (await enabledChip(flavors, 1)).click();

  const zeros: number[] = [];
  for (let i = 0; i < (await flavors.count()); i++) {
    if ((await countOf(flavors.nth(i))) === 0) zeros.push(i);
  }
  expect(zeros.length, "조합 불가 값이 하나도 없다 — 이 테스트가 아무것도 지키지 않는다")
    .toBeGreaterThan(0);

  for (const i of zeros) {
    await expect(flavors.nth(i), "0 건인데 누를 수 있다").toBeDisabled();
  }

  // 고른 값은 0 이 되어도 누를 수 있어야 한다 — 끄지 못하면 그 조합에서 못 나온다
  await expect(flavors.nth(0)).toBeEnabled();
});

/** RED 14 — 다른 축을 고르면 개수가 즉시 갱신된다. */
test("RED14 - 개수가 실시간으로 갱신된다", async ({ page }) => {
  await page.goto(SEARCH);
  const style = axisChips(page, "스타일 STYLE").first();
  const before = await countOf(style);

  await axisChips(page, "기주 BASE SPIRIT").first().click();

  await expect
    .poll(async () => countOf(style), { message: "다른 축을 골랐는데 개수가 그대로다" })
    .toBeLessThanOrEqual(before);
});

/**
 * RED 15 — **같은 축의 선택은 그 축 카운트에 영향을 주지 않는다** (이슈 019 RED 1~5).
 *
 * OR 축이라 하나 더 고르면 결과가 늘어난다. 자기 선택을 반영한 카운트를 보여 주면
 * 고르지 않은 값이 "보드카 0" 처럼 읽혀 다시 고를 수 없게 된다.
 */
test("RED15 - 같은 축 선택은 그 축 카운트에 영향을 주지 않는다", async ({ page }) => {
  await page.goto(SEARCH);
  const bases = axisChips(page, "기주 BASE SPIRIT");

  const before: number[] = [];
  for (let i = 0; i < (await bases.count()); i++) before.push(await countOf(bases.nth(i)));

  await bases.nth(0).click();

  for (let i = 0; i < (await bases.count()); i++) {
    expect(await countOf(bases.nth(i)), `${i}번 칩의 개수가 흔들렸다`).toBe(before[i]);
  }
});

/**
 * RED 18 — 코퍼스에 없는 값은 **칩 자체가 없다** (ADR-0002 §5).
 *
 * enum 은 카테고리 URL 의 정본이라 완전하게 두지만, 항목 0건짜리 칩을 늘어놓으면
 * 고장난 것처럼 보인다. 뒤집으면 이렇게 말할 수 있다 — **필터가 없을 때 0 인 칩은 없다.**
 * 0 은 조합 때문에만 생긴다.
 */
test("RED18 - 코퍼스에 없는 값은 칩 자체가 없다", async ({ page }) => {
  await page.goto(SEARCH);

  for (const { label } of AXES) {
    const chips = axisChips(page, label);
    for (let i = 0; i < (await chips.count()); i++) {
      const name = (await chips.nth(i).getAttribute("aria-label")) ?? "";
      expect(await countOf(chips.nth(i)), `${name} — 항목이 없는 값에 칩이 있다`).toBeGreaterThan(0);
    }
  }
});

// ── RED 21~24 : 도수 4구간 (FR-SEARCH-003 · ADR-0003) ─────────────────────

test("RED21,22 - 도수가 4개 칩이고 연속 슬라이더가 없다", async ({ page }) => {
  await page.goto(SEARCH);

  // 코퍼스에 없는 구간은 칩이 없으므로 4개 이하다. 지금은 논알콜까지 있어 4개다.
  expect(await axisChips(page, "도수 ABV").count()).toBeLessThanOrEqual(4);
  await expect(page.locator('input[type="range"]')).toHaveCount(0);
  // `abvMin`·`abvMax` 를 받는 자리를 만들지 않는다 (이슈 018 RED 21)
  await axisChips(page, "도수 ABV").first().click();
  expect(page.url()).not.toMatch(/abvM(in|ax)/);
});

/**
 * RED 23·24 — 구간 정의가 **한 곳**이다 (ADR-0003).
 *
 * 서버(`AbvBand.kt`)·탐색·파인더가 같은 경계를 쓴다. 서버와의 대조는 `facet-parity` 가
 * 하고, 여기서는 **탐색과 파인더가 같은 표를 쓰는지**를 본다 — 다르면 파인더가 추천한
 * 도수대를 탐색에서 다시 찾을 수 없다.
 */
test("RED23,24 - 파인더와 같은 구간 정의를 쓴다", async ({ page }) => {
  const source = readFileSync(join(process.cwd(), "../../packages/domain/src/search.ts"), "utf8");
  expect(source, "파인더 질문이 ABV_BANDS 를 쓰지 않는다 — 구간이 두 벌이 된다").toMatch(
    /ABV_BANDS\.map\(\(b\) => \(\{ ko: b\.colloquial/,
  );

  await page.goto(SEARCH);
  const chips = await axisChips(page, "도수 ABV").count();

  await page.goto("/finder");
  const options = await page.getByRole("button", { name: /무알콜|가볍게|적당히|독하게/ }).count();

  expect(options, "파인더의 도수 선택지가 4구간이 아니다").toBe(4);
  // 탐색은 코퍼스에 없는 구간을 빼므로 같거나 적다. 많아지면 정의가 갈린 것이다.
  expect(chips).toBeLessThanOrEqual(options);
});

// ── RED 25~29 : URL 계약 (FR-SEARCH-005) ─────────────────────────────────

/**
 * RED 25·27 — 필터가 쿼리스트링에 반영되고, **그 이름이 서버 API 와 같다** (SPEC-05 §4).
 *
 * "데이터가 커지면 서버 필터로 옮기되 URL 계약은 유지한다." 이름이 다르면 옮기는 날
 * 공유된 링크가 전부 깨진다.
 */
test("RED25,27 - 쿼리스트링 형식이 서버 API 와 같다", async ({ page }) => {
  await page.goto(SEARCH);

  await axisChips(page, "기주 BASE SPIRIT").nth(0).click();
  await axisChips(page, "기주 BASE SPIRIT").nth(1).click();
  await (await enabledChip(axisChips(page, "스타일 STYLE"))).click();
  await (await enabledSweet(page)).click();

  // 화면은 클릭 즉시 바뀌고 주소는 한 박자 뒤에 따라온다 (`router.replace`).
  // 주소를 읽는 테스트라 따라올 때까지 기다린다.
  await expect(page).toHaveURL(/sweet=/);

  const params = new URL(page.url()).searchParams;
  expect([...params.keys()].sort()).toEqual(["base", "style", "sweet"]);
  expect(params.get("base"), "같은 축의 복수 값은 콤마로 잇는다").toMatch(/^[a-z-]+,[a-z-]+$/);
  // 서버가 받는 이름 그대로다 — `bases`·`abvBands` 같은 내부 이름이 주소에 나타나면 안 된다
  expect(page.url()).not.toMatch(/[?&](bases|styles|methods|abvBands|flavors)=/);
});

test("RED26,28 - URL 을 공유하면 같은 결과가 나오고 뒤로가기가 동작한다", async ({ page }) => {
  await page.goto(SEARCH);
  await axisChips(page, "기주 BASE SPIRIT").first().click();
  await expect(page, "주소가 아직 따라오지 않았다").toHaveURL(/base=/);

  const shared = page.url();
  const expected = await resultCount(page);

  await page.goto(shared); // 새로 연 것과 같다

  // 정적으로 미리 그려진 화면이라 필터는 **브라우저에서** 적용된다 (SPEC-05 §4 —
  // 클라이언트 필터). 그래서 값이 들어오기를 기다린다. 서버 렌더로 바꾸면 첫 그림부터
  // 맞지만 칩을 누를 때마다 서버를 왕복하게 된다 (RED 36·37).
  await expect(page.locator(".results-count b")).toHaveText(String(expected));
  await expect(axisChips(page, "기주 BASE SPIRIT").first()).toHaveAttribute(
    "aria-pressed",
    "true",
  );

});

/**
 * RED 28 — 뒤로가기가 동작한다.
 *
 * 칩 하나에 히스토리 한 칸을 쓰지 않는다 (`router.replace`). 열 번 눌렀다고 뒤로가기를
 * 열 번 눌러야 화면을 벗어난다면 그것이 고장난 뒤로가기다. 주소는 그대로 바뀌므로
 * 공유·새로고침은 영향을 받지 않는다 (RED 26 이 그 쪽을 본다).
 */
test("RED28 - 필터를 여러 번 만져도 뒤로가기 한 번이면 벗어난다", async ({ page }) => {
  await page.goto("/cocktails/negroni");
  await page.getByRole("link", { name: /탐색으로/ }).click();
  await expect(page).toHaveURL(new RegExp(`${SEARCH}$`));

  await axisChips(page, "기주 BASE SPIRIT").first().click();
  await (await enabledChip(axisChips(page, "스타일 STYLE"))).click();
  await expect(page).toHaveURL(/style=/); // 주소가 따라온 뒤에 뒤로 간다

  await page.goBack();
  expect(new URL(page.url()).pathname, "필터 조작이 히스토리를 채웠다").toBe(
    "/cocktails/negroni",
  );
});

/**
 * RED 29 — 필터 화면은 `noindex` 다 (`NFR-S-02` — 배포 차단).
 *
 * 상세·카테고리는 색인해야 하므로 **전역으로 걸지 않는다.** 그 경계가 지켜지는지
 * 두 쪽을 같이 본다.
 */
test("RED29 - 탐색은 noindex 이고 상세·카테고리는 아니다", async ({ page }) => {
  await page.goto(SEARCH);
  await expect(page.locator('meta[name="robots"]')).toHaveAttribute("content", /noindex/);

  for (const indexed of ["/cocktails/base/gin", "/cocktails/negroni"]) {
    await page.goto(indexed);
    const robots = page.locator('meta[name="robots"]');
    if (await robots.count()) {
      await expect(robots, `${indexed} 가 색인에서 빠졌다`).not.toHaveAttribute(
        "content",
        /noindex/,
      );
    }
  }
});

/** 필터 경로는 사이트맵에 없다 (`NFR-S-02` · 이슈 039 RED 25 와 같은 규칙). */
test("RED29 - 사이트맵에 탐색 경로가 없다", async ({ request }) => {
  const xml = await (await request.get("/sitemap.xml")).text();
  expect(xml).not.toContain(SEARCH);
});

// ── RED 30~34 : 접근성 (NFR-A-04·05·06·08) ───────────────────────────────

test("RED30,31,32 - 비활성 칩이 개수와 함께 읽힌다", async ({ page }) => {
  await page.goto(SEARCH);
  const flavors = axisChips(page, "맛 / 향 FLAVOR PROFILE");
  await flavors.nth(0).click();
  await (await enabledChip(flavors, 1)).click();

  for (let i = 0; i < (await flavors.count()); i++) {
    const c = flavors.nth(i);
    if ((await countOf(c)) !== 0) continue;

    await expect(c).toBeDisabled(); // RED 30 — 색이 아니라 상태다
    expect(await c.getAttribute("aria-label")).toMatch(/0개$/); // RED 31
    await expect(c.locator(".count"), "숫자를 지우면 색만 남는다").toHaveText("0"); // RED 32
  }
});

test("RED33,34 - 키보드로 모든 칩에 도달하고 아웃라인이 보인다", async ({ page }) => {
  await page.goto(SEARCH);

  const chips = axisChips(page, "기주 BASE SPIRIT");
  const first = chips.first();

  // 하이드레이션이 끝나기 전에 준 포커스는 리액트가 붙으면서 풀린다.
  // 붙을 때까지 다시 준다 — 화면의 문제가 아니라 시점의 문제다.
  await expect
    .poll(async () => {
      await first.focus();
      return first.evaluate((el) => el === document.activeElement);
    })
    .toBe(true);

  // 탭이 다음 칩으로 간다 — 칩이 div 였다면 여기서 멈춘다
  await page.keyboard.press("Tab");
  await expect(chips.nth(1)).toBeFocused();

  const outline = await chips.nth(1).evaluate((el) => getComputedStyle(el).outlineWidth);
  expect(outline, "focus-visible 아웃라인이 없다 (NFR-A-05)").not.toBe("0px");

  await page.keyboard.press("Enter");
  await expect(chips.nth(1)).toHaveAttribute("aria-pressed", "true");
});

// ── RED 35~37 : 성능 ─────────────────────────────────────────────────────

/**
 * RED 37 — **전체 코퍼스를 한 번만 받는다** (SPEC-05 §4).
 *
 * 필터를 만질 때마다 서버를 부르면 클라이언트 필터를 고른 이유가 사라진다.
 * 코퍼스는 서버 컴포넌트가 받아 넘기므로 브라우저에서는 목록 호출이 **0** 이다.
 */
test("RED37 - 필터를 만져도 목록을 다시 받지 않는다", async ({ page }) => {
  const calls: string[] = [];
  page.on("request", (r) => {
    if (/\/api\/v1\/cocktails(\?|$)/.test(r.url())) calls.push(r.url());
  });

  await page.goto(SEARCH);
  await axisChips(page, "기주 BASE SPIRIT").first().click();
  await axisChips(page, "스타일 STYLE").first().click();
  await axisChips(page, "맛 / 향 FLAVOR PROFILE").first().click();

  expect(calls, "필터마다 목록을 다시 받고 있다").toEqual([]);
});

/**
 * RED 35 — LCP ≤ 2.5s (`NFR-P-01`).
 *
 * 로컬 프로덕션 빌드에서 잰 값이라 **회귀 감지용**이다. 실제 게이트는 이슈 046 의
 * Lighthouse 다 (SPEC-04 §9.1) — 네트워크·기기 조건까지 재는 것은 거기다.
 */
test("RED35 - LCP 가 2.5s 이하다", async ({ page }) => {
  await page.goto(SEARCH);

  const lcp = await page.evaluate(
    () =>
      new Promise<number>((resolve) => {
        new PerformanceObserver((list) => {
          const entries = list.getEntries();
          resolve(entries[entries.length - 1].startTime);
        }).observe({ type: "largest-contentful-paint", buffered: true });
        setTimeout(() => resolve(0), 5000);
      }),
  );

  expect(lcp, "LCP 를 재지 못했다").toBeGreaterThan(0);
  expect(lcp).toBeLessThanOrEqual(2500);
});

/**
 * RED 36 — 필터 조작이 INP 200ms 이하 (`NFR-P-02`).
 *
 * 클릭에서 결과 수가 바뀔 때까지를 잰다. INP 자체가 아니라 그 대리값이고, 여기서
 * 느리면 INP 도 반드시 느리다. 실제 계측은 이슈 046 이다.
 */
test("RED36 - 필터 조작이 즉시 반영된다", async ({ page }) => {
  await page.goto(SEARCH);
  const before = await resultCount(page);

  const started = Date.now();
  await axisChips(page, "기주 BASE SPIRIT").first().click();
  await expect.poll(async () => resultCount(page)).not.toBe(before);
  const elapsed = Date.now() - started;

  expect(elapsed, `필터 반영에 ${elapsed}ms 걸렸다`).toBeLessThan(1000);
});

// ── RED 38 : 법적 (NFR-L-01) ─────────────────────────────────────────────

test("RED38 - 과음 경고가 하단에 있다", async ({ page }) => {
  await page.goto(SEARCH);
  await expect(page.getByTestId("legal-notice")).toBeVisible();
});

// ── 라우트 ────────────────────────────────────────────────────────────────

/** SPEC-05 §4 가 정한 자리다. 홈(`/`)은 색인 대상이라 필터 화면을 둘 수 없다. */
test("탐색 화면이 /cocktails/search 에 있다", async ({ page }) => {
  expect(existsSync(join(process.cwd(), "app/cocktails/search/page.tsx"))).toBe(true);

  const res = await page.goto("/");
  expect(res?.status()).toBe(200);
  // 이동이 응답으로 오는지 화면에서 일어나는지는 프레임워크의 사정이다. 도착지만 본다.
  await expect(page, "`/` 가 탐색으로 보내지 않는다").toHaveURL(new RegExp(`${SEARCH}$`));
});

// ── 검색이 탐색 안으로 들어왔다 ───────────────────────────────────────────

/**
 * 이름 검색은 **필터 패널 밖**에 있다.
 *
 * 예전에는 패널 맨 아래 한 칸이었다. 가장 자주 손이 가는 것이 가장 눈에 안 띄는 자리에
 * 있었고, 그래서 통합 검색이 탭으로 따로 있어야 할 것처럼 보였다. 자리를 지키는 이유는
 * 배치가 곧 "이것이 아래 전체에 걸린다" 는 말이기 때문이다.
 */
test("검색 칸이 필터 패널 밖에 있다", async ({ page }) => {
  await page.goto(SEARCH);

  const field = page.locator(".search-field");
  await expect(field).toBeVisible();
  await expect(page.locator(".filter-panel .search-field")).toHaveCount(0);

  // 필터 패널보다 먼저 온다 — 문서 순서가 곧 읽는 순서이고 탭 순서다.
  const first = await page.evaluate(() => {
    const q = document.querySelector(".search-field");
    const panel = document.querySelector(".filter-panel");
    return q && panel ? q.compareDocumentPosition(panel) & Node.DOCUMENT_POSITION_FOLLOWING : 0;
  });
  expect(first, "검색 칸이 필터 패널보다 뒤에 있다").toBeTruthy();
});

/** 검색어와 필터는 **AND** 다. 한쪽이 다른 쪽을 지우지 않는다. */
test("검색어와 필터가 함께 걸린다", async ({ page }) => {
  await page.goto(SEARCH);

  const box = page.getByLabel("칵테일 이름 검색");
  await box.fill("네그로니");
  // 네그로니 · 화이트 네그로니 · 킹스톤 네그로니
  await expectResults(page, 3);

  // 기주를 럼으로 좁히면 킹스톤 네그로니만 남는다 — 검색어가 살아 있다는 뜻이다
  await axisChips(page, "기주 BASE SPIRIT").filter({ hasText: "럼" }).first().click();
  await expectResults(page, 1);
  await expect(page.locator(".cocktail-card").first()).toContainText("킹스톤");
});

/** 검색어를 한 번에 비운다. 글자를 하나씩 지우면 그때마다 결과가 다시 계산된다. */
test("검색어를 지우는 버튼이 있다", async ({ page }) => {
  await page.goto(SEARCH);

  const box = page.getByLabel("칵테일 이름 검색");
  // 값이 없을 때는 나오지 않는다 — 누를 것이 없는 버튼을 두지 않는다
  await expect(page.getByRole("button", { name: "검색어 지우기" })).toHaveCount(0);

  await box.fill("네그로니");
  await page.getByRole("button", { name: "검색어 지우기" }).click();

  await expect(box).toHaveValue("");
  await expectResults(page, 49);
});

// ── 쪽 넘김 ───────────────────────────────────────────────────────────────

/**
 * 거른 결과를 **전부 그리지 않는다**.
 *
 * 코퍼스를 통째로 받아 클라이언트에서 거르는 것은 그대로다 (SPEC-05 §4). 그래야 칩 옆
 * 숫자가 실시간으로 맞는다. 다만 **그리는 것**은 한 쪽뿐이다 — Phase 1 목표가 500 종이라
 * (SPEC-07 §1.5) 필터를 안 걸면 500장이 한꺼번에 DOM 에 오른다.
 *
 * 지금 코퍼스는 49종이라 한 쪽에 다 들어간다. 그 경계를 여기 적어 둔다 —
 * 코퍼스가 `PAGE_SIZE` 를 넘기는 날 이 테스트가 먼저 말해 준다.
 */
test("한 쪽에 다 들어가면 쪽 넘김이 없다", async ({ page }) => {
  await page.goto(SEARCH);

  const total = await page.locator(".cocktail-card").count();
  expect(total, "코퍼스가 한 쪽을 넘었다 — 쪽 넘김 동작을 여기서 함께 봐야 한다").toBeLessThanOrEqual(50);
  await expect(page.locator(".pager")).toHaveCount(0);
});

/** 주소로 들어온 쪽이 범위를 넘으면 마지막 쪽으로 당긴다. 빈 화면을 보지 않는다. */
test("범위를 넘는 쪽 번호는 마지막 쪽이 된다", async ({ page }) => {
  await page.goto(`${SEARCH}?page=99`);
  await expect(page.locator(".cocktail-card").first()).toBeVisible();
});
