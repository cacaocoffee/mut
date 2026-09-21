/**
 * #196 RED 2·3 — 배지·대체재 카드 판정. e2e 는 API 없이 도는 날이 있어(프로토타입 폴백)
 * 판정을 순수 함수로 빼고 여기서 고정한다.
 *
 *     npx tsx --test apps/web/lib/ingredient-distribution.test.ts
 */
import assert from "node:assert/strict";
import { test } from "node:test";
import { availabilityBadge, needsSubstituteCard, reportedMonth } from "./ingredient-distribution";

test("RED2 - common·specialty 는 '국내 유통 · 최근 신고 YYYY-MM' 배지다", () => {
  assert.deepEqual(availabilityBadge("common", "2026-07-30"), {
    text: "국내 유통 · 최근 신고 2026-07",
    tone: "accent",
  });
  assert.deepEqual(availabilityBadge("specialty", "2025-01-15"), {
    text: "전문점 유통 · 최근 신고 2025-01",
    tone: "accent-2",
  });
});

test("RED2 - 신고일이 없으면 날짜 없이 유통 여부만 쓴다 — 없는 값을 지어내지 않는다", () => {
  assert.equal(availabilityBadge("common", null).text, "국내 유통");
  assert.equal(availabilityBadge("common", undefined).text, "국내 유통");
});

test("RED3 - import_only·unavailable 만 대체재 카드가 필수다", () => {
  assert.equal(needsSubstituteCard("import_only"), true);
  assert.equal(needsSubstituteCard("unavailable"), true);
  assert.equal(needsSubstituteCard("common"), false);
  assert.equal(needsSubstituteCard("specialty"), false);
  assert.equal(availabilityBadge("import_only", "2026-01-01").text, "해외 구매만");
  assert.equal(availabilityBadge("unavailable", null).text, "국내 유통 없음");
});

test("신고 월은 연-월까지만", () => {
  assert.equal(reportedMonth("2026-07-30"), "2026-07");
  assert.equal(reportedMonth("어제"), null);
  assert.equal(reportedMonth(null), null);
});
