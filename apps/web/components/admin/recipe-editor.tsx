"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import type { AdminRecipe, AdminIngredient } from "@/lib/admin-api";

/**
 * 표준 레시피 편집 (ISSUE-051 · `NFR-O-01` · `FR-COCKTAIL-019`·`020`).
 *
 * ## 이 화면이 없어서 어드민만으로 발행할 수 없었다
 *
 * 발행 게이트가 표준 레시피를 요구하는데(`GATE-COCKTAIL-03`) 레시피를 쓸 자리가
 * 없었다 ([G-38](../../../../docs/prd/GAPS.md)). 재료는 **참조**라(`PRIN-D01`) 직접
 * 타이핑하는 칸을 두지 않는다 — 찾아서 고른다.
 *
 * ## 통째로 저장한다
 *
 * 줄마다 저장하지 않는다. 순서가 데이터의 일부라(`position`·`step_no`) 줄 단위로
 * 저장하면 순서를 다시 매기는 규칙이 화면과 서버 두 벌이 된다.
 *
 * ## 게이트를 여기서 흉내 내지 않는다
 *
 * 재료가 비어도 저장된다. 쓰다 만 초안을 못 저장하게 하면 에디터는 메모장에 쓰게 되고,
 * 그때 게이트는 아무것도 지키지 못한다 (`PRIN-T05`). 막는 것은 발행이다.
 */
const UNITS = [
  { value: "", ko: "단위 없음" },
  { value: "ml", ko: "ml" },
  { value: "dash", ko: "대시" },
  { value: "barspoon", ko: "바스푼" },
  { value: "piece", ko: "조각" },
  { value: "top_up", ko: "채운다" },
] as const;

const ROLES = [
  { value: "", ko: "역할 없음" },
  { value: "base", ko: "베이스" },
  { value: "modifier", ko: "부재료" },
  { value: "sweetener", ko: "감미" },
  { value: "citrus", ko: "시트러스" },
  { value: "garnish", ko: "가니시" },
] as const;

/** 수량이 필요 없는 단위. "채운다" 는 잔 크기에 종속되면 잔 수 환산이 틀어진다. */
const NO_AMOUNT = new Set(["top_up"]);

const MAX_SERVINGS = 8;

type Row = {
  ingredientId: number;
  nameKo: string;
  isApproved: boolean;
  amount: string;
  unit: string;
  amountLabel: string;
  role: string;
  isOptional: boolean;
  substituteNote: string;
};

