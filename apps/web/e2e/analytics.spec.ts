import { test, expect, type Page, type Route } from "@playwright/test";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import type { components } from "@mut/domain/generated/api";

type EventRequest = components["schemas"]["EventRequest"];
type Batch = { idempotencyKey?: string; events: EventRequest[] };

/**
 * ISSUE-035 — 계측 기반 + `cocktail_view` · `search_miss` (SPEC-10 §4.1·§4.3·§9 · `NFR-R-04`).
 *
 * ## 보내는 것을 가로채서 본다
 *
 * 브라우저가 `/api/events` 로 보내는 배치를 그대로 받아 읽는다. 상류(API)를 띄우지 않아도
 * 돌고, **무엇을 보내는가**가 이 이슈의 전부다 — 저장은 이슈 034 가 이미 테스트한다.
 *
 * ## 이벤트는 소급이 안 된다
 *
 * SPEC-10 §1 — "나중에 심으면 그 기간의 데이터가 **영원히 없다**." 그래서 화면이 셋뿐인 지금
 * 심는 것이고, 심은 것이 실제로 나가는지 여기서 확인한다.
 */

const DETAIL = "/cocktails/negroni";
// 두 번째 상세. 마티니는 향·맛 서술이 없어 API 를 붙이면 404 라(`GATE-COCKTAIL-01`)
// 코퍼스 둘 다에 있는 것을 쓴다.
const OTHER_DETAIL = "/cocktails/manhattan";

/** 나가는 배치를 모은다. 상류로 보내지 않고 `202` 로 답한다 — 서버가 하는 것과 같다. */
async function collect(page: Page): Promise<Batch[]> {
  const batches: Batch[] = [];

  await page.route("**/api/events", async (route: Route) => {
    const body = route.request().postData();
    if (body) batches.push(JSON.parse(body) as Batch);
    await route.fulfill({ status: 202, body: "" });
  });

  return batches;
}

function eventsOf(batches: Batch[], eventType: string): EventRequest[] {
  return batches.flatMap((b) => b.events).filter((e) => e.eventType === eventType);
}

/**
 * 그 이벤트가 `atLeast` 건 나갈 때까지 기다린다.
 *
 * 큐가 모아서 보내므로 즉시 오지 않는다. **몇 번째를 기다리는지 세는 것이 중요하다** —
 * 개수만 보면 앞서 나간 이벤트를 새 것으로 착각한다.
 *
 * `pagehide` 를 흘려 보내 재촉한다. `visibilitychange` 로는 안 된다 — 화면이 보이는 상태라
 * 큐가 "떠나는 중" 으로 치지 않는다.
 */
async function flushed(
  page: Page,
  batches: Batch[],
  eventType: string,
  atLeast = 1,
): Promise<EventRequest[]> {
  await expect
    .poll(
      async () => {
        const found = eventsOf(batches, eventType).length;
        if (found < atLeast) await page.evaluate(() => window.dispatchEvent(new Event("pagehide")));
        return found;
      },
      { message: `${eventType} 이벤트가 ${atLeast}건까지 나가지 않았다`, timeout: 10_000 },
    )
    .toBeGreaterThanOrEqual(atLeast);

  return eventsOf(batches, eventType);
}

// ── RED 14~16 : cocktail_view (SPEC-10 §4.1) ─────────────────────────────

test("RED14 - 상세에 들어가면 cocktail_view 가 나간다", async ({ page }) => {
  const batches = await collect(page);
  await page.goto(DETAIL);

  const [event] = await flushed(page, batches, "cocktail_view");
  expect(event.payload).toMatchObject({ cocktailSlug: "negroni" });
});

/**
 * RED 15·16 — `entryPoint` 다섯 갈래. **`external` 비율이 곧 SEO 성과다.**
 *
 * 어디서 왔는지는 직전 화면이 말한다. 주소를 직접 열면 밖에서 온 것이고, 사이트 안에서
 * 링크를 타고 오면 그 화면이 갈래를 정한다.
 */
