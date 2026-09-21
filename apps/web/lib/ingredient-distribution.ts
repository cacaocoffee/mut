/**
 * 재료 상세의 유통 배지·대체재 카드 판정 (#196 · G-41 · `FR-INGREDIENT-003`).
 *
 * 순수 함수다 — 화면은 이 결과를 그리기만 한다. 값의 뜻은 `PRIN-P05`:
 * "이 서비스가 해외 DB 의 번역판이 아닌 이유는 `domestic_availability` 하나다."
 *
 * 데이터가 말하는 것은 "신고가 있었다" 까지다. 그래서 배지는 "국내 유통 · 최근 신고 YYYY-MM" 이지
 * "판매 중" 이 아니다.
 */

/** 미유통 두 값. 서버의 `DomesticAvailability.needsSubstitute` 와 같다. */
export const NEEDS_SUBSTITUTE = new Set(["import_only", "unavailable"]);

export type Badge = {
  text: string;
  /** styles.css 의 `.tag-*` 접미. */
  tone: "accent" | "accent-2" | "neutral" | "outline";
};

const LABEL: Record<string, string> = {
  common: "국내 유통",
  specialty: "전문점 유통",
  import_only: "해외 구매만",
  unavailable: "국내 유통 없음",
};

/** `2026-07-30` → `2026-07`. 날짜까지 보여 주면 "그날 들어왔다" 로 읽힌다. */
export function reportedMonth(isoDate: string | null | undefined): string | null {
  const m = isoDate?.match(/^(\d{4})-(\d{2})/);
  return m ? `${m[1]}-${m[2]}` : null;
}

export function availabilityBadge(
  availability: string,
  lastReportedOn: string | null | undefined,
): Badge {
  const label = LABEL[availability] ?? availability;
  const month = reportedMonth(lastReportedOn);

  if (availability === "common" || availability === "specialty") {
    return {
      text: month ? `${label} · 최근 신고 ${month}` : label,
      tone: availability === "common" ? "accent" : "accent-2",
    };
  }
  return { text: label, tone: availability === "unavailable" ? "neutral" : "outline" };
}

/**
 * 대체재 카드를 반드시 그려야 하는가 (`FR-INGREDIENT-003` — 미유통이면 필수 노출).
 * 미유통인데 안내가 없으면 서버 게이트(`INV-INGREDIENT-01`)가 이미 막았으니 여기 오지 않는다.
 */
export function needsSubstituteCard(availability: string): boolean {
  return NEEDS_SUBSTITUTE.has(availability);
}
