import { test, expect, type Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

/**
 * ISSUE-046 — 접근성 자동 게이트 (SPEC-04 §9.1 · `NFR-A-01`·`A-02`·`A-03`·`A-05`).
 *
 * ## 왜 CI 에서 막나
 *
 * SPEC-04 §2.3 — ADR-0001 이 각주로 경고했는데 **코드가 따르지 않고 있었다** (18군데).
 * "각주는 강제되지 않는다는 증거" 라 `NFR-A-01` 을 배포 차단으로 올렸다. 여기가 그 강제다.
 *
 * ## 렌더된 화면을 본다
 *
 * axe 는 실제로 그려진 것의 대비·이름·역할을 잰다. **렌더되지 않는 분기**(조건부 클래스,
 * 안 열린 화면)는 못 본다 — 그쪽은 `scripts/contrast-check.mjs` 의 소스 검사가 맡는다.
 * 두 겹인 이유가 그것이다 (이슈 046 RED 21).
 */

/** 공개 화면 전부. 하나 늘면 여기 적는다 — 검사에서 빠진 화면은 없는 것과 같다. */
const PUBLIC_PAGES = [
  { path: "/cocktails/search", label: "탐색" },
  { path: "/cocktails/negroni", label: "상세" },
  { path: "/cocktails/base/gin", label: "카테고리" },
  { path: "/finder", label: "파인더" },
  { path: "/search", label: "통합 검색" },
  { path: "/my-bar", label: "내 술장" },
  { path: "/ingredients", label: "재료 사전" },
  { path: "/ingredients/campari", label: "재료 상세" },
  { path: "/privacy", label: "개인정보" },
  { path: "/terms", label: "약관" },
];

/**
 * WCAG 2.1 AA 까지 본다.
 *
 * `best-practice` 는 넣지 않는다 — 규범이 아니라 권고라, 차단으로 걸면 스펙에 없는 것을
 * 배포 조건으로 만드는 셈이다 (SPEC-04 §9 "전부를 차단으로 만들면 아무것도 못 나간다").
 */
function axe(page: Page) {
  return new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]);
}

/**
 * **봐주는 위반이 없다.**
 *
 * 하나 있었다 — accent 바탕(#ec3013) 위의 흰 글자 3.76:1 (`G-16`). 선택된 칩과
 * `.btn-primary` 가 그 조합이었고, 시안의 정체성에 닿는 결정이라 이슈 050(#52)이 들고
 * 있었다. [ADR-0006](../../../docs/decisions/ADR-0006-btn-primary-contrast.md) 이
 * **흰 글자를 얹는 면만 accent-700 으로** 정해서(6.41:1) 예외가 없어졌다.
 *
 * 다시 예외를 만들려면 GAPS 등재와 ADR 이 먼저다 (SPEC-00 §4). 여기에 필터를 되살리는
 * 것으로 시작하지 않는다.
 */

for (const { path, label } of PUBLIC_PAGES) {
  test(`RED2 - ${label} 화면에 axe 위반이 없다`, async ({ page }) => {
    await page.goto(path);
    await expect(page.getByRole("heading", { level: 1 }).first()).toBeVisible();

    const { violations } = await axe(page).analyze();

    // 어디가 왜 걸렸는지 한 줄로 남긴다. 규칙 이름만 나오면 고칠 자리를 못 찾는다.
    const summary = violations.flatMap((v) =>
      v.nodes.map((n) => `${v.id} (${v.impact}) — ${n.target.join(" ")}`),
    );
    expect(summary, `${label}: ${summary.join(" / ")}`).toEqual([]);
  });
}

/**
 * RED 16~20 — 대비는 **화면에서도** 잰다.
 *
 * 토큰 표(`scripts/contrast-check.mjs`)는 "이 조합이 AA 인가" 를 계산하지만, 그 조합이
 * 실제로 그 자리에 쓰였는지는 모른다. axe 의 `color-contrast` 가 그리는 쪽을 본다.
 */
test("RED16~20 - 그려진 글자의 대비가 AA 다", async ({ page }) => {
  for (const { path, label } of PUBLIC_PAGES) {
    await page.goto(path);
    const { violations } = await axe(page).withRules(["color-contrast"]).analyze();

    const failures = violations.flatMap((v) =>
      v.nodes.map(
        (n) => `${label} ${n.target.join(" ")} — ${n.failureSummary?.split("\n")[1]?.trim()}`,
      ),
    );
    expect(failures, failures.join(" / ")).toEqual([]);
  }
});

/**
 * RED 23·24 — 포커스가 보인다 (`NFR-A-05`).
 *
 * "기본 링을 제거만 하지 않는다" 가 요구사항이다. `outline: none` 만 두고 대체 표시가 없으면
 * 키보드로 다니는 사람이 자기 위치를 잃는다.
 */
test("RED23,24 - 포커스 링이 2px accent 다", async ({ page }) => {
  await page.goto("/cocktails/search");
  await expect(page.locator("main[data-ready]")).toBeVisible();

  // `:focus-visible` 은 **키보드로 옮긴 포커스**에만 붙는다. `focus()` 로 준 포커스는
  // 브라우저에 따라 해당되지 않아, Tab 으로 옮겨 가며 처음 걸리는 것을 본다.
  let style: { width: string; color: string; style: string } | null = null;

  for (let i = 0; i < 12 && !style; i++) {
    await page.keyboard.press("Tab");
    style = await page.evaluate(() => {
      const el = document.activeElement as HTMLElement | null;
      if (!el || el === document.body || !el.matches(":focus-visible")) return null;

      const s = getComputedStyle(el);
      return { width: s.outlineWidth, color: s.outlineColor, style: s.outlineStyle };
    });
  }

  expect(style, "탭으로 옮긴 포커스에 아무 표시가 없다").not.toBeNull();
  expect(style!.style, "아웃라인이 없다 — 기본 링만 지운 상태다").not.toBe("none");
  expect(parseFloat(style!.width), `아웃라인이 ${style!.width} 다`).toBeGreaterThanOrEqual(2);

  // 색은 `oklch()` 로 적혀 있고 브라우저는 `lab()`·`rgb()` 중 하나로 돌려준다.
  // 표기가 아니라 **실제 색**을 본다 — 캔버스에 한 픽셀 칠해 값을 읽는다.
  const rgb = await page.evaluate((color) => {
    const ctx = document.createElement("canvas").getContext("2d")!;
    ctx.fillStyle = color;
    ctx.fillRect(0, 0, 1, 1);
    return Array.from(ctx.getImageData(0, 0, 1, 1).data).slice(0, 3);
  }, style!.color);

  const [r, g, b] = rgb;
  expect(
    Math.abs(r - 236) < 12 && Math.abs(g - 48) < 12 && Math.abs(b - 19) < 12,
    `아웃라인 색이 rgb(${rgb.join(", ")}) 다 — accent(236, 48, 19)가 아니다`,
  ).toBe(true);
});
