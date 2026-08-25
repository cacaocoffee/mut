import { notFound } from "next/navigation";
import { requireAdmin } from "@/lib/admin-session";
import { adminArticle } from "@/lib/admin-api";
import { ArticleForm } from "@/components/admin/article-form";

/** 아티클 편집 (ADR-0011). draft 포함 조회 — 어드민만 미발행을 본다. */
export const dynamic = "force-dynamic";

export default async function EditArticle({ params }: { params: Promise<{ id: string }> }) {
  await requireAdmin();
  const { id } = await params;
  const article = await adminArticle(id);
  if (!article) notFound();
  return <ArticleForm article={article} />;
}
