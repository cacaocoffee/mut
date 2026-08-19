import { notFound } from "next/navigation";
import { requireAdmin } from "@/lib/admin-session";
import { adminCocktail } from "@/lib/admin-api";
import { CocktailForm } from "@/components/admin/cocktail-form";

/** 칵테일 편집 (ISSUE-047). 발행 조건 패널이 여기 붙는다. */
export const dynamic = "force-dynamic";

export default async function EditCocktail({ params }: { params: Promise<{ id: string }> }) {
  await requireAdmin();

  const { id } = await params;
  const cocktail = await adminCocktail(id);
  if (!cocktail) notFound();

  return <CocktailForm cocktail={cocktail} />;
}
