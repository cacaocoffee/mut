/**
 * 레시피 계량 표기 (ISSUE-043 · `FR-COCKTAIL-019`·`020`).
 *
 * ## 환산은 화면이 하고, **무엇을 환산할지는 서버가 정한다**
 *
 * `isScalable` 은 계약 필드다 (`IngredientLine`). 서버의 `RecipeIngredient.isScalable()` 이
 * "`amountLabel` 이 없고 `amount` 가 있는 줄" 로 판정하고, FE 와 어드민 미리보기가 그 판정을
 * 함께 쓴다 — 두 곳이 다르게 판단하면 에디터가 미리보기에서 본 것과 사용자가 보는 것이 달라진다.
 * **여기서 다시 판정하지 않는다** (이슈 043 RED 6).
 *
 * ## `ml` 만 다른 단위로 바꾼다
 *
 * `FR-COCKTAIL-020` 이 `dash`·`barspoon`·`piece`·`top_up` 을 변환에서 뺐다. 바꿀 수 있는
 * 단위를 **적어 두고** 그 밖은 건드리지 않는다 — 빼는 목록으로 두면 새 단위가 생겼을 때
 * 조용히 변환 대상이 된다.
 */
import type { components } from "./generated/api";

/** 계약의 재료 한 줄. 화면이 이 모양으로 모아서 넘긴다. */
export type IngredientLine = components["schemas"]["IngredientLine"];

/** 잔 수 상한. 8 잔이면 셰이커가 넘친다 — 계산이 아니라 실제 제조의 한계다. */
export const MAX_SERVINGS = 8;

/**
 * 1 oz = 29.5735 ml.
 *
 * 바텐딩에서는 30ml 로 어림하는 경우가 많지만, 어림한 값을 저장한 수치에 다시 곱하면
 * 오차가 쌓인다. **정확한 비율로 계산하고 표기에서 한 번만 반올림한다.**
 */
export const ML_PER_OZ = 29.5735;

/** 화면이 고르는 표기 단위. 저장값의 단위(`unit`)와 다른 것이다. */
export type DisplayUnit = "ml" | "oz";

/** 표기 단위를 바꿀 수 있는 저장 단위. 여기 없는 것은 그대로 둔다 (`FR-COCKTAIL-020`). */
const CONVERTIBLE = new Set(["ml"]);

/**
 * 수량 없이 단위만으로 뜻이 서는 것.
 *
 * `top_up` 은 "채운다" 라서 수량이 없다 — 몇 ml 인지 정하는 순간 잔 크기에 종속되고,
 * 그러면 잔 수 환산이 틀어진다 (SPEC-02 §2.7).
 */
const UNIT_ONLY: Record<string, string> = {
  top_up: "채운다",
};

/** 저장 단위의 표기. 바에서 그대로 쓰는 말(`ml`·`dash`)은 그대로 둔다. */
const UNIT_LABELS: Record<string, string> = {
  ml: "ml",
  dash: "dash",
  barspoon: "바스푼",
  piece: "개",
};

/**
 * 표기에 필요한 것만.
 *
 * 계약의 [IngredientLine] 도, 화면이 만든 줄(`null` 을 쓰는 쪽)도 그대로 들어온다 —
 * 옮겨 담느라 값이 한 번 더 손을 타면 그 자리가 곧 규칙이 갈라지는 자리다.
 */
export interface Quantity {
  amount?: number | null;
  unit?: string | null;
  amountLabel?: string | null;
  /** 서버 판정 (이슈 010). 화면이 만들지 않는다. */
  isScalable: boolean;
}

/**
 * 한 줄의 용량 표기.
 *
 * @param servings 잔 수 (1~[MAX_SERVINGS])
 * @param display 화면이 고른 표기 단위
 */
export function formatQuantity(line: Quantity, servings: number, display: DisplayUnit): string {
  // 배수 대상이 아니면 적힌 그대로다. `1조각` 은 두 잔이어도 `1조각` 이고,
  // 잔 수를 곱해 `2조각` 으로 바꾸면 적어 둔 사람의 뜻과 달라진다.
  if (!line.isScalable) {
    if (line.amountLabel) return line.amountLabel;
    if (line.unit && UNIT_ONLY[line.unit]) return UNIT_ONLY[line.unit];
    return "—";
  }

  const total = (line.amount ?? 0) * servings;
  const unit = line.unit ?? "";

  if (display === "oz" && CONVERTIBLE.has(unit)) {
    return `${round(total / ML_PER_OZ, 1)} oz`;
  }
  return `${round(total, 1)} ${UNIT_LABELS[unit] ?? unit}`.trim();
}

/** 소수 자리를 맞추되 `30.0 ml` 처럼 늘어지지 않게 한다. */
function round(value: number, digits: number): string {
  const factor = 10 ** digits;
  return String(Math.round(value * factor) / factor);
}
