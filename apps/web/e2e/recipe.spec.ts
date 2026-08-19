import { test, expect, type Locator, type Page } from "@playwright/test";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { ML_PER_OZ, MAX_SERVINGS, formatQuantity } from "@mut/domain";

/**
 * ISSUE-043 — 상세 인터랙션: 잔 수 · 단위 · 대체재
 * (`FR-COCKTAIL-019`·`020`·`021` · `R-F1.3-2` · `NFR-A-04` · `NFR-P-02`).
 *
 * ## 두 층을 따로 본다
 *
 * 환산 **규칙**은 `@mut/domain` 의 순수 함수라 값으로 바로 잰다 — 30ml 를 두 잔으로 하면
 * 60ml 인가, `1조각` 은 그대로인가, `dash` 는 oz 로 안 바뀌는가. 브라우저를 띄워 확인할
 * 성질이 아니고, 표에서 읽으면 자릿수·공백까지 함께 걸려 무엇이 틀렸는지 흐려진다.
 *
 * 화면은 **그 규칙이 실제로 걸리는지**와 조작 가능성(키보드·펼침·안내)을 본다.
 *
 * ## 계측은 이 이슈가 아니다
 *
 * RED 24~27 의 `recipe_interact` 는 이슈 049(#51)가 소유한다 — 그 이슈가
 * `apps/web/lib/analytics` 를 만들고 화면에 호출을 심으며, 기반인 035(#37)가 아직 열려 있다.
 */

const NEGRONI = "/cocktails/negroni"; // 진 30 · 캄파리 30 · 베르무트 30 · 오렌지 필 `1조각`
// `2 dash` 와 `1개` 가 있다. 발행분이라 API 를 붙여도 있다 — 마티니는 향·맛 서술이 없어
// 아직 draft 이고(`GATE-COCKTAIL-01`), 그 차이가 코퍼스 둘 사이에서 테스트를 가른다.
const MANHATTAN = "/cocktails/manhattan";

function panel(page: Page) {
  return page.locator("table.table").first();
}

/** 재료 이름으로 그 줄의 용량 칸을 집는다. */
function amountOf(page: Page, nameKo: string): Locator {
  return panel(page).locator("tr", { hasText: nameKo }).locator(".amount-cell").first();
}

async function ready(page: Page, path: string) {
  await page.goto(path);
  // 스텝퍼가 눌리려면 스크립트가 붙어야 한다.
  await expect(page.getByRole("button", { name: "잔 수 늘리기" })).toBeEnabled();
}

async function addServing(page: Page, times = 1) {
  for (let i = 0; i < times; i++) await page.getByRole("button", { name: "잔 수 늘리기" }).click();
}

/**
 * 표기 단위를 고른다.
 *
 * 라디오 입력은 `opacity: 0` 으로 감춰져 있고 눈에 보이는 것은 라벨이다 — 사용자가 누르는
 * 것도 라벨이라 테스트도 그것을 누른다 (키보드 경로는 입력에 포커스를 준다).
 */
async function chooseUnit(page: Page, unit: "ml" | "oz") {
  await page.locator("label.seg-opt").filter({ hasText: new RegExp(`^${unit}$`) }).click();
}

// ── 환산 규칙 (RED 2·3·5·8·10~15·17·18) ──────────────────────────────────

