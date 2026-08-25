import { requireAdmin } from "@/lib/admin-session";
import { ArticleForm } from "@/components/admin/article-form";

/** 새 아티클 (ADR-0011). 생성은 항상 draft 로 시작한다. */
export const dynamic = "force-dynamic";

export default async function NewArticle() {
  await requireAdmin();
  return <ArticleForm article={null} />;
}
