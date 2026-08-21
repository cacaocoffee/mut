import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import {
  CATEGORY_LABELS,
  INGREDIENTS,
  cocktailsUsing,
  getIngredient,
  countsForStock,
} from "@mut/domain";
import { searchCorpus } from "@/lib/api";
import { INGREDIENTS_PATH } from "@/lib/routes";
import { CocktailCard } from "@/components/cocktail-card";

/**
 * 재료 상세 (SCREENS-01 01-D · `FR-INGREDIENT-002`).
 *
 * ## 지금 있는 것만 낸다
 *
 * 01-D 는 국내 유통 · 대체재 · 가격대 · 대표 브랜드까지 설계했다. 그 데이터가 아직
 * 없어서 (`FR-INGREDIENT-002`·`003`·`004`) 이름 · 카테고리 · **이 재료를 쓰는 칵테일**
 * 까지만 그린다. 빈 칸을 만들어 두면 "없다" 와 "안 채웠다" 를 구분할 수 없다.
 *
 * ## 미리 만든 것만 존재한다
 *
 * 카테고리 화면과 같은 규칙이다 — 없는 슬러그가 200 을 주면 soft 404 가 된다
 * (이슈 038 에서 겪었다).
 */
export const dynamicParams = false;
export const revalidate = 3600;

export function generateStaticParams() {
  return INGREDIENTS.map((i) => ({ slug: i.slug }));
}

export async function generateMetadata({
  params,
}: PageProps<"/ingredients/[slug]">): Promise<Metadata> {
  const { slug } = await params;
  const ing = getIngredient(slug);
  if (!ing) return {};

  const uses = cocktailsUsing(slug).length;
  return {
    title: `${ing.nameKo} ${ing.nameEn}`,
    description: `${ing.nameKo}를 쓰는 칵테일 ${uses}종.`,
  };
}

export default async function IngredientPage({ params }: PageProps<"/ingredients/[slug]">) {
  const { slug } = await params;
  const ing = getIngredient(slug);
  if (!ing) notFound();

  const corpus = await searchCorpus();
  const using = new Set(cocktailsUsing(slug));
  const items = corpus.filter((c) => using.has(c.slug));

  return (
    <main className="shell">
      <header className="page-head">
        <div>
          <Link
            href={INGREDIENTS_PATH}
            className="btn btn-ghost"
            style={{ fontSize: 11, paddingLeft: 0, marginBottom: 14 }}
          >
            ← 재료 사전 ALL INGREDIENTS
          </Link>
          <h1>
            {ing.nameKo}
            <span className="sub">{ing.nameEn}</span>
          </h1>
        </div>
        <p className="lede">
          {CATEGORY_LABELS[ing.category]}
          {/* 가니시는 필수 재료가 아니다 — 마스터의 countsForStock 구분을 그대로 보여 준다 */}
          {!countsForStock(ing.slug) && " · 없어도 만들 수 있는 가니시입니다"}
        </p>
      </header>

      <section className="section-head">
        <h4 style={{ margin: 0 }}>
          이 재료를 쓰는 칵테일<span className="ingredient-group__count">{items.length}</span>
        </h4>
      </section>

      {items.length > 0 ? (
        <div className="card-grid" style={{ marginTop: 20 }}>
          {items.map((c) => (
            <CocktailCard key={c.slug} cocktail={c} />
          ))}
        </div>
      ) : (
        <div className="empty-state">
          <h3>아직 이 재료를 쓰는 칵테일이 없습니다</h3>
        </div>
      )}
    </main>
  );
}
