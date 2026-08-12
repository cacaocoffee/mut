import { PourLoader } from "@/components/pour-loader";

/**
 * 전체 dim 로딩 — 화면을 덮고 가운데에 잔을 세운다.
 *
 * 스켈레톤과 역할이 다르다. **스켈레톤은 "무엇이 올지"를, 이쪽은 "지금 못 만진다"를**
 * 말한다. 골격만 깔아 두면 사용자는 그것을 눌러 보고, 아무 반응이 없으면 고장으로 읽는다.
 * dim 이 그 시도를 미리 막는다 (`inert` 가 아니라 시각적으로).
 *
 * 잔과 문구를 판 위에 얹는 이유: 잔의 획이 `--color-divider`(텍스트 40%)라
 * 어두워진 배경 위에서는 보이지 않는다. 판을 깔아야 대비가 산다.
 * 라운드 0 · 2px 테두리라 시스템의 말에서 벗어나지 않는다.
 */
export function LoadingOverlay({ label, sub }: { label: string; sub: string }) {
  return (
    <div className="loading-scrim">
      <div className="loading-panel">
        <PourLoader label={label} sub={sub} />
      </div>
    </div>
  );
}
