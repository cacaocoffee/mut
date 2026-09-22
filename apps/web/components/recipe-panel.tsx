"use client";

import Link from "next/link";
import { INGREDIENTS_PATH, PRODUCTS_PATH } from "@/lib/routes";

import { useEffect, useState } from "react";
import { MAX_SERVINGS, formatQuantity, type DisplayUnit } from "@mut/domain";
import type { CocktailView } from "@/lib/cocktail-view";
import { recipeInteract } from "@/lib/analytics/events";

type Line = CocktailView["ingredients"][number];

/** 고른 표기 단위를 기억해 둔다 (RED 16) — 잔마다 다시 고르게 하지 않는다. */
const UNIT_KEY = "mut:recipe-unit";

/**
 * 재료 표 + 잔 수 · 단위 · 대체재 (ISSUE-043 · `FR-COCKTAIL-019`·`020`·`021`).
 *
 * ## 계산은 전부 여기서 끝난다
 *
 * 잔 수를 바꿔도 서버를 부르지 않는다 (RED 35). 필요한 것(수치 · 단위 · 배수 대상 판정)이
 * 이미 응답에 있고, 환산 규칙은 `@mut/domain` 의 `formatQuantity` 한 곳에 있다.
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
  // 신고 제품 펼침도 한 줄만 — 대체재와 같은 규칙 (#209)
  const [openProducts, setOpenProducts] = useState<number | null>(null);

  // 저장해 둔 단위를 되살린다. 첫 그림은 `ml` 이라 서버가 그린 것과 어긋나지 않는다.
  useEffect(() => {
    const restore = () => {
      const saved = window.localStorage.getItem(UNIT_KEY);
      if (saved === "ml" || saved === "oz") setUnit(saved);
    };
    restore();
  }, []);

  /** SPEC-10 §4.5 — 상세에서 실제로 뭘 만지나. 아무도 안 쓰는 컨트롤은 화면을 복잡하게만 한다. */
  const changeServings = (next: number) => {
    setServings(next);
    recipeInteract({ cocktailSlug: slug, action: "servings_change", detail: String(next) });
  };

  const changeUnit = (next: DisplayUnit) => {
    setUnit(next);
    recipeInteract({ cocktailSlug: slug, action: "unit_toggle", detail: next });
    try {
      window.localStorage.setItem(UNIT_KEY, next);
    } catch {
      // 사생활 보호 모드에서 저장이 막힐 수 있다. 이번 화면에서만 유지되면 된다.
    }
  };

  return (
    <>
      <div className="rule-head">
        {/* h2 다 — 상세의 다른 섹션 머리와 같은 층이라 h1 아래로 나란히 선다.
            20px 은 h4 가 갖던 크기로, 보이는 것은 그대로다 */}
        <h2 style={{ margin: 0, fontSize: 20 }}>재료</h2>
        <div className="recipe-controls">
          <div className="stepper">
            <button
              type="button"
              className="btn"
              onClick={() => changeServings(Math.max(1, servings - 1))}
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
              onClick={() => changeServings(Math.min(MAX_SERVINGS, servings + 1))}
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
              productsOpen={openProducts === i}
              onToggleProducts={() => {
                const opening = openProducts !== i;
                setOpenProducts(opening ? i : null);
                if (opening) {
                  setOpenSub(null);
                  recipeInteract({ cocktailSlug: slug, action: "products_open", detail: line.nameKo });
                }
              }}
              onToggle={() => {
                const opening = openSub !== i;
                setOpenSub(opening ? i : null);
                if (opening) setOpenProducts(null);
                // 펼칠 때만 센다. 접는 것은 같은 관심의 뒷면이라 두 번 세면 부풀린다.
                if (opening) {
                  recipeInteract({
                    cocktailSlug: slug,
                    action: "substitute_open",
                    detail: line.nameKo,
                  });
                }
              }}
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
  productsOpen,
  onToggleProducts,
}: {
  line: Line;
  servings: number;
  unit: DisplayUnit;
  open: boolean;
  onToggle: () => void;
  productsOpen: boolean;
  onToggleProducts: () => void;
}) {
  return (
    <>
      <tr>
        <td style={{ fontWeight: 500 }}>
          {/* 이름을 누르면 국내 신고 제품이 그 줄 밑에 펼쳐진다 (#209 · R-F1.3-2) */}
          <button
            type="button"
            className="ingredient-name"
            aria-expanded={productsOpen}
            onClick={onToggleProducts}
          >
            {line.nameKo}
          </button>
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
      {productsOpen ? (
        <tr>
          <td colSpan={3}>
            {/* key — 재료가 바뀌면 새로 마운트해 "찾는 중…" 부터 다시 시작한다.
                이펙트 안에서 상태를 되돌리면 첫 렌더에 헛도는 재렌더가 생긴다 */}
            <IngredientProducts key={line.nameKo} nameKo={line.nameKo} slug={line.slug} />
          </td>
        </tr>
      ) : null}
      {open && line.substitute ? (
        <tr>
          <td colSpan={3}>
            <dl className="substitute-note">
              <dt>SUBSTITUTE · {line.nameKo}</dt>
              <dd>
                {line.substitute}
                {/* 재료 참조가 있으면 그 재료로 보낸다 — 유통 여부·쓰는 칵테일이 거기 있다 (#196 · R-F2.2-3) */}
                {line.substituteSlug ? (
                  <>
                    {" "}
                    <Link href={`${INGREDIENTS_PATH}/${line.substituteSlug}`} className="substitute-note__link">
                      대체품 둘러보기 →
                    </Link>
                  </>
                ) : null}
              </dd>
            </dl>
          </td>
        </tr>
      ) : null}
    </>
  );
}

