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

/**
 * 우리가 쓴 층. 시안 정본(`styles.css`)은 여기 없다 — ADR-0005 결정 1.
 * 간격·죽은 토큰 게이트는 이쪽에만 건다. 시안의 램프는 **팔레트**라서
 * 지금 안 쓰는 단이 있다고 죽은 코드가 아니다.
 */
const OURS = ["packages/ui/app.css", "apps/web/app/globals.css"];

/** 토큰 참조는 CSS 밖에서도 일어난다 — 인라인 style 이 램프를 직접 쓴다. */
const USAGE_ALSO = [
  "apps/web/components/sweet-tag.tsx",
  "apps/web/components/cocktail-card.tsx",
  "apps/web/components/finder-screen.tsx",
  "apps/web/components/search-screen.tsx",
  "apps/web/components/flavor-radar.tsx",
  "apps/web/app/cocktails/[slug]/page.tsx",
];

/** 사진 슬롯을 담는 그리드 — 트랙이 min-content 바닥을 가지면 안 된다 (게이트 3). */
const IMAGE_GRIDS = [".card-grid", ".result-grid"];

/** 클릭 대상 — 레이블이 두 줄로 접히면 안 된다 (게이트 4). */
const CLICKABLE = [".tab", ".chip", ".chip-tag"];

/*
 * 게이트 7(spacing_token)은 **의도적으로 없다.**
 *
 * 감사가 "간격 스케일을 만들어놓고 안 쓴다"고 지적했고 처음엔 4pt 강제로 잡으려 했다.
 * 실측해 보니 둘 다 성립하지 않았다 (ISSUE-052 / #70):
 *
 *   - 68개 리터럴 / 고유값 20종. 4pt 로 강제하면 눈에 보이는 변경이 30곳 생긴다
 *   - 전부 토큰화하려면 --space-0-5 · 1-5 · 2-5 · 3-5 · 4-5 · 5 · 7 · 7-5 ·
 *     9 · 10 · 11 · 13 · 16 을 새로 만들어야 한다
 *
 * 토큰 13개를 더한 것은 스케일이 아니라 **px 에 이름만 붙인 것**이다. 모든 값이 이름을
 * 얻으면 아무것도 제약되지 않고, "리듬을 한 번에 조일 손잡이"라는 원래 논거가 무너진다 —
 * 공유되는 단이 없기 때문이다.
 *
 * ADR-0005 결과 항목대로 **취향이면 취향이라고 적고 남긴다.** 되살리는 조건:
 * 섹션 리듬을 실제로 재설계할 때. 그때는 큰 간격(≥20px)만 좁게 거는 편이 맞다.
 */

/**
 * 숫자 열을 담는 클래스 — 자릿수가 흔들리면 안 된다 (게이트 10).
 * 필터를 누를 때마다 바뀌는 자리가 특히 눈에 띈다.
 */
const NUMERIC = [".results-count", ".spec-strip", ".cocktail-card__abv", ".profile-row", ".amount-cell"];

/**
 * 앱이 주입하는 것으로 알려진 토큰 — `packages/ui` 가 정의하지 않아도 된다 (게이트 9).
 * next/font 가 런타임에 넣는다. 이 목록 밖의 미정의 토큰은 실패로 본다.
 */
const APP_PROVIDED = ["--font-archivo", "--font-noto-sans", "--font-noto-serif"];

/**
 * 한글을 담는 칸 — 12px 바닥 (게이트 13).
 * 라틴 캡션이면 10px 도 성립하지만 한글은 x-height 가 커서 획이 뭉갠다.
 * 라틴 전용 레이블(.spec-strip dt · .seg-stack .en)은 여기 없다 — 그 자리는 9.5px 로 둔다.
 */
const HANGUL_BEARING = [".chip-tag", ".cocktail-card__foot"];

/**
 * 본문 글자색으로 쓸 수 없는 토큰 (SPEC-04 §2.1 실측표 · `NFR-A-01`·`A-03`).
 *
 * | 토큰 | 대비 |
 * |---|---|
 * | `--color-accent` | 3.76:1 |
 * | `--color-neutral-600` | 3.85:1 |
 * | `--color-neutral-500` | 2.59:1 |
 *
 * 배경으로 쓰는 것은 막지 않는다 — 문제는 **글자**다.
 */
