import Link from "next/link";
import { requireAdmin } from "@/lib/admin-session";
import { adminArticles } from "@/lib/admin-api";

/**
 * 어드민 아티클 목록 (ADR-0011). 초안·발행·보관 상태가 한눈에 보인다.
 */
export const dynamic = "force-dynamic";

const STATUS_LABELS: Record<string, string> = {
  draft: "초안",
  published: "발행됨",
  archived: "보관됨",
};

const CATEGORY_LABELS: Record<string, string> = {
  cocktail: "칵테일",
  bar: "바",
  spirits: "스피릿",
};

export default async function AdminArticleList() {
  await requireAdmin();

  const items = await adminArticles();
  const byStatus = (status: string) => items.filter((a) => a.status === status).length;

  return (
    <>
      <div className="admin__section-head admin__section-head--row">
        <span>
          아티클 {items.length}건 · 초안 {byStatus("draft")} · 발행 {byStatus("published")}
        </span>
        <Link className="btn btn-primary" href="/admin/articles/new">
          새로 만들기
        </Link>
      </div>

      {items.length === 0 ? (
        <p className="admin__empty">
          아직 없습니다. <Link href="/admin/articles/new">새로 만들기</Link>로 첫 아티클을 씁니다.
        </p>
      ) : (
        <ul className="admin__list">
          {items.map((a) => (
            <li key={a.id}>
              <Link href={`/admin/articles/${a.id}`}>
                <b>{a.title}</b>
              </Link>
              <span>
                {a.slug} · {CATEGORY_LABELS[a.category] ?? a.category} · {STATUS_LABELS[a.status] ?? a.status}
                {a.isSponsored ? " · 제휴" : ""}
              </span>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