export function RecipeEditor({ cocktailId, recipe }: { cocktailId: number; recipe: AdminRecipe }) {
  const router = useRouter();

  const [rows, setRows] = useState<Row[]>(() =>
    recipe.ingredients.map((i) => ({
      ingredientId: i.ingredientId,
      nameKo: i.nameKo ?? `#${i.ingredientId}`,
      isApproved: i.isApproved,
      amount: i.amount == null ? "" : String(i.amount),
      unit: i.unit ?? "",
      amountLabel: i.amountLabel ?? "",
      role: i.role ?? "",
      isOptional: i.isOptional,
      substituteNote: i.substituteNote ?? "",
    })),
  );
  const [steps, setSteps] = useState<string[]>(() =>
    recipe.steps.length > 0 ? recipe.steps.map((s) => s.text) : [""],
  );
  const [servings, setServings] = useState(String(recipe.servingCount || 1));
  const [note, setNote] = useState(recipe.note ?? "");
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  function setRow(index: number, patch: Partial<Row>) {
    setRows((current) => current.map((r, i) => (i === index ? { ...r, ...patch } : r)));
  }

  /** 순서는 배열이 정본이다. 서버가 1부터 다시 매긴다. */
  function move(index: number, delta: number) {
    setRows((current) => {
      const next = [...current];
      const target = index + delta;
      if (target < 0 || target >= next.length) return current;
      [next[index], next[target]] = [next[target], next[index]];
      return next;
    });
  }

  async function save() {
    setBusy(true);
    setMessage(null);
    try {
      const res = await fetch(`/api/admin/cocktails/${cocktailId}/recipe`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          servingCount: Number(servings) || 1,
          note: note.trim() || undefined,
          ingredients: rows.map((r) => ({
            ingredientId: r.ingredientId,
            // 수량이 없는 단위에 값을 보내면 잔 수 환산이 그것을 곱한다
            amount: NO_AMOUNT.has(r.unit) || r.amount.trim() === "" ? undefined : Number(r.amount),
            unit: r.unit || undefined,
            amountLabel: r.amountLabel.trim() || undefined,
            role: r.role || undefined,
            isOptional: r.isOptional,
            substituteNote: r.substituteNote.trim() || undefined,
          })),
          steps: steps.filter((t) => t.trim() !== "").map((text) => ({ text: text.trim() })),
        }),
      });

      if (res.ok) {
        const saved = (await res.json()) as AdminRecipe;
        setMessage(
          saved.abvCalculated == null
            ? "저장했습니다"
            : `저장했습니다 — 도수 ${saved.abvCalculated}%`,
        );
        router.refresh();
        return;
      }

      const body = await res.text();
      setMessage(
        res.status === 400
          ? `저장할 수 없습니다 — ${body.slice(0, 200)}`
          : res.status === 404
            ? "권한이 없거나 없는 항목입니다"
            : `저장하지 못했습니다 (HTTP ${res.status})`,
      );
    } catch {
      setMessage("서버를 부르지 못했습니다");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="admin-recipe">
      <div className="admin__section-head admin__section-head--row">
        <span>
          레시피 {recipe.exists ? "" : "— 아직 없습니다"}
          {recipe.abvCalculated != null ? ` · 계산된 도수 ${recipe.abvCalculated}%` : ""}
        </span>
        <button type="button" className="btn btn-primary" onClick={save} disabled={busy}>
          {busy ? "저장 중…" : "레시피 저장"}
        </button>
      </div>

      <div className="admin-form__grid">
        <label className="admin-field">
          <span className="admin-field__label">잔 수</span>
          <select value={servings} onChange={(e) => setServings(e.target.value)}>
            {Array.from({ length: MAX_SERVINGS }, (_, i) => String(i + 1)).map((n) => (
              <option key={n} value={n}>
                {n}잔
              </option>
            ))}
          </select>
          <span className="admin-field__hint">
            화면이 이 값을 기준으로 배수를 환산합니다 (<code>FR-COCKTAIL-019</code>)
          </span>
        </label>
        <label className="admin-field">
          <span className="admin-field__label">메모</span>
          <input value={note} onChange={(e) => setNote(e.target.value)} />
        </label>
      </div>

      <h3 className="admin__section-head">재료 {rows.length}줄</h3>

      <IngredientPicker
        onPick={(ing) =>
          setRows((current) => [
            ...current,
            {
              ingredientId: ing.id,
              nameKo: ing.nameKo,
              isApproved: ing.isApproved,
              amount: "",
              unit: "ml",
              amountLabel: "",
              role: "",
              isOptional: false,
              substituteNote: "",
            },
          ])
        }
      />

      {rows.length === 0 ? (
        <p className="admin__empty">
          재료가 없습니다. 위에서 찾아 담으세요 — 발행하려면 최소 1개가 필요합니다
          (<code>GATE-COCKTAIL-03</code>).
        </p>
      ) : (
        <ul className="admin__list">
          {rows.map((row, index) => (
            <li key={`${row.ingredientId}-${index}`}>
              <div className="admin__section-head--row">
                <b>
                  {row.nameKo}
                  {/* 미승인으로도 저장은 된다. 발행에서 막힌다 (GATE-COCKTAIL-04) */}
                  {row.isApproved ? "" : " · 승인 대기"}
                </b>
                <span className="admin-inline">
                  <button type="button" className="btn btn-ghost" onClick={() => move(index, -1)}>
                    ↑<span className="visually-hidden"> 위로</span>
                  </button>
                  <button type="button" className="btn btn-ghost" onClick={() => move(index, 1)}>
                    ↓<span className="visually-hidden"> 아래로</span>
                  </button>
                  <button
                    type="button"
                    className="btn btn-ghost"
                    onClick={() => setRows((c) => c.filter((_, i) => i !== index))}
                  >
                    빼기<span className="visually-hidden"> — {row.nameKo}</span>
                  </button>
                </span>
              </div>

              <div className="admin-recipe__row">
                <label className="admin-field">
                  <span className="admin-field__label">수량</span>
                  <input
                    inputMode="decimal"
                    value={row.amount}
                    disabled={NO_AMOUNT.has(row.unit)}
                    onChange={(e) => setRow(index, { amount: e.target.value })}
                  />
                </label>
                <label className="admin-field">
                  <span className="admin-field__label">단위</span>
                  <select value={row.unit} onChange={(e) => setRow(index, { unit: e.target.value })}>
                    {UNITS.map((u) => (
                      <option key={u.value} value={u.value}>
                        {u.ko}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="admin-field">
                  <span className="admin-field__label">표기</span>
                  <input
                    value={row.amountLabel}
                    placeholder="1조각"
                    onChange={(e) => setRow(index, { amountLabel: e.target.value })}
                  />
                </label>
                <label className="admin-field">
                  <span className="admin-field__label">역할</span>
                  <select value={row.role} onChange={(e) => setRow(index, { role: e.target.value })}>
                    {ROLES.map((r) => (
                      <option key={r.value} value={r.value}>
                        {r.ko}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="admin-field admin-field--check">
                  <input
                    type="checkbox"
                    checked={row.isOptional}
                    onChange={(e) => setRow(index, { isOptional: e.target.checked })}
                  />
                  <span>선택 재료</span>
                </label>
              </div>

              <label className="admin-field">
                <span className="admin-field__label">대체재 안내</span>
                <input
                  value={row.substituteNote}
                  onChange={(e) => setRow(index, { substituteNote: e.target.value })}
                />
                <span className="admin-field__hint">
                  국내에서 못 구하는 재료면 필수입니다 (<code>GATE-COCKTAIL-06</code>)
                </span>
              </label>
            </li>
          ))}
        </ul>
      )}

      <h3 className="admin__section-head">만드는 순서</h3>

      <ol className="admin-recipe__steps">
        {steps.map((text, index) => (
          <li key={index}>
            <textarea
              rows={2}
              value={text}
              onChange={(e) =>
                setSteps((c) => c.map((s, i) => (i === index ? e.target.value : s)))
              }
            />
            <button
              type="button"
              className="btn btn-ghost"
              onClick={() => setSteps((c) => c.filter((_, i) => i !== index))}
            >
              빼기<span className="visually-hidden"> — {index + 1}번째 순서</span>
            </button>
          </li>
        ))}
      </ol>

      <div className="admin-form__actions">
        <button type="button" className="btn btn-secondary" onClick={() => setSteps((c) => [...c, ""])}>
          순서 추가
        </button>
        <button type="button" className="btn btn-primary" onClick={save} disabled={busy}>
          {busy ? "저장 중…" : "레시피 저장"}
        </button>
      </div>

      {message ? (
        <p className="admin-form__message" role="status">
          {message}
        </p>
      ) : null}
    </section>
  );
}

/**
 * 재료 고르기.
 *
 * **미승인도 나온다** — 레시피를 쓰다 새 재료가 필요하면 승인을 기다리지 않는다
 * (DECISIONS §1.1). 공개 사전은 승인분만이라 여기에 못 쓴다.
 */
function IngredientPicker({ onPick }: { onPick: (ingredient: AdminIngredient) => void }) {
  const [query, setQuery] = useState("");
  const [found, setFound] = useState<AdminIngredient[]>([]);
  const [searching, setSearching] = useState(false);

  async function search() {
    setSearching(true);
    try {
      const res = await fetch(`/api/admin/ingredients?q=${encodeURIComponent(query)}&limit=20`);
      setFound(res.ok ? ((await res.json()) as AdminIngredient[]) : []);
    } catch {
      setFound([]);
    } finally {
      setSearching(false);
    }
  }

  return (
    <div className="admin-recipe__picker">
      <label className="admin-field">
        <span className="admin-field__label">재료 찾기</span>
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.preventDefault();
              void search();
            }
          }}
          placeholder="이름 · 영문명 · 슬러그"
        />
      </label>
      <button type="button" className="btn btn-secondary" onClick={search} disabled={searching}>
        {searching ? "찾는 중…" : "찾기"}
      </button>

      {found.length > 0 ? (
        <ul className="admin-recipe__found">
          {found.map((ing) => (
            <li key={ing.id}>
              <button
                type="button"
                className="btn btn-ghost"
                onClick={() => {
                  onPick(ing);
                  setFound([]);
                  setQuery("");
                }}
              >
                {ing.nameKo} <span className="en">{ing.nameEn}</span>
                {ing.isApproved ? "" : " · 승인 대기"}
              </button>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}
