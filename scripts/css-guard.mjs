#!/usr/bin/env node
/**
 * CSS 가드 — 화면 층이 스스로 무너지지 않게 막는다.
 *
 * 웹에는 테스트 러너가 없다 (apps/web 에 vitest·playwright 가 없다). 저장소가 이미 쓰는 방식 —
 * `scripts/taxonomy-parity.mjs` · `packages/domain/check.ts` — 을 따라 정적 가드로 고정한다.
 *
 * 이 파일이 막는 것은 전부 **한 번 겪은 결함**이다. 새 규칙을 취향으로 추가하지 않는다.
 * 근거: ISSUE-051 (#69) · ADR-0005 · GAPS G-27.
 *
 * 예외는 EXCEPTIONS 에 근거와 함께 등록한다. 억제가 쌓이는 것이 코드에서 보여야 한다 —
 * 늘어나면 규칙 쪽이 틀렸다는 신호다 (G-25 가 스키마 린트에서 쓴 것과 같은 방식).
 */

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join, relative } from "node:path";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");

const FILES = [
  "packages/ui/styles.css",
  "packages/ui/app.css",
  "apps/web/app/globals.css",
];

/** 사진 슬롯을 담는 그리드 — 트랙이 min-content 바닥을 가지면 안 된다 (게이트 3). */
const IMAGE_GRIDS = [".card-grid", ".result-grid"];

/** 클릭 대상 — 레이블이 두 줄로 접히면 안 된다 (게이트 4). */
const CLICKABLE = [".tab", ".chip", ".chip-tag"];

const EXCEPTIONS = [
  // 형식: { gate, selector, why }
  // 예외를 넣기 전에 규칙이 틀린 것은 아닌지 먼저 의심한다.
];

// ─────────────────────────────────────────────────────────────────────────────

