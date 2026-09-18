/**
 * 식약처 응답 한 건을 `distributed_product` 한 행으로 바꾸는 순수 함수들 (#194).
 *
 * 네트워크는 여기 없다 — `fetch-distributed-products.ts` 가 받아서 넘긴다.
 * 그래서 고정 응답으로 시험할 수 있다 (`distributed-product.test.ts`).
 */

export type Source = "mfds_import" | "mfds_domestic";

export interface ProductRow {
  source: Source;
  sourceKey: string;
  nameKo: string;
  nameEn: string | null;
  importerOrMaker: string | null;
  manufacturer: string | null;
  originCountry: string | null;
  foodType: string;
  /** `YYYY-MM-DD`. 수입은 처리일자, 국산은 보고일. 둘 다 없는 건 manual 뿐이다. */
  lastReportedOn: string | null;
  raw: Record<string, unknown>;
}

/**
 * 식품공전의 주류 유형 12종. 사용자 결정(2026-09-18): 위스키·리큐르만이 아니라 전부 받는다.
 * 비교는 [normalizeType] 을 거친 뒤 한다 — "기타 주류" 와 "기타주류" 가 둘 다 온다.
 */
export const LIQUOR_TYPES = [
  "탁주",
  "약주",
  "청주",
  "맥주",
  "과실주",
  "소주",
  "위스키",
  "브랜디",
  "일반증류주",
  "리큐르",
  "기타주류",
  "주정",
] as const;

const LIQUOR_SET = new Set<string>(LIQUOR_TYPES);

/** 띄어쓰기를 지우고 앞뒤 공백을 없앤다. 유형 이름 비교에만 쓴다. */
export function normalizeType(s: string | null | undefined): string {
  return (s ?? "").replace(/\s+/g, "");
}

/** 주류 유형이면 정규화된 이름을, 아니면 null. API 가 부분일치로 돌려준 "위스키향 시럽" 을 거른다. */
export function liquorType(s: string | null | undefined): string | null {
  const t = normalizeType(s);
  return LIQUOR_SET.has(t) ? t : null;
}

/**
 * data.go.kr 응답을 JSON 으로 읽는다. 한글표시사항(KORLABEL) 안에 개행·탭이 **이스케이프 없이**
 * 그대로 들어 있어 `JSON.parse` 가 거부한다. 제어문자를 전부 띄어쓰기로 바꾼 뒤 읽는다 —
 * 구조 사이의 개행도 띄어쓰기가 되므로 JSON 으로는 여전히 맞다. 표시사항 줄바꿈은 잃는다.
 *
 * 또 한 건이 `{ item: {...} }` 로 한 겹 싸여 온다. 벗겨서 돌려준다.
 */
export function parseItems(text: string): { totalCount: number; items: Record<string, unknown>[]; resultCode: string | null } {
  const d = JSON.parse(text.replace(/[\u0000-\u001f]/g, " ")) as {
    header?: { resultCode?: string };
    body?: { totalCount?: number | string; items?: unknown };
    OpenAPI_ServiceResponse?: { cmmMsgHeader?: { errMsg?: string; returnReasonCode?: string } };
  };
  // 게이트웨이가 낸 오류는 header 가 아니라 이 모양으로 온다 (05 = 서비스 연결실패, 22 = 일일 한도).
  const gw = d.OpenAPI_ServiceResponse?.cmmMsgHeader;
  if (gw) throw new Error(`게이트웨이 ${gw.returnReasonCode ?? "?"} ${gw.errMsg ?? ""}`);
  const raw = d.body?.items;
  const list: unknown[] = Array.isArray(raw)
    ? raw
    : raw && typeof raw === "object" && Array.isArray((raw as { item?: unknown }).item)
      ? ((raw as { item: unknown[] }).item)
      : [];
  const items = list.map((x) => {
    const o = x as Record<string, unknown>;
    return o && typeof o.item === "object" && o.item !== null ? (o.item as Record<string, unknown>) : o;
  });
  return { totalCount: Number(d.body?.totalCount ?? 0), items, resultCode: d.header?.resultCode ?? null };
}

function str(v: unknown): string | null {
  if (v == null) return null;
  const s = String(v).trim();
  return s.length ? s : null;
}

