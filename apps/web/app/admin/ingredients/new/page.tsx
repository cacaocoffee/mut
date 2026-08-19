import Link from "next/link";
import { requireAdmin } from "@/lib/admin-session";
import { IngredientForm } from "@/components/admin/ingredient-form";

/**
 * 재료 생성 (ISSUE-048 · `FR-ADMIN-007`).
 *
 * `editor` 도 만든다 (RED 6). 만들기와 승인을 가른 것이 이 기능의 요점이다 — 만든 사람이
 * 스스로 통과시키면 승인 단계가 없는 것과 같다.
 */
export const dynamic = "force-dynamic";

export default async function NewIngredient() {
  await requireAdmin();

  return (
    <>
      <div className="admin-form__head">
        <h2 className="admin__section-head">재료 새로 만들기</h2>
      </div>

      <IngredientForm />

      <p className="admin__foot">
        <Link href="/admin/ingredients">← 승인 대기 목록으로</Link>
      </p>
    </>
  );
}
