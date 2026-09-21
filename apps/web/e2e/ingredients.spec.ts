import { test, expect } from "@playwright/test";

/**
 * #181 — 재료 사전 목록의 "N잔" 과 상세의 "이 재료를 쓰는 칵테일 N" 이 같아야 한다.
 * 목록은 코드 데이터로, 상세는 코퍼스로 세서 실서비스에서 진 10 ↔ 8 로 어긋났다.
 */
test("목록의 잔 수와 상세의 잔 수가 같다 (#181)", async ({ page }) => {
  await page.goto("/ingredients");
  const gin = page.locator(".ingredient-list__item", { hasText: "Dry Gin" }).first();
  const listed = await gin.locator(".ingredient-list__uses").textContent();
  const listCount = Number(listed?.replace(/[^0-9]/g, ""));
  expect(listCount).toBeGreaterThan(0);

  await gin.getByRole("link").click();
  await expect(page).toHaveURL(/\/ingredients\/gin$/);
  const detailCount = Number(await page.locator(".ingredient-group__count").first().textContent());
  expect(detailCount).toBe(listCount);
  await expect(page.locator(".cocktail-card")).toHaveCount(listCount);
});

/**
 * #196 — 재료 상세의 신고 제품 목록엔 링크·가격이 없다 (`NFR-L-05` 자문 전).
 * API 가 없는 날(프로토타입 폴백)엔 목록 자체가 없고, 있는 날엔 있어도 링크가 0 이어야 한다 —
 * 어느 쪽이든 아래가 성립한다. 배지·대체재 카드 판정은 `lib/ingredient-distribution.test.ts` 가 고정한다.
 */
test("신고 제품 목록에 구매 링크가 없다 (#196 · NFR-L-05)", async ({ page }) => {
  await page.goto("/ingredients/campari");
  await expect(page.locator("h1")).toContainText("캄파리");
  await expect(page.locator(".ingredient-products a")).toHaveCount(0);
  await expect(page.locator(".ingredient-products").getByText(/원|₩|\$/)).toHaveCount(0);
});

/**
 * #205 — 공개 유통 제품 목록. API 없는 날엔 빈 상태를 그리지만 화면 자체는 있어야 하고,
 * 어느 쪽이든 구매 링크가 없어야 한다. 필터가 붙은 주소는 noindex 다.
 */
test("국내 유통 술 목록 — 화면이 있고 링크가 없으며 필터 주소는 noindex 다 (#205)", async ({ page }) => {
  await page.goto("/products");
  await expect(page.locator("h1")).toContainText("국내 유통 술");
  await expect(page.locator("meta[name=robots]")).toHaveCount(0);
  await expect(page.locator(".products-table-wrap a")).toHaveCount(0);

  await page.goto("/products?q=campari");
  await expect(page.locator("meta[name=robots]")).toHaveAttribute("content", /noindex/);
});
