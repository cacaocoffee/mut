import { test, expect, type Page, type Route } from "@playwright/test";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import type { components } from "@mut/domain/generated/api";

type EventRequest = components["schemas"]["EventRequest"];
type Batch = { idempotencyKey?: string; events: EventRequest[] };

/**
 * ISSUE-049 — 계측 3~4단계 이벤트 5종 (SPEC-10 §4.2·§4.4·§4.5·§4.6·§9).
 *
 * ## 이 다섯이 무엇에 쓰이나
 *
 * | 지표 | 계산 | 쓰임 |
 * |---|---|---|
 * | 필터 축 사용률 | 축별 `filter_apply` 분포 | **UI 정리** — 안 쓰는 축은 내린다 |
 * | 빈 결과율 | `resultCount = 0` 비율 | **패싯 카운트 건강도** |
 * | 파인더 완주율 | `step = 4` / `step = 1` | **파인더 존치 판단** |
 *
 * 그래서 값이 맞는지가 중요하다 — `resultCount` 가 적용 전 숫자면 빈 결과율이 거짓이 되고,
 * `activeAxisCount` 가 틀리면 몇 축까지 겹쳐 쓰는지를 잘못 읽는다.
 *
 * ## 전송은 035 것을 그대로 쓴다
 *
 * 이 이슈는 **호출 지점과 payload** 만 더한다 (RED 21). 큐·세션·공통 필드는 035 가 만들었고,
 * 여기서 다시 만들면 한쪽만 고치는 날 절반이 사라진다.
 */

const SEARCH = "/cocktails/search";
const FINDER = "/finder";
const DETAIL = "/cocktails/negroni";

async function collect(page: Page): Promise<Batch[]> {
  const batches: Batch[] = [];

  await page.route("**/api/events", async (route: Route) => {
    const body = route.request().postData();
    if (body) batches.push(JSON.parse(body) as Batch);
    await route.fulfill({ status: 202, body: "" });
  });

  return batches;
}

/**
 * payload 를 읽는다.
 *
 * 계약의 payload 는 자유 형태 맵이라 생성 타입이 값을 `never` 로 좁혀 둔다 —
 * 값을 견주려면 여기서 한 번 풀어야 한다. **보내는 쪽 타입**은 `lib/analytics/events` 가 잡는다.
 */
function payloadOf(event: EventRequest): Record<string, string | number> {
  return (event.payload ?? {}) as unknown as Record<string, string | number>;
}

function eventsOf(batches: Batch[], eventType: string): EventRequest[] {
  return batches.flatMap((b) => b.events).filter((e) => e.eventType === eventType);
}

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
      { message: `${eventType} 이 ${atLeast}건까지 나가지 않았다`, timeout: 10_000 },
    )
    .toBeGreaterThanOrEqual(atLeast);

  return eventsOf(batches, eventType);
}

function chips(page: Page, label: string) {
  return page.locator(".filter-group").filter({ hasText: label }).locator("button[aria-pressed]");
}

async function ready(page: Page, path: string) {
  await page.goto(path);
  await expect(page.locator("main[data-ready]")).toBeVisible();
}

// ── RED 1~6 : filter_apply (SPEC-10 §4.2) ────────────────────────────────

test("RED1,3,4 - 필터를 걸면 축·값·결과 수가 실려 나간다", async ({ page }) => {
  const batches = await collect(page);
  await ready(page, SEARCH);

  const gin = chips(page, "기주 BASE SPIRIT").first();
  const expected = Number(((await gin.getAttribute("aria-label")) ?? "").match(/(\d+)개/)![1]);
  await gin.click();

  const [event] = await flushed(page, batches, "filter_apply");
  expect(event.payload).toMatchObject({ axis: "base", resultCount: expected });
  expect(String(event.payload!.value).length, "무엇을 골랐는지가 없다").toBeGreaterThan(0);
});

