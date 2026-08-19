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
 * **계약에서 온다** — 서버가 422 응답에 스키마를 달아 두어(`ValidationProblemResponse`)
 * 생성물에 실린다 (G-39 에서 고쳤다). 손으로 적어 두면 서버가 `field` 를 `path` 로 바꿔도
 * 빌드가 안 깨지고, 발행 실패 패널이 조용히 빈 목록을 그린다.
 *
 * `code` 는 `INV-`·`GATE-` ID 를 그대로 쓴다 — **문구가 아니라 코드로 분기한다.**
 * `field` 는 없을 수 있다: 여러 필드에 걸친 규칙은 가리킬 곳이 하나가 아니다.
 */
export type Violation = components["schemas"]["Violation"];

/** 422 응답 전체. `violations` 는 실패한 항목을 **전부** 담는다 (`FR-ADMIN-003`). */
export type ValidationProblem = components["schemas"]["ValidationProblem"];

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

// ── 재료 승인 (ISSUE-048 · `FR-ADMIN-007`) ────────────────────────────────

export type AdminIngredient = components["schemas"]["AdminIngredientResponse"];
export type IngredientCapacity = components["schemas"]["IngredientCapacity"];

/** 승인 대기 큐. `editor` 도 본다 — 승인만 `admin` 이다 (SPEC-08 §2). 이름순은 서버가 고정한다. */
export async function pendingIngredients(): Promise<AdminIngredient[]> {
  return (await get<AdminIngredient[]>("/ingredients/pending")) ?? [];
}

/**
 * 승인된 재료 수와 상한.
 *
 * 상한을 넘어도 **승인을 막지 않는다** (DECISIONS §1.2) — 경고다. 막으면 300번째 재료가
 * 필요한 날 아무도 아무것도 못 한다.
 */
export async function ingredientCapacity(): Promise<IngredientCapacity | null> {
  return get<IngredientCapacity>("/ingredients/capacity");
}
