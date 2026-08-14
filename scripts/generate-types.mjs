#!/usr/bin/env node
/**
 * OpenAPI → TS 타입 (ISSUE-004, `PRIN-T02`).
 *
 * 정본은 `apps/api/openapi.json` 이고 이 스크립트는 그것을 옮겨 적을 뿐이다.
 * **손으로 쓴 TS DTO 를 두지 않는다.** 언어가 둘이라 양쪽을 손으로 맞추면 반드시 어긋난다.
 *
 *   npm run generate:types          생성
 *   npm run generate:types -- --check   드리프트만 확인 (CI)
 */
import { execFileSync } from "node:child_process";
import { readFileSync, writeFileSync, existsSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const spec = resolve(root, "apps/api/openapi.json");
const out = resolve(root, "packages/domain/src/generated/api.ts");
const labelsOut = resolve(root, "packages/domain/src/generated/labels.ts");
const checkOnly = process.argv.includes("--check");

/**
 * 한국어 레이블을 갖는 분류 축 (ISSUE-037).
 *
 * 계약이 `x-labels` 확장으로 레이블을 함께 내보낸다 — `openapi-typescript` 는 타입만 뽑고
 * 확장을 버리므로 여기서 따로 옮겨 적는다.
 *
 * **손으로 쓴 레이블 맵을 두지 않는다** (`PRIN-T02`). 정본은 Kotlin 이고,
 * 손으로 쓰면 축이 늘 때 한쪽만 늘어난다.
 */
const LABELED_AXES = ["BaseSpirit", "StyleKey", "FlavorKey", "SweetLevel", "Technique"];

if (!existsSync(spec)) {
  console.error(
    `계약이 없다: ${spec}\n` +
      `  cd apps/api && ./gradlew generateOpenApiDocs\n` +
      `로 먼저 만든다 (PRIN-T02).`,
  );
  process.exit(1);
}

const HEADER = `/* eslint-disable */
/**
 * AUTO-GENERATED — DO NOT EDIT
 *
 * 정본은 apps/api/openapi.json 이고, 그 정본은 Kotlin 코드다 (PRIN-T02).
 * 이 파일을 손으로 고치면 CI 가 되돌린다.
 *
 *   cd apps/api && ./gradlew generateOpenApiDocs   계약 갱신
 *   npm run generate:types                          이 파일 갱신
 */

`;

const generated =
  HEADER +
  execFileSync(
    "npx",
    ["--no-install", "openapi-typescript", spec, "--enum", "false"],
    { cwd: root, encoding: "utf8", maxBuffer: 32 * 1024 * 1024 },
  );

const labels = generateLabels();

if (checkOnly) {
  const stale = [
    [out, generated],
    [labelsOut, labels],
  ].filter(([path, expected]) => (existsSync(path) ? readFileSync(path, "utf8") : "") !== expected);

  if (stale.length > 0) {
    console.error(
      `생성물이 계약과 다르다: ${stale.map(([p]) => p.replace(root + "/", "")).join(", ")}\n` +
        `  npm run generate:types\n` +
        `로 갱신하고 함께 커밋한다. 손으로 고치지 않는다 (PRIN-T02).`,
    );
    process.exit(1);
  }
  console.log("생성물이 계약과 일치한다.");
} else {
  writeFileSync(out, generated, "utf8");
  writeFileSync(labelsOut, labels, "utf8");
  console.log(`생성: ${out}\n생성: ${labelsOut}`);
}

/**
 * `x-labels` 를 TS 상수로 옮긴다.
 *
 * 화면이 슬러그를 받고 한국어를 보여 줘야 하는데, 그 대응표의 정본은 Kotlin 이다.
 * API 응답(`labelKo`)으로 매번 실어 나르는 방법도 있지만 정적인 값이라 낭비이고,
 * **화면이 API 를 부르기 전에도 레이블이 필요하다** (카테고리 링크·필터 칩).
 */
function generateLabels() {
  const contract = JSON.parse(readFileSync(spec, "utf8"));
  const schemas = contract.components?.schemas ?? {};
  const blocks = [];

  for (const axis of LABELED_AXES) {
    const schema = schemas[axis];
    if (!schema) throw new Error(`계약에 ${axis} 이 없다 — 축이 사라졌거나 이름이 바뀌었다`);

    const values = schema.enum ?? [];
    const axisLabels = schema["x-labels"] ?? {};

    const missing = values.filter((v) => !(v in axisLabels));
    if (missing.length > 0) {
      // 레이블이 없으면 화면에 슬러그가 그대로 나온다. 조용히 넘기지 않는다.
      throw new Error(`${axis} 에 레이블이 없는 값: ${missing.join(", ")}`);
    }

    const entries = values.map((v) => `  ${JSON.stringify(v)}: ${JSON.stringify(axisLabels[v])},`);
    blocks.push(
      `/** ${schema.description ?? axis} */\n` +
        `export const ${labelConstName(axis)} = {\n${entries.join("\n")}\n} as const;`,
    );
  }

  return `${HEADER}${blocks.join("\n\n")}\n`;
}

/** `BaseSpirit` → `BASE_SPIRIT_LABELS`. */
function labelConstName(axis) {
  return `${axis.replace(/([a-z0-9])([A-Z])/g, "$1_$2").toUpperCase()}_LABELS`;
}
