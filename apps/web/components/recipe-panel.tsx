"use client";

import { useEffect, useState } from "react";
import { formatAmount, type Cocktail } from "@kca/domain";
import { rememberLastViewed } from "@/lib/use-last-viewed";

const MAX_SERVINGS = 8;

export function RecipePanel({ cocktail }: { cocktail: Cocktail }) {
  const [servings, setServings] = useState(1);
  const [unit, setUnit] = useState<"ml" | "oz">("ml");
  const [openSub, setOpenSub] = useState<number | null>(null);

  // 상세 탭이 마지막으로 본 칵테일로 돌아가도록 기록한다.
  useEffect(() => {
    rememberLastViewed(cocktail.id);
  }, [cocktail.id]);

  const substitute = openSub !== null ? cocktail.ingredients[openSub] : null;

  return (
    <>
      <div className="rule-head">
        <h4 style={{ margin: 0 }}>재료 INGREDIENTS</h4>
        <div className="recipe-controls">
          <div className="stepper">
            <button
              type="button"
              className="btn"
              onClick={() => setServings((n) => Math.max(1, n - 1))}
              disabled={servings === 1}
              aria-label="잔 수 줄이기"
            >
              −
            </button>
            <div className="value">
              <b>{servings}</b> serving
            </div>
            <button
              type="button"
              className="btn"
              onClick={() => setServings((n) => Math.min(MAX_SERVINGS, n + 1))}
              disabled={servings === MAX_SERVINGS}
              aria-label="잔 수 늘리기"
            >
              ＋
            </button>
          </div>
          <div className="seg">
            {(["ml", "oz"] as const).map((u) => (
              <label className="seg-opt" key={u}>
                <input
                  type="radio"
                  name="unit"
                  checked={unit === u}
                  onChange={() => setUnit(u)}
                />
                {u}
              </label>
            ))}
          </div>
        </div>
      </div>

      <table className="table" style={{ marginTop: 2 }}>
        <thead>
          <tr>
            <th style={{ width: "52%" }}>재료</th>
            <th style={{ width: "24%", textAlign: "right" }}>용량</th>
            <th style={{ width: "24%" }}>비고</th>
          </tr>
        </thead>
        <tbody>
          {cocktail.ingredients.map((ing, i) => (
            <tr key={ing.ko}>
              <td style={{ fontWeight: 500 }}>
                {ing.ko}
                <div className="ingredient-en">{ing.en}</div>
              </td>
              <td className="amount-cell">
                {ing.amount ?? (ing.ml ? formatAmount(ing.ml, servings, unit) : "—")}
              </td>
              <td>
                {ing.sub ? (
                  <button
                    type="button"
                    className="tag tag-outline"
                    style={{ cursor: "pointer", background: "transparent", whiteSpace: "nowrap" }}
                    aria-expanded={openSub === i}
                    onClick={() => setOpenSub(openSub === i ? null : i)}
                  >
                    대체 가능 ⓘ
                  </button>
                ) : null}
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {substitute ? (
        <dl className="substitute-note">
          <dt>SUBSTITUTE · {substitute.ko}</dt>
          <dd>{substitute.sub}</dd>
        </dl>
      ) : null}
    </>
  );
}