/**
 * RED 2 — 축 이름이 화면과 같아야 한다.
 *
 * **`method` 는 SPEC-10 목록에 없다** — 그 문서 뒤에 이슈 040 이 축을 여섯으로 늘렸다.
 * 빼고 보내면 "안 쓰는 축은 내린다" 는 판단에서 메이킹만 영영 빠진다 (GAPS G-37).
 */
test("RED2 - 여섯 축과 키워드가 각자의 이름으로 나간다", async ({ page }) => {
  // 축마다 화면을 새로 여는 테스트라 일곱 번 그린다. 다른 테스트와 함께 돌 때 기본 30초를 넘는다.
  test.slow();

  const batches = await collect(page);

  // 축마다 새로 연다. 하나를 걸면 다른 축의 값이 0건이 되어 잠기는데(그것이 패싯의 일이다),
  // 잠긴 것을 누르면 이벤트가 안 나가 "축이 빠졌다" 로 읽힌다.
  const byChip: [string, string][] = [
    ["기주 BASE SPIRIT", "base"],
    ["스타일 STYLE", "style"],
    ["메이킹 METHOD", "method"],
    ["도수 ABV", "abv"],
    ["맛 / 향 FLAVOR PROFILE", "flavor"],
  ];

  let sent = 0;
  const step = async (axis: string, act: () => Promise<void>) => {
    await ready(page, SEARCH);
    await act();
    // 같은 축 연속 조작을 접느라 400ms 기다렸다 보낸다. 그 뒤에 재촉해야 의미가 있다.
    await page.waitForTimeout(600);

    const events = await flushed(page, batches, "filter_apply", ++sent);
    expect(payloadOf(events.at(-1)!).axis, `${axis} 축이 나가지 않았다`).toBe(axis);
  };

  for (const [label, axis] of byChip) {
    await step(axis, () => chips(page, label).first().click());
  }
  await step("sweet", () => page.locator("label.seg-opt").nth(1).click());
  await step("query", () => page.getByRole("searchbox").fill("네그"));

  const axes = eventsOf(batches, "filter_apply").map((e) => payloadOf(e).axis);

  for (const axis of ["base", "style", "method", "abv", "flavor", "sweet", "query"]) {
    expect(axes, `${axis} 축이 안 나간다`).toContain(axis);
  }
});

test("RED5 - activeAxisCount 가 그때 걸린 축 수다", async ({ page }) => {
  const batches = await collect(page);
  await ready(page, SEARCH);

  await chips(page, "기주 BASE SPIRIT").first().click();
  let events = await flushed(page, batches, "filter_apply", 1);
  expect(events.at(-1)!.payload).toMatchObject({ activeAxisCount: 1 });

  await chips(page, "스타일 STYLE").first().click();
  events = await flushed(page, batches, "filter_apply", 2);
  expect(events.at(-1)!.payload, "두 축을 걸었는데 하나로 셌다").toMatchObject({
    activeAxisCount: 2,
  });
});

/**
 * RED 6 — 같은 축을 연달아 고르면 마지막 것만 보낸다.
 *
 * 칩 다중 선택은 한 번의 조작에 가깝다. 중간 상태까지 세면 축 사용률이 손가락 빠른 사람
 * 쪽으로 기운다.
 */
