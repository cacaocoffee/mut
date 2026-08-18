import { EventQueue } from "./queue";

export { EventQueue, MAX_BATCH } from "./queue";
export { classifyReferrer, currentReferrerType, type ReferrerType } from "./referrer";
export { sessionId } from "./session";
export { previousPath, recordVisit } from "./navigation";

/**
 * 계측 입구 (ISSUE-035 · SPEC-10).
 *
 * 화면은 이 함수 하나만 안다. 큐·세션·유입 분류는 이 아래에 있고, 이슈 049 가 이벤트를
 * 더할 때도 **전송 경로는 여기 한 곳**이다.
 *
 * ```ts
 * track("cocktail_view", { cocktailSlug: "negroni", entryPoint: "external" });
 * ```
 *
 * 이름과 payload 키는 서버의 `EventType` 이 정한다 — 모르는 이름이나 키는 **조용히 버려진다.**
 * 화면에서 확인할 방법이 없으므로 `e2e/analytics.spec.ts` 가 계약과 맞는지 본다.
 */
const queue = new EventQueue();

export function track(eventType: string, payload: Record<string, unknown> = {}) {
  queue.push(eventType, payload);
}

/** 지금 모아 둔 것을 즉시 보낸다. 화면을 떠나기 직전처럼 기다릴 수 없을 때만 쓴다. */
export function flushEvents() {
  queue.flush();
}