/** `20250312` · `2025-03-12` · `2025-03-12 00:00:00` → `2025-03-12`. 그 외는 null. */
export function toIsoDate(v: unknown): string | null {
  const s = str(v);
  if (!s) return null;
  const m = s.match(/^(\d{4})-?(\d{2})-?(\d{2})/);
  return m ? `${m[1]}-${m[2]}-${m[3]}` : null;
}

/**
 * 「수입식품 수입신고별 한글표시사항」(data.go.kr 15110213, getIprtFoodPrdtKoreanLabelingText) 한 건.
 * 필드: BSN_OFC_NAME(수입업체) · PRDUCT_KOREAN_NM · PRDUCT_NM(영문) · ITM_NM(품목) · PROCS_DTM(처리일자)
 *       · OVSMNFST_NM(해외제조업소) · MNF_NTNCD_NM(제조국) · XPORT_NTNCD_NM(수출국) · KORLABEL · IRDNT_NM.
 *
 * 「수입식품 제품DB」(15073949) 를 쓰지 않는 이유: 수입업체와 처리일자가 없다.
 * #195 가 "누가 언제 들여왔나"로 유통 여부를 제안하므로 그 둘이 있는 쪽을 쓴다.
 *
 * 관리번호가 없어 **수입업체 + 제품명(한글)** 을 키로 삼는다. 같은 제품을 여러 번 신고하면
 * 여러 건이 오고, [dedupe] 가 처리일자가 가장 늦은 한 건만 남긴다.
 */
export function fromLabelingRecord(rec: Record<string, unknown>): ProductRow | null {
  const foodType = liquorType(str(rec.ITM_NM));
  const nameKo = str(rec.PRDUCT_KOREAN_NM);
  if (!foodType || !nameKo) return null;

  const importer = str(rec.BSN_OFC_NAME);
  // raw 에는 컬럼으로 옮기지 않은 것만 남긴다. 표시사항 원문(KORLABEL)·원재료명은 4만 건이면
  // 그 둘만 50MB 라 뺀다. 필요해지면 .cache 의 원본 쪽에서 다시 꺼낸다.
  const slim = {
    DCL_PRDUCT_SE_CD_NM: rec.DCL_PRDUCT_SE_CD_NM,
    XPORT_NTNCD_NM: rec.XPORT_NTNCD_NM,
    EXPIRDE_BEGIN_DTM: rec.EXPIRDE_BEGIN_DTM,
    EXPIRDE_END_DTM: rec.EXPIRDE_END_DTM,
  };
  return {
    source: "mfds_import",
    sourceKey: [importer ?? "", nameKo].join("|"),
    nameKo,
    nameEn: str(rec.PRDUCT_NM),
    importerOrMaker: importer,
    manufacturer: str(rec.OVSMNFST_NM),
    originCountry: str(rec.MNF_NTNCD_NM),
    foodType,
    lastReportedOn: toIsoDate(rec.PROCS_DTM),
    raw: slim,
  };
}

/**
 * 「식품(첨가물)품목제조보고」(foodsafetykorea I1250) 한 건.
 * 필드: PRDLST_REPORT_NO · PRDLST_NM(제품명) · BSSH_NM(업소명) · PRDLST_DCNM(식품유형) · PRMS_DT(보고일).
 * 이름이 헷갈린다 — 여기서 PRDLST_NM 은 **제품명**이고 유형은 PRDLST_DCNM 이다. 수입 API 와 반대다.
 */
export function fromDomesticRecord(rec: Record<string, unknown>): ProductRow | null {
  const foodType = liquorType(str(rec.PRDLST_DCNM));
  const nameKo = str(rec.PRDLST_NM);
  if (!foodType || !nameKo) return null;

  const maker = str(rec.BSSH_NM);
  const sourceKey = str(rec.PRDLST_REPORT_NO) ?? [maker ?? "", nameKo].join("|");

  return {
    source: "mfds_domestic",
    sourceKey,
    nameKo,
    nameEn: null,
    importerOrMaker: maker,
    manufacturer: null,
    originCountry: "대한민국",
    foodType,
    lastReportedOn: toIsoDate(rec.PRMS_DT),
    raw: rec,
  };
}