test("RED6 - 연속 조작이 디바운스된다", async ({ page }) => {
  const batches = await collect(page);
  await ready(page, SEARCH);

  const bases = chips(page, "기주 BASE SPIRIT");
  for (let i = 0; i < 3; i++) {
    await bases.nth(i).click();
    await page.waitForTimeout(80); // 400ms 창 안에 세 번
  }

  await expect(page).toHaveURL(/base=/);
  await flushed(page, batches, "filter_apply");

  // **누른 수보다 적게** 나가야 한다. 정확히 1건을 요구하면 클릭 하나가 느려진 날
  // 디바운스가 제 일을 했는데도 실패한다 — 재는 것은 "덜 보내는가" 다.
  expect(eventsOf(batches, "filter_apply").length, "누를 때마다 보냈다").toBeLessThan(3);

  // 창 크기 자체는 코드에서 본다. 규칙이 사라지면 위 검사가 우연히 통과할 수 있다.
  const source = readFileSync(join(process.cwd(), "lib/analytics/events/index.ts"), "utf8");
  expect(source, "디바운스가 없어졌다").toMatch(/DEBOUNCE_MS\s*=\s*400/);
  expect(source, "축별로 접지 않는다").toMatch(/debounced\(`filter:\$\{payload\.axis\}`/);
});

// ── RED 7~11 : finder_step (SPEC-10 §4.4) ────────────────────────────────

test("RED7,8,9,10 - 네 단계가 각각 기록되고 step=4 가 완주다", async ({ page }) => {
  const batches = await collect(page);
  await ready(page, FINDER);

  for (let i = 0; i < 4; i++) {
    // 첫 질문은 마지막 선택지(가장 독한 구간) — 논알콜로 시작하면 후보가 1종 이하다
    await (i === 0
      ? page.locator(".quiz-options button").last()
      : page.locator(".quiz-options button").first()
    ).click();
  }

  const events = await flushed(page, batches, "finder_step", 4);
  expect(events.map((e) => payloadOf(e).step)).toEqual([1, 2, 3, 4]);

  for (const event of events) {
    expect(String(payloadOf(event).answered).length, "무엇을 골랐는지가 없다").toBeGreaterThan(0);
    expect(typeof payloadOf(event).candidateCount, "남은 후보 수가 없다").toBe("number");
  }

  // 완주는 `step = 4` 도달로 판정한다 — 따로 알리지 않는다
  expect(eventsOf(batches, "finder_complete"), "완주 이벤트를 따로 만들었다").toEqual([]);
});

/**
 * RED 11 — 중간에 그만둬도 거기까지는 남는다.
 *
 * 단계마다 보내므로 완주하지 않아도 1·2단계가 있다. **완주율은 `step=4` / `step=1` 로
 * 계산**하므로(SPEC-10 §6.1) 이탈한 사람의 앞 단계가 없으면 분모가 사라진다.
 *
 * 떠나는 순간의 전송은 브라우저가 대신 보내는데(`sendBeacon`) 그 요청은 문서가 사라진 뒤라
 * 테스트가 가로채지 못한다. 그래서 **떠나기 직전 상태**를 본다 — 큐에 두 단계가 들어 있고
 * 화면이 숨겨지는 순간 나간다.
 */
test("RED11 - 중간에 그만둬도 거기까지는 기록된다", async ({ page }) => {
  const batches = await collect(page);
  await ready(page, FINDER);

  await page.locator(".quiz-options button").last().click();
  await page.locator(".quiz-options button").first().click();
  await expect(page.locator(".quiz-kicker")).toContainText("QUESTION 3 / 4");

  const events = await flushed(page, batches, "finder_step", 2);
  expect(events.map((e) => payloadOf(e).step), "그만둔 지점까지가 안 남았다").toEqual([1, 2]);
});

test("RED9 - candidateCount 가 화면이 보여 준 수와 같다", async ({ page }) => {
  const batches = await collect(page);
  await ready(page, FINDER);

  await page.locator(".quiz-options button").last().click();
  const shown = Number(await page.locator(".quiz-count b").innerText());

  const [event] = await flushed(page, batches, "finder_step");
  expect(event.payload, "화면의 후보 수와 보낸 수가 다르다").toMatchObject({
    candidateCount: shown,
  });
});

// ── RED 12~16 : recipe_interact (SPEC-10 §4.5) ───────────────────────────

test("RED12,13,14,15 - 세 조작이 각각 기록된다", async ({ page }) => {
  const batches = await collect(page);
  await page.goto(DETAIL);
  await expect(page.getByRole("button", { name: "잔 수 늘리기" })).toBeEnabled();

  await page.getByRole("button", { name: "잔 수 늘리기" }).click();
  await page.locator("label.seg-opt").filter({ hasText: /^oz$/ }).click();
  await page.getByRole("button", { name: "대체 가능" }).first().click();

  const events = await flushed(page, batches, "recipe_interact", 3);
  const byAction = new Map(events.map((e) => [payloadOf(e).action, payloadOf(e)]));

  expect(byAction.get("servings_change"), "잔 수 변경이 기록되지 않았다").toMatchObject({
    cocktailSlug: "negroni",
    detail: "2",
  });
  expect(byAction.get("unit_toggle")).toMatchObject({ detail: "oz" });
  expect(String(byAction.get("substitute_open")!.detail), "어느 재료인지가 없다").toContain("캄파리");
});

test("RED16 - 세 가지 밖의 액션이 없다", async ({ page }) => {
  const batches = await collect(page);
  await page.goto(DETAIL);
  await expect(page.getByRole("button", { name: "잔 수 늘리기" })).toBeEnabled();

  await page.getByRole("button", { name: "잔 수 늘리기" }).click();
  await page.getByRole("button", { name: "대체 가능" }).first().click();
  await page.getByRole("button", { name: "대체 가능" }).first().click(); // 접는다

  const events = await flushed(page, batches, "recipe_interact", 2);
  for (const event of events) {
    expect(["servings_change", "unit_toggle", "substitute_open"]).toContain(payloadOf(event).action);
  }
  // 접는 것은 세지 않는다 — 같은 관심의 뒷면이라 두 번 세면 부풀린다
  expect(events.filter((e) => payloadOf(e).action === "substitute_open").length).toBe(1);
});

// ── RED 17~20 : bookmark_add · share_click (SPEC-10 §4.6) ────────────────

test("RED17,18 - 저장이 된 뒤에 bookmark_add 가 나간다", async ({ page }) => {
  const batches = await collect(page);
  await page.route("**/api/v1/me/bookmarks", (route) => route.fulfill({ status: 201, json: {} }));

  await page.goto(DETAIL);
  await page.getByRole("button", { name: /저장/ }).click();
  await expect(page.getByRole("button", { name: /저장됨/ })).toBeVisible();

  const [event] = await flushed(page, batches, "bookmark_add");
  expect(event.payload).toMatchObject({ targetType: "cocktail", targetSlug: "negroni" });
});

/**
 * 저장이 **안 됐으면** 세지 않는다.
 *
 * 누른 것을 세면 로그인 유도로 끝난 것까지 저장으로 잡히고, 그러면 저장률이 실제보다 높다.
 */
test("로그인이 필요하면 bookmark_add 가 나가지 않는다", async ({ page }) => {
  const batches = await collect(page);
  await page.route("**/api/v1/me/bookmarks", (route) => route.fulfill({ status: 401, json: {} }));

  await page.goto(DETAIL);
  await page.getByRole("button", { name: /저장/ }).click();
  await expect(page.getByText("로그인하면 저장할 수 있습니다")).toBeVisible();

  await page.evaluate(() => window.dispatchEvent(new Event("pagehide")));
  expect(eventsOf(batches, "bookmark_add")).toEqual([]);
});

test("RED19,20 - 공유하면 채널과 함께 share_click 이 나간다", async ({ page }) => {
  const batches = await collect(page);
  await page.goto(DETAIL);

  // 공유 시트가 없는 환경이면 링크 복사로 떨어진다 — 그 경로가 `link` 다
  await page.evaluate(() => {
    // @ts-expect-error 테스트에서 공유 시트를 지운다
    delete navigator.share;
  });
  await page.context().grantPermissions(["clipboard-read", "clipboard-write"]);
  await page.getByRole("button", { name: /공유/ }).click();

  const [event] = await flushed(page, batches, "share_click");
  expect(event.payload).toMatchObject({
    targetType: "cocktail",
    targetSlug: "negroni",
    channel: "link",
  });
});

test("RED20 - 채널 세 가지가 타입에 있다", () => {
  const source = readFileSync(join(process.cwd(), "lib/analytics/events/index.ts"), "utf8");
  expect(source).toMatch(/ShareChannel\s*=\s*"kakao"\s*\|\s*"link"\s*\|\s*"system"/);
});

// ── RED 21~23 : 035 재사용 (SPEC-10 §2) ──────────────────────────────────

/**
 * RED 21 — **전송을 다시 만들지 않는다.**
 *
 * 큐·세션·공통 필드는 035 것이다. 여기서 `fetch` 나 `sendBeacon` 을 부르면 경로가 두 벌이
 * 되고, 한쪽만 고치는 날 절반의 이벤트가 조용히 사라진다.
 */
test("RED21 - 전송 로직을 다시 만들지 않았다", () => {
  const source = readFileSync(join(process.cwd(), "lib/analytics/events/index.ts"), "utf8");

  expect(source, "이벤트 층이 직접 보내고 있다").not.toMatch(/sendBeacon|fetch\(/);
  expect(source, "035 의 track 을 쓰지 않는다").toMatch(/import \{ track \} from "\.\.\/core"/);
});

test("RED22 - 공통 필드가 035 와 같다", async ({ page }) => {
  const batches = await collect(page);
  await ready(page, SEARCH);
  await chips(page, "기주 BASE SPIRIT").first().click();

  const [event] = await flushed(page, batches, "filter_apply");
  expect(event.sessionId).toMatch(/^[0-9a-f-]{36}$/i);
  expect(event.path, "쿼리스트링이 실려 나갔다").toBe(SEARCH);
  expect(["organic", "internal", "social", "direct", "unknown"]).toContain(event.referrerType);
  expect(event.occurredAt).toBeTruthy();
});

test("RED23 - 전송이 막혀도 화면이 그대로 돈다", async ({ page }) => {
  const errors: string[] = [];
  page.on("pageerror", (e) => errors.push(e.message));
  await page.route("**/api/events", (route) => route.abort("blockedbyclient"));

  await ready(page, SEARCH);
  await chips(page, "기주 BASE SPIRIT").first().click();
  await expect(page.locator(".results-count b")).toBeVisible();

  await page.goto(FINDER);
  await expect(page.locator("main[data-ready]")).toBeVisible();
  await page.locator(".quiz-options button").first().click();
  await expect(page.locator(".quiz-kicker")).toContainText("QUESTION 2 / 4");

  expect(errors, "계측 실패가 페이지 오류로 튀었다").toEqual([]);
});

/** 이벤트 이름과 payload 키는 서버가 정한다 — 모르는 것은 조용히 버려진다. */
test("다섯 이벤트의 이름과 키가 서버 계약과 같다", () => {
  const contract = readFileSync(
    join(process.cwd(), "../../apps/api/src/main/kotlin/kr/mut/common/analytics/EventType.kt"),
    "utf8",
  );

  const expected: Record<string, string[]> = {
    filter_apply: ["axis", "value", "resultCount", "activeAxisCount"],
    finder_step: ["step", "answered", "candidateCount"],
    recipe_interact: ["cocktailSlug", "action", "detail"],
    bookmark_add: ["targetType", "targetSlug"],
    share_click: ["targetType", "targetSlug", "channel"],
  };

  const source = readFileSync(join(process.cwd(), "lib/analytics/events/index.ts"), "utf8");

  for (const [event, keys] of Object.entries(expected)) {
    expect(contract, `${event} 이 계약에 없다`).toContain(`"${event}"`);
    for (const key of keys) {
      expect(contract, `${event}.${key} 를 서버가 버린다`).toContain(`"${key}"`);
      expect(source, `${key} 를 보내지 않는다`).toContain(key);
    }
  }
});