type ProductItem = {
  nameKo: string;
  nameEn?: string;
  importerOrMaker?: string;
  originCountry?: string;
  lastReportedOn?: string;
};

/**
 * 재료 줄 밑의 신고 제품 (#209). 재료명으로 `/api/products` 를 찾아 다섯 건까지 보여 준다 —
 * 승인된 매핑(재료 상세)이 아니라 이름 검색이라, 표기가 다른 제품은 빠질 수 있다.
 * 제품명·수입사·신고 월뿐이다. 구매 링크·가격은 없다 (`NFR-L-05`).
 */
function IngredientProducts({ nameKo, slug: ingredientSlug }: { nameKo: string; slug: string | null }) {
  const [state, setState] = useState<{ items: ProductItem[]; total: number } | "loading" | "error">("loading");

  useEffect(() => {
    let alive = true;
    fetch(`/api/products?q=${encodeURIComponent(nameKo)}&size=5`)
      .then(async (r) => {
        if (!r.ok) throw new Error(String(r.status));
        const body = (await r.json()) as { items: ProductItem[]; page: { totalElements: number } };
        if (alive) setState({ items: body.items, total: body.page.totalElements });
      })
      .catch(() => {
        if (alive) setState("error");
      });
    return () => {
      alive = false;
    };
  }, [nameKo]);

  return (
    <dl className="substitute-note ingredient-products-note">
      <dt>
        국내 신고 제품 · {nameKo}
        {ingredientSlug ? (
          <>
            {" "}
            <Link href={`${INGREDIENTS_PATH}/${ingredientSlug}`} className="substitute-note__link">
              재료 상세 →
            </Link>
          </>
        ) : null}
      </dt>
      {/* 펼친 뒤 내용이 비동기로 채워진다. 고지가 없으면 스크린리더는 "찾는 중…" 에서
          목록으로 바뀐 것을 모른다 — 잔 수·단위는 .recipe-live 가 같은 일을 한다 */}
      <dd role="status" aria-atomic="true" aria-busy={state === "loading"}>
        {state === "loading" ? (
          "찾는 중…"
        ) : state === "error" ? (
          "지금은 조회할 수 없습니다."
        ) : state.items.length === 0 ? (
          <>
            이 이름으로 신고된 제품이 없습니다.{" "}
            <Link href={`${PRODUCTS_PATH}?q=${encodeURIComponent(nameKo)}`} className="substitute-note__link">
              다른 표기로 찾기 →
            </Link>
          </>
        ) : (
          <>
            <ul className="ingredient-products-note__list">
              {state.items.map((p, i) => (
                <li key={`${p.nameKo}-${p.importerOrMaker ?? ""}-${i}`}>
                  <b>{p.nameKo}</b>
                  {p.nameEn ? <span className="en"> {p.nameEn}</span> : null}
                  <span className="ingredient-products-note__meta">
                    {p.importerOrMaker ?? "수입사 미상"}
                    {p.originCountry ? ` · ${p.originCountry}` : ""}
                    {p.lastReportedOn ? ` · 신고 ${p.lastReportedOn.slice(0, 7)}` : ""}
                  </span>
                </li>
              ))}
            </ul>
            <Link href={`${PRODUCTS_PATH}?q=${encodeURIComponent(nameKo)}`} className="substitute-note__link">
              {state.total > state.items.length ? `전체 ${state.total}건 보기 →` : "유통 술 목록에서 보기 →"}
            </Link>
            <span className="ingredient-products-note__hint"> 식약처 수입신고 기준. 지금 팔리는지는 매장에서 확인하세요.</span>
          </>
        )}
      </dd>
    </dl>
  );
}
