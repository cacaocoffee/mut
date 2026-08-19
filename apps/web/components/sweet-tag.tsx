import { SWEETNESS } from "@kca/domain";
import type { SweetLevel } from "@kca/domain";

/**
 * 당도 4단계를 accent 램프 위에서 단계적으로 진해지게 표기한다.
 *
 * 키가 슬러그다 (이슈 037). 예전에는 `0`~`3` 이었는데, **숫자는 의미가 순서에 숨어**
 * 있어서 이 표만 보고는 `2` 가 무엇인지 알 수 없었다.
 */
const STYLES: Record<SweetLevel, React.CSSProperties> = {
  dry: {
    background: "var(--color-neutral-200)",
    color: "var(--color-neutral-900)",
    border: "1px solid var(--color-neutral-400)",
  },
  semi_dry: {
    background: "var(--color-accent-2-100)",
    color: "var(--color-accent-2-800)",
    border: "1px solid var(--color-accent-2-300)",
  },
  semi_sweet: {
    background: "var(--color-accent-200)",
    color: "var(--color-accent-800)",
    border: "1px solid var(--color-accent-300)",
  },
  // 흰 글자를 얹는 면은 accent 가 아니라 accent-700 이다 (G-16 · ADR-0006) —
  // accent 바탕은 3.76:1 이라 11px 글자에 못 쓴다.
  sweet: {
    background: "var(--color-accent-700)",
    color: "var(--color-bg)",
    border: "1px solid var(--color-accent-700)",
  },
};

export function SweetTag({ level, en = false }: { level: SweetLevel; en?: boolean }) {
  return (
    <span className="tag" style={{ ...STYLES[level], whiteSpace: "nowrap" }}>
      {SWEETNESS[level][en ? 1 : 0]}
    </span>
  );
}
