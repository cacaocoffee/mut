import { headers } from "next/headers";

/**
 * 본인 리소스 프록시 (SPEC-07 §2.5 — /me/*).
 *
 * 브라우저가 API 를 직접 못 부른다(다른 오리진·CORS 안 열림, 이슈 047 어드민 프록시와 같은
 * 사정). 저장(북마크)·컬렉션·프로필이 여기를 지난다. **그대로 옮기기만 한다** —
 * 경로·질의·본문·상태 코드를 손대지 않는다.
 *
 * 세션 쿠키와 CSRF 토큰을 넘긴다(SPEC-08 §4.3). 쓰기(POST·DELETE)는 서버가
 * `X-CSRF-Token` 을 요구한다. 비로그인은 상류가 401 을 주고, 화면은 그걸로 로그인을 유도한다.
 */
const BASE = process.env.MUT_API_URL?.replace(/\/$/, "") ?? "";

async function proxy(request: Request, path: string[]): Promise<Response> {
  if (!BASE) return Response.json({ error: "API 주소가 없다" }, { status: 503 });

  const incoming = await headers();
  const url = new URL(request.url);
  const target = `${BASE}/api/v1/me/${path.join("/")}${url.search}`;

  const forward: Record<string, string> = {};
  for (const name of ["cookie", "x-csrf-token", "content-type"]) {
    const value = incoming.get(name);
    if (value) forward[name] = value;
  }

  const body =
    request.method === "GET" || request.method === "HEAD" ? undefined : await request.arrayBuffer();

  try {
    const res = await fetch(target, { method: request.method, headers: forward, body, cache: "no-store" });

    const out = new Headers({ "Content-Type": res.headers.get("content-type") ?? "application/json" });
    // 세션 연장·재발급 쿠키와 CSRF 토큰을 옮긴다 (G-45) — 떨어뜨리면 로그인이 웹 오리진에서
    // 성립하지 않는다.
    const csrf = res.headers.get("x-csrf-token");
    if (csrf) out.set("X-CSRF-Token", csrf);
    for (const cookie of res.headers.getSetCookie()) out.append("Set-Cookie", cookie);

    // 204(삭제)는 본문이 없다 — text() 로 빈 문자열을 만들지 말고 그대로 비운다.
    const payload = res.status === 204 ? null : await res.text();
    return new Response(payload, { status: res.status, headers: out });
  } catch (e) {
    console.warn(`[me-proxy] 상류 호출 실패: ${e instanceof Error ? e.message : String(e)}`);
    return Response.json({ error: "API 를 부르지 못했다" }, { status: 502 });
  }
}

type Ctx = { params: Promise<{ path: string[] }> };

export async function GET(request: Request, ctx: Ctx) {
  return proxy(request, (await ctx.params).path);
}
export async function POST(request: Request, ctx: Ctx) {
  return proxy(request, (await ctx.params).path);
}
export async function DELETE(request: Request, ctx: Ctx) {
  return proxy(request, (await ctx.params).path);
}
