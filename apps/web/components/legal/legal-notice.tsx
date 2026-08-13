/**
 * 과음 경고 · 미성년자 판매 금지 (`NFR-L-01` · `FR-COCKTAIL-028` · `R-F1.1-8`).
 *
 * ## props 를 받지 않는다
 *
 * 이 컴포넌트에 `hidden` 이나 `variant` 같은 것을 두지 않는 것이 이 이슈의 요점 하나다.
 * **있으면 언젠가 쓰인다** — `PRIN-P02` 가 노출 규칙에 대해 말한 논리 그대로다.
 * "이 페이지만 잠깐" 이 가능해지는 순간 `NFR-L-01`(배포 차단)이 규칙이 아니라 관행이 된다.
 *
 * 타입에 자리가 없으면 우회할 방법도 없다 (`PRIN-T05`, 이슈 025·030 과 같은 방식).
 *
 * ## 문구를 상수로 내보낸다
 *
 * 테스트가 화면의 글자와 대조할 근거가 필요하다. 테스트에 문구를 다시 적으면
 * 둘이 갈라지고, 그때 **테스트는 자기가 적은 문구가 있는지**만 확인하게 된다.
 */
export const LEGAL_NOTICE_LINES = [
  "지나친 음주는 뇌졸중, 기억력 손상이나 치매를 유발합니다. 임신 중 음주는 기형아 출생 위험을 높입니다.",
  "만 19세 미만 청소년에게 판매하지 않습니다.",
] as const;

export function LegalNotice() {
  return (
    <footer className="legal" data-testid="legal-notice">
      {/* 두 문장을 한 요소에 이어 붙이지 않는다 — 미성년자 문구는 `FR-COCKTAIL-028` 의
          독립 요구라, 나중에 과음 경고만 손대다 함께 지워지면 안 된다. */}
      <p>{LEGAL_NOTICE_LINES[0]}</p>
      <p>{LEGAL_NOTICE_LINES[1]}</p>

      <nav className="legal-links" aria-label="약관">
        {/* `NFR-L-04` — 두 페이지가 존재하고 **닿을 수 있어야** 한다.
            페이지만 만들고 링크를 안 걸면 없는 것과 같다. */}
        <a href="/privacy">개인정보 처리방침</a>
        <a href="/terms">이용약관</a>
      </nav>
    </footer>
  );
}
