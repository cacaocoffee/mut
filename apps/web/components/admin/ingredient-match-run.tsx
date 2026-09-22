"use client";

import { useToast } from "@/components/toast";
import { adminWrite } from "@/lib/admin-csrf";
import { useState } from "react";
import { useRouter } from "next/navigation";

/**
 * 「전체 다시 매칭」 (#213). 승인된 재료 전부에 브랜드 검색어·별명으로 제품을 찾는다.
 * 매일 04:10 에도 돈다 — 이 버튼은 검색어를 고치고 바로 보고 싶을 때다. 몇 초 걸린다.
 */
export function IngredientMatchRun() {
  const router = useRouter();
  const toast = useToast();
  const [busy, setBusy] = useState(false);

  async function run() {
    setBusy(true);
    try {
      const res = await adminWrite("/api/admin/ingredients/matches/run", { method: "POST" });
      if (res.ok) {
        const r = (await res.json()) as {
          ingredients: number;
          newMatches: number;
          autoApproved: number;
          availabilityChanged: number;
        };
        toast.success(
          `재료 ${r.ingredients}종 · 새 매핑 ${r.newMatches}건 (자동 승인 ${r.autoApproved}) · 유통 여부 바뀜 ${r.availabilityChanged}종`,
        );
        router.refresh();
        return;
      }
      toast.error(res.status === 403 ? "권한이 없습니다" : `실패했습니다 (HTTP ${res.status})`);
    } catch {
      toast.error("서버를 부르지 못했습니다");
    } finally {
      setBusy(false);
    }
  }

  return (
    <button type="button" className="btn" onClick={run} disabled={busy}>
      {busy ? "매칭 중…" : "전체 다시 매칭"}
    </button>
  );
}
