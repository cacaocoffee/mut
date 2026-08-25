"use client";

import { useToast } from "@/components/toast";
import { adminWrite } from "@/lib/admin-csrf";
import { useState } from "react";
import { useRouter } from "next/navigation";

/**
 * 재료 승인 버튼 (ISSUE-048 · `FR-ADMIN-007` · SPEC-08 §2).
 *
 * **`admin` 에게만 그려진다** — 부르는 쪽이 역할을 보고 결정한다 (RED 2·3). 여기서
 * 다시 역할을 묻지 않는 이유는, 화면이 권한 판정을 두 곳에서 하면 한쪽만 고쳐지기
 * 때문이다. 서버도 `Action.APPROVE_INGREDIENT` 로 막는다 (이슈 026).
 *
 * 승인은 감사에 남는다 — 그래서 되돌리는 버튼이 여기 없다. 잘못 승인했으면 기록이
 * 남은 채로 다음 행위가 이어진다 (`PRIN-T08`).
 */
export function IngredientApprove({ id, name }: { id: number; name: string }) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const toast = useToast();

  async function approve() {
    setBusy(true);
    try {
      const res = await adminWrite(`/api/admin/ingredients/${id}/approve`, { method: "POST" });

      if (res.ok) {
        // 승인하면 대기 큐에서 빠진다 (RED 4). 목록을 손으로 지우지 않고 다시 받는다 —
        // 서버가 뺀 것과 화면이 뺀 것이 다르면 새로고침에서 되살아난다.
        toast.success("승인했습니다");
        router.refresh();
        return;
      }

      toast.error(
        res.status === 409
          ? "이미 승인된 재료입니다"
          : res.status === 403 || res.status === 404
            ? "승인 권한이 없습니다"
            : `승인하지 못했습니다 (HTTP ${res.status})`,
      );
    } catch {
      toast.error("서버를 부르지 못했습니다");
    } finally {
      setBusy(false);
    }
  }

  return (
    <span className="admin-inline">
      <button type="button" className="btn btn-primary" onClick={approve} disabled={busy}>
        {busy ? "승인 중…" : `승인`}
        <span className="visually-hidden"> — {name}</span>
      </button>
    </span>
  );
}