test.describe("환산 규칙", () => {
  const ml = (amount: number) => ({ amount, unit: "ml", isScalable: true });

  test("RED2,8 - ml 이 배수로 환산되고 소수가 살아 있다", () => {
    expect(formatQuantity(ml(30), 2, "ml")).toBe("60 ml");
    expect(formatQuantity(ml(7.5), 3, "ml")).toBe("22.5 ml"); // RED 8
    expect(formatQuantity(ml(30), 1, "ml")).toBe("30 ml"); // 30.0 이 아니다
  });

  test("RED3 - amount_label 재료는 환산되지 않는다", () => {
    const peel = { amountLabel: "1조각", isScalable: false };
    for (const servings of [1, 2, MAX_SERVINGS]) {
      expect(formatQuantity(peel, servings, "ml")).toBe("1조각");
      expect(formatQuantity(peel, servings, "oz")).toBe("1조각");
    }
  });

  /**
   * RED 4 — `2 dash` 가 라벨인지 수량인지가 이슈에서 모호했다.
   *
   * **`amountLabel` 이 있으면 제외, `unit=dash` 는 환산**으로 정했다. 지금 데이터는 전부
   * 라벨 쪽이다 (GAPS G-35) — 그래도 규칙은 양쪽 다 지켜야 나중에 데이터가 바뀔 때 안 깨진다.
   */
  test("RED4 - dash 는 라벨이면 그대로, 수량이면 배수다", () => {
    expect(formatQuantity({ amountLabel: "2 dash", isScalable: false }, 3, "ml")).toBe("2 dash");
    expect(formatQuantity({ amount: 2, unit: "dash", isScalable: true }, 3, "ml")).toBe("6 dash");
  });

  test("RED5 - top_up 은 수량이 없고 환산되지 않는다", () => {
    const topUp = { unit: "top_up", isScalable: false };
    expect(formatQuantity(topUp, 4, "ml")).toBe("채운다");
    expect(formatQuantity(topUp, 4, "oz")).toBe("채운다");
  });

  test("RED10,15 - oz 변환이 정확한 비율을 쓰고 소수 1자리로 읽힌다", () => {
    expect(ML_PER_OZ).toBe(29.5735); // 30 으로 어림하지 않는다
    expect(formatQuantity(ml(30), 1, "oz")).toBe("1 oz"); // 1.0141… → 1
    expect(formatQuantity(ml(45), 1, "oz")).toBe("1.5 oz");
    expect(formatQuantity(ml(10), 1, "oz")).toBe("0.3 oz");
  });

  test("RED11,12,13,14 - ml 밖의 단위는 oz 로 바뀌지 않는다", () => {
    expect(formatQuantity({ amount: 2, unit: "dash", isScalable: true }, 1, "oz")).toBe("2 dash");
    expect(formatQuantity({ amount: 1, unit: "barspoon", isScalable: true }, 1, "oz")).toBe(
      "1 바스푼",
    );
    expect(formatQuantity({ amount: 2, unit: "piece", isScalable: true }, 1, "oz")).toBe("2 개");
    expect(formatQuantity({ unit: "top_up", isScalable: false }, 1, "oz")).toBe("채운다");
  });

  test("RED17,18 - 잔 수와 단위를 함께 걸어도 결과가 같다", () => {
    // 30ml × 2잔 = 60ml = 2.0288… oz
    expect(formatQuantity(ml(30), 2, "oz")).toBe("2 oz");
    // 곱하고 나누는 순서가 결과를 바꾸지 않는다 — 한 식으로 계산하기 때문이다
    expect(formatQuantity(ml(30), 2, "oz")).toBe(formatQuantity(ml(60), 1, "oz"));
  });

  test("RED6 - 배수 대상 판정을 화면이 만들지 않는다", () => {
    const panelSource = readFileSync(join(process.cwd(), "components/recipe-panel.tsx"), "utf8");
    expect(panelSource, "화면이 amountLabel 을 보고 다시 판정하고 있다").not.toMatch(
      /amountLabel\s*[=!]==?/,
    );

    const viewSource = readFileSync(join(process.cwd(), "lib/cocktail-view.ts"), "utf8");
    // API 경로는 서버 값을 그대로 옮긴다. 프로토타입 폴백만 스스로 정한다.
    expect(viewSource).toMatch(/isScalable: line\.isScalable/);
  });
});

// ── 잔 수 (RED 1·7) ──────────────────────────────────────────────────────

test("RED1,2 - 잔 수를 올리면 계량이 따라 오른다", async ({ page }) => {
  await ready(page, NEGRONI);

  await expect(amountOf(page, "진")).toHaveText("30 ml");
  await addServing(page);
  await expect(amountOf(page, "진")).toHaveText("60 ml");
  await addServing(page);
  await expect(amountOf(page, "진")).toHaveText("90 ml");

  await page.getByRole("button", { name: "잔 수 줄이기" }).click();
  await expect(amountOf(page, "진")).toHaveText("60 ml");
});

