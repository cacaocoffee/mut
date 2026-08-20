#!/usr/bin/env node
/**
 * 이미지 규칙 (ISSUE-046 · `NFR-P-06`·`P-07`).
 *
 * | 규칙 | 근거 |
 * |---|---|
 * | WebP · AVIF 로 낸다 | `NFR-P-06` (차단) |
 * | 히어로 밖은 `loading="lazy"` | `NFR-P-06` (차단) |
 * | 반응형 `sizes` 를 준다 | `NFR-P-06` (차단) |
 *
 * ## 사진 목록 ↔ 디렉터리 대조 (ISSUE-060 #139)
 *
 * 사진은 `apps/web/public/cocktails/{slug}.webp` 에 있고(#87 · D-6), 화면은
 * `apps/web/lib/cocktail-photos.ts` 의 슬러그 목록을 보고 사진과 자리표시자를 가른다.
 * 둘이 어긋나면 — 목록에만 있으면 깨진 이미지가 나가고, 파일만 있으면 화면에 안 나온다.
 * 그래서 양방향으로 대조한다.
 *
 * 몇 개를 봤는지 함께 찍는다 — "통과" 만 나오면 검사기가 아무것도 안 보고 있는
 * 상태와 구분되지 않는다.
 *
 *   node scripts/image-guard.mjs        (npm run check 가 부른다)
 */
import { readFileSync, readdirSync, statSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join, relative } from "node:path";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const SCAN = ["apps/web/app", "apps/web/components", "apps/web/lib"];

/** 위에 접히지 않고 바로 보이는 것. 이것만 `lazy` 를 붙이지 않는다. */
const EAGER_ALLOWED = /hero|opengraph/i;

const findings = [];
let checked = 0;

function walk(dir) {
  for (const name of readdirSync(dir)) {
    const abs = join(dir, name);
    if (statSync(abs).isDirectory()) {
      walk(abs);
      continue;
    }
    if (!/\.(tsx|ts)$/.test(name)) continue;
    inspect(abs);
  }
}

function inspect(abs) {
  const rel = relative(ROOT, abs);
  const source = readFileSync(abs, "utf8");

  // `next/image` 는 포맷·`sizes` 를 스스로 다루지만 `alt` 와 `sizes` 는 사람이 적는다.
  const usesNextImage = /from ["']next\/image["']/.test(source);

  for (const m of source.matchAll(/<(img|Image)\s([^>]*)>/gs)) {
    const [, tag, attrs] = m;
    checked += 1;
    const line = source.slice(0, m.index).split("\n").length;
    const where = `${rel}:${line} <${tag}>`;

    if (!/\balt=/.test(attrs)) {
      findings.push(`${where} — alt 이 없다 (NFR-A-01 계열, 스크린리더가 파일명을 읽는다)`);
    }
    if (!EAGER_ALLOWED.test(attrs) && !/loading=["{]?["']?lazy/.test(attrs) && tag === "img") {
      findings.push(`${where} — loading="lazy" 가 없다 (NFR-P-06)`);
    }
    if (!/\bsizes=/.test(attrs) && !/\bwidth=/.test(attrs)) {
      findings.push(`${where} — 반응형 sizes 가 없다 (NFR-P-06 · P-07)`);
    }
    if (/src=["'][^"']+\.(png|jpe?g)["']/.test(attrs) && !usesNextImage) {
      findings.push(`${where} — png/jpg 를 직접 쓴다. WebP·AVIF 로 낸다 (NFR-P-06)`);
    }
  }
}

for (const dir of SCAN) walk(join(ROOT, dir));

// ── 사진 목록 ↔ 디렉터리 양방향 대조 (ISSUE-060 #139) ─────────────────────
const PHOTO_DIR = join(ROOT, "apps/web/public/cocktails");
const MANIFEST = join(ROOT, "apps/web/lib/cocktail-photos.ts");

const photoFiles = new Set(
  readdirSync(PHOTO_DIR)
    .filter((n) => n.endsWith(".webp"))
    .map((n) => n.replace(/\.webp$/, "")),
);
const listedSlugs = new Set(
  [...readFileSync(MANIFEST, "utf8").matchAll(/^\s*"([a-z0-9]+)",$/gm)].map((m) => m[1]),
);

for (const slug of listedSlugs) {
  if (!photoFiles.has(slug)) {
    findings.push(
      `apps/web/lib/cocktail-photos.ts — "${slug}" 가 목록에 있는데 public/cocktails/${slug}.webp 가 없다 (깨진 이미지가 나간다)`,
    );
  }
}
for (const slug of photoFiles) {
  if (!listedSlugs.has(slug)) {
    findings.push(
      `apps/web/public/cocktails/${slug}.webp — 파일은 있는데 cocktail-photos.ts 목록에 없다 (화면에 안 나온다)`,
    );
  }
}

if (findings.length > 0) {
  console.error(`image-guard: ${findings.length}건\n`);
  findings.forEach((f) => console.error(`  ${f}`));
  console.error("\n근거: SPEC-04 §1 (NFR-P-06·P-07) — 배포 차단 항목이다.");
  process.exit(1);
}

console.log(
  `image-guard: <img> ${checked}개 · 사진 목록 ${listedSlugs.size}건 ↔ 파일 ${photoFiles.size}장 대조 통과`,
);
