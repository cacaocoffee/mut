"use client";

import { useToast } from "@/components/toast";
import { adminWrite } from "@/lib/admin-csrf";
import type { AdminIngredient, AvailabilityProposal } from "@/lib/admin-api";
import { AVAILABILITY_LABELS, NEEDS_SUBSTITUTE, label } from "@/lib/ingredient-labels";
import { useState } from "react";
import { useRouter } from "next/navigation";

/**
 * 재료 유통 정보 폼 (#195 · `FR-INGREDIENT-001~003`).
 *
 * ## 제안은 보여 주고, 확정은 사람이 한다
 *
 * 서버가 승인된 매핑으로 유통 여부를 계산해 `proposal` 로 준다. 배치가 재료를 직접 바꾸지
 * 않는 이유: 미유통(`import_only`·`unavailable`)으로 내리면 대체재가 필수인데(`INV-INGREDIENT-01`)
 * 배치는 대체재를 쓸 수 없다. 그래서 "제안 적용" 버튼은 select 값만 바꾸고, 저장은 사람이 누른다.
 *
 * ## 막지 않고 알려 준다
 *
 * 미유통을 고르면 대체재 칸에 안내가 뜬다. 판정은 서버가 한다 (`PRIN-T05`) — 422 로 돌아온다.
 */
export function IngredientDistributionForm({
  ingredient,
  proposal,
}: {
  ingredient: AdminIngredient;
  proposal: AvailabilityProposal | null;
}) {
  const router = useRouter();
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  const [form, setForm] = useState({
    domesticAvailability: ingredient.domesticAvailability,
    substituteNote: ingredient.substituteNote ?? "",
    brandKeywords: ingredient.brandKeywords.join(", "),
    priceBand: ingredient.priceBand ?? "",
  });

  const set = (key: keyof typeof form, value: string) => setForm((f) => ({ ...f, [key]: value }));
  const substituteNeeded = NEEDS_SUBSTITUTE.has(form.domesticAvailability);
  const proposalDiffers = proposal != null && proposal.availability !== form.domesticAvailability;

  async function save() {
    setBusy(true);
    try {
      const res = await adminWrite(`/api/admin/ingredients/${ingredient.id}/distribution`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          domesticAvailability: form.domesticAvailability,
          substituteNote: form.substituteNote.trim() || undefined,
          // 쉼표로 끊고 빈 조각은 버린다 — 서버도 한 번 더 다듬는다
          brandKeywords: form.brandKeywords
            .split(",")
            .map((k) => k.trim())
            .filter(Boolean),
          priceBand: form.priceBand.trim() || undefined,
        }),
      });

      if (res.ok) {
        toast.success("유통 정보를 저장했습니다");
        router.refresh();
        return;
      }
      toast.error(
        res.status === 422 || res.status === 409
          ? "저장할 수 없습니다 — 미유통이면 대체재 안내가 필요합니다 (INV-INGREDIENT-01)"
          : res.status === 403
            ? "수정 권한이 없습니다"
            : `저장하지 못했습니다 (HTTP ${res.status})`,
      );
    } catch {
      toast.error("서버를 부르지 못했습니다");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="admin-form">
      <div className="admin__section-head">유통 정보</div>

      {proposal ? (
        <p className={proposalDiffers ? "admin-warn" : "admin-field__hint"} role="status">
          제안: <b>{label(AVAILABILITY_LABELS, proposal.availability)}</b> — {proposal.reason}
          {proposal.latestReportedOn ? ` (마지막 신고 ${proposal.latestReportedOn})` : ""}.{" "}
          {proposalDiffers ? (
            <button
              type="button"
              className="btn"
              onClick={() => set("domesticAvailability", proposal.availability)}
            >
              제안 적용
            </button>
          ) : (
            "지금 값과 같습니다."
          )}
        </p>
      ) : null}

      <div className="admin-form__grid">
        <Field label="국내 유통">
          <select
            value={form.domesticAvailability}
            onChange={(e) => set("domesticAvailability", e.target.value)}
          >
            {Object.entries(AVAILABILITY_LABELS).map(([slug, ko]) => (
              <option key={slug} value={slug}>
                {ko}
              </option>
            ))}
          </select>
        </Field>
        <Field label="가격대" hint="예: 2만원대">
          <input value={form.priceBand} onChange={(e) => set("priceBand", e.target.value)} />
        </Field>
        <Field
          label="브랜드 검색어"
          hint="쉼표로 구분. 유통 제품명에서 이 재료를 찾을 때 씁니다 — 별명보다 좁게 (예: campari, 캄파리, 깜빠리)"
        >
          <input value={form.brandKeywords} onChange={(e) => set("brandKeywords", e.target.value)} />
        </Field>
      </div>

      <Field
        label="대체재 안내"
        hint={
          substituteNeeded
            ? "국내에서 구하기 어려운 재료입니다 — 대체재나 자가제조 안내가 필요합니다 (INV-INGREDIENT-01)"
            : "필요하면 적습니다"
        }
      >
        <textarea
          rows={2}
          value={form.substituteNote}
          onChange={(e) => set("substituteNote", e.target.value)}
        />
      </Field>

      <div className="admin-form__actions">
        <button type="button" className="btn btn-primary" onClick={save} disabled={busy}>
          {busy ? "저장 중…" : "유통 정보 저장"}
        </button>
      </div>
    </div>
  );
}

function Field({
  label,
  hint,
  children,
}: {
  label: string;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <label className="admin-field">
      <span className="admin-field__label">{label}</span>
      {children}
      {hint ? <span className="admin-field__hint">{hint}</span> : null}
    </label>
  );
}
