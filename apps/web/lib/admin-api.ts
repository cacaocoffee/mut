import { headers } from "next/headers";
import type { components } from "@kca/domain/generated/api";

/**
 * 어드민 조회 (ISSUE-047).
 *
 * 읽기는 서버 컴포넌트에서 한다 — 들어온 쿠키를 그대로 넘기면 된다 (SPEC-07 §1.2).
 * 쓰기는 브라우저에서 `/api/admin/*` 프록시를 거친다.
 */
export type AdminCocktail = components["schemas"]["AdminCocktailResponse"];

/**
 * 발행 게이트 실패 한 건 (SPEC-07 §1.4).
 *
 * **계약 타입에 없다** — 에러 본문이라 OpenAPI 응답 스키마로 안 나온다 (GAPS G-38).
 * 서버의 `Violation` 과 같은 모양을 여기 적어 두고, 어긋나면 화면이 빈 목록을 그린다.
 */
export interface Violation {
  /** `INV-`·`GATE-` ID 를 그대로 쓴다. **문구가 아니라 코드로 분기한다.** */
  code: string;
  /** 여러 필드에 걸친 규칙은 가리킬 곳이 하나가 아니라 비어 있다. */
  field?: string | null;
  message: string;
}

const BASE = process.env.KC_API_URL?.replace(/\/$/, "") ?? "";

export async function adminCocktails(status?: string): Promise<AdminCocktail[]> {
  const query = status ? `?status=${encodeURIComponent(status)}&size=100` : "?size=100";
  const body = await get<{ items: AdminCocktail[] }>(`/cocktails${query}`);
  return body?.items ?? [];
}

export async function adminCocktail(id: string): Promise<AdminCocktail | null> {
  return get<AdminCocktail>(`/cocktails/${encodeURIComponent(id)}`);
}

async function get<T>(path: string): Promise<T | null> {
  if (!BASE) return null;

  const cookie = (await headers()).get("cookie");
  if (!cookie) return null;

  try {
    const res = await fetch(`${BASE}/api/v1/admin${path}`, {
      headers: { cookie },
      cache: "no-store",
    });
    return res.ok ? ((await res.json()) as T) : null;
  } catch (e) {
    console.warn(`[admin] 조회 실패 (${path}): ${e instanceof Error ? e.message : String(e)}`);
    return null;
  }
}
