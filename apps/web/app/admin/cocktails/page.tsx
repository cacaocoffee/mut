import Link from "next/link";
import { requireAdmin } from "@/lib/admin-session";
import { adminCocktails } from "@/lib/admin-api";

/**
 * 칵테일 목록 (ISSUE-047 · `FR-ADMIN-002`).
 *
 * 상태가 보이는 것이 이 화면의 일이다 — 초안이 몇 건 밀려 있는지가 에디터의 오늘 할 일이다.
 */
export const dynamic = "force-dynamic";

const STATUS_LABELS: Record<string, string> = {
  draft: "초안",
  published: "발행됨",
  archived: "보관됨",
};

export default async function AdminCocktailList() {
  await requireAdmin();

  const items = await adminCocktails();
  const byStatus = (status: string) => items.filter((c) => c.status === status).length;

  return (
    <>
      <div className="admin__section-head admin__section-head--row">
        <span>
          칵테일 {items.length}건 · 초안 {byStatus("draft")} · 발행 {byStatus("published")}
        </span>
        <Link className="btn btn-primary" href="/admin/cocktails/new">
          새로 만들기
        </Link>
      </div>

      {items.length === 0 ? (
        // SCREENS-00 §3.2 — "결과 없음" 만 쓰지 않는다. 무엇을 하면 되는지 말한다.
        <p className="admin__empty">
          아직 없습니다. <Link href="/admin/cocktails/new">새로 만들기</Link>로 첫 칵테일을
          등록하세요.
        </p>
      ) : (
        <ul className="admin__list">
          {items.map((c) => (
            <li key={c.id}>
              <Link href={`/admin/cocktails/${c.id}`}>
                <b>{c.nameKo}</b>
              </Link>
              <span>
                {c.slug} · {STATUS_LABELS[c.status] ?? c.status}
                {c.tastingNote ? "" : " · 향·맛 서술 없음"}
              </span>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
