"use client";

import { useEffect, useState } from "react";
import { MAX_SERVINGS, formatQuantity, type DisplayUnit } from "@kca/domain";
import { rememberLastViewed } from "@/lib/use-last-viewed";
import type { CocktailView } from "@/lib/cocktail-view";

type Line = CocktailView["ingredients"][number];

/** 고른 표기 단위를 기억해 둔다 (RED 16) — 잔마다 다시 고르게 하지 않는다. */
const UNIT_KEY = "kca:recipe-unit";

/**
 * 재료 표 + 잔 수 · 단위 · 대체재 (ISSUE-043 · `FR-COCKTAIL-019`·`020`·`021`).
 *
 * ## 계산은 전부 여기서 끝난다
 *
 * 잔 수를 바꿔도 서버를 부르지 않는다 (RED 35). 필요한 것(수치 · 단위 · 배수 대상 판정)이
 * 이미 응답에 있고, 환산 규칙은 `@kca/domain` 의 `formatQuantity` 한 곳에 있다.
 *
 * ## 무엇이 배수 대상인지는 서버가 정한다
 *
 * `isScalable` 은 계약 필드다 (이슈 010). `amountLabel` 이 있는지 화면이 다시 보지 않는다 —
 * 그래야 어드민 미리보기와 이 화면이 같은 답을 낸다.
 *
 * ## 바뀐 것을 소리로도 알린다
 *
 * 잔 수·단위를 바꾸면 표의 숫자가 한꺼번에 바뀐다. 화면을 보는 사람은 알지만 스크린리더는
 * 표를 다시 읽지 않으므로, 바뀐 결과를 한 줄로 읽어 준다 (`NFR-A-07`).
 */
export function RecipePanel({ slug, ingredients }: { slug: string; ingredients: Line[] }) {
  const [servings, setServings] = useState(1);
  const [unit, setUnit] = useState<DisplayUnit>("ml");
  const [openSub, setOpenSub] = useState<number | null>(null);

  // 상세 탭이 마지막으로 본 칵테일로 돌아가도록 기록한다.
  useEffect(() => {
    rememberLastViewed(slug);
  }, [slug]);

  // 저장해 둔 단위를 되살린다. 첫 그림은 `ml` 이라 서버가 그린 것과 어긋나지 않는다.
  useEffect(() => {
    const restore = () => {
      const saved = window.localStorage.getItem(UNIT_KEY);
      if (saved === "ml" || saved === "oz") setUnit(saved);
    };
    restore();
  }, []);

  const changeUnit = (next: DisplayUnit) => {
    setUnit(next);
    try {
      window.localStorage.setItem(UNIT_KEY, next);
    } catch {
      // 사생활 보호 모드에서 저장이 막힐 수 있다. 이번 화면에서만 유지되면 된다.
    }
  };

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
              {/* 반각 + (U+002B). 전각 플러스(U+FF0B)였는데 짝인 −(U+2212)와 폭이 달라
                  두 버튼이 광학적으로 안 맞았다 (ISSUE-054). */}
              +
            </button>
          </div>
          <div className="seg">
            {(["ml", "oz"] as const).map((u) => (
              <label className="seg-opt" key={u}>
                <input
                  type="radio"
                  name="unit"
                  checked={unit === u}
                  aria-label={`${u} 로 보기`}
                  onChange={() => changeUnit(u)}
                />
                {u}
              </label>
            ))}
          </div>
        </div>
      </div>

      {/* 표의 숫자가 한꺼번에 바뀐 것을 한 줄로 알린다 (RED 31) */}
      <p className="recipe-live" aria-live="polite">
        {servings}잔 기준 · {unit} 표기
      </p>

      <table className="table" style={{ marginTop: 2 }}>
        <thead>
          <tr>
            <th style={{ width: "52%" }}>재료</th>
            <th style={{ width: "24%", textAlign: "right" }}>용량</th>
            <th style={{ width: "24%" }}>비고</th>
          </tr>
        </thead>
        <tbody>
          {ingredients.map((line, i) => (
            <RecipeRow
              key={`${line.nameKo}-${i}`}
              line={line}
              servings={servings}
              unit={unit}
              open={openSub === i}
              onToggle={() => setOpenSub(openSub === i ? null : i)}
            />
          ))}
        </tbody>
      </table>
    </>
  );
}

/**
 * 한 줄과, 펼쳤을 때의 대체재 안내.
 *
 * 안내를 표 아래 한 자리에 모아 두면 어느 재료의 이야기인지 다시 찾아야 한다.
 * 누른 줄 **바로 밑**에 편다 (`FR-COCKTAIL-021` · `R-F1.3-2`).
 */
function RecipeRow({
  line,
  servings,
  unit,
  open,
  onToggle,
}: {
  line: Line;
  servings: number;
  unit: DisplayUnit;
  open: boolean;
  onToggle: () => void;
}) {
  return (
    <>
      <tr>
        <td style={{ fontWeight: 500 }}>
          {line.nameKo}
          {line.isOptional && <span className="ingredient-optional">선택</span>}
          <div className="ingredient-en">{line.nameEn}</div>
        </td>
        <td className="amount-cell">{formatQuantity(line, servings, unit)}</td>
        <td>
          {line.substitute ? (
            <button
              type="button"
              className="tag tag-outline"
              style={{ cursor: "pointer", background: "transparent", whiteSpace: "nowrap" }}
              aria-expanded={open}
              onClick={onToggle}
            >
              {/* ⓘ 를 뺐다 — OS 마다 다르게 그려지는 글리프를 아이콘 자리에 쓰고 있었다.
                  텍스트와 aria-expanded 만으로 어포던스가 성립한다 (ISSUE-054). */}
              대체 가능
            </button>
          ) : null}
        </td>
      </tr>
      {open && line.substitute ? (
        <tr>
          <td colSpan={3}>
            <dl className="substitute-note">
              <dt>SUBSTITUTE · {line.nameKo}</dt>
              <dd>{line.substitute}</dd>
            </dl>
          </td>
        </tr>
      ) : null}
    </>
  );
}
