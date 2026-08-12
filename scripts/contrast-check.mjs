#!/usr/bin/env node
/**
 * 대비 게이트 — 함께 쓰이는 (글자, 배경) 토큰 쌍이 WCAG AA 를 넘는가.
 *
 * ISSUE-055 가 이걸 만들게 했다. 내비 탭이 선택되면 글자가 안 보였는데 원인이 둘이었다:
 *
 *   ① 명시도 충돌 — `styles.css` 의 `.nav a[aria-current='page']`(0,2,1)가
 *      `.tab[aria-current]`(0,2,0)를 이겨 accent 배경에 accent 글자가 됐다. 대비 1:1.
 *   ② 그걸 이겨도 의도했던 조합이 3.76:1 로 소형 텍스트 AA(4.5:1)에 미달이었다.
 *
 * ①은 정적으로 잡기 어렵다(캐스케이드 계산이 필요하다). **②는 잡을 수 있고,
 * ②를 막으면 ①도 눈에 띈다** — 안 보이는 조합을 애초에 등록할 수 없기 때문이다.
 *
 * PAIRS 는 화면에서 실제로 겹치는 조합만 적는다. 늘리는 것이 목적이 아니라,
 * **위험한 자리를 아는 것**이 목적이다.
 *
 * `NFR-A-01` 은 배포 차단 조건이다 (SPEC-04). G-16 이 실측표를 만들었다.
 */

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");

/** 소형 텍스트 AA. 18.66px+ bold 나 24px+ 는 3:1 이지만 여기 등록된 자리는 전부 소형이다. */
const AA_SMALL = 4.5;

const PAIRS = [
  { where: ".tab (비선택)", fg: "--color-neutral-700", bg: "--color-bg" },
  { where: ".tab[aria-current] (선택)", fg: "--color-accent-700", bg: "--color-bg" },
  { where: ".tab:hover", fg: "--color-text", bg: "--color-bg" },
  { where: "본문", fg: "--color-text", bg: "--color-bg" },
  { where: ".card-body 계열", fg: "--color-neutral-800", bg: "--color-surface" },
  { where: ".chip-tag", fg: "--color-neutral-800", bg: "--color-bg" },
  { where: ".chip-tag[aria-pressed]", fg: "--color-accent-800", bg: "--color-accent-100" },
  { where: ".cocktail-card__foot em", fg: "--color-accent-700", bg: "--color-surface" },
  { where: ".substitute-note", fg: "--color-accent-900", bg: "--color-accent-100" },
];

/**
 * 알려진 미달. **고치라고 남겨둔 것이지 봐주는 것이 아니다.**
 * 늘어나면 토큰 쪽을 고쳐야 한다는 신호다.
 */
const KNOWN = [
  {
    where: ".chip[aria-pressed] / .btn-primary",
    fg: "--color-bg",
    bg: "--color-accent",
    why: "3.76:1 — G-16 등재, 이슈 050(#52)이 BLOCKED 로 들고 있는 제품 결정. 임의로 못 고친다",
  },
];

// ── 파싱 ─────────────────────────────────────────────────────────────────────

const css = readFileSync(join(ROOT, "packages/ui/styles.css"), "utf8");

function token(name) {
  const m = css.match(new RegExp(`${name}\\s*:\\s*([^;]+);`));
  if (!m) return null;
  const v = m[1].trim();
  const hex = v.match(/#([0-9a-fA-F]{6})/);
  if (hex) return hexToRgb("#" + hex[1]);
  const ok = v.match(/oklch\(\s*([\d.]+)%\s+([\d.]+)\s+([\d.]+)/);
  if (ok) return oklchToRgb(Number(ok[1]) / 100, Number(ok[2]), Number(ok[3]));
  return null;
}

const hexToRgb = (h) => {
  const n = parseInt(h.slice(1), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
};

const toGamma = (c) => (c <= 0.0031308 ? 12.92 * c : 1.055 * c ** (1 / 2.4) - 0.055);

function oklchToRgb(L, C, H) {
  const A = C * Math.cos((H * Math.PI) / 180);
  const B = C * Math.sin((H * Math.PI) / 180);
  const l = (L + 0.3963377774 * A + 0.2158037573 * B) ** 3;
  const m = (L - 0.1055613458 * A - 0.0638541728 * B) ** 3;
  const s = (L - 0.0894841775 * A - 1.291485548 * B) ** 3;
  return [
    4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
    -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
    -0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s,
  ].map((v) => Math.round(Math.min(1, Math.max(0, toGamma(v))) * 255));
}

const lin = (c) => (c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4);
const lum = ([r, g, b]) => 0.2126 * lin(r / 255) + 0.7152 * lin(g / 255) + 0.0722 * lin(b / 255);

function ratio(fg, bg) {
  const a = lum(fg);
  const b = lum(bg);
  const [hi, lo] = a > b ? [a, b] : [b, a];
  return (hi + 0.05) / (lo + 0.05);
}

// ── 판정 ─────────────────────────────────────────────────────────────────────

const problems = [];
const rows = [];

for (const p of PAIRS) {
  const fg = token(p.fg);
  const bg = token(p.bg);
  if (!fg || !bg) {
    problems.push(`${p.where} — 토큰을 읽지 못했다 (${!fg ? p.fg : p.bg})`);
    continue;
  }
  const r = ratio(fg, bg);
  rows.push([r, p.where]);
  if (r < AA_SMALL) {
    problems.push(
      `${p.where} — ${r.toFixed(2)}:1 (${p.fg} on ${p.bg}). 소형 텍스트는 ${AA_SMALL}:1 이 바닥이다`
    );
  }
}

for (const k of KNOWN) {
  const fg = token(k.fg);
  const bg = token(k.bg);
  if (!fg || !bg) continue;
  const r = ratio(fg, bg);
  // 알려진 미달이 **좋아졌으면** 목록에서 빼라고 알린다. 억제가 굳는 것을 막는다.
  if (r >= AA_SMALL) {
    problems.push(`${k.where} — ${r.toFixed(2)}:1 로 이제 AA 를 넘는다. KNOWN 에서 뺀다`);
  }
}

if (problems.length) {
  console.error(`contrast-check: ${problems.length}건\n`);
  for (const p of problems) console.error(`  ${p}`);
  console.error("\n`NFR-A-01` 은 배포 차단 조건이다 (SPEC-04). 근거는 G-16.");
  process.exit(1);
}

console.log(`contrast-check: ${PAIRS.length}쌍 AA 통과 · 알려진 미달 ${KNOWN.length}건`);
for (const [r, where] of rows.sort((a, b) => a[0] - b[0])) {
  console.log(`  ${r.toFixed(2).padStart(6)}:1  ${where}`);
}
