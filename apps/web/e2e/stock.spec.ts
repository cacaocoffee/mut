import { test, expect, type Page } from "@playwright/test";

/**
 * 내 술장 역검색 (`R-F2.2-1`·`2`·`4`·`5`).
 *
 * ## 요체는 **가니시를 빼는 것**이다
 *
 * `R-F2.2-5` — "가니시와 얼음, 물은 보유 재료 판정에서 제외한다. **이걸 요구하면 매칭이
 * 거의 안 된다.**" 데이터에서 레몬 필 · 오렌지 필 · 민트가 상위권이라, 빼지 않으면
 * 오렌지 껍질이 없어서 네그로니를 못 만든다는 답이 나온다.
 *
 * 아래 첫 테스트가 그것을 본다 — 네그로니의 재료 넷 중 오렌지 필을 **고르지 않고도**
 * 만들 수 있는 것에 나와야 한다.
 *
 * ## 「1개만 더」가 이 화면의 값이다
 *
 * PRD 가 `R-F2.2-2` 를 "체류와 재방문의 핵심 동인" 이라고 적었다. 만들 수 있는 것만
 * 보여 주면 재료가 적은 사람에게는 빈 화면이고, 그 자리에서 나간다. **무엇이 빠졌는지**
 * 까지 말하는지를 함께 본다.
 */

const MY_BAR = "/my-bar";

/** 화면이 브라우저에서 붙기를 기다린다. 붙기 전 클릭은 아무 일도 하지 않는다. */
async function ready(page: Page) {
  await page.locator("[data-ready]").waitFor();
}

/** 왼쪽 패널에서 재료를 담는다. 이름이 정확히 같은 칩만 고른다 (`럼` 이 `화이트 럼` 을 잡지 않게). */
async function pick(page: Page, ...names: string[]) {
  for (const name of names) {
    await page
      .locator(".filter-panel .chip", { hasText: new RegExp(`^${name}$`) })
      .first()
      .click();
  }
}

function shelfCount(page: Page, title: string) {
  return page.locator(".stock-shelf", { hasText: title }).locator(".results-count b");
}

/**
 * RED 1 — **가니시가 없어도 만들 수 있다** (`R-F2.2-5`).
 *
 * 네그로니는 진 · 캄파리 · 스위트 베르무트 · 오렌지 필이다. 앞의 셋만 담아도 나와야 한다.
 * 이 하나가 깨지면 기능 전체가 쓸모없어진다 — 아무것도 안 나오는 화면이 된다.
 */
test("RED1 - 가니시를 빼고 판정한다", async ({ page }) => {
  await page.goto(MY_BAR);
  await ready(page);

  await pick(page, "진", "캄파리", "스위트 베르무트");

  await expect(shelfCount(page, "지금 만들 수 있는 것")).toHaveText("1");
  await expect(page.locator(".card-grid .cocktail-card")).toContainText("네그로니");
});

/** RED 2 — 가니시는 **고를 수도 없다**. 판정에 안 쓰는 것을 고르게 하면 왜 안 바뀌냐가 된다. */
test("RED2 - 가니시는 목록에 없다", async ({ page }) => {
  await page.goto(MY_BAR);
  await ready(page);

  const labels = await page.locator(".filter-panel .chip").allInnerTexts();
  for (const garnish of ["레몬 필", "오렌지 필", "민트", "마라스키노 체리", "소금"]) {
    expect(labels, `${garnish} 가 고를 수 있는 재료로 나온다 (R-F2.2-5)`).not.toContain(garnish);
  }
});

/**
 * RED 3 — 재료 **1개만 더 있으면** 목록과 빠진 재료 (`R-F2.2-2`).
 *
 * 진 · 캄파리만 담으면 네그로니는 스위트 베르무트 하나가 빈다. 무엇이 빠졌는지 적지
 * 않으면 목록이 아니라 놀림이다.
 */
test("RED3 - 하나만 더 있으면 목록에 빠진 재료가 적힌다", async ({ page }) => {
  await page.goto(MY_BAR);
  await ready(page);

  await pick(page, "진", "캄파리");

  await expect(shelfCount(page, "지금 만들 수 있는 것")).toHaveText("0");

  // 「가진 재료가 들어가는 것」에도 같은 줄 모양이 쓰이고 거기에 화이트·킹스톤 네그로니가
  // 있다. 묶음 안으로 좁혀야 이름이 겹치지 않는다.
  const shelf = page.locator(".stock-shelf", { hasText: "재료 하나만 더 있으면" });
  const row = shelf.locator(".one-away__row").filter({ hasText: /^네그로니/ });
  await expect(row).toBeVisible();
  await expect(row.locator(".one-away__need")).toHaveText("스위트 베르무트");
});

