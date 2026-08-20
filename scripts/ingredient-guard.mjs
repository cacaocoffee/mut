#!/usr/bin/env node
/**
 * 재료 마스터 불변식 (`FR-INGREDIENT-006` · `R-F2.2-5`).
 *
 * | 규칙 | 왜 |
 * |---|---|
 * | 레시피 줄이 전부 마스터로 읽힌다 | 미아 하나가 그 칵테일을 역검색에서 통째로 틀리게 한다 |
 * | 마스터에 죽은 항목이 없다 | 안 쓰이는 이름은 오타이거나 지운 재료의 잔해다 |
 * | 슬러그 · 이름 · 별칭이 겹치지 않는다 | 겹치면 어느 쪽으로 읽힐지 순서가 정한다 |
 * | `garnish` 는 판정에서 빠진다 | `R-F2.2-5` — 안 빼면 매칭이 거의 안 된다 |
 * | 요구 재료가 빈 칵테일이 없다 | 아무것도 없이 만들 수 있는 것이 되어 버린다 |
 *
 * `npm run check` 가 부른다. 재료 이름은 `data.ts` 에 자유 텍스트로 적히므로
 * **이 검사가 없으면 조용히 어긋난다** — 그것이 SPEC-00 §7 이 경고한 상황이다.
 *
 *   node scripts/ingredient-guard.mjs
 */
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");

/**
 * 도메인 패키지가 TS 라 `tsx` 로 한 번 건너 읽는다.
 * `check.ts` 와 같은 방식이다 — 검사기가 데이터를 다시 해석하지 않게 하려는 것이다.
 */
const probe = `
import { COCKTAILS } from "./packages/domain/src/data";
import { INGREDIENTS, resolveIngredient, countsForStock, getIngredient } from "./packages/domain/src/ingredients";
import { STOCK_INDEX } from "./packages/domain/src/stock";

const orphans = [];
const used = new Set();
for (const c of COCKTAILS) {
  for (const line of c.ingredients) {
    const req = resolveIngredient(line.ko);
    if (!req) orphans.push({ name: line.ko, cocktail: c.ko });
    else req.forEach((s) => used.add(s));
  }
}

const names = new Map();
const dupes = [];
for (const ing of INGREDIENTS) {
  for (const key of [ing.slug, ing.nameKo, ...(ing.aliases ?? [])]) {
    if (names.has(key)) dupes.push({ key, a: names.get(key), b: ing.slug });
    names.set(key, ing.slug);
  }
}

console.log(JSON.stringify({
  total: INGREDIENTS.length,
  orphans,
  dupes,
  unused: INGREDIENTS.filter((i) => !used.has(i.slug)).map((i) => i.slug),
  countedGarnish: INGREDIENTS.filter((i) => i.category === "garnish" && countsForStock(i.slug)).map((i) => i.slug),
  uncountedNonGarnish: INGREDIENTS.filter((i) => i.category !== "garnish" && !countsForStock(i.slug)).map((i) => i.slug),
  empty: STOCK_INDEX.filter((e) => e.needs.length === 0).map((e) => e.slug),
  stockable: INGREDIENTS.filter((i) => countsForStock(i.slug)).length,
  unknownInIndex: STOCK_INDEX.flatMap((e) => e.needs.flat()).filter((s) => !getIngredient(s)),
}));
`;

const out = execFileSync("npx", ["--yes", "tsx", "--eval", probe], {
  cwd: ROOT,
  encoding: "utf8",
  stdio: ["ignore", "pipe", "inherit"],
});
const r = JSON.parse(out.trim().split("\n").at(-1));

const findings = [];

for (const o of r.orphans) {
  findings.push(`"${o.name}" 를 마스터에서 못 찾는다 (${o.cocktail}) — ingredients.ts 에 넣거나 별칭으로 잇는다`);
}
for (const d of r.dupes) {
  findings.push(`"${d.key}" 가 ${d.a} 와 ${d.b} 둘 다에 있다 — 어느 쪽으로 읽힐지 순서가 정하게 된다`);
}
for (const s of r.unused) {
  findings.push(`${s} 는 어느 레시피에도 안 쓰인다 — 오타이거나 지운 재료의 잔해다`);
}
for (const s of r.countedGarnish) {
  findings.push(`${s} 는 garnish 인데 판정에 들어간다 (R-F2.2-5)`);
}
for (const s of r.uncountedNonGarnish) {
  findings.push(`${s} 는 garnish 가 아닌데 판정에서 빠진다 (FR-INGREDIENT-006)`);
}
for (const s of r.empty) {
  findings.push(`${s} 는 요구 재료가 없다 — 아무것도 없이 만들 수 있는 것이 된다`);
}
for (const s of r.unknownInIndex) {
  findings.push(`역검색 색인에 마스터에 없는 슬러그가 있다: ${s}`);
}

if (findings.length > 0) {
  console.error(`ingredient-guard: ${findings.length}건\n`);
  findings.forEach((f) => console.error(`  ${f}`));
  console.error("\n근거: SPEC-00 §7 — 재료를 문자열로 두면 역검색이 조용히 틀린다.");
  process.exit(1);
}

console.log(
  `ingredient-guard: 재료 ${r.total}개 통과 — 미아 0건 · 체크 대상 ${r.stockable}개 · 가니시 ${r.total - r.stockable}개`
);
