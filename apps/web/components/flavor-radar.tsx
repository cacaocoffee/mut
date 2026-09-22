import { AXES, type Profile } from "@mut/domain";

const CX = 130;
const CY = 118;
const R = 88;

function point(i: number, value: number): [number, number] {
  const angle = ((-90 + i * 72) * Math.PI) / 180;
  return [CX + (Math.cos(angle) * R * value) / 5, CY + (Math.sin(angle) * R * value) / 5];
}

function ring(step: number): string {
  return AXES.map((_, i) =>
    point(i, step)
      .map((n) => Math.round(n * 10) / 10)
      .join(",")
  ).join(" ");
}

export function FlavorRadar({ profile }: { profile: Profile }) {
  const dots = profile.map((v, i) => {
    const [x, y] = point(i, v);
    return { x: Math.round(x), y: Math.round(y) };
  });

  return (
    /* 화면에 읽히지 않는다 — 바로 아래 `.profile-row` 목록이 축 이름과 `n/5` 를
       글자로 주므로, 차트까지 읽으면 같은 값을 두 번 듣게 된다 */
    <svg
      viewBox="0 0 260 250"
      aria-hidden="true"
      style={{ width: "100%", height: "auto", overflow: "visible", marginTop: 12 }}
    >
      {[1, 2, 3, 4, 5].map((k) => (
        <polygon
          key={k}
          points={ring(k)}
          fill="none"
          stroke={k === 5 ? "var(--color-divider)" : "var(--color-neutral-300)"}
          strokeWidth={1}
        />
      ))}
      {AXES.map((_, i) => {
        const [x2, y2] = point(i, 5);
        return (
          <line
            key={i}
            x1={CX}
            y1={CY}
            x2={Math.round(x2)}
            y2={Math.round(y2)}
            stroke="var(--color-divider)"
            strokeWidth={1}
          />
        );
      })}
      <polygon
        points={dots.map((p) => `${p.x},${p.y}`).join(" ")}
        fill="color-mix(in srgb, var(--color-accent) 22%, transparent)"
        stroke="var(--color-accent)"
        strokeWidth={2}
      />
      {dots.map((p, i) => (
        <circle key={i} cx={p.x} cy={p.y} r={3.5} fill="var(--color-accent)" />
      ))}
      {AXES.map((axis, i) => {
        const angle = ((-90 + i * 72) * Math.PI) / 180;
        const x = CX + Math.cos(angle) * (R + 26);
        const y = CY + Math.sin(angle) * (R + 20) + 4;
        const anchor =
          Math.abs(Math.cos(angle)) < 0.2 ? "middle" : Math.cos(angle) > 0 ? "start" : "end";
        return (
          <text
            key={axis}
            x={Math.round(x)}
            y={Math.round(y)}
            textAnchor={anchor}
            fontSize={10}
            letterSpacing="0.08em"
            fill="var(--color-neutral-700)"
          >
            {axis}
          </text>
        );
      })}
    </svg>
  );
}