test("RED15,16 - entryPoint 가 들어온 곳을 구분한다", async ({ page }) => {
  const batches = await collect(page);

  // 주소창으로 바로 — referrer 가 없다
  await page.goto(DETAIL);
  let events = await flushed(page, batches, "cocktail_view");
  expect(events.at(-1)!.payload, "직접 열었는데 external 이 아니다").toMatchObject({
    entryPoint: "external",
  });

  // 탐색 → 상세 (링크를 눌러 옮긴다 — 문서가 바뀌지 않는 이동이다)
  await page.goto("/cocktails/search");
  await page.locator(".cocktail-card").first().click();
  await expect(page).toHaveURL(/\/cocktails\/[a-z0-9-]+$/);
  events = await flushed(page, batches, "cocktail_view", 2);
  expect(events.at(-1)!.payload, "탐색에서 눌러 왔는데 밖에서 온 것으로 셌다").toMatchObject({
    entryPoint: "search",
  });

  // 카테고리 → 상세
  await page.goto("/cocktails/base/gin");
  await page.locator(".card").first().click();
  events = await flushed(page, batches, "cocktail_view", 3);
  expect(events.at(-1)!.payload).toMatchObject({ entryPoint: "category" });
});

// ── RED 17~20 : search_miss ★ (SPEC-10 §4.3) ─────────────────────────────

/** 상류 검색 응답을 흉내 낸다 — 이 이슈가 보는 것은 **0건일 때 무엇을 보내는가**다. */
async function mockSearch(page: Page, over: { matchedCount: number; hadChosung: boolean }) {
  await page.route(/\/api\/search\?/, async (route) => {
    const q = new URL(route.request().url()).searchParams.get("q") ?? "";
    await route.fulfill({
      json: {
        query: q,
        hadChosung: over.hadChosung,
        matchedCount: over.matchedCount,
        groups: {
          cocktail: { items: [], count: 0 },
          ingredient: { items: [], count: 0 },
          bar: { items: [], count: 0 },
          article: { items: [], count: 0 },
        },
      },
    });
  });
  await page.route(/\/api\/search\/suggest\?/, (route) => route.fulfill({ json: [] }));
}

test("RED17,19 - 결과가 0건이면 search_miss 가 나간다", async ({ page }) => {
  const batches = await collect(page);
  await mockSearch(page, { matchedCount: 0, hadChosung: false });

  await page.goto("/search");
  await page.getByRole("combobox").fill("없는칵테일");
  await page.getByRole("combobox").press("Enter");

  const [event] = await flushed(page, batches, "search_miss");
  expect(event.payload).toMatchObject({ query: "없는칵테일", matchedCount: 0 });
});

test("결과가 있으면 search_miss 가 나가지 않는다", async ({ page }) => {
  const batches = await collect(page);
  await mockSearch(page, { matchedCount: 3, hadChosung: false });

  await page.goto("/search");
  await page.getByRole("combobox").fill("네그로니");
  await page.getByRole("combobox").press("Enter");
  await expect(page.locator(".search-status__count")).toBeVisible();

  await page.evaluate(() => window.dispatchEvent(new Event("pagehide")));
  expect(eventsOf(batches, "search_miss")).toEqual([]);
});

/**
 * RED 18·20 — `hadChosung` 은 **서버가 판정한 값**이다.
 *
 * 0건의 원인이 둘이다 — 콘텐츠가 없거나, 초성 색인이 고장났거나. 프론트가 다시 판정하면
 * 서버와 다른 답을 낼 수 있고, 그 순간 두 원인을 나눌 수 없게 된다 (이슈 024).
 */
test("RED18,20 - hadChosung 이 서버 응답 그대로 실린다", async ({ page }) => {
  for (const hadChosung of [true, false]) {
    const batches = await collect(page);
    await mockSearch(page, { matchedCount: 0, hadChosung });

    await page.goto("/search");
    await page.getByRole("combobox").fill(hadChosung ? "ㅁㄹㄱㄹㅌ" : "없는이름");
    await page.getByRole("combobox").press("Enter");

    const events = await flushed(page, batches, "search_miss");
    expect(events.at(-1)!.payload, `hadChosung=${hadChosung} 이 그대로 실리지 않았다`).toMatchObject(
      { hadChosung },
    );
    await page.unrouteAll();
  }
});

