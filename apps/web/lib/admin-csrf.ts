/**
 * 어드민 쓰기 요청의 CSRF 배선 (SPEC-08 §4.3 · SPEC-07 §1.2).
 *
 * 서버는 상태 변경(POST·PATCH·PUT)에 **세션에 묶인 CSRF 토큰**을 요구한다.
 * 토큰은 `GET /api/v1/auth/csrf` 가 주고, 프록시(G-45)가 응답을 그대로 옮긴다.
 * 화면이 이 배선 없이 그냥 fetch 를 쓰다가 실서버에서 쓰기가 전부 403 이 났다
 * (2026-08-24) — 1단계에서는 API 가 없어 이 공백이 드러나지 않았다.
 *
 * 토큰은 세션 단위라 모듈에 한 번만 받아 둔다. 재로그인 등으로 403 이 나면
 * 한 번 재발급해 다시 보낸다 — 그래도 403 이면 권한 문제라 그대로 돌려준다.
 */
let cached: Promise<string | null> | null = null;

function fetchToken(): Promise<string | null> {
  return fetch("/api/v1/auth/csrf", { cache: "no-store" })
    .then(async (res) => (res.ok ? (((await res.json()) as { token?: string }).token ?? null) : null))
    .catch(() => null);
}

export async function adminWrite(input: string, init: RequestInit = {}): Promise<Response> {
  cached ??= fetchToken();

  const attempt = async (token: string | null) => {
    const headers = new Headers(init.headers);
    if (token) headers.set("X-CSRF-Token", token);
    return fetch(input, { ...init, headers });
  };

  let res = await attempt(await cached);
  if (res.status === 403) {
    cached = fetchToken();
    res = await attempt(await cached);
  }
  return res;
}
