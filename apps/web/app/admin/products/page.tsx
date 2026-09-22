import { PageLinks } from "@/components/page-links";
import { requireAdmin } from "@/lib/admin-session";
import { adminProducts } from "@/lib/admin-api";

/**
 * 유통 제품 목록 (#203 · G-41).
 *
 * 식약처 신고 4만 건을 훑는 화면. 재료의 브랜드 검색어를 적으려면 어떤 제품이 어떤 이름으로
 * 들어와 있는지 먼저 봐야 한다 ("캄파리"·"깜빠리"·"CAMPARI BITTER" 가 다 다르게 적혀 있다).
 *
 * 구매 링크·가격은 없다 — 계약에 필드가 없다 (`NFR-L-05`). 여기 있는 건 "신고가 있었다" 는
 * 사실과 그 날짜뿐이다.
 */
export const dynamic = "force-dynamic";

export default async function AdminProducts({
  searchParams,
}: {
  searchParams: Promise<{ q?: string; importer?: string; foodType?: string; page?: string }>;
}) {
  await requireAdmin();
  const sp = await searchParams;
  const q = sp.q?.trim() ?? "";
  const importer = sp.importer?.trim() ?? "";
  const foodType = sp.foodType?.trim() ?? "";
  const page = Math.max(0, Number(sp.page ?? 0) || 0);

  const result = await adminProducts({ q, importer, foodType, page });
  const items = result?.items ?? [];
  const meta = result?.page;

  const href = (p: number) => {
    const query = new URLSearchParams();
    if (q) query.set("q", q);
    if (importer) query.set("importer", importer);
    if (foodType) query.set("foodType", foodType);
    if (p > 0) query.set("page", String(p));
    const s = query.toString();
    return `/admin/products${s ? `?${s}` : ""}`;
  };

  return (
    <>
      <div className="admin__section-head admin__section-head--row">
        <span>
          유통 제품 {meta ? `${meta.totalElements.toLocaleString()}건` : ""}
          {q || importer || foodType ? " (걸러짐)" : ""}
        </span>
        <span className="admin-field__hint">식약처 수입신고 기준 · 최근 신고순</span>
      </div>

      <form className="admin-form__grid" action="/admin/products" method="get">
        <label className="admin-field">
          <span className="admin-field__label">제품명</span>
          <input name="q" defaultValue={q} placeholder="캄파리, CAMPARI …" />
        </label>
        <label className="admin-field">
          <span className="admin-field__label">수입사</span>
          <input name="importer" defaultValue={importer} placeholder="트랜스베버리지 …" />
        </label>
        <label className="admin-field">
          <span className="admin-field__label">식품유형</span>
          <select name="foodType" defaultValue={foodType}>
            <option value="">전체</option>
            {(result?.foodTypes ?? []).map((t) => (
              <option key={t.foodType} value={t.foodType}>
                {t.foodType} ({t.count.toLocaleString()})
              </option>
            ))}
          </select>
        </label>
        <div className="admin-form__actions">
          <button type="submit" className="btn btn-primary">
            찾기
          </button>
        </div>
      </form>

      {!result ? (
        <p className="admin__empty">목록을 불러오지 못했습니다. API 를 확인해 주세요.</p>
      ) : items.length === 0 ? (
        <p className="admin__empty">맞는 제품이 없습니다.</p>
      ) : (
        <div className="admin-products">
          <table className="table">
            <thead>
              <tr>
                <th>제품명</th>
                <th>수입사 · 제조</th>
                <th>제조국</th>
                <th>유형</th>
                <th>마지막 신고</th>
              </tr>
            </thead>
            <tbody>
              {items.map((p) => (
                <tr key={p.id}>
                  <td>
                    <b>{p.nameKo}</b>
                    {p.nameEn ? <div className="ingredient-en">{p.nameEn}</div> : null}
                  </td>
                  <td>
                    {p.importerOrMaker ?? "—"}
                    {p.manufacturer ? <div className="admin-field__hint">{p.manufacturer}</div> : null}
                  </td>
                  <td>{p.originCountry ?? "—"}</td>
                  <td>{p.foodType}</td>
                  <td>{p.lastReportedOn ?? "—"}</td>
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
    </>
  );
}
