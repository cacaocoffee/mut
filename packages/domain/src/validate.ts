import { BASES, COCKTAILS, FLAVOR_KEYS, STYLE_KEYS, TECHNIQUES } from "./data";

/**
 * 코퍼스 불변식. PRD가 하드 제약이라고 못박은 것들이라 어기면 빌드를 세운다.
 * 24종일 때는 눈으로 보이지만 500종이 되면 이것 말고는 확인할 방법이 없다.
 *
 * **규칙의 정본은 이제 Kotlin이다** (ISSUE-013, `PRIN-T05`).
 * 발행 게이트와 도메인 불변식은 서버 트랜잭션 안에서 강제한다 —
 * `apps/api/.../cocktail/api/PublishGate.kt` 가 `GATE-COCKTAIL-01~06` 의 정본이고,
 * 여기는 시드 코퍼스를 지키는 **보조 수단이지 근거가 아니다.**
 * 두 곳이 어긋나면 Kotlin이 맞다.
 */
export function validateCorpus(): string[] {
  const errors: string[] = [];
  const seen = new Set<string>();

  for (const c of COCKTAILS) {
    const at = `[${c.id}]`;

    if (seen.has(c.id)) errors.push(`${at} id 중복`);
    seen.add(c.id);

    // R-C-1 — 카테고리 3축은 전부 필수
    if (!BASES.includes(c.base)) errors.push(`${at} 알 수 없는 기주: ${c.base}`);
    if (!TECHNIQUES[c.method]) errors.push(`${at} 알 수 없는 메이킹 방법: ${c.method}`);
    if (c.styles.length === 0) errors.push(`${at} 스타일이 비어 있음 (R-C-1)`);
    for (const s of c.styles) {
      if (!STYLE_KEYS.includes(s)) errors.push(`${at} 알 수 없는 스타일: ${s}`);
    }

    // R-C-3 — stylePrimary는 배리에이션 추천의 유일한 기준이라 styles 안에 있어야 한다
    if (!c.styles.includes(c.stylePrimary)) {
      errors.push(`${at} stylePrimary(${c.stylePrimary})가 styles에 없음 (R-C-3)`);
    }

    // R-F1.2-1 — 향·맛 태그 최소 1개, 최대 3개
    if (c.flavors.length < 1 || c.flavors.length > 3) {
      errors.push(`${at} 향·맛 태그 ${c.flavors.length}개 — 1~3개여야 함 (R-F1.2-1)`);
    }
    for (const f of c.flavors) {
      if (!FLAVOR_KEYS.includes(f)) errors.push(`${at} 알 수 없는 향 태그: ${f}`);
    }

    // R-F1.1-2 — 향과 맛 서술은 발행 필수. 현재는 summary가 그 자리를 대신한다.
    if (!c.summary.trim()) errors.push(`${at} 향과 맛 서술 없음 (R-F1.1-2)`);

    if (c.sweet < 0 || c.sweet > 3) errors.push(`${at} 당도 범위 밖: ${c.sweet}`);
    if (c.abv < 0 || c.abv > 60) errors.push(`${at} 도수 범위 밖: ${c.abv}`);
    if (c.profile.length !== 5) errors.push(`${at} 프로필 축이 5개가 아님`);
    if (c.ingredients.length === 0) errors.push(`${at} 재료 없음`);
    if (c.steps.length === 0) errors.push(`${at} 제조 순서 없음`);

    // 무알콜인데 도수가 있거나, 그 반대인 경우
    if (c.base === "non-alcoholic" && c.abv > 0) errors.push(`${at} 무알콜인데 도수 ${c.abv}%`);
    // 기주가 슬러그가 됐다 (이슈 037). 예전에는 `"무알콜"` 이었다.
    if (c.base !== "non-alcoholic" && c.abv === 0) {
      errors.push(`${at} 도수 0%인데 기주가 ${c.base}`);
    }
  }

  return errors;
}
