import {
  ABV_BANDS,
  BASES_IN_CORPUS,
  COCKTAILS,
  FLAVORS_IN_CORPUS,
  STYLES_IN_CORPUS,
  abvBandOf,
} from "./src/data";
import { validateCorpus } from "./src/validate";

const errors = validateCorpus();
if (errors.length) {
  console.error(`✗ 코퍼스 불변식 위반 ${errors.length}건\n`);
  for (const e of errors) console.error("  " + e);
  process.exit(1);
}

console.log(`✓ ${COCKTAILS.length}종 통과`);
console.log(`  기주   ${BASES_IN_CORPUS.length}종: ${BASES_IN_CORPUS.join(", ")}`);
console.log(`  스타일 ${STYLES_IN_CORPUS.length}종: ${STYLES_IN_CORPUS.join(", ")}`);
console.log(`  향     ${FLAVORS_IN_CORPUS.length}종: ${FLAVORS_IN_CORPUS.join(", ")}`);
console.log("  도수 구간:");
let total = 0;
for (const b of ABV_BANDS) {
  const n = COCKTAILS.filter((c) => abvBandOf(c.abv) === b.key).length;
  total += n;
  console.log(`    ${b.ko.padEnd(12)} ${String(n).padStart(2)}종`);
}
if (total !== COCKTAILS.length) {
  console.error(`✗ 도수 구간 합계 ${total} ≠ ${COCKTAILS.length} — 구간에 구멍이 있다`);
  process.exit(1);
}
