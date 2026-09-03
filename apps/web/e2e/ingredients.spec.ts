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
