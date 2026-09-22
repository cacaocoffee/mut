import Link from "next/link";
import { requireAdmin } from "@/lib/admin-session";
import { pendingIngredients, ingredientCapacity, searchAdminIngredients } from "@/lib/admin-api";
import { IngredientApprove } from "@/components/admin/ingredient-approve";
import { IngredientMatchRun } from "@/components/admin/ingredient-match-run";
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

export default async function AdminIngredients({
  searchParams,
}: {
  searchParams: Promise<{ q?: string }>;
}) {
  const role = await requireAdmin();
  const q = (await searchParams).q?.trim() ?? "";

  const [pending, capacity, found] = await Promise.all([
    pendingIngredients(),
    ingredientCapacity(),
    q ? searchAdminIngredients(q) : Promise.resolve([]),
  ]);

  return (
    <>
      <div className="admin__section-head admin__section-head--row">
        <span>승인 대기 {pending.length}건</span>
        <span className="admin-inline">
          {/* 유통 제품 매칭 배치 — 매일 04:10 에도 돈다 (#213) */}
          <IngredientMatchRun />
          {/* `editor` 도 만들 수 있다 (RED 6). 승인만 `admin` 이다. */}
          <Link className="btn btn-primary" href="/admin/ingredients/new">
            재료 새로 만들기
          </Link>
        </span>
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

      {/* 승인된 재료로 들어가는 입구 (#195). 승인 큐엔 대기분만 있어서 유통 정보를 고칠 길이 없었다. */}
      <form className="admin-form__grid" action="/admin/ingredients" method="get">
        <label className="admin-field">
          <span className="admin-field__label">재료 찾기</span>
          <input name="q" defaultValue={q} placeholder="이름 · 영문명 · 슬러그" />
          <span className="admin-field__hint">유통 여부·대체재·유통 제품 매핑은 재료 이름을 눌러 고칩니다</span>
        </label>
      </form>
      {q ? (
        found.length === 0 ? (
          <p className="admin__empty">&ldquo;{q}&rdquo; 에 맞는 재료가 없습니다.</p>
        ) : (
          <ul className="admin__list">
            {found.map((ing) => (
              <li key={ing.id}>
                <b>
                  <Link href={`/admin/ingredients/${ing.id}`}>
                    {ing.nameKo} <span className="en">{ing.nameEn}</span>
                  </Link>
                </b>
                <span>
                  {ing.slug} · {label(CATEGORY_LABELS, ing.category)} ·{" "}
                  {label(AVAILABILITY_LABELS, ing.domesticAvailability)}
                  {ing.isApproved ? "" : " · 승인 대기"}
                </span>
              </li>
            ))}
          </ul>
        )
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
                  <Link href={`/admin/ingredients/${ing.id}`}>
                    {ing.nameKo} <span className="en">{ing.nameEn}</span>
                  </Link>
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