test("RED3 - 고정 표기 재료는 잔 수를 올려도 그대로다", async ({ page }) => {
  await ready(page, NEGRONI);

  await expect(amountOf(page, "오렌지 필")).toHaveText("1조각");
  await addServing(page, 3);
  await expect(amountOf(page, "진")).toHaveText("120 ml");
  await expect(amountOf(page, "오렌지 필"), "고정 표기가 배수를 탔다").toHaveText("1조각");
});

test("RED4 - dash 표기도 배수를 타지 않는다", async ({ page }) => {
  await ready(page, MANHATTAN);

  await expect(amountOf(page, "앙고스투라 비터스")).toHaveText("2 dash");
  await addServing(page, 2);
  await expect(amountOf(page, "앙고스투라 비터스")).toHaveText("2 dash");
});

/** RED 7 — 상한은 8잔이다. 셰이커가 넘치는 수를 화면이 제안하지 않는다. */
test("RED7 - 잔 수 상한이 8이다", async ({ page }) => {
  await ready(page, NEGRONI);

  const up = page.getByRole("button", { name: "잔 수 늘리기" });
  await addServing(page, MAX_SERVINGS - 1);

  await expect(page.locator(".stepper .value b")).toHaveText(String(MAX_SERVINGS));
  await expect(up, "상한을 넘겨 누를 수 있다").toBeDisabled();
  await expect(page.getByRole("button", { name: "잔 수 줄이기" })).toBeEnabled();
});

test("잔 수 1에서는 줄이기가 잠긴다", async ({ page }) => {
  await ready(page, NEGRONI);
  await expect(page.getByRole("button", { name: "잔 수 줄이기" })).toBeDisabled();
});

// ── 단위 (RED 9·16) ──────────────────────────────────────────────────────

test("RED9,11 - ml 에서 oz 로 바꾸면 ml 만 바뀐다", async ({ page }) => {
  await ready(page, MANHATTAN);

  await chooseUnit(page, "oz");
  await expect(amountOf(page, "라이 위스키")).toHaveText("2 oz"); // 60ml
  await expect(amountOf(page, "앙고스투라 비터스"), "dash 가 변환됐다").toHaveText("2 dash");
  await expect(amountOf(page, "마라스키노 체리")).toHaveText("1개");
});

/** RED 16 — 고른 단위를 기억한다. 잔마다 다시 고르게 하지 않는다. */
test("RED16 - 단위 선택이 다음 화면에서도 유지된다", async ({ page }) => {
  await ready(page, MANHATTAN);
  await chooseUnit(page, "oz");
  await expect(amountOf(page, "라이 위스키")).toHaveText("2 oz");

  await ready(page, NEGRONI);
  await expect(page.getByRole("radio", { name: "oz 로 보기" })).toBeChecked();
  await expect(amountOf(page, "진")).toHaveText("1 oz");
});

test("RED17 - 잔 수와 단위를 함께 걸어도 표가 맞는다", async ({ page }) => {
  await ready(page, NEGRONI);

  await addServing(page); // 2잔
  await chooseUnit(page, "oz");
  await expect(amountOf(page, "진")).toHaveText("2 oz"); // 60ml
  await expect(amountOf(page, "오렌지 필")).toHaveText("1조각");
});

// ── 대체재 (RED 19~22) ───────────────────────────────────────────────────

