import { notFound } from "next/navigation";
import { requireAdmin } from "@/lib/admin-session";
import { adminCocktail, adminRecipe } from "@/lib/admin-api";
import { CocktailForm } from "@/components/admin/cocktail-form";
import { RecipeEditor } from "@/components/admin/recipe-editor";

/**
 * 칵테일 편집 (ISSUE-047·051). 발행 조건 패널과 레시피가 한 화면에 있다.
 *
 * 레시피를 따로 떼지 않은 이유: 발행 조건 패널이 "재료 1개 이상 · 스텝 1개 이상"
 * (`GATE-COCKTAIL-03`)을 말하는데 고칠 자리가 다른 화면에 있으면, 에디터가 무엇이
 * 모자란지 본 곳과 채우는 곳을 오간다.
 */
export const dynamic = "force-dynamic";

export default async function EditCocktail({ params }: { params: Promise<{ id: string }> }) {
  await requireAdmin();

  const { id } = await params;
  const [cocktail, recipe] = await Promise.all([adminCocktail(id), adminRecipe(id)]);
  if (!cocktail) notFound();

  return (
    <>
      <CocktailForm cocktail={cocktail} />
      {/* API 를 못 불렀으면 레시피 편집을 그리지 않는다 — 빈 폼을 저장하면 지워진다 */}
      {recipe ? <RecipeEditor cocktailId={cocktail.id} recipe={recipe} /> : null}
    </>
  );
}
