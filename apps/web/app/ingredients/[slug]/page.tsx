import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { CATEGORY_LABELS, INGREDIENTS, getIngredient, countsForStock } from "@mut/domain";
import { ingredientDetail, ingredientSlugs, searchCorpus } from "@/lib/api";
import { availabilityBadge, needsSubstituteCard } from "@/lib/ingredient-distribution";
import { cocktailsUsingInCorpus } from "@/lib/ingredient-uses";
import { INGREDIENTS_PATH } from "@/lib/routes";
import { CocktailCard } from "@/components/cocktail-card";

/**
 * 재료 상세 (SCREENS-01 01-D · `FR-INGREDIENT-002`·`003` · #196).
 *
 * ## 유통 정보는 API 에서만 온다
 *
 * 국내 유통 여부 · 대체재 · 승인된 유통 제품 · 마지막 신고일은 DB 가 정본이다 (G-41).
 * 코드 마스터(`packages/domain/src/ingredients.ts`)는 이름·분류·별칭까지라, API 가 없으면
 * 그 셋만 그린다 — 빈 칸을 만들어 두면 "없다" 와 "안 채웠다" 를 구분할 수 없다.
 *
 * ## 미유통이면 대체재 카드가 반드시 있다
 *
 * `FR-INGREDIENT-003`. 안내가 없는 미유통 재료는 서버 게이트(`INV-INGREDIENT-01`)가 이미 막았다.
 *
 * ## 유통 제품엔 링크·가격이 없다
 *
 * 제품명·수입사는 사실 정보라 낸다. 구매 링크·협찬·가격은 `NFR-L-05`(주류광고 자문) 뒤다 —
 * 계약(`DistributedProductItem`)에 그 필드가 아예 없다.
 *
 * ## 미리 만든 것만 존재한다
 *
 * 없는 슬러그가 200 을 주면 soft 404 가 된다 (이슈 038). 슬러그 목록은 API 가 있으면 승인분,
 * 없으면 코드 마스터다.
 */
export const dynamicParams = false;
export const revalidate = 3600;

export async function generateStaticParams() {
  const fromApi = await ingredientSlugs();
  const slugs = fromApi.length > 0 ? fromApi : INGREDIENTS.map((i) => i.slug);
  return slugs.map((slug) => ({ slug }));
}

export async function generateMetadata({
  params,
}: PageProps<"/ingredients/[slug]">): Promise<Metadata> {
  const { slug } = await params;
  const [detail, master] = [await ingredientDetail(slug), getIngredient(slug)];
  const nameKo = detail?.nameKo ?? master?.nameKo;
  const nameEn = detail?.nameEn ?? master?.nameEn;
  if (!nameKo) return {};

  const uses = cocktailsUsingInCorpus(slug, await searchCorpus()).length;
  return {
    title: `${nameKo} ${nameEn ?? ""}`.trim(),
    description: `${nameKo}를 쓰는 칵테일 ${uses}종.`,
  };
}

export default async function IngredientPage({ params }: PageProps<"/ingredients/[slug]">) {
  const { slug } = await params;
  const [detail, master] = [await ingredientDetail(slug), getIngredient(slug)];
  if (!detail && !master) notFound();

  const nameKo = detail?.nameKo ?? master!.nameKo;
  const nameEn = detail?.nameEn ?? master!.nameEn;
  const category = detail?.category ?? master!.category;
  const items = cocktailsUsingInCorpus(slug, await searchCorpus());

  const badge = detail ? availabilityBadge(detail.domesticAvailability, detail.lastReportedOn) : null;
  const substituteCard = detail && needsSubstituteCard(detail.domesticAvailability);

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
            {nameKo}
            <span className="sub">{nameEn}</span>
          </h1>
          {badge ? (
            <p className="ingredient-availability">
              <span className={`tag tag-${badge.tone}`}>{badge.text}</span>
              {detail?.priceBand ? <span className="tag tag-neutral">{detail.priceBand}</span> : null}
            </p>
          ) : null}
        </div>
        <p className="lede">
          {CATEGORY_LABELS[category as keyof typeof CATEGORY_LABELS] ?? category}
          {/* 가니시는 필수 재료가 아니다 — 마스터의 countsForStock 구분을 그대로 보여 준다 */}
          {master && !countsForStock(master.slug) && " · 없어도 만들 수 있는 가니시입니다"}
          {detail?.description ? (
            <>
              <br />
              {detail.description}
            </>
          ) : null}
        </p>
      </header>

      {/* FR-INGREDIENT-003 — 미유통이면 대체재 카드가 반드시 있다. 유통이어도 안내가 있으면 낸다 */}
      {detail && (substituteCard || detail.substituteNote) ? (
        <section className="substitute-card" aria-labelledby="substitute-heading">
          <h4 id="substitute-heading" style={{ margin: 0 }}>
            {substituteCard ? "국내에서 구하기 어렵습니다 — 대신 쓸 수 있는 것" : "대신 쓸 수 있는 것"}
          </h4>
          <p>{detail.substituteNote}</p>
        </section>
      ) : null}

      {detail && detail.products.length > 0 ? (
        <section className="ingredient-products" aria-labelledby="products-heading">
          <div className="section-head">
            <h4 id="products-heading" style={{ margin: 0 }}>
              국내에 신고된 제품<span className="ingredient-group__count">{detail.products.length}</span>
            </h4>
          </div>
          <p className="lede" style={{ marginTop: 6 }}>
            식약처 수입신고 기준입니다. 신고가 있었다는 뜻이고, 지금 팔리는지는 매장에서 확인하세요.
          </p>
          <ul className="ingredient-products__list">
            {detail.products.map((p) => (
              <li key={`${p.nameKo}-${p.importerOrMaker ?? ""}`}>
                <b>{p.nameKo}</b>
                {p.nameEn ? <span className="en"> {p.nameEn}</span> : null}
                <span className="ingredient-products__meta">
                  {p.importerOrMaker ?? "수입사 미상"}
                  {p.originCountry ? ` · ${p.originCountry}` : ""}
                  {p.lastReportedOn ? ` · 신고 ${p.lastReportedOn.slice(0, 7)}` : ""}
                </span>
              </li>
            ))}
          </ul>
        </section>
      ) : null}

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
