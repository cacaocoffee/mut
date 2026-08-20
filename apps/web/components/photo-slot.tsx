/**
 * 사진 자리. 시안이 사선 해칭 플레이스홀더로 잡아둔 블록이다.
 *
 * **컬러로 낸다** ([ADR-0008](../../../docs/decisions/ADR-0008-color-photos.md)).
 * 시안은 `.grayscale` 래퍼를 씌웠는데, 칵테일에서 색은 장식이 아니라 **식별 정보다** —
 * 흑백이면 네그로니와 맨해튼이 거의 같은 사진이 되고 49종 그리드가 서로 구별되지 않는다.
 * 클래스는 `styles.css` 에 남아 있다. 되돌리려면 아래 래퍼에 다시 넣는다.
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
    <div className={`photo-slot photo-slot--${ratio}`}>
      {label ? <div className="photo-slot__label">{label}</div> : null}
      {caption ? <div className="photo-slot__caption">{caption}</div> : null}
    </div>
  );
}
