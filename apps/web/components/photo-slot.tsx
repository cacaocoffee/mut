/**
 * 사진 자리. 시안이 사선 해칭 플레이스홀더로 잡아둔 블록이다.
 *
 * 빈 자리엔 캡션(영문 이름)만 놓는다. `IMAGE 4:5` 같은 개발용 라벨은 사용자에게
 * 그대로 보였던 것이라 뺐다 (#174).
 *
 * `src` 가 오면 자리표시자 대신 사진을 꽉 채워 그린다 (ISSUE-060 #139) —
 * 사진이 있는 것은 #87 로 들어온 25종뿐이라 두 모습이 공존한다.
 *
 * **컬러로 낸다** ([ADR-0008](../../../docs/decisions/ADR-0008-color-photos.md)).
 * 시안은 `.grayscale` 래퍼를 씌웠는데, 칵테일에서 색은 장식이 아니라 **식별 정보다** —
 * 흑백이면 네그로니와 맨해튼이 거의 같은 사진이 되고 49종 그리드가 서로 구별되지 않는다.
 * 클래스는 `styles.css` 에 남아 있다. 되돌리려면 아래 래퍼에 다시 넣는다.
 */
export function PhotoSlot({
  ratio,
  caption,
  src,
  alt,
}: {
  ratio: "4x5" | "4x3" | "3x2";
  caption?: string;
  /** 사진 경로 — 있으면 라벨·캡션 대신 사진을 그린다 */
  src?: string | null;
  /** 사진일 때만 쓴다. G-46 이 문구 주인을 정하기 전까지 칵테일 이름을 넣는다 */
  alt?: string;
}) {
  if (src) {
    return (
      <div className={`photo-slot photo-slot--${ratio} photo-slot--photo`}>
        <img
          className="photo-slot__img"
          src={src}
          alt={alt ?? ""}
          loading="lazy"
          width={800}
          height={800}
        />
      </div>
    );
  }
  return (
    <div className={`photo-slot photo-slot--${ratio}`}>
      {caption ? <div className="photo-slot__caption">{caption}</div> : null}
    </div>
  );
}
