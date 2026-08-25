"use client";

/**
 * 아티클 본문 블록 편집기 (ADR-0011 5단계).
 *
 * 문단 · 소제목 · 인용 · 사진 네 종류를 줄 단위로 추가 · 삭제 · 순서변경한다.
 * 상태는 부모(article-form)가 들고, 여기는 그 배열을 그리고 바꾸는 콜백만 받는다 —
 * 저장은 부모가 메타데이터와 함께 한 번에 보낸다.
 */
export type Block =
  | { kind: "paragraph"; text: string }
  | { kind: "heading"; text: string }
  | { kind: "quote"; text: string }
  | { kind: "figure"; src: string; width?: number; height?: number; caption?: string };

const KIND_LABEL: Record<Block["kind"], string> = {
  paragraph: "문단",
  heading: "소제목",
  quote: "인용",
  figure: "사진",
};

function emptyBlock(kind: Block["kind"]): Block {
  if (kind === "figure") return { kind, src: "", caption: "" };
  return { kind, text: "" };
}

export function ArticleBlockEditor({
  blocks,
  onChange,
}: {
  blocks: Block[];
  onChange: (next: Block[]) => void;
}) {
  const replace = (i: number, block: Block) => onChange(blocks.map((b, j) => (j === i ? block : b)));
  const remove = (i: number) => onChange(blocks.filter((_, j) => j !== i));
  const move = (i: number, dir: -1 | 1) => {
    const j = i + dir;
    if (j < 0 || j >= blocks.length) return;
    const next = [...blocks];
    [next[i], next[j]] = [next[j], next[i]];
    onChange(next);
  };
  const add = (kind: Block["kind"]) => onChange([...blocks, emptyBlock(kind)]);

  return (
    <div className="block-editor">
      <div className="block-editor__list">
        {blocks.map((b, i) => (
          <div key={i} className="block-editor__row">
            <div className="block-editor__meta">
              <span className="block-editor__kind">{KIND_LABEL[b.kind]}</span>
              <div className="block-editor__moves">
                <button type="button" className="btn btn-icon" aria-label="위로" disabled={i === 0} onClick={() => move(i, -1)}>
                  ↑
                </button>
                <button
                  type="button"
                  className="btn btn-icon"
                  aria-label="아래로"
                  disabled={i === blocks.length - 1}
                  onClick={() => move(i, 1)}
                >
                  ↓
                </button>
                <button type="button" className="btn btn-icon btn-danger" aria-label="삭제" onClick={() => remove(i)}>
                  ✕
                </button>
              </div>
            </div>

            {b.kind === "figure" ? (
              <div className="block-editor__figure">
                <input
                  className="input"
                  placeholder="사진 주소 (/articles/…/00.webp)"
                  value={b.src}
                  onChange={(e) => replace(i, { ...b, src: e.target.value })}
                />
                <input
                  className="input"
                  placeholder="사진 설명 (선택)"
                  value={b.caption ?? ""}
                  onChange={(e) => replace(i, { ...b, caption: e.target.value })}
                />
              </div>
            ) : (
              <textarea
                className="input block-editor__text"
                rows={b.kind === "paragraph" ? 4 : 2}
                placeholder={KIND_LABEL[b.kind]}
                value={b.text}
                onChange={(e) => replace(i, { ...b, text: e.target.value })}
              />
            )}
          </div>
        ))}
        {blocks.length === 0 && <p className="block-editor__empty">아래에서 블록을 추가해 본문을 씁니다.</p>}
      </div>

      <div className="block-editor__add">
        {(Object.keys(KIND_LABEL) as Block["kind"][]).map((kind) => (
          <button key={kind} type="button" className="btn" onClick={() => add(kind)}>
            + {KIND_LABEL[kind]}
          </button>
        ))}
      </div>
    </div>
  );
}
