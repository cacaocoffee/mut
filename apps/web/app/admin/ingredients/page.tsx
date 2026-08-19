import Link from "next/link";
import { requireAdmin } from "@/lib/admin-session";
import { pendingIngredients, ingredientCapacity } from "@/lib/admin-api";
import { IngredientApprove } from "@/components/admin/ingredient-approve";
import { CATEGORY_LABELS, AVAILABILITY_LABELS, label } from "@/lib/ingredient-labels";

/**
 * 재료 승인 큐 (ISSUE-048 · `FR-ADMIN-007` · SPEC-08 §2).
 *
 * ## 목록은 둘 다 보고, 승인은 `admin` 만 한다
 *
 * SPEC-08 §2 — "재료 마스터 승인" 은 `admin` 뿐이다. `editor` 는 재료를 **만들 수는
 * 있지만**(승인 대기로 들어간다) 스스로 승인하지 못한다. 만든 사람과 통과시키는 사람이
 * 같으면 승인 단계가 있으나 마나다.
 *
 * ## 상한은 경고다
 *
 * 승인된 재료가 상한을 넘어도 **막지 않는다** ([DECISIONS §1.2](../../../../docs/issues/DECISIONS.md)).
 * SPEC-04 §9 의 차단·경고 구분이 그대로다 — 데이터가 틀린 것이 아니라 많은 것이라
 * 사람이 판단할 일이다.
 */
export const dynamic = "force-dynamic";

export default async function AdminIngredients() {
  const role = await requireAdmin();

  const [pending, capacity] = await Promise.all([pendingIngredients(), ingredientCapacity()]);

  return (
    <>
      <div className="admin__section-head admin__section-head--row">
        <span>승인 대기 {pending.length}건</span>
        {/* `editor` 도 만들 수 있다 (RED 6). 승인만 `admin` 이다. */}
        <Link className="btn btn-primary" href="/admin/ingredients/new">
          재료 새로 만들기
        </Link>
      </div>

      {capacity?.warning ? (
        // 경고이지 차단이 아니다. 아래 승인 버튼은 그대로 눌린다.
        <p className="admin-warn" role="status">
          승인된 재료가 {capacity.approved}개로 상한 {capacity.cap}개를 넘었습니다. 승인을 막지는
          않습니다 — 이미 있는 재료와 겹치지 않는지만 확인해 주세요.
        </p>
      ) : capacity ? (
        <p className="admin-field__hint">
          승인된 재료 {capacity.approved}개 / 상한 {capacity.cap}개
        </p>
      ) : null}

      {pending.length === 0 ? (
        <p className="admin__empty">
          승인을 기다리는 재료가 없습니다.{" "}
          <Link href="/admin/ingredients/new">재료 새로 만들기</Link>로 추가할 수 있습니다.
        </p>
      ) : (
        <ul className="admin__list">
          {pending.map((ing) => (
            <li key={ing.id}>
              <div className="admin__section-head--row">
                <b>
                  {ing.nameKo} <span className="en">{ing.nameEn}</span>
                </b>
                {role === "admin" ? (
                  <IngredientApprove id={ing.id} name={ing.nameKo} />
                ) : (
                  // RED 3 — `editor` 에게는 버튼이 없다. 왜 없는지는 적어 준다.
                  <span className="admin-field__hint">승인은 admin 이 합니다</span>
                )}
              </div>
              <span>
                {ing.slug} · {label(CATEGORY_LABELS, ing.category)} ·{" "}
                {label(AVAILABILITY_LABELS, ing.domesticAvailability)}
                {ing.abv != null ? ` · ${ing.abv}%` : ""}
              </span>
              {ing.substituteNote ? <span>대체재 — {ing.substituteNote}</span> : null}
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