const TEXT_FORBIDDEN = ["--color-accent", "--color-neutral-600", "--color-neutral-500"];
const HANGUL_MIN_PX = 12;

const EXCEPTIONS = [
  // 형식: { gate, selector, why }
  // 예외를 넣기 전에 규칙이 틀린 것은 아닌지 먼저 의심한다.
  {
    gate: "hangul_min_size",
    selector: ".chip-tag .count",
    why:
      "숫자만 담는 칸이다 (ISSUE-040). 한글 레이블은 형제 텍스트 노드에 있고 그 크기는" +
      " .chip-tag 가 정한다 — 이 규칙은 후손 선택자에서 '담는 글자'를 구분하지 못한다.",
  },
];

// ─────────────────────────────────────────────────────────────────────────────

/** 중괄호 깊이를 세며 규칙을 뽑는다. @media 안쪽도 규칙으로 잡되 at 을 기록한다. */
/**
 * 큰 글자인가 — WCAG 는 **24px 이상**, 또는 **18.66px 이상이면서 굵게**를 큰 글자로 본다.
 *
 * 큰 글자의 기준은 3:1 이라(`NFR-A-02`) accent(3.76:1)를 쓸 수 있다. 같은 규칙 안에 적힌
 * 크기만 본다 — 다른 곳에서 정해진 크기는 알 수 없고, 그쪽은 axe 가 그려진 것으로 잡는다.
 */
function isLargeText(decls) {
  const size = parseFloat(prop(decls, "font-size") ?? "");
  if (!size) return false;

  const weight = prop(decls, "font-weight") ?? "";
  const bold = /bold|[7-9]00/.test(weight);
  return size >= 24 || (size >= 18.66 && bold);
}