/**
 * 빠진 재료는 그 자리에서 담는다.
 *
 * 이름만 적혀 있으면 위 선반으로 올라가 같은 이름을 다시 찾아야 한다 — 「하나만 더」가
 * 만든 동기를 화면이 스스로 끊는 왕복이다. 이름이 곧 담기 버튼이다.
 */
test("하나만 더의 빠진 재료를 누르면 바로 담긴다", async ({ page }) => {
  await page.goto(MY_BAR);
  await ready(page);

  await pick(page, "진", "캄파리");

  const shelf = page.locator(".stock-shelf", { hasText: "재료 하나만 더 있으면" });
  const row = shelf.locator(".one-away__row").filter({ hasText: /^네그로니/ });
  await row.getByRole("button", { name: "스위트 베르무트 담기" }).click();

  // 담기는 순간 네그로니가 「만들 수 있는 것」으로 올라간다
  await expect(shelfCount(page, "지금 만들 수 있는 것")).toHaveText("1");
  await expect(page.locator(".card-grid .cocktail-card")).toContainText("네그로니");
});

/** RED 4 — 택일로 적힌 줄은 **둘 중 아무거나** 로 읽힌다 (`버번 또는 라이`). */
test("RED4 - 택일 재료는 어느 쪽이든 채워진다", async ({ page }) => {
  await page.goto(MY_BAR);
  await ready(page);

  // 올드 패션드 = (버번 또는 라이) + 설탕 시럽 + 앙고스투라. 라이로 채워도 성립해야 한다
  await pick(page, "라이 위스키", "설탕 시럽", "앙고스투라 비터스");
  await expect(page.locator(".card-grid .cocktail-card")).toContainText("올드 패션드");
});

/** RED 5 — 새로고침해도 담은 것이 남는다. 로그인 없이 쓰는 것이 전제다 (`R-F2.2-4`). */
test("RED5 - 담은 재료가 새로고침을 넘긴다", async ({ page }) => {
  await page.goto(MY_BAR);
  await ready(page);

  await pick(page, "진", "캄파리", "스위트 베르무트");
  await page.reload();
  await ready(page);

  await expect(page.locator('.filter-panel .chip[aria-pressed="true"]')).toHaveCount(3);
  await expect(shelfCount(page, "지금 만들 수 있는 것")).toHaveText("1");
});

/** RED 6 — 비우면 처음 상태로 돌아간다. */
test("RED6 - 비움이 담은 것을 지운다", async ({ page }) => {
  await page.goto(MY_BAR);
  await ready(page);

  await pick(page, "진", "캄파리");
  await page.getByRole("button", { name: /비움/ }).click();

  await expect(page.locator('.filter-panel .chip[aria-pressed="true"]')).toHaveCount(0);
  await expect(page.locator(".empty-state")).toBeVisible();
});

/**
 * RED 7 — 아무것도 안 담았을 때가 **빈 화면이 아니다**.
 *
 * 무엇을 하는 곳인지와 누를 것을 준다. 첫 체크 하나가 결과를 바꾸는 것을 보여 주지
 * 않으면 사람은 왜 골라야 하는지 모른 채 나간다.
 */
test("RED7 - 빈 술장이 다음 행동을 준다", async ({ page }) => {
  await page.goto(MY_BAR);
  await ready(page);

  const empty = page.locator(".empty-state");
  await expect(empty).toBeVisible();

  // 권하는 재료를 누르면 그대로 담긴다
  await empty.locator(".chip").first().click();
  await expect(page.locator('.filter-panel .chip[aria-pressed="true"]')).toHaveCount(1);
});

/**
 * RED 8 — 「1개만 더」는 **아무것도 안 담았을 때 비어 있다**.
 *
 * 빈 술장에서는 재료가 하나뿐인 칵테일이 전부 걸린다. 그것은 추천이 아니라 목록이다.
 */
