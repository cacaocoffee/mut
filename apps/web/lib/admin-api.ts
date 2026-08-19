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

// ── 검증 태스크 (ISSUE-048 · `FR-ADMIN-004`) ──────────────────────────────

export type VerificationTask = components["schemas"]["VerificationTaskItem"];

/** 검증 태스크 큐. 정렬은 최근 탐지순으로 서버가 고정한다 (인덱스가 그 순서다). */
export async function verificationTasks(filter: {
  status?: string;
  taskType?: string;
}): Promise<VerificationTask[]> {
  const query = new URLSearchParams({ size: "50" });
  if (filter.status) query.set("status", filter.status);
  if (filter.taskType) query.set("taskType", filter.taskType);

  const body = await get<{ items: VerificationTask[] }>(`/tasks?${query}`);
  return body?.items ?? [];
}

// ── 감사 로그 (ISSUE-048 · `FR-ADMIN-005`) ────────────────────────────────

export type AuditLogEntry = components["schemas"]["AuditLogItem"];

export type AuditFilter = {
  entityType?: string;
  action?: string;
  actorUserId?: string;
  from?: string;
  to?: string;
};

/**
 * 감사 로그. **`admin` 만 부를 수 있다** (SPEC-08 §2.2).
 *
 * 필터는 AND 로 묶이고 정렬은 최신순으로 서버가 고정한다. 전체 건수를 함께 돌려주는
 * 이유는 필터가 걸린 화면에서 "몇 건 중 몇 건" 이 안 보이면 거른 것인지 없는 것인지
 * 알 수 없어서다.
 */
export async function auditLogs(
  filter: AuditFilter,
): Promise<{ items: AuditLogEntry[]; total: number }> {
  const query = new URLSearchParams({ size: "50" });
  for (const [key, value] of Object.entries(filter)) {
    if (value) query.set(key, value);
  }

  const body = await get<{ items: AuditLogEntry[]; page: { totalElements: number } }>(
    `/audit-logs?${query}`,
  );
  return { items: body?.items ?? [], total: body?.page.totalElements ?? 0 };
}

// ── 표준 레시피 (ISSUE-051 · `NFR-O-01`) ─────────────────────────────────

export type AdminRecipe = components["schemas"]["AdminRecipeResponse"];
export type AdminRecipeIngredient = components["schemas"]["AdminRecipeIngredient"];

/**
 * 표준 레시피. **아직 안 쓴 것은 `exists: false`** 로 온다 (404 가 아니다).
 *
 * 없는 칵테일만 404 다 — 편집 화면이 둘을 구분하지 못하면 새로 쓰기를 시작할 수 없다.
 */
export async function adminRecipe(cocktailId: string | number): Promise<AdminRecipe | null> {
  return get<AdminRecipe>(`/cocktails/${encodeURIComponent(String(cocktailId))}/recipe`);
}
