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
const checkOnly = process.argv.includes("--check");

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

if (checkOnly) {
  const current = existsSync(out) ? readFileSync(out, "utf8") : "";
  if (current !== generated) {
    console.error(
      `생성 타입이 계약과 다르다.\n` +
        `  npm run generate:types\n` +
        `로 갱신하고 함께 커밋한다. 손으로 고치지 않는다 (PRIN-T02).`,
    );
    process.exit(1);
  }
  console.log("생성 타입이 계약과 일치한다.");
} else {
  writeFileSync(out, generated, "utf8");
  console.log(`생성: ${out}`);
}