test("RED19,20,21,22 - 대체재가 있는 줄만 펼친다", async ({ page }) => {
  await ready(page, NEGRONI);

  const rows = panel(page).locator("tbody tr");
  const buttons = panel(page).getByRole("button", { name: "대체 가능" });

  // 캄파리·베르무트에만 있다. 진·오렌지 필에는 없다 (RED 22)
  await expect(buttons).toHaveCount(2);
  await expect(
    rows.filter({ hasText: "진" }).first().getByRole("button", { name: "대체 가능" }),
  ).toHaveCount(0);

  const first = buttons.first();
  await expect(first).toHaveAttribute("aria-expanded", "false");
  await first.click();

  const note = page.locator(".substitute-note");
  await expect(note).toBeVisible();
  await expect(note).toContainText("캄파리"); // 어느 재료의 이야기인지 (RED 20)
  await expect(note).toContainText("아페롤"); // substitute_note (RED 21)
  await expect(first).toHaveAttribute("aria-expanded", "true"); // RED 33

  await first.click();
  await expect(page.locator(".substitute-note")).toHaveCount(0);
});

// ── 접근성 (RED 28~33) ───────────────────────────────────────────────────

test("RED28,29,30,32 - 키보드로 잔 수·단위·대체재를 조작한다", async ({ page }) => {
  await ready(page, NEGRONI);

  const up = page.getByRole("button", { name: "잔 수 늘리기" });
  await expect
    .poll(async () => {
      await up.focus();
      return up.evaluate((el) => el === document.activeElement);
    })
    .toBe(true);

  const outline = await up.evaluate((el) => getComputedStyle(el).outlineWidth);
  expect(outline, "focus-visible 아웃라인이 없다 (NFR-A-05)").not.toBe("0px");

  await page.keyboard.press("Enter");
  await expect(amountOf(page, "진")).toHaveText("60 ml");

  // 단위는 라디오라 키보드로 고른다
  await page.getByRole("radio", { name: "oz 로 보기" }).focus();
  await page.keyboard.press("Space");
  await expect(amountOf(page, "진")).toHaveText("2 oz");

  const sub = panel(page).getByRole("button", { name: "대체 가능" }).first();
  await sub.focus();
  await page.keyboard.press("Enter");
  await expect(page.locator(".substitute-note")).toBeVisible();
});

test("RED31 - 잔 수·단위 변경이 스크린리더에 안내된다", async ({ page }) => {
  await ready(page, NEGRONI);

  const live = page.locator(".recipe-live[aria-live]");
  await expect(live).toHaveAttribute("aria-live", "polite");
  await expect(live).toContainText("1잔 기준");
  await expect(live).toContainText("ml 표기");

  await addServing(page);
  await expect(live).toContainText("2잔 기준");

  await chooseUnit(page, "oz");
  await expect(live).toContainText("oz 표기");
});

// ── 성능 (RED 34·35) ─────────────────────────────────────────────────────

/**
 * RED 35 — 환산에 서버 왕복이 없다.
 *
 * 필요한 것(수치 · 단위 · 배수 대상 판정)이 이미 응답에 있다. 잔 수를 만질 때마다 서버를
 * 부르면 상세를 미리 그려 둔 뜻이 사라진다.
 */
test("RED34,35 - 서버가 끊겨도 환산이 되고 즉시 끝난다", async ({ page, context }) => {
  await ready(page, NEGRONI);

  // RED 34 — 반영이 즉시인가. 선이 붙어 있는 상태에서 한 번 잰다. 여기 걸리는 시간에는
  // 테스트 쪽 폴링 간격이 섞이므로 **멈춤을 잡는 눈금**이지 INP 자체가 아니다 —
  // 실제 계측은 이슈 046 의 Lighthouse 다 (SPEC-04 §9.1).
  const started = Date.now();
  await addServing(page);
  await expect(amountOf(page, "진")).toHaveText("60 ml");
  const elapsed = Date.now() - started;
  expect(elapsed, `조작 반영에 ${elapsed}ms 걸렸다`).toBeLessThan(3000);

  // RED 35 — 선을 끊고 만진다. 환산에 서버가 필요했다면 여기서 표가 멈춘다.
  // 요청 수를 세는 것보다 분명하다 (프레임워크의 링크 프리페치가 섞이지 않는다).
  await context.setOffline(true);
  await addServing(page);
  await expect(amountOf(page, "진")).toHaveText("90 ml");
  await chooseUnit(page, "oz");
  await expect(amountOf(page, "진")).toHaveText("3 oz");
  await context.setOffline(false);
});