function parseRules(css) {
  const src = css.replace(/\/\*[\s\S]*?\*\//g, (m) => m.replace(/[^\n]/g, " "));
  const rules = [];
  const stack = [];
  let start = 0;

  const lineAt = (idx) => src.slice(0, idx).split("\n").length;

  for (let i = 0; i < src.length; i++) {
    const ch = src[i];
    if (ch === "{") {
      // `@import url(...);` 처럼 중괄호 없이 끝나는 문이 앞에 있으면 그것까지 프렐류드에
      // 딸려 들어와 다음 규칙이 at-rule 로 오인된다. 마지막 `;` 뒤만 취한다.
      // (셀렉터에는 `;` 가 오지 않으므로 안전하다.)
      const raw = src.slice(start, i);
      const prelude = raw.slice(raw.lastIndexOf(";") + 1).trim();
      stack.push({ prelude, at: i, bodyStart: i + 1 });
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
const defined = new Map();   // --name → "file:line"  (packages/ui 가 정의한 것)
const used = new Map();      // --name → "file:line"  (packages/ui 가 참조한 것)

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

    // 5. nav_fits — 320px 에서 브랜드 + 탭 3개가 한 줄에 안 들어간다.
    //    접거나(flex-wrap) 쌓거나(flex-direction: column) 둘 중 하나여야 한다.
    //    셀렉터를 정확히 `.site-nav` 로 본다 — `.site-nav .tabs` 의 flex-wrap 이
    //    우연히 게이트를 만족시키면 정작 컨테이너는 안 접히는데 통과한다.
    if (sel.trim() === ".site-nav" && !r.media) {
      seen.add("site-nav");
      const wraps = prop(r.decls, "flex-wrap");
      const dir = prop(r.decls, "flex-direction");
      if ((wraps && /wrap/.test(wraps)) || (dir && /column/.test(dir))) seen.add("site-nav:fits");
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

    // 12. oklch_only — 시안 정본에 hex 리터럴이 없다. 색은 oklch() 로 적는다.
    //     값 자체가 시안과 같은지는 scripts/color-parity.mjs 가 왕복으로 강제한다.
    if (rel === "packages/ui/styles.css" && /#[0-9a-fA-F]{6}\b/.test(r.decls)) {
      fail("oklch_only", rel, r.line, sel,
        "시안 정본에 hex 리터럴 — oklch() 로 적거나 토큰을 참조한다");
    }

    // 14. text_contrast_tokens — 본문에 못 쓰는 토큰이 글자색으로 왔는가 (ISSUE-046).
    //
    // SPEC-04 §2.1 실측표: accent 3.76:1 · neutral-600 3.85:1 · neutral-500 2.59:1 —
    // 셋 다 본문 AA(4.5:1) 미달이라 `NFR-A-01`·`A-03` 이 **배포 차단**으로 뒀다.
    //
    // axe(`e2e/a11y.spec.ts`)는 **그려진 것**을 보고 이 게이트는 **적힌 것**을 본다.
    // 조건부 클래스처럼 그 화면에서 안 그려지는 자리는 axe 가 못 잡는다 (이슈 046 RED 21).
    if (OURS.includes(rel) && !isLargeText(r.decls)) {
      const color = prop(r.decls, "color");
      for (const token of TEXT_FORBIDDEN) {
        if (color && color.includes(`var(${token})`)) {
          fail("text_contrast_tokens", rel, r.line, sel,
            `${token} 를 글자색으로 썼다 — 본문 AA 미달이다 (SPEC-04 §2.1). accent-700 · neutral-700 이상을 쓴다`);
        }
      }
    }

    // 13. hangul_min_size — 한글은 x-height 가 커서 10px 대에서 획이 뭉갠다.
    for (const name of matchedNames(sel, HANGUL_BEARING)) {
      const fs = prop(r.decls, "font-size");
      if (!fs) continue;
      const px = parseFloat(fs);
      if (/px/.test(fs) && px < HANGUL_MIN_PX) {
        fail("hangul_min_size", rel, r.line, sel,
          `${name} 은 한글을 담는데 font-size: ${fs} — ${HANGUL_MIN_PX}px 이상으로 둔다`);
      }
    }

    // 7. tabular_nums — 자릿수가 흔들리면 눈에 띈다.
    for (const name of matchedNames(sel, NUMERIC)) {
      seen.add(`num:${name}`);
      if (/tabular-nums/.test(r.decls)) seen.add(`num:${name}:ok`);
    }

    // 게이트 8·9 를 위한 수집 — packages/ui 안에서만 본다.
    if (rel.startsWith("packages/ui/")) {
      for (const m of r.decls.matchAll(/(--[\w-]+)\s*:/g)) {
        if (!defined.has(m[1])) defined.set(m[1], `${rel}:${r.line}`);
      }
      for (const m of r.decls.matchAll(/var\(\s*(--[\w-]+)/g)) {
        if (!used.has(m[1])) used.set(m[1], `${rel}:${r.line}`);
      }
    }
  }
}

// 토큰 참조는 CSS 밖에서도 일어난다. 여기를 안 보면 램프가 통째로 "죽은 토큰"이 된다.
for (const rel of USAGE_ALSO) {
  let text;
  try {
    text = readFileSync(join(ROOT, rel), "utf8");
  } catch {
    findings.push({
      gate: "usage_scan", file: rel, line: 0, selector: "-",
      msg: "USAGE_ALSO 에 있는 파일을 찾지 못했다 — 목록이 코드와 어긋났다",
    });
    continue;
  }
  for (const m of text.matchAll(/var\(\s*(--[\w-]+)/g)) {
    if (!used.has(m[1])) used.set(m[1], rel);
  }
}

// 존재 자체를 요구하는 게이트는 순회가 끝난 뒤 판정한다.
if (!seen.has("site-nav")) {
  fail("nav_fits", "-", 0, ".site-nav", ".site-nav 규칙을 찾지 못했다 — 가드가 코드와 어긋났다");
} else if (!seen.has("site-nav:fits")) {
  fail("nav_fits", "packages/ui/app.css", 0, ".site-nav",
    ".site-nav 가 접히지도 쌓이지도 않는다 — 320px 에서 브랜드 + 탭 3개가 한 줄에 안 들어간다");
}

// 14. no_fullwidth_glyph — 전각 기호는 반각 짝과 폭이 달라 나란히 두면 안 맞는다.
//     ＋(U+FF0B)와 −(U+2212)를 스테퍼 양쪽에 쓰고 있었다.
for (const rel of USAGE_ALSO.concat(["apps/web/components/recipe-panel.tsx"])) {
  let text;
  try { text = readFileSync(join(ROOT, rel), "utf8"); } catch { continue; }
  for (const m of text.matchAll(/[！-～]/g)) {
    fail("no_fullwidth_glyph", rel, text.slice(0, m.index).split("\n").length, m[0],
      `전각 '${m[0]}' (U+${m[0].codePointAt(0).toString(16).toUpperCase()}) — 반각으로 통일한다`);
  }
}

// 11. no_decorative_eyebrow — 서수가 아닌 <h6> 커커는 장식이다.
//     전부 챕터면 아무것도 챕터가 아니다. 폼·목록 레이블은 기능이 있으므로 남긴다.
const EYEBROW_ALLOWED = [
  { file: "apps/web/components/search-screen.tsx", text: "필터 FILTERS", why: "필터 패널의 폼 레이블" },
  { file: "apps/web/app/cocktails/[slug]/page.tsx", text: "같은 기주 RELATED", why: "목록 레이블" },
];
for (const rel of USAGE_ALSO) {
  let text;
  try { text = readFileSync(join(ROOT, rel), "utf8"); } catch { continue; }
  for (const m of text.matchAll(/<h6[^>]*>([\s\S]*?)<\/h6>/g)) {
    const label = m[1].replace(/\{[^}]*\}/g, "").replace(/\s+/g, " ").trim();
    const ok = EYEBROW_ALLOWED.some((e) => e.file === rel && label.includes(e.text));
    if (!ok) {
      fail("no_decorative_eyebrow", rel, text.slice(0, m.index).split("\n").length, `<h6>${label}</h6>`,
        "서수도 폼 레이블도 아닌 <h6> — 장식성 커커는 지운다 (허용은 EYEBROW_ALLOWED 에 근거와 함께)");
    }
  }
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

// 8. no_dead_tokens — 선언만 하고 아무도 안 쓰는 토큰은 죽은 정보다.
//    ADR-0001 이 부록 A 에서 "이 한 줄만 살렸다"고 적어둔 것이 실제로 안 쓰이면
//    문서와 코드가 어긋난 채로 굳는다.
//    시안 정본의 램프는 제외한다 — 팔레트는 지금 안 쓰는 단이 있어도 완결돼 있는 게 맞다.
//    우리가 쓴 층(app.css)이 선언한 것만 본다.
for (const [name, where] of defined) {
  const file = where.split(":")[0];
  if (!OURS.includes(file)) continue;
  if (!used.has(name)) {
    fail("no_dead_tokens", file, Number(where.split(":")[1]), name,
      `${name} 을 선언만 하고 아무도 참조하지 않는다 — 쓰거나 지운다`);
  }
}

// 9. token_defined_where_used — 패키지가 쓰는 토큰은 패키지가 정의한다.
//    소비자가 하나뿐이라 지금은 안 드러나지만, @mut/ui 를 다른 곳에서 가져가면
//    값이 조용히 사라진다.
for (const [name, where] of used) {
  if (!defined.has(name) && !APP_PROVIDED.includes(name)) {
    fail("token_defined_where_used", where.split(":")[0], Number(where.split(":")[1]), name,
      `${name} 을 packages/ui 가 쓰는데 정의는 밖에 있다 — 폴백을 패키지 안에 둔다`);
  }
}

for (const name of NUMERIC) {
  if (!seen.has(`num:${name}`)) {
    fail("tabular_nums", "-", 0, name, `${name} 규칙을 찾지 못했다 — NUMERIC 목록이 코드와 어긋났다`);
  } else if (!seen.has(`num:${name}:ok`)) {
    fail("tabular_nums", "packages/ui/app.css", 0, name,
      `${name} 에 font-variant-numeric 미지정 — 값이 바뀔 때 자릿수가 흔들린다`);
  }
}

// ─────────────────────────────────────────────────────────────────────────────

if (findings.length === 0) {
  const n = FILES.length;
  console.log(`css-guard: 13개 게이트 통과 (${n}개 파일)`);
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
