/**
 * 재료 카테고리 · 국내 유통 현황의 표시 문구 (ISSUE-048).
 *
 * **슬러그가 정본이다** — `IngredientCategory` · `DomesticAvailability` (Kotlin) 가 그것을
 * 정하고, 계약은 문자열로만 준다. 유통 현황의 문구는 Kotlin 의 `labelKo` 와 같은 말을
 * 쓴다 (두 벌로 쓰면 같은 값이 화면마다 다르게 읽힌다). 카테고리는 서버에 표시 문구가
 * 없어서 여기서 정한다 — 서버가 주기 시작하면 이 표를 지운다.
 *
 * 모르는 슬러그가 오면 슬러그를 그대로 보여 준다. 빈칸으로 두면 어드민이 무엇을 보고
 * 있는지 알 수 없다.
 */
export const CATEGORY_LABELS: Record<string, string> = {
  spirit: "증류주",
  liqueur: "리큐어",
  bitters: "비터스",
  syrup: "시럽",
  juice: "주스",
  garnish: "가니시",
  mixer: "탄산·음료",
};

export const AVAILABILITY_LABELS: Record<string, string> = {
  common: "쉽게 구할 수 있음",
  specialty: "전문점",
  import_only: "해외 구매만",
  unavailable: "국내 유통 없음",
};

/**
 * `INV-INGREDIENT-01` (`R-F1.3-2`) — 이 둘은 대체재 안내가 **필수**다.
 *
 * 서버가 정본이고 (DB CHECK `ck_ingredient__substitute` 까지 같은 조건이다) 화면은
 * 미리 알려 주기만 한다. 여기서 막지 않는다 — 막으면 규칙이 두 벌이 된다 (`PRIN-T05`).
 */
export const NEEDS_SUBSTITUTE = new Set(["import_only", "unavailable"]);

export const label = (table: Record<string, string>, slug: string): string =>
  table[slug] ?? slug;