test("RED8 - 빈 술장에서는 하나만 더가 나오지 않는다", async ({ page }) => {
  await page.goto(MY_BAR);
  await ready(page);

  await expect(page.locator(".one-away__row")).toHaveCount(0);
});

/** RED 9 — 담은 것에 따라 내용이 달라지는 화면이라 색인하지 않는다 (`PRIN-P06` · `NFR-S-02`). */
test("RED9 - 내 술장은 noindex 다", async ({ page }) => {
  await page.goto(MY_BAR);
  await expect(page.locator('head meta[name="robots"]')).toHaveAttribute("content", /noindex/);
});

/**
 * RED 13 — **가진 재료가 들어가는 것**과 **다음에 살 것**.
 *
 * 캄파리 · 진 · 카시스만 있는 사람에게는 「만들 수 있는 것」도 「하나만 더」도 거의 비어
 * 있다. 그러면 뭘 사야 하는지 알 방법이 없다. 두 칸 이상 빈 것까지 펼치고, **빠진 재료가
 * 겹치는 것을 코드가 세어** 위에 적는다 — 스무 줄을 눈으로 세게 하지 않는다.
 */
test("RED13 - 멀리 있는 것과 다음에 살 것이 나온다", async ({ page }) => {
  await page.goto(MY_BAR);
  await ready(page);

  await pick(page, "캄파리", "진", "크렘 드 카시스");

  const shelf = page.locator(".stock-shelf", { hasText: "가진 재료가 들어가는 것" });
  await expect(shelf.locator(".one-away__row").first()).toBeVisible();

  // 스위트 베르무트가 네그로니(하나만 더) · 불바디에 · 킹스톤 네그로니 · 비어 아메리카노를
  // 막고 있다. 그것이 첫 줄이어야 한다 — 이 화면이 답하려는 물음이 "뭘 사야 하나" 다.
  await expect(shelf.locator(".buy-hint__list li").first()).toContainText("스위트 베르무트");
});

/** RED 14 — 세 묶음이 서로 겹치지 않는다. 같은 잔을 세 번 보여 주면 새로 아는 것이 없다. */
test("RED14 - 세 묶음에 같은 칵테일이 두 번 나오지 않는다", async ({ page }) => {
  await page.goto(MY_BAR);
  await ready(page);

  await pick(page, "진", "캄파리", "스위트 베르무트");

  const names = [
    ...(await page.locator(".card-grid .cocktail-card h3, .card-grid .cocktail-card").allInnerTexts()),
  ]
    .concat(await page.locator(".one-away__name b").allInnerTexts())
    .map((t) => t.split("\n")[0].trim())
    .filter(Boolean);

  const dupes = names.filter((n, i) => names.indexOf(n) !== i);
  expect(dupes, `두 번 나온 것: ${dupes.join(", ")}`).toEqual([]);
});

/** RED 15 — 빈 술장에서는 「다음에 살 것」이 없다. 담은 것이 없으면 추천할 근거도 없다. */
test("RED15 - 빈 술장에는 다음에 살 것이 없다", async ({ page }) => {
  await page.goto(MY_BAR);
  await ready(page);
  await expect(page.locator(".buy-hint")).toHaveCount(0);
});

// ── 재료 찾기 ─────────────────────────────────────────────────────────────

/** 담을 재료를 이름으로 찾는 칸. 59종을 훑는 대신 치는 길이다. */
function picker(page: Page) {
  return page.getByLabel("담을 재료를 이름으로 찾습니다");
}

/** RED 16 — 이름 일부로 찾으면 해당하는 것만 나온다. */
test("RED16 - 이름으로 재료를 찾는다", async ({ page }) => {
  await page.goto(MY_BAR);
  await ready(page);

  await picker(page).fill("베르무트");
  const options = page.locator(".search-box__option");
  await expect(options).toHaveCount(2);
  await expect(options.first()).toContainText("스위트 베르무트");
});

/**
 * RED 17 — **별칭으로도 찾힌다.**
 *
 * `data.ts` 가 같은 것을 `쿠앵트로` · `코앵트로` 두 표기로 적었고 마스터가 그것을 흡수했다
 * (`ingredients.ts` 의 `aliases`). 찾기가 그 목록을 다시 만들지 않는다는 뜻이다.
 */
