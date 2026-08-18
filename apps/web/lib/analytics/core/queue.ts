import type { components } from "@kca/domain/generated/api";
import { sessionId } from "./session";
import { currentReferrerType } from "./referrer";

type EventRequest = components["schemas"]["EventRequest"];

/**
 * 이벤트 큐 (ISSUE-035 · SPEC-10 §2 · `NFR-R-04`).
 *
 * ## 모아서 보낸다
 *
 * 이벤트마다 요청을 내면 상세 한 번 보는 동안 서너 번 나간다. 잠깐 모았다가 한 번에 보낸다 —
 * 서버도 배치로 받게 돼 있다 (`POST /events`, 요청당 50건).
 *
 * ## 실패해도 조용히
 *
 * `NFR-R-04` 는 **수집 실패가 사용자 흐름을 막지 않는 것**을 배포 차단 조건으로 뒀다.
 * 광고 차단기가 이 요청을 막는 일이 흔하고, 그때 화면이 멈추면 계측 때문에 서비스를 잃는다.
 * 그래서 여기서는 던지지 않고, 로그도 `debug` 다 (DECISIONS §1.11) —
 * `console.error` 로 남기면 사용자 콘솔이 빨개지고 그것을 버그로 신고하게 된다.
 *
 * ## 떠날 때 남은 것을 보낸다
 *
 * 페이지를 닫는 순간의 요청은 보통 취소된다. `sendBeacon` 은 브라우저가 대신 마저 보내 준다.
 *
 * ## 같은 배치는 한 번만 센다
 *
 * `Idempotency-Key` 를 배치마다 만든다 (`PRIN-T07`). 재시도가 집계를 부풀리지 않아야 한다.
 * **키를 본문에 담는다** — `sendBeacon` 은 헤더를 못 붙이기 때문이고, 같은 오리진의
 * `/api/events` 가 그것을 헤더로 올려 서버에 넘긴다.
 */

/** SPEC-10 §7 · 이슈 034 — 요청당 50건. 넘으면 서버가 400 이다. */
export const MAX_BATCH = 50;

/** 모으는 시간. 짧으면 배치가 안 되고, 길면 떠날 때 놓친다. */
const FLUSH_MS = 2_000;

export class EventQueue {
  private pending: EventRequest[] = [];
  private timer: ReturnType<typeof setTimeout> | null = null;
  private bound = false;

  /**
   * 이벤트를 큐에 넣는다. **여기서 던지지 않는다** — 호출부는 계측이 되는지 신경 쓰지 않는다.
   */
  push(eventType: string, payload: Record<string, unknown>) {
    if (typeof window === "undefined") return;

    try {
      this.pending.push({
        eventType,
        sessionId: sessionId(),
        // 쿼리스트링을 빼고 보낸다 (SPEC-10 §3). 서버도 자르지만, 보내지 않는 편이 낫다 —
        // 검색어가 주소에 실리는 화면이 있다.
        path: window.location.pathname,
        referrerType: currentReferrerType(),
        occurredAt: new Date().toISOString(),
        payload: payload as EventRequest["payload"],
      });

      this.bindUnload();
      if (this.pending.length >= MAX_BATCH) this.flush();
      else this.schedule();
    } catch {
      // 계측이 화면을 멈추게 두지 않는다 (`NFR-R-04`)
    }
  }

  /** 모아 둔 것을 보낸다. 큐는 먼저 비운다 — 실패해도 다시 쌓아 두지 않는다(집계가 부풀지 않게). */
  flush() {
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }
    if (this.pending.length === 0) return;

    const batch = this.pending.slice(0, MAX_BATCH);
    this.pending = this.pending.slice(MAX_BATCH);

    const body = JSON.stringify({ idempotencyKey: keyFor(batch), events: batch });

    try {
      // 떠나는 중이어도 브라우저가 마저 보낸다.
      if (navigator.sendBeacon?.(ENDPOINT, new Blob([body], { type: "application/json" }))) return;

      // `sendBeacon` 이 없거나 거절하면(큐 한도 초과) 보통 요청으로. `keepalive` 로 떠나도 살린다.
      void fetch(ENDPOINT, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body,
        keepalive: true,
      }).catch(quiet);
    } catch (e) {
      quiet(e);
    }
  }

  private schedule() {
    if (this.timer) return;
    this.timer = setTimeout(() => this.flush(), FLUSH_MS);
  }

  /**
   * 떠날 때를 한 번만 걸어 둔다.
   *
   * `pagehide` 는 뒤로가기 캐시로 나갈 때도 온다. `visibilitychange` 를 함께 보는 이유는
   * 모바일에서 앱을 전환하면 `pagehide` 없이 사라지는 경우가 있어서다.
   */
  private bindUnload() {
    if (this.bound) return;
    this.bound = true;

    window.addEventListener("pagehide", () => this.flush());
    document.addEventListener("visibilitychange", () => {
      if (document.visibilityState === "hidden") this.flush();
    });
  }
}

/** 같은 오리진을 거친다 — API 는 다른 오리진이고 CORS 를 열지 않았다 (이슈 042 와 같은 이유). */
const ENDPOINT = "/api/events";

/**
 * 배치 하나의 키.
 *
 * 재시도가 같은 배치를 다시 보내면 같은 키여야 한다 (`PRIN-T07`). 배치 내용에서 만들면
 * 그 성질이 저절로 성립한다 — 무작위로 만들면 재시도마다 새 키가 되어 집계가 부푼다.
 */
function keyFor(batch: EventRequest[]): string {
  const seed = batch.map((e) => `${e.eventType}:${e.occurredAt}:${e.path}`).join("|");

  let hash = 0;
  for (let i = 0; i < seed.length; i++) hash = (hash * 31 + seed.charCodeAt(i)) | 0;

  return `evt-${batch[0]?.sessionId ?? "anon"}-${(hash >>> 0).toString(36)}-${batch.length}`;
}

/** 실패는 `debug` 다. 콘솔이 빨개지면 사용자가 그것을 버그로 신고한다 (DECISIONS §1.11). */
function quiet(e: unknown) {
  console.debug("[analytics] 전송 실패 — 무시한다", e);
}
