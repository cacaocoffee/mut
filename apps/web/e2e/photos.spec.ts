import { test, expect } from "@playwright/test";

/**
 * ISSUE-060 (#139) — 사진 연결 (ADR-0008 · D-6 · NFR-P-06).
 *
 * #87 이 `apps/web/public/cocktails/{slug}.webp` 25장을 커밋했는데 화면 코드가
 * 그 파일을 읽는 곳이 없었다. 여기서 고정하는 규칙은 갈림이다 —
 * **사진이 있는 종은 사진, 없는 종(기존 24종)은 자리표시자.**
 */

const WITH_PHOTO = "vesper"; // #87 로 들어온 25종 중 하나
const WITHOUT_PHOTO = "negroni"; // 기존 24종 — 사진 파일이 없다

test("RED1 - 사진이 있는 종의 상세 히어로에 사진이 나온다", async ({ page }) => {
  await page.goto(`/cocktails/${WITH_PHOTO}`);
  await expect(
    page.locator(`.detail-hero img[src="/cocktails/${WITH_PHOTO}.webp"]`),
  ).toBeVisible();
});

test("RED2 - 사진이 없는 종의 상세는 히어로 슬롯 없이 제목부터 온다 (#174)", async ({ page }) => {
  await page.goto(`/cocktails/${WITHOUT_PHOTO}`);
  await expect(page.locator(".detail-hero img")).toHaveCount(0);
  await expect(page.locator(".detail-hero .photo-slot")).toHaveCount(0);
  await expect(page.locator(".detail-hero h1")).toBeInViewport();
  // 개발용 자리표시 문구가 사용자에게 보이지 않는다
  await expect(page.getByText(/PLACEHOLDER|IMAGE 4:5|자리입니다/)).toHaveCount(0);
});

test("RED3 - 탐색 카드에도 같은 규칙이 적용된다", async ({ page }) => {
  // 쪽당 50개(PAGE_SIZE)라 49종 전부 첫 쪽에 있다 — 쪽 넘김 없이 둘 다 잡힌다.
  await page.goto("/cocktails/search");

  const withCard = page.locator(`.cocktail-card[href="/cocktails/${WITH_PHOTO}"]`);
  await expect(
    withCard.locator(`img[src="/cocktails/${WITH_PHOTO}.webp"]`),
  ).toBeVisible();

  const withoutCard = page.locator(
    `.cocktail-card[href="/cocktails/${WITHOUT_PHOTO}"]`,
  );
  await expect(withoutCard.locator("img")).toHaveCount(0);
  await expect(withoutCard.locator(".photo-slot")).toBeVisible();
});
