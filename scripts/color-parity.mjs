#!/usr/bin/env node
/**
 * 색 왕복 대조 — `packages/ui/styles.css` 의 `oklch()` 표기가 시안의 hex 와 같은 색인가.
 *
 * ADR-0005 결정 3 이 램프 표기 변경을 **"표기 변경이지 값 변경이 아니다"**로 규정했다.
 * 그 주장을 사람이 지키는 대신 이 스크립트가 지킨다 — 표기를 바꾸다 값이 밀리면 여기서 걸린다.
 *
 * BASELINE 은 매거진판 팔레트(ADR-0007)의 hex 정본이고 **손으로 고치지 않는다.**
 * 색을 정말 바꿔야 한다면 ADR 을 먼저 쓴다 (SPEC-00 §4). Modernist 시절에는
 * 시안 원본(`docs/design/source/MUT.dc.html`)이 이 자리를 맡았다.
 *
 *   node scripts/color-parity.mjs             대조 (npm run check 가 부른다)
 *   node scripts/color-parity.mjs --generate  BASELINE 을 oklch() 로 출력 (이관용)
 */

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const TARGET = "packages/ui/styles.css";

/** 매거진판이 정한 색 (ADR-0007). 이 표가 hex 정본이다. */
const BASELINE = {
  "--color-bg": "#f6f3eb",
  "--color-surface": "#ece8df",
  "--color-text": "#241e18",
  "--color-accent": "#93293b",
  "--color-accent-2": "#9f5c54",

  "--color-neutral-100": "#f8f5ee", "--color-neutral-200": "#ebe8e0", "--color-neutral-300": "#d8d4cc",
  "--color-neutral-400": "#bbb7af", "--color-neutral-500": "#9b9891", "--color-neutral-600": "#7d7a74",
  "--color-neutral-700": "#605e58", "--color-neutral-800": "#44423d", "--color-neutral-900": "#2d2b27",

  "--color-accent-100": "#fff1f1", "--color-accent-200": "#ffdcde", "--color-accent-300": "#ffc1c4",
  "--color-accent-400": "#f39ba2", "--color-accent-500": "#da767f", "--color-accent-600": "#bc5460",
  "--color-accent-700": "#993846", "--color-accent-800": "#6f252f", "--color-accent-900": "#481b20",

  "--color-accent-2-100": "#fef2f0", "--color-accent-2-200": "#fde0dc", "--color-accent-2-300": "#f6c8c1",
  "--color-accent-2-400": "#e4a59c", "--color-accent-2-500": "#ca8379", "--color-accent-2-600": "#ac6359",
  "--color-accent-2-700": "#8b473f", "--color-accent-2-800": "#64312b", "--color-accent-2-900": "#41211d",
};

// ── 색 변환 (Björn Ottosson, Oklab) ──────────────────────────────────────────

const toLinear = (c) => (c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4);
const toGamma = (c) => (c <= 0.0031308 ? 12.92 * c : 1.055 * c ** (1 / 2.4) - 0.055);

function hexToOklch(hex) {
  const n = parseInt(hex.slice(1), 16);
  const r = toLinear(((n >> 16) & 255) / 255);
  const g = toLinear(((n >> 8) & 255) / 255);
  const b = toLinear((n & 255) / 255);

  const l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
  const m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
  const s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);

  const L = 0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s;
  const A = 1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s;
  const B = 0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s;

  const C = Math.hypot(A, B);
  let H = (Math.atan2(B, A) * 180) / Math.PI;
  if (H < 0) H += 360;
  return { L, C, H };
}

function oklchToHex({ L, C, H }) {
  const A = C * Math.cos((H * Math.PI) / 180);
  const B = C * Math.sin((H * Math.PI) / 180);

  const l = (L + 0.3963377774 * A + 0.2158037573 * B) ** 3;
  const m = (L - 0.1055613458 * A - 0.0638541728 * B) ** 3;
  const s = (L - 0.0894841775 * A - 1.291485548 * B) ** 3;

  const rgb = [
    4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
    -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
    -0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s,
  ].map((v) => Math.round(Math.min(1, Math.max(0, toGamma(v))) * 255));

  return "#" + rgb.map((v) => v.toString(16).padStart(2, "0")).join("");
}

/** 표기용 반올림. 이 자리수로 왕복이 성립하는지는 아래에서 검증한다. */
const fmt = ({ L, C, H }) =>
  `oklch(${(L * 100).toFixed(2)}% ${C.toFixed(4)} ${C < 0.0005 ? 0 : H.toFixed(2)})`;

// ── 생성 모드 ────────────────────────────────────────────────────────────────

if (process.argv.includes("--generate")) {
  for (const [name, hex] of Object.entries(BASELINE)) {
    console.log(`  ${name}: ${fmt(hexToOklch(hex))}; /* ${hex} */`);
  }
  process.exit(0);
}

// ── 대조 모드 ────────────────────────────────────────────────────────────────

const css = readFileSync(join(ROOT, TARGET), "utf8");
const problems = [];

for (const [name, hex] of Object.entries(BASELINE)) {
  const m = css.match(new RegExp(`${name}\\s*:\\s*(oklch\\([^)]*\\))`));
  if (!m) {
    problems.push(`${name} — ${TARGET} 에서 oklch() 선언을 찾지 못했다 (아직 hex 인가?)`);
    continue;
  }
  const [, Ls, Cs, Hs] = m[1].match(/oklch\(\s*([\d.]+)%\s+([\d.]+)\s+([\d.]+)/) ?? [];
  if (Ls === undefined) {
    problems.push(`${name} — oklch() 를 읽지 못했다: ${m[1]}`);
    continue;
  }
  const back = oklchToHex({ L: Number(Ls) / 100, C: Number(Cs), H: Number(Hs) });
  if (back !== hex) {
    problems.push(`${name} — ${m[1]} 은 ${back} 로 돌아온다. 시안은 ${hex} 다`);
  }
}

if (problems.length) {
  console.error(`color-parity: ${problems.length}건\n`);
  for (const p of problems) console.error(`  ${p}`);
  console.error(
    "\n색을 바꾸려면 표기가 아니라 결정이 먼저다 — ADR-0005 결정 2 의 3단 절차를 밟고 BASELINE 을 함께 고친다."
  );
  process.exit(1);
}

console.log(`color-parity: ${Object.keys(BASELINE).length}색 왕복 일치 (${TARGET})`);
