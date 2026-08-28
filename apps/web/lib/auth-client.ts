import { adminWrite } from "@/lib/admin-csrf";

/**
 * 회원 로그인·로그아웃 배선 (SPEC-07 §2.5 · SPEC-08 §4).
 *
 * 어드민과 같은 카카오 흐름이다 — 다른 것은 진입점(내비의 로그인 버튼)과 하는 일(저장)뿐이다.
 * `adminWrite` 는 이름은 어드민이지만 실제로는 **세션 CSRF 토큰을 붙이는 범용 쓰기**라
 * 로그아웃에도 그대로 쓴다.
 */

/** 로그인한 본인 정보. API `GET /me/profile` 의 응답과 같은 모양. */
export interface MyProfile {
  displayName: string;
  roles: string[];
}

/**
 * 지금 로그인 상태인지 본다. 로그인 안 했으면 상류가 401 을 주고, 여기서는 null 로 바꾼다.
 * 네트워크·서버 오류도 null 로 본다 — 로그인 버튼을 보여 주는 편이 안전하다.
 */
export async function fetchProfile(): Promise<MyProfile | null> {
  try {
    const res = await fetch("/api/v1/me/profile", { cache: "no-store" });
    if (!res.ok) return null;
    return (await res.json()) as MyProfile;
  } catch {
    return null;
  }
}

/**
 * 카카오 로그인 시작. 지금 보던 주소로 돌아온다(returnTo).
 * authorize 는 서버가 302 로 카카오에 보내므로 링크가 아니라 주소 이동으로 건다.
 */
export function startLogin(): void {
  const returnTo = encodeURIComponent(window.location.href);
  // Next 페이지가 아니라 서버가 카카오로 302 시키는 프록시 경로다 — router.push 는 클라이언트
  // 이동이라 302 를 따라가지 못한다. 그래서 브라우저 주소를 통째로 옮긴다.
  // eslint-disable-next-line @next/next/no-location-assign-relative-destination
  window.location.href = `/api/v1/auth/kakao/authorize?returnTo=${returnTo}`;
}

/** 로그아웃. 세션을 무효화하고 지금 화면을 새로 읽는다(저장 상태 등이 초기화되도록). */
export async function logout(): Promise<void> {
  try {
    await adminWrite("/api/v1/auth/logout", { method: "POST" });
  } finally {
    window.location.reload();
  }
}
