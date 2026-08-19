import { cache } from "react";
import { headers } from "next/headers";
import { notFound } from "next/navigation";

/**
 * 어드민 접근 · 역할 판정 (ISSUE-045·048 · SPEC-08 §2·§2.2·§4.1 · `FR-ADMIN-001`).
 *
 * ## 역할을 물어볼 곳이 없다
 *
 * 계약에 `/me` 가 없다 (`/me/bookmarks` 뿐이다). 그래서 **어드민 엔드포인트를 두드려**
 * 답으로 판정한다 — `200` 이면 되고, `401` 이면 로그인이 필요하고, `403`·`404` 면 역할이
 * 모자라다. 새 API 를 만들지 않는 것이 중요하다: 판정을 두 벌로 두면 화면이 허용한
 * 사람을 서버가 막는 상태가 생긴다.
 *
 * ## `admin` 과 `editor` 를 가르는 것은 감사 로그다
 *
 * SPEC-08 §2 에서 `admin` 만 되는 것은 재료 승인과 감사 조회 둘이다. 그중 **읽기**이고
 * 부작용이 없는 쪽이 감사 조회라 그것으로 두드린다 — 승인으로 두드리면 판정하려다
 * 재료를 승인해 버린다.
 *
 * `admin` 은 요청 하나로 끝나고, `editor` 는 두 번 두드린다 (감사 거부 → 칵테일 목록).
 * 내부 도구라 그 한 번을 아끼는 것보다 판정이 서버와 같은 것이 중요하다.
 *
 * ## 권한이 없으면 404 다
 *
 * SCREENS-00 §3.4 — "권한 없음" 을 보여 주지 않는다. 있다는 사실 자체가 정보다.
 *
 * ## 쿠키를 그대로 넘긴다
 *
 * SPEC-07 §1.2 — 서버 컴포넌트에서 API 를 부를 때 **들어온 쿠키를 그대로 전달**한다.
 * 세션은 `httpOnly` 라 브라우저 JS 가 못 읽고, 서버가 옮기는 수밖에 없다.
 */
const BASE = process.env.KC_API_URL?.replace(/\/$/, "") ?? "";

/** 어드민에 들어올 수 있는 두 역할. `member` 는 여기까지 오지 못한다 (미들웨어가 404). */
export type AdminRole = "admin" | "editor";

export type AdminAccess =
  | { kind: "allowed"; role: AdminRole }
  /** 로그인부터 해야 한다. */
  | { kind: "unauthenticated" }
  /** 로그인은 했지만 역할이 모자라다 (`member`). */
  | { kind: "forbidden" }
  /** API 를 못 불렀다. 막지도 열지도 않고 화면이 안내한다. */
  | { kind: "unavailable"; reason: string };

/**
 * 한 요청 안에서는 한 번만 두드린다.
 *
 * 레이아웃(메뉴 노출)과 페이지(가드)가 같은 답을 봐야 한다 — 따로 물으면 메뉴는 보이는데
 * 페이지는 404 인 상태가 렌더 도중에 생길 수 있다. `cache` 는 요청 단위라 사용자 간에
 * 섞이지 않는다.
 */
export const adminAccess = cache(async (): Promise<AdminAccess> => {
  if (!BASE) return { kind: "unavailable", reason: "KC_API_URL 이 없다" };

  const cookie = (await headers()).get("cookie");
  if (!cookie) return { kind: "unauthenticated" };

  // 감사 조회는 `admin` 뿐이다 (SPEC-08 §2.2). 목록을 쓰는 것이 아니라 **답만** 본다.
  const audit = await probe("/audit-logs?size=1", cookie);
  if (typeof audit === "string") return { kind: "unavailable", reason: audit };
  if (audit === 200) return { kind: "allowed", role: "admin" };
  if (audit === 401) return { kind: "unauthenticated" };
  if (audit !== 403 && audit !== 404) return { kind: "unavailable", reason: `HTTP ${audit}` };

  // 감사가 막혔다고 `editor` 인 것은 아니다 — `member` 도 여기로 온다. 한 번 더 두드린다.
  const admin = await probe("/cocktails?size=1", cookie);
  if (typeof admin === "string") return { kind: "unavailable", reason: admin };
  if (admin === 200) return { kind: "allowed", role: "editor" };
  if (admin === 401) return { kind: "unauthenticated" };
  if (admin === 403 || admin === 404) return { kind: "forbidden" };

  return { kind: "unavailable", reason: `HTTP ${admin}` };
});

/** 상태 코드만 돌려준다. 부르지 못하면 사유 문자열이다. */
async function probe(path: string, cookie: string): Promise<number | string> {
  try {
    const res = await fetch(`${BASE}/api/v1/admin${path}`, {
      headers: { cookie },
      cache: "no-store",
    });
    return res.status;
  } catch (e) {
    return e instanceof Error ? e.message : String(e);
  }
}

/**
 * 어드민 페이지가 첫 줄에서 부른다. 들어올 수 있으면 **역할**을 돌려준다.
 *
 * **레이아웃만으로는 상태 코드가 안 붙는다** — 레이아웃에서 `notFound()` 를 불러도 응답은
 * `200` 이고 본문만 not-found 로 그려진다 (Next 16 에서 확인). 크롤러와 스크립트는 본문이
 * 아니라 상태를 보므로, 없는 것처럼 두려면 **페이지에서** 불러야 한다.
 *
 * 레이아웃의 가드는 그대로 둔다 — 새 어드민 화면이 이 호출을 빠뜨려도 내용은 안 그려진다.
 * 둘 다 있는 것이 의도다 (상태는 페이지가, 렌더는 레이아웃이).
 */
export async function requireAdmin(): Promise<AdminRole> {
  const access = await adminAccess();
  if (access.kind === "allowed") return access.role;

  if (access.kind === "unavailable") {
    console.warn(`[admin] 접근 판정 실패 — 404 로 답한다: ${access.reason}`);
  }
  notFound();
}

/**
 * `admin` 전용 화면이 부른다 — 감사 로그가 그렇다 (SPEC-08 §2.2).
 *
 * `editor` 에게 404 를 준다. **메뉴에서 숨기는 것으로는 부족하다** — 주소를 직접 치면
 * 들어올 수 있어서다. 숨김은 UX 이고 막는 것은 서버지만, 화면도 서버와 같은 답을 해야
 * 한다 (여기서 그린 다음 API 가 403 을 주면 빈 표가 남는다).
 */
export async function requireAdminRole(): Promise<void> {
  const role = await requireAdmin();
  if (role !== "admin") notFound();
}
