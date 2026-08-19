import { headers } from "next/headers";
import { notFound } from "next/navigation";

/**
 * 어드민 접근 판정 (ISSUE-045 · SPEC-08 §2·§4.1 · `FR-ADMIN-001`).
 *
 * ## 역할을 물어볼 곳이 없다
 *
 * 계약에 `/me` 가 없다 (`/me/bookmarks` 뿐이다). 그래서 **어드민 엔드포인트를 한 번 두드려**
 * 답으로 판정한다 — `200` 이면 들어갈 수 있고, `401` 이면 로그인이 필요하고, `403` 이면
 * 역할이 모자란다. 새 API 를 만들지 않는 것이 중요하다: 판정을 두 벌로 두면 화면이
 * 허용한 사람을 서버가 막는 상태가 생긴다.
 *
 * ## 권한이 없으면 404 다
 *
 * SCREENS-00 §3.4 — "권한 없음" 을 보여 주지 않는다. 있다는 사실 자체가 정보라서다.
 * 화면은 이 함수의 결과로 `notFound()` 를 부른다.
 *
 * ## 쿠키를 그대로 넘긴다
 *
 * SPEC-07 §1.2 — 서버 컴포넌트에서 API 를 부를 때 **들어온 쿠키를 그대로 전달**한다.
 * 세션은 `httpOnly` 라 브라우저 JS 가 못 읽고, 서버가 옮기는 수밖에 없다.
 */
const BASE = process.env.KC_API_URL?.replace(/\/$/, "") ?? "";

export type AdminAccess =
  /** 들어갈 수 있다. 어느 역할인지까지는 모른다 — 화면이 필요로 하는 것은 "되나" 다. */
  | { kind: "allowed" }
  /** 로그인부터 해야 한다. */
  | { kind: "unauthenticated" }
  /** 로그인은 했지만 역할이 모자라다 (`member`). */
  | { kind: "forbidden" }
  /** API 를 못 불렀다. 막지도 열지도 않고 화면이 안내한다. */
  | { kind: "unavailable"; reason: string };

export async function adminAccess(): Promise<AdminAccess> {
  if (!BASE) return { kind: "unavailable", reason: "KC_API_URL 이 없다" };

  const cookie = (await headers()).get("cookie");
  if (!cookie) return { kind: "unauthenticated" };

  try {
    // 가장 가벼운 어드민 조회로 두드린다. 목록을 쓰는 것이 아니라 **답만** 본다.
    const res = await fetch(`${BASE}/api/v1/admin/cocktails?size=1`, {
      headers: { cookie },
      cache: "no-store",
    });

    if (res.ok) return { kind: "allowed" };
    if (res.status === 401) return { kind: "unauthenticated" };
    if (res.status === 403 || res.status === 404) return { kind: "forbidden" };

    return { kind: "unavailable", reason: `HTTP ${res.status}` };
  } catch (e) {
    return { kind: "unavailable", reason: e instanceof Error ? e.message : String(e) };
  }
}

/**
 * 어드민 페이지가 첫 줄에서 부른다.
 *
 * **레이아웃만으로는 상태 코드가 안 붙는다** — 레이아웃에서 `notFound()` 를 불러도 응답은
 * `200` 이고 본문만 not-found 로 그려진다 (Next 16 에서 확인). 크롤러와 스크립트는 본문이
 * 아니라 상태를 보므로, 없는 것처럼 두려면 **페이지에서** 불러야 한다.
 *
 * 레이아웃의 가드는 그대로 둔다 — 새 어드민 화면이 이 호출을 빠뜨려도 내용은 안 그려진다.
 * 둘 다 있는 것이 의도다 (상태는 페이지가, 렌더는 레이아웃이).
 */
export async function requireAdmin(): Promise<void> {
  const access = await adminAccess();
  if (access.kind === "allowed") return;

  if (access.kind === "unavailable") {
    console.warn(`[admin] 접근 판정 실패 — 404 로 답한다: ${access.reason}`);
  }
  notFound();
}