test("RED18 - 화면이 초성을 다시 판정하지 않는다", () => {
  for (const rel of ["app/search/unified-search.tsx", "lib/analytics/core/queue.ts"]) {
    const source = readFileSync(join(process.cwd(), rel), "utf8");
    expect(source, `${rel} 에 초성 판정이 있다`).not.toMatch(/ㄱ-ㅎ/);
  }
});

// ── RED 1~13 : 공통 필드와 세션 (SPEC-10 §3) ─────────────────────────────

test("RED1,4,5 - sessionId 가 UUID 이고 localStorage 에 남는다", async ({ page }) => {
  const batches = await collect(page);
  await page.goto(DETAIL);
  const [event] = await flushed(page, batches, "cocktail_view");

  expect(event.sessionId).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i);
  expect(event.userId, "비로그인은 userId 가 없다").toBeUndefined();

  // 탭 간 공유라 localStorage 다 (DECISIONS §1.11)
  const stored = await page.evaluate(() => window.localStorage.getItem("mut:analytics-session"));
  expect(stored, "세션이 저장되지 않았다").toContain(event.sessionId!);
});

test("RED3 - 활동이 이어지면 같은 세션이다", async ({ page }) => {
  const batches = await collect(page);

  await page.goto(DETAIL);
  await flushed(page, batches, "cocktail_view");
  await page.goto(OTHER_DETAIL);
  await flushed(page, batches, "cocktail_view", 2);

  const ids = new Set(batches.flatMap((b) => b.events).map((e) => e.sessionId));
  expect(ids.size, "같은 방문인데 세션이 갈렸다").toBe(1);
});

/**
 * RED 2 — **30분 무활동이면 다른 방문이다.**
 *
 * 아침에 열어 둔 탭으로 저녁에 보는 것은 같은 방문이 아니다. 30분을 기다릴 수 없으니
 * 저장된 마지막 활동 시각을 과거로 밀어 둔다 — 시계가 아니라 그 값이 판정 근거다.
 */
test("RED2 - 30분이 지나면 세션이 갱신된다", async ({ page }) => {
  const batches = await collect(page);

  await page.goto(DETAIL);
  const [first] = await flushed(page, batches, "cocktail_view");

  await page.evaluate(() => {
    const key = "mut:analytics-session";
    const stored = JSON.parse(window.localStorage.getItem(key)!) as { id: string };
    // 31분 전으로 민다
    window.localStorage.setItem(
      key,
      JSON.stringify({ id: stored.id, lastSeen: Date.now() - 31 * 60 * 1000 }),
    );
  });

  await page.goto(OTHER_DETAIL);
  await flushed(page, batches, "cocktail_view", 2);

  const ids = batches.flatMap((b) => b.events).map((e) => e.sessionId);
  expect(ids.at(-1), "30분이 지났는데 같은 세션이다").not.toBe(first.sessionId);
});

test("RED6,13 - path 에 쿼리스트링이 없고 원본 referrer 를 보내지 않는다", async ({ page }) => {
  const batches = await collect(page);
  await mockSearch(page, { matchedCount: 0, hadChosung: false });

  await page.goto("/search?q=%EC%97%86%EB%8A%94%EA%B2%83");
  const events = await flushed(page, batches, "search_miss");

  expect(events.at(-1)!.path, "주소에 검색어가 실려 나갔다").toBe("/search");
  // 원본 referrer 는 분류값으로 접어서만 나간다 (SPEC-10 §3)
  const raw = JSON.stringify(batches);
  expect(raw).not.toContain("referrer\":\"http");
  expect(raw).not.toMatch(/"document\.referrer"/);
});

test("RED7,11 - referrerType 이 5종 중 하나다", async ({ page }) => {
  const batches = await collect(page);
  await page.goto(DETAIL);
  const [event] = await flushed(page, batches, "cocktail_view");

  // 주소를 직접 열었으므로 direct 다 (RED 11)
  expect(event.referrerType).toBe("direct");
  expect(["organic", "internal", "social", "direct", "unknown"]).toContain(event.referrerType);
});

test("RED9 - 내부 이동은 internal 이다", async ({ page }) => {
  const batches = await collect(page);

  await page.goto("/cocktails/base/gin");
  await page.locator(".card").first().click();
  const events = await flushed(page, batches, "cocktail_view");

  expect(events.at(-1)!.referrerType, "화면 안에서 옮겼는데 internal 이 아니다").toBe("internal");
});

