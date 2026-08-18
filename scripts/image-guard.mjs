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
 * ## 지금은 검사할 이미지가 없다
 *
 * 화면의 이미지는 전부 자리표시자다 (`PhotoSlot` — 해칭 무늬와 캡션). 실제 사진 자산이
 * 없어서이고(G-07 이미지 저장소 미정), **그래서 이 게이트를 지금 세운다** —
 * 첫 사진이 들어오는 날 규칙을 기억하는 사람이 없어도 여기서 걸린다.
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

if (findings.length > 0) {
  console.error(`image-guard: ${findings.length}건\n`);
  findings.forEach((f) => console.error(`  ${f}`));
  console.error("\n근거: SPEC-04 §1 (NFR-P-06·P-07) — 배포 차단 항목이다.");
  process.exit(1);
}

console.log("image-guard: 통과 — 검사 대상 이미지 없음 (전부 자리표시자, G-07)");
