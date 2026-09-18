import Link from "next/link";
import { notFound } from "next/navigation";
import { requireAdmin } from "@/lib/admin-session";
import { adminIngredient, ingredientMatches } from "@/lib/admin-api";
import { IngredientDistributionForm } from "@/components/admin/ingredient-distribution-form";
import { IngredientMatches } from "@/components/admin/ingredient-matches";
import { CATEGORY_LABELS, label } from "@/lib/ingredient-labels";

/**
 * 어드민 재료 상세 (#195 · GAPS G-41).
 *
 * 두 덩어리다. 위는 **유통 정보 폼** — 유통 여부·대체재·브랜드 검색어·가격대를 사람이 확정한다.
 * 아래는 **유통 제품 매핑** — 식약처 신고 제품 가운데 이 재료인 것을 제안받고 승인·거절한다.
 * 승인된 매핑으로 서버가 유통 여부를 **제안**하고, 폼의 값은 그 제안을 보고 사람이 고른다.
 * 배치가 재료를 직접 바꾸지 않는 이유는 폼 안내문에 있다.
 */
export const dynamic = "force-dynamic";

export default async function AdminIngredientDetail({ params }: { params: Promise<{ id: string }> }) {
  await requireAdmin();

  const { id } = await params;
  const [ingredient, matches] = await Promise.all([adminIngredient(id), ingredientMatches(id)]);
  if (!ingredient) notFound();

  return (
    <>
      <div className="admin__section-head admin__section-head--row">
        <span>
          {ingredient.nameKo} <span className="en">{ingredient.nameEn}</span>
          <span className="admin-field__hint">
            {" "}
            · {ingredient.slug} · {label(CATEGORY_LABELS, ingredient.category)}
            {ingredient.isApproved ? "" : " · 승인 대기"}
          </span>
        </span>
        <Link className="btn" href="/admin/ingredients">
          목록으로
        </Link>
      </div>

      <IngredientDistributionForm ingredient={ingredient} proposal={matches?.proposal ?? null} />

      {/* API 를 못 불렀으면 매핑 패널을 그리지 않는다 — 빈 목록을 "매핑 없음"으로 읽게 된다 */}
      {matches ? <IngredientMatches ingredientId={ingredient.id} matches={matches.matches} /> : null}
    </>
  );
}