test("RED17 - 별칭으로도 찾힌다", async ({ page }) => {
  await page.goto(MY_BAR);
  await ready(page);

  await picker(page).fill("코앵트로");
  await expect(page.locator(".search-box__option").first()).toContainText("쿠앵트로");
});

/** RED 18 — 치고 Enter 면 담긴다. 고른 것이 없으면 첫 줄이다 — 가장 흔한 손놀림이다. */
test("RED18 - Enter 로 담고 입력이 비워진다", async ({ page }) => {
  await page.goto(MY_BAR);
  await ready(page);

  await picker(page).fill("캄파리");
  await picker(page).press("Enter");

  await expect(page.locator('.filter-panel .chip[aria-pressed="true"]')).toHaveCount(1);
  // 비워야 다음 재료를 바로 칠 수 있다
  await expect(picker(page)).toHaveValue("");
});

/** RED 19 — 연달아 담긴다. 한 호흡에 세 개를 넣는 것이 이 칸을 둔 이유다. */
test("RED19 - 연달아 담을 수 있다", async ({ page }) => {
  await page.goto(MY_BAR);
  await ready(page);

  for (const name of ["진", "캄파리", "스위트 베르"]) {
    await picker(page).fill(name);
    await picker(page).press("Enter");
  }

  await expect(page.locator('.filter-panel .chip[aria-pressed="true"]')).toHaveCount(3);
  await expect(shelfCount(page, "지금 만들 수 있는 것")).toHaveText("1");
});

/** RED 20 — 없는 이름이면 그렇다고 말한다. 목록이 그냥 사라지면 "찾는 중" 과 구분이 안 된다. */
test("RED20 - 없는 이름에 안내가 나온다", async ({ page }) => {
  await page.goto(MY_BAR);
  await ready(page);

  await picker(page).fill("없는재료");
  await expect(page.locator(".search-box__option--empty")).toBeVisible();
});

/** RED 21 — 화살표와 `aria-activedescendant` 로 키보드만으로 고를 수 있다 (`NFR-A-04`). */
test("RED21 - 키보드로 고를 수 있다", async ({ page }) => {
  await page.goto(MY_BAR);
  await ready(page);

  const box = picker(page);
  await box.fill("베르무트");
  await box.press("ArrowDown");
  await box.press("ArrowDown");
  await expect(box).toHaveAttribute("aria-activedescendant", /.+/);

  await box.press("Enter");
  // 둘째 줄이 드라이 베르무트다
  await expect(
    page.locator('.filter-panel .chip[aria-pressed="true"]', { hasText: "드라이 베르무트" })
  ).toBeVisible();
});

// ── 재료 사전 ─────────────────────────────────────────────────────────────

/** RED 10 — 재료 상세가 **이 재료를 쓰는 칵테일**을 보여 준다 (`FR-INGREDIENT-002`). */
test("RED10 - 재료 상세에 그 재료를 쓰는 칵테일이 있다", async ({ page }) => {
  await page.goto("/ingredients/campari");

  await expect(page.locator("h1")).toContainText("캄파리");
  // 네그로니 · 불바디에 · 올드 팔 · 바나나 불바디에 · 킹스톤 네그로니 …
  await expect(page.locator(".card-grid .cocktail-card").first()).toBeVisible();
  await expect(page.locator(".card-grid .cocktail-card")).toContainText(["네그로니"]);
});

/** RED 11 — 없는 재료는 404 다. 미리 만든 것만 존재한다 (이슈 038 의 soft 404 를 되풀이하지 않는다). */
test("RED11 - 없는 재료 슬러그는 404 다", async ({ page }) => {
  const res = await page.goto("/ingredients/no-such-thing");
  expect(res?.status()).toBe(404);
});

/** RED 12 — 재료 사전은 색인한다. 사람마다 달라지지 않고 실제로 들어오는 질의다. */
test("RED12 - 재료 사전은 색인 대상이다", async ({ page, request }) => {
  await page.goto("/ingredients");
  await expect(page.locator('head meta[name="robots"][content*="noindex"]')).toHaveCount(0);

  const xml = await (await request.get("/sitemap.xml")).text();
  expect(xml, "재료 사전이 사이트맵에 없다").toContain("/ingredients/campari");
  expect(xml, "내 술장이 사이트맵에 있다 — 담은 것에 따라 달라지는 화면이다").not.toContain("/my-bar");
});
