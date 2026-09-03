import { test, expect } from "@playwright/test";
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

/** #183 — 블로그에서 이관한 본문의 영문 소제목(About the Cocktail · Ingredients · How to Mix)을 한국어로. */
test("아티클 본문 소제목이 한국어다 (#183)", async ({ page }) => {
  await page.goto("/articles/negroni");
  await expect(page.getByRole("heading", { name: "재료" })).toBeVisible();
  await expect(page.getByRole("heading", { name: /Ingredients|How to Mix|About the Cocktail/i })).toHaveCount(0);
});

test("코드 시드에 영문 소제목이 남아 있지 않다 (#183)", () => {
  const dir = join(process.cwd(), "..", "..", "packages", "domain", "src", "articles");
  const offenders = readdirSync(dir)
    .filter((f) => f.endsWith(".ts"))
    .filter((f) => /kind: "heading", text: "[A-Za-z][^"]*"/.test(readFileSync(join(dir, f), "utf8")));
  expect(offenders).toEqual([]);
});
