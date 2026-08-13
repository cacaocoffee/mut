/**
 * `제휴 콘텐츠` 라벨 (`NFR-L-02` · `R-F4.2-3` · `INV-INGREDIENT-02`).
 *
 * ## 끌 수 있는 prop 이 없다
 *
 * `isSponsored` 하나만 받는다. `hidden` · `variant` · `size` 같은 것을 두지 않는 이유는
 * **공정위 심사지침상 의무**이기 때문이다 — 표시 여부가 코드에서 선택 가능해지는 순간
 * 그 의무는 협상 대상이 된다.
 *
 * `PRIN-P02` 가 같은 말을 더 강하게 했다:
 *
 * > **라벨을 축소하거나 흐리게 처리하는 스타일 변경도 이 원칙 위반이다.**
 *
 * 그래서 `className` 도 받지 않는다. 받으면 `opacity: .4` 를 밖에서 걸 수 있고,
 * 그건 라벨을 끄는 것과 실질적으로 같다.
 *
 * ## 색이 파트너 배지와 달라야 한다 (`NFR-L-03`)
 *
 * `--color-accent-700` 을 쓴다 (6.41:1 — `NFR-A-01` 충족).
 * 파트너 배지는 Phase 1b 인데, **그때 이 색을 피해야 한다** —
 * 앞은 등급 정보고 뒤는 법적 고지라 성격이 다르다 (SPEC-02 §5.3).
 * `EPICS-1B-PHASE2.md` 에 제약으로 적어 뒀다.
 *
 * 새 토큰을 만들지 않았다 — `packages/ui` 는 시안 정본이라 수정 금지다 (ADR-0001 · CONVENTIONS §4).
 */
export const SPONSORED_LABEL_TEXT = "제휴 콘텐츠";

export function SponsoredLabel({ isSponsored }: { isSponsored: boolean }) {
  if (!isSponsored) return null;

  return (
    <span className="sponsored-label" data-testid="sponsored-label">
      {SPONSORED_LABEL_TEXT}
    </span>
  );
}
