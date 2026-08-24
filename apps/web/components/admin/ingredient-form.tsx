"use client";

import { adminWrite } from "@/lib/admin-csrf";
import { useState } from "react";
import { useRouter } from "next/navigation";
import {
  CATEGORY_LABELS,
  AVAILABILITY_LABELS,
  NEEDS_SUBSTITUTE,
} from "@/lib/ingredient-labels";

/**
 * 재료 생성 폼 (ISSUE-048 · `FR-ADMIN-007`).
 *
 * ## 승인 상태를 고르는 칸이 없다
 *
 * 생성 시점은 **항상 승인 대기**다 (계약: "생성 시점은 항상 승인 대기(is_approved=false)").
 * 체크박스를 두면 만든 사람이 스스로 통과시킬 수 있어 승인 단계가 사라진다.
 *
 * ## 막지 않고 알려 준다
 *
 * `INV-INGREDIENT-01` — 해외 구매만·국내 유통 없음이면 대체재 안내가 필수다. 화면은
 * 그 사실을 미리 보여 주고, 판정은 서버가 한다 (`PRIN-T05` — 규칙을 두 벌로 두지 않는다).
 */
const EMPTY = {
  slug: "",
  nameKo: "",
  nameEn: "",
  category: "spirit",
  domesticAvailability: "common",
  abv: "",
  priceBand: "",
  aliases: "",
  description: "",
  substituteNote: "",
};

export function IngredientForm() {
  const router = useRouter();
  const [form, setForm] = useState(EMPTY);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const set = (key: keyof typeof form, value: string) =>
    setForm((f) => ({ ...f, [key]: value }));

  const substituteNeeded = NEEDS_SUBSTITUTE.has(form.domesticAvailability);
  const canSave =
    form.slug.trim() !== "" && form.nameKo.trim() !== "" && form.nameEn.trim() !== "";

  async function save() {
    setBusy(true);
    setMessage(null);
    try {
      const res = await adminWrite("/api/admin/ingredients", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          slug: form.slug.trim(),
          nameKo: form.nameKo.trim(),
          nameEn: form.nameEn.trim(),
          category: form.category,
          domesticAvailability: form.domesticAvailability,
          abv: form.abv.trim() === "" ? undefined : Number(form.abv),
          priceBand: form.priceBand.trim() || undefined,
          // 별명은 검색이 쓴다. 쉼표로 끊고 빈 조각은 버린다.
          aliases: form.aliases
            .split(",")
            .map((a) => a.trim())
            .filter(Boolean),
          description: form.description.trim() || undefined,
          substituteNote: form.substituteNote.trim() || undefined,
        }),
      });

      if (res.ok) {
        // 승인 대기 큐로 돌아간다 — 방금 만든 것이 거기 있다는 사실이 다음 할 일이다.
        router.push("/admin/ingredients");
        router.refresh();
        return;
      }

      setMessage(
        res.status === 409
          ? "같은 슬러그의 재료가 이미 있습니다"
          : res.status === 400 || res.status === 422
            ? "저장할 수 없습니다 — 대체재 안내가 필요한지 확인해 주세요"
            : `저장하지 못했습니다 (HTTP ${res.status})`,
      );
    } catch {
      setMessage("서버를 부르지 못했습니다");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="admin-form">
      <p className="admin-field__hint">
        새 재료는 <b>승인 대기</b> 상태로 들어갑니다. 승인은 admin 이 합니다 (SPEC-08 §2).
      </p>

      <div className="admin-form__grid">
        <Field label="주소 (slug)" hint="영문 소문자와 하이픈">
          <input value={form.slug} onChange={(e) => set("slug", e.target.value)} />
        </Field>
        <Field label="이름 (한국어)">
          <input value={form.nameKo} onChange={(e) => set("nameKo", e.target.value)} />
        </Field>
        <Field label="이름 (영문)">
          <input value={form.nameEn} onChange={(e) => set("nameEn", e.target.value)} />
        </Field>
        <Field label="분류">
          <select value={form.category} onChange={(e) => set("category", e.target.value)}>
            {Object.entries(CATEGORY_LABELS).map(([slug, ko]) => (
              <option key={slug} value={slug}>
                {ko}
              </option>
            ))}
          </select>
        </Field>
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
        <Field label="도수 (%)" hint="술이 아니면 비웁니다">
          <input
            inputMode="decimal"
            value={form.abv}
            onChange={(e) => set("abv", e.target.value)}
          />
        </Field>
        <Field label="가격대" hint="예: 2만원대">
          <input value={form.priceBand} onChange={(e) => set("priceBand", e.target.value)} />
        </Field>
        <Field label="별명" hint="쉼표로 구분. 검색이 씁니다">
          <input value={form.aliases} onChange={(e) => set("aliases", e.target.value)} />
        </Field>
      </div>

      <Field label="설명">
        <textarea
          rows={3}
          value={form.description}
          onChange={(e) => set("description", e.target.value)}
        />
      </Field>

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
        <button type="button" className="btn btn-primary" onClick={save} disabled={!canSave || busy}>
          {busy ? "저장 중…" : "저장"}
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
