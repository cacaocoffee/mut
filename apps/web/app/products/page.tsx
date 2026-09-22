import type { Metadata } from "next";
import { PageLinks } from "@/components/page-links";
import { products } from "@/lib/api";
import { PRODUCTS_PATH } from "@/lib/routes";

/**
 * 국내 유통 술 — 공개 목록 (#205 · G-41 · `FR-INGREDIENT-002`).
 *
 * "이 술 국내에 들어오나" 에 답하는 화면. 식약처 수입신고 기준이라 **"신고가 있었다"** 까지만
 * 말한다 — 재고·판매 중단은 모른다. 그 말을 표 위에 그대로 적는다.
 *
 * ## 색인은 첫 화면만
 *
 * `/products` 는 색인한다(사이트맵에도 있다). 검색어·유형·페이지가 붙은 주소는 `noindex` —
 * 같은 데이터의 조합이 수만 개라 크롤 예산만 먹는다 (탐색 화면과 같은 규칙, SPEC-05 §4).
 *
 * ## 링크·가격이 없다
 *
 * 제품명·수입사는 사실 정보라 낸다. 구매 링크·협찬·가격은 `NFR-L-05`(주류광고 자문) 뒤다 —
 * 계약(`DistributedProductItem`)에 그 필드가 없다.
 */
export const dynamic = "force-dynamic";

type Params = { q?: string; importer?: string; foodType?: string; page?: string };

export async function generateMetadata({ searchParams }: { searchParams: Promise<Params> }): Promise<Metadata> {
  const sp = await searchParams;
  const filtered = Boolean(sp.q || sp.importer || sp.foodType || sp.page);
  return {
    title: "국내 유통 술",
    description: "식약처 수입신고 기준으로 국내에 들어온 술을 제품명·수입사·유형으로 찾습니다.",
    robots: filtered ? { index: false, follow: true } : undefined,
    alternates: { canonical: PRODUCTS_PATH },
  };
}

export default async function ProductsPage({ searchParams }: { searchParams: Promise<Params> }) {
  const sp = await searchParams;
  const q = sp.q?.trim() ?? "";
  const importer = sp.importer?.trim() ?? "";
  const foodType = sp.foodType?.trim() ?? "";
  const page = Math.max(0, Number(sp.page ?? 0) || 0);

  const result = await products({ q, importer, foodType, page });
  const items = result?.items ?? [];
  const meta = result?.page;

  const href = (p: number) => {
    const query = new URLSearchParams();
    if (q) query.set("q", q);
    if (importer) query.set("importer", importer);
    if (foodType) query.set("foodType", foodType);
    if (p > 0) query.set("page", String(p));
    const s = query.toString();
    return `${PRODUCTS_PATH}${s ? `?${s}` : ""}`;
  };

  return (
    <main className="shell">
      <header className="page-head">
        <div>
          {/* SPIRITS(증류주)가 아니다 — 시드 40,454건 중 과실주가 26,607건(65%)이고
              맥주·청주도 5,408건이다. 증류주는 다 합쳐 16% 다. LIQUOR 로 넓힌다 */}
          <h1>
            국내 유통 술<span className="sub">IMPORTED LIQUOR</span>
          </h1>
        </div>
        <p className="lede">
          식약처 수입신고에 올라온 술입니다. 신고가 있었다는 뜻이고, 지금 팔리는지는 매장에서
          확인하세요. 제품명과 수입사만 보여 드립니다.
          {meta ? ` 전체 ${meta.totalElements.toLocaleString()}건.` : ""}
        </p>
      </header>

      <form className="products-filter" action={PRODUCTS_PATH} method="get">
        <label>
          제품명
          <input name="q" defaultValue={q} placeholder="캄파리, Laphroaig …" />
        </label>
        <label>
          수입사
          <input name="importer" defaultValue={importer} placeholder="트랜스베버리지 …" />
        </label>
        <label>
          유형
          <select name="foodType" defaultValue={foodType}>
            <option value="">전체</option>
            {(result?.foodTypes ?? []).map((t) => (
              <option key={t.foodType} value={t.foodType}>
                {t.foodType} ({t.count.toLocaleString()})
              </option>
            ))}
          </select>
        </label>
        <button type="submit" className="btn btn-primary">
          찾기
        </button>
      </form>

      {!result ? (
        <div className="empty-state">
          <h3>목록을 불러오지 못했습니다</h3>
        </div>
      ) : items.length === 0 ? (
        <div className="empty-state">
          <h3>맞는 제품이 없습니다</h3>
          <p>수입사마다 표기가 다릅니다 — 영문명으로도 찾아 보세요.</p>
        </div>
      ) : (
        // 가로로 넘칠 때 마우스·터치로만 밀 수 있으면 키보드로는 오른쪽 열을 볼 방법이
        // 없다. 스크롤 영역에 포커스를 주면 방향키로 밀 수 있다
        <div className="products-table-wrap" tabIndex={0} role="region" aria-label="유통 제품 표">
          <table className="table">
            <thead>
              {/* table-layout: fixed 라 이 폭이 그대로 열 폭이 된다 —
                  제품명이 한글+영문 두 줄이라 가장 넓게 준다 */}
              <tr>
                <th style={{ width: "32%" }}>제품명</th>
                <th style={{ width: "24%" }}>수입사</th>
                <th style={{ width: "12%" }}>제조국</th>
                <th style={{ width: "16%" }}>유형</th>
                <th style={{ width: "16%" }}>마지막 신고</th>
              </tr>
            </thead>
            <tbody>
              {items.map((p, i) => (
                <tr key={`${p.nameKo}-${p.importerOrMaker ?? ""}-${i}`}>
                  <td>
                    {p.nameKo}
                    {p.nameEn ? <div className="ingredient-en">{p.nameEn}</div> : null}
                  </td>
                  <td>{p.importerOrMaker ?? "—"}</td>
                  <td>{p.originCountry ?? "—"}</td>
                  <td>{p.foodType}</td>
                  <td>{p.lastReportedOn ? p.lastReportedOn.slice(0, 7) : "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {meta ? (
        <PageLinks
          page={page}
          totalPages={meta.totalPages}
          totalElements={meta.totalElements}
          size={meta.size}
          href={href}
        />
      ) : null}
    </main>
  );
}
