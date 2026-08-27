"use client";

import { useState } from "react";

/**
 * 아티클 본문 블록 편집기 (ADR-0011 5단계).
 *
 * 문단 · 소제목 · 인용 · 사진 네 종류를 줄 단위로 추가 · 삭제 · 순서변경한다.
 * 상태는 부모(article-form)가 들고, 여기는 그 배열을 그리고 바꾸는 콜백만 받는다 —
 * 저장은 부모가 메타데이터와 함께 한 번에 보낸다.
 *
 * 사진은 파일을 골라 올리면 GCS 에 저장되고 주소가 채워진다 (ADR-0011 이미지 업로드).
 * 업로드 자체는 부모가 넘기는 `uploadImage` 가 한다 — 편집기는 slug 를 모른다.
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
  uploadImage,
}: {
  blocks: Block[];
  onChange: (next: Block[]) => void;
  /** 파일을 GCS 에 올리고 공개 주소를 돌려준다. 실패하면 null. */
  uploadImage: (file: File) => Promise<string | null>;
}) {
  const [uploadingAt, setUploadingAt] = useState<number | null>(null);

  const replace = (i: number, block: Block) => onChange(blocks.map((b, j) => (j === i ? block : b)));

  async function pickFile(i: number, block: Extract<Block, { kind: "figure" }>, file: File | undefined) {
    if (!file) return;
    setUploadingAt(i);
    try {
      const url = await uploadImage(file);
      if (url) replace(i, { ...block, src: url });
    } finally {
      setUploadingAt(null);
    }
  }
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
                {b.src ? (
                  // eslint-disable-next-line @next/next/no-img-element -- 편집기 미리보기라 next/image 를 쓰지 않는다
                  <img className="block-editor__preview" src={b.src} alt="" />
                ) : null}
                <label className="btn block-editor__upload">
                  {uploadingAt === i ? "올리는 중…" : b.src ? "사진 바꾸기" : "사진 올리기"}
                  <input
                    type="file"
                    accept="image/*"
                    hidden
                    disabled={uploadingAt !== null}
                    onChange={(e) => {
                      void pickFile(i, b, e.target.files?.[0]);
                      e.target.value = ""; // 같은 파일을 다시 골라도 onChange 가 뜨게 비운다
                    }}
                  />
                </label>
                <input
                  className="input"
                  placeholder="사진 주소 (올리면 자동으로 채워진다)"
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
