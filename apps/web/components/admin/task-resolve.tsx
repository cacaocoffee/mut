"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";

/**
 * 검증 태스크 해소 (ISSUE-048 · `FR-ADMIN-004`).
 *
 * ## 넘길 때는 사유가 필수다
 *
 * [DECISIONS §1.11](../../../../docs/issues/DECISIONS.md) — `dismissed` 는 사유 없이 못 넘긴다.
 * 왜 고치지 않고 넘겼는지가 남아야 다음 사람이 같은 태스크를 다시 열지 않는다.
 * 서버도 사유 없는 `dismiss` 를 거부한다 — 여기서 버튼을 잠그는 것은 왕복을 줄이는 것뿐이다.
 *
 * `editor` 도 처리한다 (SPEC-08 §2 "검증 태스크 처리"). 승인·감사와 다른 점이다.
 */
export function TaskResolve({ id }: { id: number }) {
  const router = useRouter();
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  async function resolve(dismiss: boolean) {
    setBusy(true);
    setMessage(null);
    try {
      const res = await fetch(`/api/admin/tasks/${id}/resolve`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ dismiss, reason: reason.trim() || undefined }),
      });

      if (res.ok) {
        router.refresh();
        return;
      }

      setMessage(
        res.status === 409
          ? "이미 처리된 태스크입니다"
          : res.status === 400 || res.status === 422
            ? "넘기려면 사유가 필요합니다"
            : `처리하지 못했습니다 (HTTP ${res.status})`,
      );
    } catch {
      setMessage("서버를 부르지 못했습니다");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="admin-task__resolve">
      <label className="admin-field">
        <span className="admin-field__label">사유</span>
        <input
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder="넘길 때는 필수입니다"
        />
      </label>

      <div className="admin-form__actions">
        <button type="button" className="btn btn-primary" onClick={() => resolve(false)} disabled={busy}>
          고쳤음
        </button>
        {/* 사유가 비면 누를 수 없다 — 서버가 거부하는 것을 미리 알려 준다 */}
        <button
          type="button"
          className="btn btn-ghost"
          onClick={() => resolve(true)}
          disabled={busy || reason.trim() === ""}
        >
          넘김
        </button>
      </div>

      {message ? (
        <p className="admin-form__message" role="status">
          {message}
        </p>
      ) : null}
    </div>
  );
}
