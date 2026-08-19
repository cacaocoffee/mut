import { headers } from "next/headers";

/**
 * 이벤트 수집 프록시 (ISSUE-035 · SPEC-10 §7).
 *
 * ## 왜 거치나
 *
 * 브라우저가 API 를 직접 부를 수 없다 — 다른 오리진이고 CORS 를 열지 않았다 (이슈 042 와
 * 같은 사정). 그리고 `sendBeacon` 은 **헤더를 못 붙인다.** 서버는 `Idempotency-Key` 를
 * 헤더로 요구하므로(`PRIN-T07`), 클라이언트가 본문에 담아 보내고 여기서 헤더로 올린다.
 *
 * ## 그대로 옮기기만 한다
 *
 * 키를 헤더로 옮기고 나머지는 손대지 않는다. 여기서 이벤트를 만들거나 고치면 수집 규칙이
 * 두 곳이 되고, 서버의 검증(`EventCollector`)과 어긋난 것이 조용히 저장된다.
 *
 * ## 실패를 알리지 않는다
 *
 * 수집이 안 되는 것보다 화면이 멈추는 것이 나쁘다 (`NFR-R-04`). 상류가 죽었든 주소가
 * 없든 **`202`** 로 답한다 — 클라이언트는 어차피 결과를 보지 않고, 재시도하지도 않는다.
 */
const BASE = process.env.MUT_API_URL?.replace(/\/$/, "") ?? "";

/** 서버가 요구하는 헤더 이름 (`IdempotencyFilter.HEADER`). */
const IDEMPOTENCY_HEADER = "Idempotency-Key";

export async function POST(request: Request): Promise<Response> {
  const accepted = new Response(null, { status: 202 });
  if (!BASE) return accepted; // 주소가 없으면 수집만 안 된다. 화면은 그대로다.

  let payload: { idempotencyKey?: string; events?: unknown };
  try {
    payload = (await request.json()) as typeof payload;
  } catch {
    return accepted;
  }

  const key = payload.idempotencyKey;
  if (!key || !Array.isArray(payload.events) || payload.events.length === 0) return accepted;

  const incoming = await headers();

  try {
    await fetch(`${BASE}/api/v1/events`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        [IDEMPOTENCY_HEADER]: key,
        // 레이트 리밋이 세션 기준이라 IP 가 결정적이지는 않지만, 남겨 두면
        // 상류 로그에서 한 사람의 흐름을 되짚을 수 있다 (이슈 042 와 같은 처리).
        ...forwardedFor(incoming),
      },
      // 이벤트 배열만 넘긴다 — 서버의 `EventBatch` 는 `events` 하나뿐이라
      // 키를 함께 보내면 모르는 필드가 된다.
      body: JSON.stringify({ events: payload.events }),
      cache: "no-store",
    });
  } catch (e) {
    console.debug("[events] 상류 전송 실패 — 무시한다", e);
  }

  return accepted;
}

function forwardedFor(incoming: Headers): Record<string, string> {
  const ip = incoming.get("x-forwarded-for") ?? incoming.get("x-real-ip");
  return ip ? { "X-Forwarded-For": ip } : {};
}