/**
 * (source, sourceKey) 로 겹치는 건을 하나로 줄인다. 신고일이 더 늦은 쪽을 남기고,
 * 같으면 나중에 온 것을 남긴다. DB 의 uq_distributed_product__source_key 와 같은 기준이다.
 */
export function dedupe(rows: ProductRow[]): ProductRow[] {
  const byKey = new Map<string, ProductRow>();
  for (const r of rows) {
    const k = `${r.source}\u0000${r.sourceKey}`;
    const prev = byKey.get(k);
    if (!prev || (r.lastReportedOn ?? "") >= (prev.lastReportedOn ?? "")) byKey.set(k, r);
  }
  return [...byKey.values()];
}

function sql(s: string | null): string {
  return s == null ? "NULL" : "'" + s.replace(/'/g, "''") + "'";
}

function jsonb(value: unknown): string {
  return sql(JSON.stringify(value)) + "::jsonb";
}

/**
 * repeatable 시드 본문. **지우지 않고 덧쓴다** (`ON CONFLICT DO UPDATE`).
 *
 * 지우고 다시 넣으면 id 가 바뀌어 `ingredient_product_match` 의 승인이 통째로 날아간다 (#195).
 * 출처에서 사라진 제품도 남긴다 — 이 데이터가 말하는 건 "신고가 있었다" 이고, `last_reported_on` 이
 * 얼마나 오래됐는지를 말한다. `manual` 은 어드민이 넣은 것이라 애초에 건드리지 않는다.
 */
export function toSeedSql(rows: ProductRow[], fetchedOn: string): string {
  const lines: string[] = [
    "-- #194 — 식약처 신고 기준 국내 유통 술 시드 (repeatable).",
    "--",
    "-- ⚠️ 손으로 고치지 않는다. scripts/fetch-distributed-products.ts 를 돌려 다시 뽑는다.",
    `--    받은 날: ${fetchedOn} · ${rows.length}건`,
    "",
    "-- 지우지 않고 덧쓴다. 지우면 id 가 바뀌어 ingredient_product_match 의 승인이 날아간다 (#195).",
    "",
  ];
  // 동시 수집이라 받은 순서가 매번 다르다. 키로 정렬해 두 번 뽑아도 같은 파일이 나오게 한다.
  const sorted = [...rows].sort((a, b) =>
    a.source === b.source ? (a.sourceKey < b.sourceKey ? -1 : a.sourceKey > b.sourceKey ? 1 : 0) : a.source < b.source ? -1 : 1,
  );
  // 한 INSERT 에 여러 행 — 4만 건을 한 줄씩 넣으면 파일도 크고 적재도 느리다.
  const BATCH = 500;
  for (let i = 0; i < sorted.length; i += BATCH) {
    lines.push(
      "INSERT INTO distributed_product",
      "  (source, source_key, name_ko, name_en, importer_or_maker, manufacturer, origin_country, food_type, last_reported_on, raw)",
      "VALUES",
    );
    const chunk = sorted.slice(i, i + BATCH);
    chunk.forEach((r, j) => {
      lines.push(
        `(${sql(r.source)}, ${sql(r.sourceKey)}, ${sql(r.nameKo)}, ${sql(r.nameEn)}, ${sql(r.importerOrMaker)}, ` +
          `${sql(r.manufacturer)}, ${sql(r.originCountry)}, ${sql(r.foodType)}, ${sql(r.lastReportedOn)}, ${jsonb(r.raw)})` +
          (j < chunk.length - 1 ? "," : ""),
      );
    });
    lines.push(
      "ON CONFLICT (source, source_key) DO UPDATE SET",
      "  name_en = EXCLUDED.name_en, importer_or_maker = EXCLUDED.importer_or_maker,",
      "  manufacturer = EXCLUDED.manufacturer, origin_country = EXCLUDED.origin_country,",
      "  food_type = EXCLUDED.food_type, raw = EXCLUDED.raw,",
      "  last_reported_on = GREATEST(distributed_product.last_reported_on, EXCLUDED.last_reported_on);",
      "",
    );
  }
  return lines.join("\n");
}
