#!/usr/bin/env node
/**
 * 분류 축이 **계약에서 오는지** 확인한다 (ISSUE-004 → ISSUE-037).
 *
 * ## 역할이 한 번 바뀌었다
 *
 * 처음에는 프로토타입 `types.ts` 와 계약(`openapi.json`)의 축을 대조했다 —
 * 손으로 쓴 목록과 계약이 어긋나는지 보는 일이었다.
 *
 * 이슈 037 이 `types.ts` 를 **계약 생성물로 대체**하면서 대조할 두 목록이 없어졌다.
 * 축은 이제 한 곳에서만 온다.
 *
 * 그래서 지금 지키는 것은 **전환이 되돌아가지 않는 것**이다:
 *
 * | 확인 | 왜 |
 * |---|---|
 * | `types.ts` 가 축을 직접 선언하지 않는다 | 손으로 쓴 목록이 다시 생기면 또 어긋난다 (`PRIN-T02`) |
 * | 생성물에 축 5종이 있다 | 계약에서 축이 사라지면 화면이 조용히 `any` 가 된다 |
 * | 레이블이 값마다 있다 | 없으면 화면에 슬러그가 그대로 나온다 |
 *
 * 이 스크립트를 지우지 않는 이유: 지우면 `types.ts` 에 `export type BaseSpirit = "진" | …`
 * 를 다시 적는 것을 아무도 못 막는다.
 */
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const typesSrc = readFileSync(resolve(root, "packages/domain/src/types.ts"), "utf8");
const labelsSrc = readFileSync(resolve(root, "packages/domain/src/generated/labels.ts"), "utf8");
const spec = JSON.parse(readFileSync(resolve(root, "apps/api/openapi.json"), "utf8"));
const schemas = spec.components.schemas;

/** 계약이 정본인 분류 축. */
const AXES = ["BaseSpirit", "StyleKey", "FlavorKey", "SweetLevel", "Technique"];

const failures = [];

for (const axis of AXES) {
  const contract = schemas[axis]?.enum;
  if (!contract) {
    failures.push(`${axis}: 계약(openapi.json)에 없다 — 축이 사라졌거나 이름이 바뀌었다`);
    continue;
  }

  // 1. `types.ts` 가 직접 선언하면 안 된다. `= components["schemas"][...]` 만 허용한다.
  const declared = typesSrc.match(new RegExp(`export type ${axis}\\s*=\\s*([^;]+);`));
  if (!declared) {
    failures.push(`${axis}: types.ts 가 내보내지 않는다`);
  } else if (!declared[1].includes('components["schemas"]')) {
    failures.push(
      `${axis}: types.ts 가 직접 선언하고 있다 — 계약 생성물에서 가져와야 한다 (PRIN-T02)\n` +
        `      ${declared[1].trim().slice(0, 80)}`,
    );
  }

  // 2. 레이블이 값마다 있어야 한다. 없으면 화면에 슬러그가 그대로 나간다.
  const labels = schemas[axis]["x-labels"] ?? {};
  const unlabeled = contract.filter((v) => !(v in labels));
  if (unlabeled.length) {
    failures.push(`${axis}: 한국어 레이블이 없는 값 [${unlabeled}]`);
  }

  // 3. 생성된 레이블 파일이 계약과 같은 값을 담고 있어야 한다.
  const missingInFile = contract.filter((v) => !labelsSrc.includes(`${JSON.stringify(v)}:`));
  if (missingInFile.length) {
    failures.push(
      `${axis}: generated/labels.ts 에 없는 값 [${missingInFile}] — npm run generate:types 를 돌린다`,
    );
  }
}

console.log(`분류 축 ${AXES.length}종이 계약에서 온다`);
for (const axis of AXES) {
  const n = schemas[axis]?.enum?.length ?? 0;
  console.log(`  ${axis.padEnd(11)} ${n}종`);
}

if (failures.length) {
  console.error("\n계약과 어긋난다:");
  failures.forEach((f) => console.error(`  ✗ ${f}`));
  console.error(
    "\n분류 축의 정본은 Kotlin 이다 (PRIN-T02). types.ts 에 다시 적지 않는다.\n" +
      "  cd apps/api && ./gradlew generateOpenApiDocs\n" +
      "  npm run generate:types",
  );
  process.exit(1);
}

console.log("\n어긋남 없음");
