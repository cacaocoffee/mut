import type { Metadata } from "next";
import Link from "next/link";
import {
  CATEGORY_LABELS,
  CATEGORY_ORDER,
  INGREDIENTS,
  cocktailsUsing,
  type IngredientCategory,
} from "@mut/domain";
import { INGREDIENTS_PATH } from "@/lib/routes";

/**
 * 재료 사전 목록 (SCREENS-01 01-D · `FR-INGREDIENT-002`).
 *
 * ## 색인한다
 *
 * 탐색과 달리 내용이 사람마다 달라지지 않는다. "이 재료를 쓰는 칵테일" 을 찾는
 * 검색은 실제로 들어오는 질의라 상세·카테고리와 같은 취급으로 둔다 (SPEC-05 §4).
 *
 * ## 지금은 이름과 쓰임새까지다
 *
 * 01-D 가 설계한 국내 유통 · 가격대 · 대표 브랜드 · 설명은 **데이터가 아직 없다**
 * (`FR-INGREDIENT-002`·`003`·`004`). 없는 칸을 비워 두는 대신 있는 것만 낸다 —
 * 저작이 끝나면 그때 채운다.
 */
export const metadata: Metadata = {
  title: "재료 사전",
  description: "칵테일에 쓰이는 재료와, 그 재료로 만드는 칵테일을 모았습니다.",
};

/** 재료는 코퍼스가 바뀌어야 바뀐다. 탐색·상세와 같은 값이다. */
export const revalidate = 600;

export default function IngredientsPage() {
  const grouped = CATEGORY_ORDER.map(
    (category) =>
      [category, INGREDIENTS.filter((i) => i.category === category)] as const
  ).filter(([, list]) => list.length > 0);

  return (
    <main className="shell">
      <header className="page-head">
        <div>
          <h1>
            재료 사전
            <span className="sub">{INGREDIENTS.length} ingredients</span>
          </h1>
        </div>
        <p className="lede">재료마다 그 재료로 만드는 칵테일을 함께 적었습니다.</p>
      </header>

      {grouped.map(([category, list]) => (
        <IngredientGroup key={category} category={category} list={list} />
      ))}
    </main>
  );
}

function IngredientGroup({
  category,
  list,
}: {
  category: IngredientCategory;
  list: typeof INGREDIENTS;
}) {
  return (
    <section className="ingredient-group">
      <div className="section-head">
        <h4 style={{ margin: 0 }}>
          {CATEGORY_LABELS[category]}
          <span className="ingredient-group__count">{list.length}</span>
        </h4>
      </div>
      <ul className="ingredient-list">
        {list.map((ing) => {
          // 쓰임새 개수가 곧 "이걸 사면 몇 잔이 열리나" 다. 사전에서 제일 쓸모 있는 숫자다.
          const uses = cocktailsUsing(ing.slug).length;
          return (
            <li key={ing.slug} className="ingredient-list__item">
              <Link href={`${INGREDIENTS_PATH}/${ing.slug}`}>
                <span className="ko">{ing.nameKo}</span>
                <span className="en">{ing.nameEn}</span>
                <span className="ingredient-list__uses">{uses}잔</span>
              </Link>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
