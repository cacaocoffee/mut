import { headers } from "next/headers";

/**
 * 인증 프록시 (G-45 판정 · SPEC-07 §1.2 · §2.5).
 *
 * 브라우저는 API 를 직접 부르지 않는다 — 세션 쿠키가 웹 오리진에 귀속되게 하려면
 * 인가 시작 · 콜백 · CSRF · 로그아웃까지 전부 이 프록시를 거쳐야 한다.
 *
 * ## 경로 모양을 상류와 같게 둔다 (`/api/v1/auth/*`)
 *
 * 어드민 프록시(`/api/admin/*`)와 달리 접두를 줄이지 않는다 — 카카오에 등록하는
 * `redirect_uri` 는 상류의 `redirectUri()` 가 `{redirect-base}/api/v1/auth/{provider}/callback`
 * 으로 조립하고 **글자 단위 일치**를 요구하므로, 웹 오리진에서도 같은 경로가 열려 있어야
 * `redirect-base` 에 웹 오리진을 넣는 것만으로 성립한다.
 *
 * ## 리다이렉트를 따라가지 않는다 (`redirect: "manual"`)
 *
 * 인가 시작은 제공자로, 콜백은 로그인 후 화면으로 302 를 준다. 프록시가 따라가 버리면
 * 브라우저는 제자리인 채 제공자 화면의 HTML 만 받는다 — `Location` 을 그대로 넘겨
 * 브라우저가 이동하게 한다.
 *
 * ## `Set-Cookie` 를 전부 옮긴다
 *
 * 인가 시작이 state 세션을, 콜백이 로그인 세션을 발급한다. `getSetCookie()` 로 받아
 * 한 장도 잃지 않고 옮긴다. 상류가 `Domain` 을 안 적으므로(`SessionCookieConfig`)
 * 브라우저는 쿠키를 웹 오리진에 귀속시킨다 — 그것이 G-45 판정의 성립 조건이다.
 */
const BASE = process.env.MUT_API_URL?.replace(/\/$/, "") ?? "";

async function proxy(request: Request, path: string[]): Promise<Response> {
  if (!BASE) return Response.json({ error: "API 주소가 없다" }, { status: 503 });

  const incoming = await headers();
  const url = new URL(request.url);
  const target = `${BASE}/api/v1/auth/${path.join("/")}${url.search}`;

  const forward: Record<string, string> = {};
  for (const name of ["cookie", "x-csrf-token", "content-type"]) {
    const value = incoming.get(name);
    if (value) forward[name] = value;
  }

  const body = request.method === "GET" || request.method === "HEAD" ? undefined : await request.text();

  try {
    const res = await fetch(target, {
      method: request.method,
      headers: forward,
      body,
      cache: "no-store",
      redirect: "manual",
    });

    const out = new Headers();
    const contentType = res.headers.get("content-type");
    if (contentType) out.set("Content-Type", contentType);
    const location = res.headers.get("location");
    if (location) out.set("Location", location);
    // CSRF 토큰은 응답 헤더로 온다 (SPEC-07 §1.2) — 여기서 떨어뜨리면 쓰기 요청이 전부 막힌다
    const csrf = res.headers.get("x-csrf-token");
    if (csrf) out.set("X-CSRF-Token", csrf);
    for (const cookie of res.headers.getSetCookie()) out.append("Set-Cookie", cookie);

    return new Response(await res.text(), { status: res.status, headers: out });
  } catch (e) {
    console.warn(`[auth-proxy] 상류 호출 실패: ${e instanceof Error ? e.message : String(e)}`);
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
