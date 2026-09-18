/**
 * 식약처에 신고된 술을 받아 `R__seed_04_distributed_product.sql` 로 뽑는다 (#194).
 *
 * ⚠️ 결과 파일을 손으로 고치지 않는다 — 여기를 고치고 다시 돌린다:
 *
 *     DATA_GO_KR_KEY=… FOODSAFETY_KEY=… npx tsx scripts/fetch-distributed-products.ts
 *
 * | 변수 | 출처 | 받는 것 |
 * |---|---|---|
 * | `DATA_GO_KR_KEY` | 공공데이터포털 인증키 | 수입 술 — 「수입식품 수입신고별 한글표시사항」 15110213 |
 * | `FOODSAFETY_KEY` | 식약처 데이터활용서비스 키 | 국산 술 — 「식품(첨가물)품목제조보고」 I1250 |
 *
 * 둘 중 하나만 있어도 돈다 — 없는 쪽은 건너뛰고 그렇게 적는다.
 *
 * ## 수입 — 전부 받아서 거른다
 *
 * 한글표시사항 API 는 품목·처리일자·제품구분 파라미터를 **전부 무시**하고(2026-09-18 확인),
 * 제품명 검색은 서버가 시간 초과를 낸다. 한 쪽은 최대 500건, 한 번 호출에 약 40초가 걸리고
 * 전체는 약 109만 건(2,181쪽)이다. 동시에 여러 쪽을 받으면 호출당 시간이 늘지 않아서
 * `CONCURRENCY`(기본 10) 개씩 받는다 — 약 2.5시간. 하루 한도(1만 번) 안이다.
 *
 * 받은 쪽은 `.cache/distributed-product/page-N.json` 에 남긴다. 중간에 죽어도 받은 쪽은 다시 안 받는다.
 * 처음부터 다시 받으려면 그 폴더를 지운다.
 *
 * 국산은 유형 이름으로 하나씩 조회한다(부분일치라 "위스키향 시럽" 이 섞여 온다).
 * 거르는 규칙은 `lib/distributed-product.ts` 다.
 *
 * 수입 술 이름은 "수입식품 제품DB"(15073949) 에도 있지만 수입업체·처리일자가 없고
 * `위스키` 검색이 1건뿐이라 쓰지 않는다.
 */
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import {
  LIQUOR_TYPES,
  dedupe,
  fromDomesticRecord,
  fromLabelingRecord,
  parseItems,
  toSeedSql,
  type ProductRow,
} from "./lib/distributed-product";

const OUT = "apps/api/src/main/resources/db/migration/R__seed_04_distributed_product.sql";
const CACHE = ".cache/distributed-product";
const IMPORT_PAGE = 500; // API 상한. 넘기면 resultCode 11.
const DOMESTIC_PAGE = 1000;
const CONCURRENCY = Number(process.env.CONCURRENCY ?? 10);
const RETRIES = 4;

type Rec = Record<string, unknown>;

/* ─────────────────  수입 — data.go.kr 한글표시사항  ───────────────── */

const IMPORT_URL =
  "https://apis.data.go.kr/1471000/IprtFoodPrdtKoreanLabelingText/getIprtFoodPrdtKoreanLabelingText";

function importUrl(key: string, page: number): string {
  return `${IMPORT_URL}?serviceKey=${encodeURIComponent(key)}&type=json&pageNo=${page}&numOfRows=${IMPORT_PAGE}`;
}

/** 한 쪽을 받는다. 캐시에 있으면 그것을 쓴다. 게이트웨이 시간 초과(05)가 잦아 몇 번 다시 부른다. */
async function importPage(key: string, page: number): Promise<{ total: number; items: Rec[] }> {
  const file = join(CACHE, `page-${page}.json`);
  if (existsSync(file)) {
    const { totalCount, items } = parseItems(readFileSync(file, "utf8"));
    return { total: totalCount, items };
  }
  let lastErr: unknown;
  for (let attempt = 1; attempt <= RETRIES; attempt++) {
    try {
      const text = await getText(importUrl(key, page));
      const { totalCount, items, resultCode } = parseItems(text);
      if (resultCode && resultCode !== "00") throw new Error(`결과코드 ${resultCode}`);
      writeFileSync(file, text);
      return { total: totalCount, items };
    } catch (e) {
      lastErr = e;
      console.warn(`  수입 · ${page}쪽 ${attempt}번째 실패: ${String(e).slice(0, 120)}`);
      await new Promise((r) => setTimeout(r, 5_000 * attempt));
    }
  }
  throw new Error(`수입 · ${page}쪽을 ${RETRIES}번 실패했다: ${String(lastErr)}`);
}

