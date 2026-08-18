/**
 * 세션 식별 (ISSUE-035 · SPEC-10 §3).
 *
 * ## 누구인지 모르는 채로 센다
 *
 * `sessionId` 는 브라우저가 만든 UUID 다. 사람을 가리키지 않고 **한 번의 방문**을 가리킨다 —
 * MAU 를 `DISTINCT sessionId` 로 세는 것이 SPEC-10 §6 의 계산법이다.
 *
 * ## 30분 무활동이면 다른 방문이다
 *
 * 아침에 열어 둔 탭으로 저녁에 다시 보는 것은 같은 방문이 아니다. 이벤트를 보낼 때마다
 * 마지막 활동 시각을 밀어 두고, 30분이 지났으면 새 UUID 를 만든다.
 *
 * ## `localStorage` 다
 *
 * 탭을 여럿 열어도 한 방문이어야 한다 (DECISIONS §1.11). `sessionStorage` 는 탭마다 달라
 * 한 사람이 탭 셋을 열면 방문 셋으로 세어진다.
 */
const KEY = "kca:analytics-session";

/** SPEC-10 §3 — 30분 무활동. */
const IDLE_MS = 30 * 60 * 1000;

interface Stored {
  id: string;
  lastSeen: number;
}

/**
 * 지금 방문의 식별자. 없으면 만들고, 오래됐으면 새로 만든다.
 *
 * 저장이 막혀 있어도(사생활 보호 모드) **빈 값을 주지 않는다** — 그날의 이벤트가 통째로
 * 버려지는 것보다 방문이 매번 새로 세어지는 편이 낫다.
 */
export function sessionId(now = Date.now()): string {
  const stored = read();

  if (stored && now - stored.lastSeen < IDLE_MS) {
    write({ id: stored.id, lastSeen: now });
    return stored.id;
  }

  const fresh = { id: uuid(), lastSeen: now };
  write(fresh);
  return fresh.id;
}

function read(): Stored | null {
  try {
    const raw = window.localStorage.getItem(KEY);
    if (!raw) return null;

    const parsed = JSON.parse(raw) as Partial<Stored>;
    return typeof parsed.id === "string" && typeof parsed.lastSeen === "number"
      ? { id: parsed.id, lastSeen: parsed.lastSeen }
      : null;
  } catch {
    return null;
  }
}

function write(value: Stored) {
  try {
    window.localStorage.setItem(KEY, JSON.stringify(value));
  } catch {
    // 저장이 막혔다. 이번 화면에서만 유지되면 된다 — 계측 때문에 화면이 멈추지 않는다.
  }
}

/** `crypto.randomUUID` 가 없는 환경을 위한 폴백. 형식만 맞으면 된다 — 서버가 UUID 로 파싱한다. */
function uuid(): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) return crypto.randomUUID();

  return "10000000-1000-4000-8000-100000000000".replace(/[018]/g, (c) =>
    (
      Number(c) ^
      (typeof crypto !== "undefined"
        ? crypto.getRandomValues(new Uint8Array(1))[0]
        : Math.floor(Math.random() * 256)) &
        (15 >> (Number(c) / 4))
    ).toString(16),
  );
}