/**
 * RED 8·10·12 — 분류 규칙 자체.
 *
 * 검색엔진·소셜에서 오는 것은 브라우저를 그렇게 띄울 수 없으니 함수로 직접 잰다.
 * `organic` 비중이 Phase 1a 의 세 지표 중 하나라(`PRIN-T04`) 규칙이 틀리면 지표가 틀린다.
 */
test("RED8,10,12 - 검색·소셜·분류불가를 나눈다", async ({ page }) => {
  await page.goto(DETAIL);

  const classify = (referrer: string) =>
    page.evaluate(
      ([ref]) => {
        // 화면 번들에 실린 규칙을 그대로 부를 수 없어 같은 목록을 여기서 확인한다.
        const host = ref ? new URL(ref).hostname.toLowerCase() : "";
        return host;
      },
      [referrer],
    );

  // 규칙은 소스에서 확인한다 — 목록이 줄면 지표가 조용히 틀린다
  const source = readFileSync(join(process.cwd(), "lib/analytics/core/referrer.ts"), "utf8");
  for (const host of ["google.", "naver.com", "daum.net"]) {
    expect(source, `검색엔진 ${host} 가 빠졌다 (RED 8)`).toContain(host);
  }
  for (const host of ["instagram.com", "kakao.com", "x.com"]) {
    expect(source, `소셜 ${host} 가 빠졌다 (RED 10)`).toContain(host);
  }
  expect(source, "분류 실패가 unknown 이 아니다 (RED 12)").toMatch(/return "unknown"/);

  expect(await classify("https://www.google.com/search?q=x")).toBe("www.google.com");
});

// ── RED 21~23 : 배치 전송 (SPEC-10 §2) ───────────────────────────────────

test("RED21 - 이벤트를 모았다가 한 번에 보낸다", async ({ page }) => {
  const batches = await collect(page);

  await page.goto(DETAIL);
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();

  // 이벤트가 생긴 **직후에는 요청이 없다.** 이벤트마다 바로 보내면 상세 한 번 보는 동안
  // 요청이 서너 번 나간다 (SPEC-10 §2 — 배치 전송).
  expect(batches, "이벤트마다 요청을 하나씩 내고 있다").toEqual([]);

  // 잠시 뒤 한 요청에 실려 나간다.
  await flushed(page, batches, "cocktail_view");
  expect(batches.length, "한 번에 보내지 않았다").toBe(1);
});

/**
 * RED 23 — 배치 상한 50.
 *
 * 넘으면 서버가 **400** 이다 (이슈 034 — 조용히 자르지 않는다). 브라우저에서 51건을 만들려면
 * 화면을 51번 움직여야 해서, 나가는 배치가 상한 안인지 보고 **자르는 규칙 자체는 코드에서**
 * 확인한다. 규칙이 사라지면 여기서 걸린다.
 */
test("RED23 - 배치가 50건을 넘지 않는다", async ({ page }) => {
  const batches = await collect(page);
  await page.goto(DETAIL);
  await flushed(page, batches, "cocktail_view");

  for (const batch of batches) {
    expect(batch.events.length, "배치가 50건을 넘었다").toBeLessThanOrEqual(50);
  }

  const queue = readFileSync(join(process.cwd(), "lib/analytics/core/queue.ts"), "utf8");
  expect(queue).toMatch(/MAX_BATCH\s*=\s*50/);
  expect(queue, "상한만큼 잘라 보내는 규칙이 없다").toMatch(/slice\(0, MAX_BATCH\)/);
});

/**
 * RED 28·29 — 배치마다 `Idempotency-Key`, **재시도는 같은 키**.
 *
 * 재시도를 브라우저에서 만들어 낼 수 없으므로(전송은 한 번이다) 성질을 코드에서 본다:
 * 키를 **배치 내용에서** 만들면 같은 배치는 언제나 같은 키가 된다. 무작위로 만들면
 * 재시도마다 새 키가 되어 집계가 부푼다 (`PRIN-T07`).
 */
