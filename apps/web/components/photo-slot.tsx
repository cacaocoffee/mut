/**
 * 사진 자리. 시안이 사선 해칭 플레이스홀더로 잡아둔 블록이며,
 * 실제 이미지가 들어와도 `.grayscale` 래퍼는 유지한다 — 콘텐츠 사진은 흑백 출력이 원칙.
 */
export function PhotoSlot({
  ratio,
  caption,
  label,
}: {
  ratio: "4x5" | "4x3" | "3x2";
  caption?: string;
  label?: string;
}) {
  return (
    <div className={`grayscale photo-slot photo-slot--${ratio}`}>
      {label ? <div className="photo-slot__label">{label}</div> : null}
      {caption ? <div className="photo-slot__caption">{caption}</div> : null}
    </div>
  );
}