async function fetchImport(key: string): Promise<ProductRow[]> {
  mkdirSync(CACHE, { recursive: true });
  const first = await importPage(key, 1);
  const pages = Math.ceil(first.total / IMPORT_PAGE);
  console.log(`  수입 · 전체 ${first.total}건 = ${pages}쪽, 동시 ${CONCURRENCY}`);

  const rows: ProductRow[] = [];
  let received = 0;
  const started = Date.now();
  const collect = (items: Rec[]) => {
    for (const rec of items) {
      const row = fromLabelingRecord(rec);
      if (row) rows.push(row);
    }
    received += 1;
    if (received % 50 === 0) {
      const min = ((Date.now() - started) / 60_000).toFixed(1);
      console.log(`  수입 · ${received}/${pages}쪽 (${min}분) · 주류 ${rows.length}건`);
    }
  };
  collect(first.items);

  let next = 2;
  const worker = async () => {
    while (next <= pages) {
      const page = next++;
      collect((await importPage(key, page)).items);
    }
  };
  await Promise.all(Array.from({ length: CONCURRENCY }, worker));
  console.log(`  수입 · 끝. 주류 ${rows.length}건`);
  return rows;
}

/* ─────────────────  국산 — foodsafetykorea  ───────────────── */

async function fetchDomestic(key: string): Promise<ProductRow[]> {
  const rows: ProductRow[] = [];
  for (const type of LIQUOR_TYPES) {
    let start = 1;
    let total = Infinity;
    while (start <= total) {
      const end = start + DOMESTIC_PAGE - 1;
      const url =
        `http://openapi.foodsafetykorea.go.kr/api/${encodeURIComponent(key)}/I1250/json/${start}/${end}` +
        `/PRDLST_DCNM=${encodeURIComponent(type)}`;
      const body = (await getJson(url)) as {
        I1250?: { total_count?: string; row?: Rec[]; RESULT?: { CODE?: string; MSG?: string } };
      };
      const svc = body.I1250;
      const code = svc?.RESULT?.CODE;
      if (code && code !== "INFO-000") {
        // INFO-200 은 "해당하는 데이터가 없습니다" — 오류가 아니다.
        if (code === "INFO-200") break;
        throw new Error(`품목제조보고 ${type}: ${code} ${svc?.RESULT?.MSG}`);
      }
      total = Number(svc?.total_count ?? 0);
      const list = svc?.row ?? [];
      if (list.length === 0) break;
      for (const rec of list) {
        const row = fromDomesticRecord(rec);
        if (row) rows.push(row);
      }
      start = end + 1;
    }
    console.log(`  국산 · ${type}: 전체 ${total === Infinity ? 0 : total}건`);
  }
  return rows;
}

/* ─────────────────  공통  ───────────────── */

async function getText(url: string): Promise<string> {
  const res = await fetch(url, { signal: AbortSignal.timeout(180_000) });
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
  return res.text();
}

async function getJson(url: string): Promise<unknown> {
  return JSON.parse((await getText(url)).replace(/[\u0000-\u001f]/g, " "));
}

async function main() {
  const importKey = process.env.DATA_GO_KR_KEY;
  const domesticKey = process.env.FOODSAFETY_KEY;

  const rows: ProductRow[] = [];
  if (importKey) {
    console.log("수입 술 — 한글표시사항");
    rows.push(...(await fetchImport(importKey)));
  } else {
    console.log("DATA_GO_KR_KEY 가 없어 수입 술은 건너뛴다.");
  }
  if (domesticKey) {
    console.log("국산 술 — 품목제조보고");
    try {
      rows.push(...(await fetchDomestic(domesticKey)));
    } catch (e) {
      // 국산이 막혀도 수입 결과는 살린다. 09~19시 제한 등.
      console.warn(`국산 술을 못 받았다. 수입만으로 시드를 쓴다: ${String(e).slice(0, 160)}`);
    }
  } else {
    console.log("FOODSAFETY_KEY 가 없어 국산 술은 건너뛴다.");
  }

  const unique = dedupe(rows);
  const fetchedOn = new Date().toISOString().slice(0, 10);
  writeFileSync(OUT, toSeedSql(unique, fetchedOn) + "\n");

  const byType = new Map<string, number>();
  for (const r of unique) {
    const k = `${r.source}/${r.foodType}`;
    byType.set(k, (byType.get(k) ?? 0) + 1);
  }
  console.log(`${OUT}: ${unique.length}건 (받은 ${rows.length}건에서 중복 ${rows.length - unique.length}건 제거)`);
  for (const [k, n] of [...byType.entries()].sort()) console.log(`  ${k}: ${n}`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
