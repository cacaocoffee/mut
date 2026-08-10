#!/usr/bin/env node
/**
 * 프로토타입 `types.ts` 와 계약(`openapi.json`)의 분류 축이 어긋나는지 본다 (ISSUE-004).
 *
 * 이슈 037 이 `types.ts` 를 생성물로 **대체**한다. 그때 어긋남을 발견하면 화면 네 개를 동시에
 * 고쳐야 한다 — 전환 전에 여기서 먼저 잡는다.
 *
 * ## 알려진 차이는 등록한다
 *
 * 표현이 달라지는 것은 의도된 전환이다. 그런 항목은 [KNOWN] 에 **근거와 함께** 적는다.
 * 목록에 없는 차이는 실패다. 별도 억제 파일을 두지 않는 이유는 억제가 쌓이는 것이
 * 리뷰에서 보여야 하기 때문이다.
 */
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const typesSrc = readFileSync(resolve(root, "packages/domain/src/types.ts"), "utf8");
const spec = JSON.parse(readFileSync(resolve(root, "apps/api/openapi.json"), "utf8"));
const schemas = spec.components.schemas;

/**
 * 프로토타입과 계약의 표현이 다른 축. **전환(037)이 처리할 목록이다.**
 * 여기 없는 차이가 나오면 스펙과 프로토타입 중 하나가 틀린 것이다.
 */
const KNOWN = {
  Technique: {
    why: "types.ts 는 PascalCase 리터럴, 계약은 슬러그. SPEC-06 §3.1 의 method 컬럼이 슬러그를 저장한다",
    gap: "G-23",
    normalize: (v) => v.toLowerCase(),
  },
  SweetLevel: {
    why: "types.ts 는 0~3 숫자, 계약은 dry·semi_dry·semi_sweet·sweet. 숫자는 의미가 위치에 숨어 마이그레이션에서 뒤집힌다",
    gap: "G-23",
    // 순서로만 대응된다. 값 자체는 비교할 수 없다.
    compareBy: "count",
  },
};

/** `export type X = "a" | "b";` 에서 멤버를 뽑는다. */
function unionMembers(name) {
  const m = typesSrc.match(new RegExp(`export type ${name}\\s*=([^;]+);`));
  if (!m) throw new Error(`types.ts 에 ${name} 이 없다`);
  return [...m[1].matchAll(/"([^"]+)"|\b(\d+)\b/g)].map((x) => x[1] ?? x[2]);
}

/** `BASE_SLUGS` 의 값(슬러그)들. 기주는 한국어가 타입 값이고 슬러그가 맵이다. */
function baseSlugs() {
  const m = typesSrc.match(/BASE_SLUGS[^=]*=\s*\{([\s\S]*?)\n\};/);
  if (!m) throw new Error("types.ts 에 BASE_SLUGS 가 없다");
  return [...m[1].matchAll(/:\s*"([^"]+)"/g)].map((x) => x[1]);
}

const AXES = [
  { name: "BaseSpirit", prototype: baseSlugs() },
  { name: "StyleKey", prototype: unionMembers("StyleKey") },
  { name: "FlavorKey", prototype: unionMembers("FlavorKey") },
  { name: "Technique", prototype: unionMembers("Technique") },
  { name: "SweetLevel", prototype: unionMembers("SweetLevel") },
];

const failures = [];
const accepted = [];

for (const { name, prototype } of AXES) {
  const contract = schemas[name]?.enum;
  if (!contract) {
    failures.push(`${name}: 계약(openapi.json)에 없다`);
    continue;
  }

  const known = KNOWN[name];
  const left = known?.normalize ? prototype.map(known.normalize) : prototype;

  if (known?.compareBy === "count") {
    if (left.length !== contract.length) {
      failures.push(
        `${name}: 개수가 다르다 — 프로토타입 ${left.length}종 / 계약 ${contract.length}종`,
      );
    } else {
      accepted.push(`${name} (${known.gap}) — ${known.why}`);
    }
    continue;
  }

  const missing = left.filter((v) => !contract.includes(v));
  const extra = contract.filter((v) => !left.includes(v));

  if (missing.length || extra.length) {
    failures.push(
      `${name}: 프로토타입에만 [${missing}] / 계약에만 [${extra}]`,
    );
  } else if (known) {
    accepted.push(`${name} (${known.gap}) — ${known.why}`);
  }
}

console.log(`분류 축 ${AXES.length}종 대조`);
for (const { name, prototype } of AXES) {
  console.log(`  ${name.padEnd(11)} ${prototype.length}종`);
}

if (accepted.length) {
  console.log("\n전환 시 표현이 바뀌는 축 (이슈 037):");
  accepted.forEach((a) => console.log(`  · ${a}`));
}

if (failures.length) {
  console.error("\n계약과 프로토타입이 어긋난다:");
  failures.forEach((f) => console.error(`  ✗ ${f}`));
  console.error(
    "\n스펙(SPEC-06 · ADR-0002)이 정본이다. 코드를 몰래 맞추지 말고 GAPS.md 에 올린다.",
  );
  process.exit(1);
}

console.log("\n✓ 어긋남 없음");
