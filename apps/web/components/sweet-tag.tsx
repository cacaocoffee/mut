import { SWEETNESS } from "@kca/domain";
import type { SweetLevel } from "@kca/domain";

/** 당도 4단계를 accent 램프 위에서 단계적으로 진해지게 표기한다. */
const STYLES: Record<SweetLevel, React.CSSProperties> = {
  0: {
    background: "var(--color-neutral-200)",
    color: "var(--color-neutral-900)",
    border: "1px solid var(--color-neutral-400)",
  },
  1: {
    background: "var(--color-accent-2-100)",
    color: "var(--color-accent-2-800)",
    border: "1px solid var(--color-accent-2-300)",
  },
  2: {
    background: "var(--color-accent-200)",
    color: "var(--color-accent-800)",
    border: "1px solid var(--color-accent-300)",
  },
  3: {
    background: "var(--color-accent)",
    color: "var(--color-bg)",
    border: "1px solid var(--color-accent)",
  },
};

export function SweetTag({ level, en = false }: { level: SweetLevel; en?: boolean }) {
  return (
    <span className="tag" style={{ ...STYLES[level], whiteSpace: "nowrap" }}>
      {SWEETNESS[level][en ? 1 : 0]}
    </span>
  );
}
