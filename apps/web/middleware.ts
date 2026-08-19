import { NextResponse, type NextRequest } from "next/server";

/**
 * 어드민 접근 판정 (ISSUE-045 · SPEC-05 §4 · SCREENS-00 §3.4).
 *
 * ## 왜 미들웨어인가 — **상태 코드는 렌더가 시작되기 전에 정해진다**
 *
 * 레이아웃이나 페이지에서 `notFound()` 를 불러도 응답은 `200` 인 채 본문만 not-found 로
 * 그려진다 (Next 16 에서 확인). 화면을 스트리밍하기 시작한 뒤라 상태를 되돌릴 수 없다.
 * 크롤러·스크립트·모니터링은 본문이 아니라 **상태**를 보므로, 없는 것처럼 두려면
 * 렌더 전에 끝내야 한다.
 *
 * ## 확인되지 않으면 열지 않는다
 *
 * 세션이 없거나(401) 역할이 모자라거나(403) **API 를 못 불러 판정을 못 한 경우도** 404 다.
 * 어드민은 API 없이는 아무것도 못 하고, "지금 서버가 이상합니다" 를 보여 주는 것보다
 * 없는 것처럼 두는 편이 안전하다. 운영자가 알아야 할 이유는 서버 로그에 남는다.
 *
 * ## 판정을 두 벌로 만들지 않는다
 *
 * 계약에 `/me` 가 없어 역할을 물어볼 곳이 없다. **어드민 엔드포인트를 그대로 두드려**
 * 답을 쓴다 — 화면이 허용한 사람을 서버가 막는 상태가 생기지 않는다.
 */
const BASE = process.env.KC_API_URL?.replace(/\/$/, "") ?? "";

export const config = {
  // 어드민만 본다. 공개 화면에 한 홉을 더할 이유가 없다.
  matcher: "/admin/:path*",
};

export async function middleware(request: NextRequest) {
  const cookie = request.headers.get("cookie");

  if (!BASE) return notFound("KC_API_URL 이 없다");
  if (!cookie) return notFound("세션 쿠키가 없다");

  try {
    // 가장 가벼운 어드민 조회로 두드린다. 목록을 쓰는 것이 아니라 **답만** 본다.
    const res = await fetch(`${BASE}/api/v1/admin/cocktails?size=1`, {
      headers: { cookie },
      cache: "no-store",
    });

    if (res.ok) return NextResponse.next();
    return notFound(`HTTP ${res.status}`);
  } catch (e) {
    return notFound(e instanceof Error ? e.message : String(e));
  }
}

/** 404 를 **상태와 함께** 돌려준다. 본문은 비운다 — 무엇이 있었는지 알리지 않는다. */
function notFound(reason: string) {
  console.warn(`[admin] 접근 거절 — 404: ${reason}`);
  return new NextResponse(null, { status: 404 });
}
