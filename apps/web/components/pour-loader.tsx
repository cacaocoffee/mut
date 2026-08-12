/**
 * 로딩 표시 — 마티니 잔이 채워지는 동작을 **쌓임**으로 표현한다 (ISSUE-057).
 *
 * 회전 스피너를 쓰지 않는 이유: 시안이 라운드 0 을 규정했고
 * "떠 있는 것도 장식된 것도 없다"고 했다. 원은 이 시스템의 말이 아니다.
 * 마티니 잔은 직선과 사선만으로 그려져 그 규정과 어긋나지 않는다 —
 * 둥근 잔이었다면 실루엣부터 라운드 0 을 어긴다.
 *
 * 순수 CSS 이고 움직이는 것은 transform 뿐이다 — 라이브러리를 들이지 않는다.
 *
 * 층이 보울 안에서 아래부터 쌓인다. 보울이 삼각형으로 잘려 있어
 * 아래 층은 좁고 위 층은 넓다 — 실제로 잔에 따를 때 보이는 모양이다.
 *
 * `aria-hidden` 인 이유: 잔도 층도 장식이고, 상태는 옆의 텍스트가 말한다.
 * 바깥 컨테이너가 `role="status"` 라 보조기술은 문구만 읽는다.
 */
export function PourLoader({ label, sub }: { label: string; sub: string }) {
  return (
    <div className="loading-block" role="status" aria-live="polite">
      <div className="pour" aria-hidden="true">
        <div className="pour__bowl">
          <div className="pour__liquid">
            <span />
            <span />
            <span />
            <span />
          </div>
        </div>
        <div className="pour__stem" />
        <div className="pour__foot" />
      </div>
      <div>
        <h2>{label}</h2>
        <p>{sub}</p>
      </div>
    </div>
  );
}
