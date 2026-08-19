import { requireAdmin } from "@/lib/admin-session";
import { CocktailForm } from "@/components/admin/cocktail-form";

/** 새 칵테일 (ISSUE-047). 저장하면 편집 화면으로 옮겨 간다. */
export const dynamic = "force-dynamic";

export default async function NewCocktail() {
  await requireAdmin();
  return <CocktailForm cocktail={null} />;
}
