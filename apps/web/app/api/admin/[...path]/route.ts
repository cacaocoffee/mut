import { headers } from "next/headers";

/**
 * 어드민 API 프록시 (ISSUE-047).
 *
 * 브라우저가 API 를 직접 못 부른다 — 다른 오리진이고 CORS 를 열지 않았다 (이슈 042·035 와
 * 같은 사정). **그대로 옮기기만 한다**: 경로·질의·본문·상태 코드를 손대지 않는다.
 * 여기서 모양을 바꾸면 계약이 둘이 되고, 서버가 막는 것을 화면이 통과시키게 된다.
 *
 * 세션 쿠키를 넘긴다 (SPEC-07 §1.2). CSRF 토큰도 그대로 옮긴다 — 쓰기 요청은 서버가
 * `X-CSRF-Token` 을 요구한다.
 */
const BASE = process.env.MUT_API_URL?.replace(/\/$/, "") ?? "";

async function proxy(request: Request, path: string[]): Promise<Response> {
  if (!BASE) return Response.json({ error: "API 주소가 없다" }, { status: 503 });

  const incoming = await headers();
  const url = new URL(request.url);
  const target = `${BASE}/api/v1/admin/${path.join("/")}${url.search}`;

  const forward: Record<string, string> = {};
  // 인증·CSRF·본문 형식만 옮긴다. 나머지 헤더는 상류가 스스로 판단한다.
  for (const name of ["cookie", "x-csrf-token", "content-type"]) {
    const value = incoming.get(name);
    if (value) forward[name] = value;
  }

  // 본문을 텍스트로 읽지 않는다 — 사진 업로드(multipart)는 바이너리라 문자열로 옮기면 깨진다.
  // 바이트 그대로 넘긴다. JSON 도 바이트로 넘어가므로 손상되지 않는다.
  const body =
    request.method === "GET" || request.method === "HEAD" ? undefined : await request.arrayBuffer();

  try {
    const res = await fetch(target, { method: request.method, headers: forward, body, cache: "no-store" });

    // 422(게이트 실패)·409(이미 발행)를 그대로 넘긴다 — 화면이 그 코드로 분기한다.
    const out = new Headers({ "Content-Type": res.headers.get("content-type") ?? "application/json" });
    // 세션 연장·재발급 쿠키와 CSRF 토큰도 옮긴다 (G-45 판정) — 떨어뜨리면
    // 쿠키가 브라우저까지 오지 못해 로그인이 웹 오리진에서 성립하지 않는다.
    const csrf = res.headers.get("x-csrf-token");
    if (csrf) out.set("X-CSRF-Token", csrf);
    for (const cookie of res.headers.getSetCookie()) out.append("Set-Cookie", cookie);

    return new Response(await res.text(), { status: res.status, headers: out });
  } catch (e) {
    console.warn(`[admin-proxy] 상류 호출 실패: ${e instanceof Error ? e.message : String(e)}`);
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
export async function PATCH(request: Request, ctx: Ctx) {
  return proxy(request, (await ctx.params).path);
}
// 레시피 저장이 PUT 이다 (계약 `PUT /admin/cocktails/{id}/recipe`). 이 내보내기가 없으면
// 상류까지 가지도 못하고 Next 가 405 를 낸다 — 실서버에서 저장이 막혔던 원인 (2026-08-24).
export async function PUT(request: Request, ctx: Ctx) {
  return proxy(request, (await ctx.params).path);
}