test("RED28,29 - 배치마다 키가 있고 내용에서 만들어진다", async ({ page }) => {
  const batches = await collect(page);
  await page.goto(DETAIL);
  await flushed(page, batches, "cocktail_view");

  expect(batches.length).toBeGreaterThan(0);
  for (const batch of batches) {
    expect(batch.idempotencyKey, "키 없이 보냈다 (PRIN-T07)").toBeTruthy();
  }

  const queue = readFileSync(join(process.cwd(), "lib/analytics/core/queue.ts"), "utf8");
  expect(queue, "키를 무작위로 만들고 있다 — 재시도가 다른 키가 된다").not.toMatch(
    /keyFor[\s\S]{0,400}(randomUUID|Math\.random)/,
  );
  expect(queue, "키가 배치 내용에서 오지 않는다").toMatch(/function keyFor\(batch/);
});

// ── RED 24~27 : 실패 격리 (NFR-R-04) ─────────────────────────────────────

/**
 * RED 24~27 — **수집 실패가 사용자 흐름을 막지 않는다** (배포 차단 조건).
 *
 * 광고 차단기가 이 요청을 막는 일이 흔하다. 그때 화면이 멈추면 계측 때문에 서비스를 잃는다.
 */
test("RED24,25,27 - 전송이 막혀도 화면이 그대로 돈다", async ({ page }) => {
  const errors: string[] = [];
  page.on("pageerror", (e) => errors.push(e.message));

  // 광고 차단기처럼 요청 자체를 끊는다
  await page.route("**/api/events", (route) => route.abort("blockedbyclient"));

  await page.goto(DETAIL);
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();

  // 계측이 죽어도 화면 기능은 그대로다
  await page.getByRole("button", { name: "잔 수 늘리기" }).click();
  await expect(page.locator(".stepper .value b")).toHaveText("2");

  expect(errors, "계측 실패가 페이지 오류로 튀었다").toEqual([]);
  await expect(page.locator(".search-status__error")).toHaveCount(0);
});

test("RED26 - 실패 로그가 debug 레벨이다", async ({ page }) => {
  const loud: string[] = [];
  page.on("console", (m) => {
    if (["error", "warning"].includes(m.type()) && m.text().includes("analytics")) loud.push(m.text());
  });

  await page.route("**/api/events", (route) => route.abort("blockedbyclient"));
  await page.goto(DETAIL);
  await page.evaluate(() => window.dispatchEvent(new Event("pagehide")));

  expect(loud, "콘솔이 빨개졌다 — 사용자가 버그로 신고한다").toEqual([]);
});

// ── RED 30~32 : 하지 않는 것 (SPEC-10 §10) ───────────────────────────────

test("RED30,31,32 - 좌표·궤적·1b 이벤트를 보내지 않는다", async ({ page }) => {
  const batches = await collect(page);

  await page.goto(DETAIL);
  await page.mouse.move(100, 200);
  await page.mouse.wheel(0, 400);
  await flushed(page, batches, "cocktail_view");

  const raw = JSON.stringify(batches);
  for (const forbidden of ["clientX", "clientY", "scrollY", "lat", "lng", "heatmap"]) {
    expect(raw, `${forbidden} 를 보내고 있다 (SPEC-10 §10)`).not.toContain(forbidden);
  }
  for (const phase1b of ["bar_view", "cross_nav", "partner_action"]) {
    expect(raw, `${phase1b} 는 Phase 1b 다`).not.toContain(phase1b);
  }
});

/** 이벤트 이름과 payload 키는 서버가 정한다 — 모르는 것은 조용히 버려진다. */
test("보내는 이름과 키가 서버 계약과 같다", async ({ page }) => {
  const batches = await collect(page);
  await page.goto(DETAIL);
  const [view] = await flushed(page, batches, "cocktail_view");

  const contract = readFileSync(
    join(process.cwd(), "../../apps/api/src/main/kotlin/kr/mut/common/analytics/EventType.kt"),
    "utf8",
  );
  expect(contract).toContain('COCKTAIL_VIEW("cocktail_view"');
  expect(contract).toContain('SEARCH_MISS("search_miss"');

  for (const key of Object.keys(view.payload ?? {})) {
    expect(contract, `payload 키 ${key} 가 계약에 없다 — 서버가 버린다`).toContain(`"${key}"`);
  }
});
