#!/usr/bin/env node
/**
 * 초기 JS 예산 (ISSUE-046 · `NFR-P-04`).
 *
 * ## 경고지 차단이 아니다
 *
 * SPEC-04 §9.1 이 이 항목만 **경고**로 뒀다. 번들은 기능을 더하면 늘고, 늘었다고 배포를
 * 막으면 "전부를 차단으로 만들면 아무것도 못 나간다" 는 그 상황이 된다. 대신 **넘은 사실이
 * 매번 보이게** 한다 — 조용히 넘는 것이 문제다.
 *
 * ## 무엇을 재나
 *
 * **모든 화면이 처음에 받는 공통 자바스크립트**다 (`build-manifest.json` 의 `rootMainFiles`
 * + 폴리필). 네트워크로 나가는 크기라 **gzip 으로** 잰다.
 *
 * 경로별 조각은 세지 않는다 — Turbopack 빌드의 매니페스트가 경로별 목록을 주지 않는다.
 * 공통 셸이 예산의 대부분이고, 이 값이 늘면 모든 화면이 함께 느려진다.
 *
 *   node scripts/bundle-budget.mjs        (npm run bundle:budget)
 */
import { readFileSync, existsSync } from "node:fs";
import { gzipSync } from "node:zlib";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const NEXT = join(ROOT, "apps/web/.next");

/** `NFR-P-04` — 초기 JS 150KB (gzip). */
const BUDGET_KB = 150;

const manifestPath = join(NEXT, "build-manifest.json");
if (!existsSync(manifestPath)) {
  console.error("✗ 빌드 산출물이 없다. `npm run build` 를 먼저 돌린다.");
  process.exit(1);
}

const manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
const files = [...(manifest.rootMainFiles ?? []), ...(manifest.polyfillFiles ?? [])];

if (files.length === 0) {
  console.error("✗ 매니페스트에 공통 조각이 없다 — 빌드 형식이 바뀌었는지 본다.");
  process.exit(1);
}

let bytes = 0;
const rows = [];
for (const file of files) {
  const abs = join(NEXT, file);
  if (!existsSync(abs)) continue;

  const size = gzipSync(readFileSync(abs)).length;
  bytes += size;
  rows.push({ file, kb: Math.round((size / 1024) * 10) / 10 });
}

const total = Math.round((bytes / 1024) * 10) / 10;

console.log(`공통 초기 JS (gzip) — 예산 ${BUDGET_KB}KB`);
for (const r of rows.sort((a, b) => b.kb - a.kb)) {
  console.log(`  ${String(r.kb).padStart(7)}KB  ${r.file}`);
}
console.log(`  ${"─".repeat(9)}`);
console.log(`  ${String(total).padStart(7)}KB  합계`);

if (total > BUDGET_KB) {
  // 경고다 — 종료 코드는 0 이다. **넘은 사실은 보이되 배포를 막지 않는다** (SPEC-04 §9.1).
  console.warn(
    `\n⚠ 예산을 ${Math.round((total - BUDGET_KB) * 10) / 10}KB 넘었다. ` +
      "`NFR-P-04` 는 경고 항목이지만, 이 줄이 반복해서 보이면 무엇이 들어왔는지 본다.",
  );
} else {
  console.log(`\n✓ 예산 안이다 (${BUDGET_KB - total}KB 남음).`);
}
