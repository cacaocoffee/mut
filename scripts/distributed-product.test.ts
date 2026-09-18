/**
 * #194 RED 3·4 의 스크립트 쪽 — 고정 응답으로 변환 규칙을 시험한다.
 *
 *     npx tsx --test scripts/distributed-product.test.ts
 */
import assert from "node:assert/strict";
import { test } from "node:test";
import {
  dedupe,
  fromDomesticRecord,
  fromLabelingRecord,
  liquorType,
  parseItems,
  toIsoDate,
  toSeedSql,
} from "./lib/distributed-product";

test("RED3 - 식품유형이 주류가 아닌 건은 넣지 않는다", () => {
  assert.equal(fromLabelingRecord({ PRDUCT_KOREAN_NM: "위스키향 시럽", ITM_NM: "기타가공품", BSN_OFC_NAME: "ㅇㅇ" }), null);
  assert.equal(fromDomesticRecord({ PRDLST_NM: "하이볼 믹서", PRDLST_DCNM: "탄산음료", BSSH_NM: "ㅇㅇ" }), null);
  assert.equal(liquorType("위스키향 시럽"), null);
});

test("주류 유형 12종은 띄어쓰기가 달라도 같은 유형이다", () => {
  assert.equal(liquorType("기타 주류"), "기타주류");
  assert.equal(liquorType(" 일반증류주 "), "일반증류주");
  assert.equal(liquorType("리큐르"), "리큐르");
});

test("수입 건 — 수입업체+제품명이 source_key 고, 수입업체·해외제조업소·제조국·처리일자를 옮긴다", () => {
  const row = fromLabelingRecord({
    DCL_PRDUCT_SE_CD_NM: "가공식품",
    BSN_OFC_NAME: "트랜스베버리지(주)",
    PRDUCT_KOREAN_NM: "깜빠리",
    PRDUCT_NM: "CAMPARI BITTER",
    ITM_NM: "리큐르",
    PROCS_DTM: "2025-03-12",
    OVSMNFST_NM: "DAVIDE CAMPARI-MILANO N.V.",
    MNF_NTNCD_NM: "이탈리아",
    XPORT_NTNCD_NM: "이탈리아",
  });
  assert.ok(row);
  assert.equal(row.source, "mfds_import");
  assert.equal(row.sourceKey, "트랜스베버리지(주)|깜빠리");
  assert.equal(row.nameKo, "깜빠리");
  assert.equal(row.nameEn, "CAMPARI BITTER");
  assert.equal(row.importerOrMaker, "트랜스베버리지(주)");
  assert.equal(row.manufacturer, "DAVIDE CAMPARI-MILANO N.V.");
  assert.equal(row.originCountry, "이탈리아");
  assert.equal(row.foodType, "리큐르");
  assert.equal(row.lastReportedOn, "2025-03-12");
});

test("수입 건 — 같은 제품을 여러 번 신고하면 처리일자가 늦은 한 건만 남는다", () => {
  const mk = (d: string) =>
    fromLabelingRecord({ BSN_OFC_NAME: "A", PRDUCT_KOREAN_NM: "라프로익 10년", ITM_NM: "위스키", PROCS_DTM: d })!;
  const rows = dedupe([mk("2024-01-05"), mk("2025-06-01"), mk("2023-11-30")]);
  assert.equal(rows.length, 1);
  assert.equal(rows[0].lastReportedOn, "2025-06-01");
});

test("국산 건 — 보고번호가 source_key 고, 업소명이 importer_or_maker, 보고일이 last_reported_on 이다", () => {
  const row = fromDomesticRecord({
    PRDLST_REPORT_NO: "20240012345",
    PRDLST_NM: "화요 41",
    BSSH_NM: "화요",
    PRDLST_DCNM: "일반증류주",
    PRMS_DT: "20240312",
  });
  assert.ok(row);
  assert.equal(row.source, "mfds_domestic");
  assert.equal(row.sourceKey, "20240012345");
  assert.equal(row.importerOrMaker, "화요");
  assert.equal(row.originCountry, "대한민국");
  assert.equal(row.lastReportedOn, "2024-03-12");
});

test("data.go.kr 응답 — 표시사항 안의 생 개행을 견디고 {item:{…}} 한 겹을 벗긴다", () => {
  const text =
    '{"header":{"resultCode":"00"},"body":{"pageNo":1,"totalCount":2,"items":[' +
    '{"item":{"PRDUCT_KOREAN_NM":"깜빠리","KORLABEL":"제품명 : 깜빠리\r\n원산지 : 이탈리아\t"}},' +
    '{"item":{"PRDUCT_KOREAN_NM":"아페롤","KORLABEL":""}}]}}';
  const { totalCount, items, resultCode } = parseItems(text);
  assert.equal(resultCode, "00");
  assert.equal(totalCount, 2);
  assert.deepEqual(items.map((i) => i.PRDUCT_KOREAN_NM), ["깜빠리", "아페롤"]);
  assert.equal(items[0].KORLABEL, "제품명 : 깜빠리  원산지 : 이탈리아 ");
});

test("data.go.kr 게이트웨이 오류는 header 없이 오고, 던진다", () => {
  const text = '{"OpenAPI_ServiceResponse":{"cmmMsgHeader":{"errMsg":"SERVICETIMEOUT_ERROR","returnReasonCode":"05"}}}';
  assert.throws(() => parseItems(text), /게이트웨이 05 SERVICETIMEOUT_ERROR/);
});

test("날짜 표기 세 가지를 YYYY-MM-DD 로 맞춘다", () => {
  assert.equal(toIsoDate("20250312"), "2025-03-12");
  assert.equal(toIsoDate("2025-03-12"), "2025-03-12");
  assert.equal(toIsoDate("2025-03-12 00:00:00"), "2025-03-12");
  assert.equal(toIsoDate("어제"), null);
  assert.equal(toIsoDate(""), null);
});

test("RED4 - 같은 건을 두 번 받아도 한 행이다. 신고일이 늦은 쪽이 남는다", () => {
  const older = fromDomesticRecord({ PRDLST_REPORT_NO: "1", PRDLST_NM: "A", BSSH_NM: "x", PRDLST_DCNM: "소주", PRMS_DT: "20230101" })!;
  const newer = fromDomesticRecord({ PRDLST_REPORT_NO: "1", PRDLST_NM: "A", BSSH_NM: "x", PRDLST_DCNM: "소주", PRMS_DT: "20250101" })!;
  const other = fromLabelingRecord({ BSN_OFC_NAME: "x", PRDUCT_KOREAN_NM: "A", ITM_NM: "소주" })!;

  const rows = dedupe([newer, older, other]);
  assert.equal(rows.length, 2);
  assert.equal(rows.find((r) => r.source === "mfds_domestic")?.lastReportedOn, "2025-01-01");
});

test("시드 SQL 은 지우지 않고 덧쓴다 — 승인된 매핑이 살아남아야 한다. 작은따옴표는 이스케이프한다", () => {
  const row = fromLabelingRecord({ BSN_OFC_NAME: "k", PRDUCT_KOREAN_NM: "O'HARA'S", ITM_NM: "맥주" })!;
  const out = toSeedSql([row], "2026-09-18");
  assert.doesNotMatch(out, /DELETE FROM/);
  assert.match(out, /'O''HARA''S'/);
  assert.match(out, /ON CONFLICT \(source, source_key\) DO UPDATE SET/);
  assert.match(out, /GREATEST\(distributed_product\.last_reported_on, EXCLUDED\.last_reported_on\)/);
  assert.match(out, /손으로 고치지 않는다/);
});