/** 중괄호 깊이를 세며 규칙을 뽑는다. @media 안쪽도 규칙으로 잡되 at 을 기록한다. */
function parseRules(css) {
  const src = css.replace(/\/\*[\s\S]*?\*\//g, (m) => m.replace(/[^\n]/g, " "));
  const rules = [];
  const stack = [];
  let start = 0;

  const lineAt = (idx) => src.slice(0, idx).split("\n").length;

  for (let i = 0; i < src.length; i++) {
    const ch = src[i];
    if (ch === "{") {
      stack.push({ prelude: src.slice(start, i).trim(), at: i, bodyStart: i + 1 });
      start = i + 1;
    } else if (ch === "}") {
      const top = stack.pop();
      if (top && !top.prelude.startsWith("@")) {
        rules.push({
          selector: top.prelude,
          decls: src.slice(top.bodyStart, i),
          line: lineAt(top.at),
          media: stack.filter((s) => s.prelude.startsWith("@")).map((s) => s.prelude).join(" "),
        });
      }
      start = i + 1;
    }
  }
  return rules;
}

/** 선언 블록에서 속성 값을 꺼낸다. 마지막 선언이 이긴다. */
function prop(decls, name) {
  const re = new RegExp(`(?:^|;)\\s*${name}\\s*:([^;}]*)`, "gi");
  let m, last = null;
  while ((m = re.exec(decls)) !== null) last = m[1].trim();
  return last;
}

/** 셀렉터가 어떤 이름들을 겨냥하는가. `.chip-tag[aria-pressed]` → [".chip-tag"] */
function matchedNames(selector, names) {
  const heads = selector.split(",").map((s) => s.trim().split(/[\s>+~]/)[0].replace(/[:\[].*$/, ""));
  return names.filter((n) => heads.includes(n));
}

const selectorHits = (selector, names) => matchedNames(selector, names).length > 0;

// ─────────────────────────────────────────────────────────────────────────────

const findings = [];
const seen = new Set();

function fail(gate, file, line, selector, msg) {
  if (EXCEPTIONS.some((e) => e.gate === gate && e.selector === selector)) return;
  findings.push({ gate, file, line, selector, msg });
}

for (const rel of FILES) {
  const abs = join(ROOT, rel);
  const css = readFileSync(abs, "utf8");
  const rules = parseRules(css);

  for (const r of rules) {
    const sel = r.selector;

    // 1. root_overflow_clip — hidden 은 요소를 스크롤 컨테이너로 만든다. clip 은 아니다.
    if (/(^|,)\s*(html|body)\s*(,|$)/.test(sel)) {
      const ox = prop(r.decls, "overflow-x") ?? prop(r.decls, "overflow");
      if (ox && /\bhidden\b/.test(ox)) {
        fail("root_overflow_clip", rel, r.line, sel,
          `html/body 에 overflow-x: ${ox} — clip 을 쓴다. hidden 은 넘침을 숨겨 결함을 안 보이게 한다`);
      }
    }

    // 2. no_100vw — 데스크톱에서 스크롤바 폭을 포함해 항상 넘친다.
    if (/\b100vw\b/.test(r.decls)) {
      fail("no_100vw", rel, r.line, sel, "100vw 는 스크롤바 폭을 포함한다 — 100% + 컨테이너 패딩을 쓴다");
    }

    // 3. image_grid_minmax — 맨 1fr 은 min-content 바닥을 갖는다.
    if (selectorHits(sel, IMAGE_GRIDS)) {
      const gtc = prop(r.decls, "grid-template-columns");
      if (gtc && /\b\d*\.?\d*fr\b/.test(gtc) && !/minmax\(\s*0/.test(gtc)) {
        fail("image_grid_minmax", rel, r.line, sel,
          `사진을 담는 그리드에 맨 fr — "${gtc}" → minmax(0, 1fr)`);
      }
    }

    // 4. clickable_nowrap — 두 줄로 접힌 클릭 대상은 결함이다.
    //    이름별로 "어딘가 한 곳에서" 선언되면 된다. 변형 규칙마다 요구하지 않는다.
    for (const name of matchedNames(sel, CLICKABLE)) {
      seen.add(`clickable:${name}`);
      if (prop(r.decls, "white-space")) seen.add(`clickable:${name}:ws`);
    }

    // 5. nav_wraps — 내비가 안 접히면 320px 에서 넘친다.
    if (selectorHits(sel, [".site-nav"]) && !r.media) {
      seen.add("site-nav");
      if (prop(r.decls, "flex-wrap")) seen.add("site-nav:wrap");
    }

    // 6. dvh_page_shell — 모바일 주소창이 접히면 100vh 가 화면보다 크다.
    if (selectorHits(sel, [".page"])) {
      // `\bvh\b` 는 "100vh" 에 안 걸린다 — 숫자와 v 사이에 단어 경계가 없다.
      // 숫자가 바로 앞에 오는 것만 잡으면 "100dvh"(앞이 d)는 자연히 빠진다.
      const mh = prop(r.decls, "min-height");
      if (mh && /\d+vh\b/.test(mh)) {
        fail("dvh_page_shell", rel, r.line, sel, `페이지 셸에 ${mh} — dvh 를 쓴다`);
      }
    }
  }
}

// 존재 자체를 요구하는 게이트는 순회가 끝난 뒤 판정한다.
if (!seen.has("site-nav")) {
  fail("nav_wraps", "-", 0, ".site-nav", ".site-nav 규칙을 찾지 못했다 — 가드가 코드와 어긋났다");
} else if (!seen.has("site-nav:wrap")) {
  fail("nav_wraps", "packages/ui/app.css", 0, ".site-nav",
    ".site-nav 에 flex-wrap 미지정 — 320px 에서 브랜드 + 탭 3개가 한 줄에 안 들어간다");
}

// 목록이 코드와 어긋나면 가드가 조용히 아무것도 안 보게 된다. 그쪽이 더 위험하다.
for (const name of CLICKABLE) {
  if (!seen.has(`clickable:${name}`)) {
    fail("clickable_nowrap", "-", 0, name,
      `${name} 규칙을 찾지 못했다 — CLICKABLE 목록이 코드와 어긋났다`);
  } else if (!seen.has(`clickable:${name}:ws`)) {
    fail("clickable_nowrap", "packages/ui/app.css", 0, name,
      `${name} 에 white-space 미지정 — 좁은 폭에서 레이블이 두 줄로 접힌다`);
  }
}

// ─────────────────────────────────────────────────────────────────────────────

if (findings.length === 0) {
  const n = FILES.length;
  console.log(`css-guard: 6개 게이트 통과 (${n}개 파일)`);
  if (EXCEPTIONS.length) console.log(`  등록된 예외 ${EXCEPTIONS.length}건`);
  process.exit(0);
}

console.error(`css-guard: ${findings.length}건\n`);
for (const f of findings) {
  const where = f.line ? `${f.file}:${f.line}` : f.file;
  console.error(`  [${f.gate}] ${where}  ${f.selector}`);
  console.error(`      ${f.msg}\n`);
}
console.error("근거: ISSUE-051 (#69) · ADR-0005. 규칙이 틀렸다고 판단되면 EXCEPTIONS 에 근거와 함께 등록한다.");
process.exit(1);
