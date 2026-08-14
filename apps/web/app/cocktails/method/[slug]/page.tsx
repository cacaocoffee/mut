import type { Metadata } from "next";
import {
  CategoryPage,
  categoryMetadata,
  categorySlugs,
  categoryView,
  notFound,
} from "@/lib/category-page";

/**
 * method 카테고리 (ISSUE-039 · `FR-COCKTAIL-029`).
 *
 * 화면은 `lib/category-page.tsx` 한 벌이고 여기서는 **축 이름만** 넘긴다.
 * 축이 하나뿐이라 조합을 표현할 방법이 없다 — 그것이 `R-C-2` 의 구현이다.
 *
 * **이 디렉터리 아래에 다른 축 디렉터리를 만들지 않는다.**
 * `method/[slug]/style/[slug]` 가 생기는 순간 색인 대상이 곱으로 늘고,
 * 그중 대부분은 결과가 0건이다 (`PRIN-P06` — 조합 폭발).
 */
const AXIS = "method" as const;

/** 미리 만든 것만 존재한다. 없는 슬러그가 200 을 주면 soft 404 다 (이슈 038 에서 겪었다). */
export const dynamicParams = false;

/** 주 경로는 발행 시 on-demand 재생성이다 (SPEC-07 §4). 이건 폴백이다. */
export const revalidate = 3600;

export async function generateStaticParams() {
  return (await categorySlugs(AXIS)).map((slug) => ({ slug }));
}

export async function generateMetadata({
  params,
}: PageProps<"/cocktails/method/[slug]">): Promise<Metadata> {
  const { slug } = await params;
  const view = await categoryView(AXIS, slug);
  return view ? categoryMetadata(AXIS, view) : {};
}

export default async function Page({ params }: PageProps<"/cocktails/method/[slug]">) {
  const { slug } = await params;
  const view = await categoryView(AXIS, slug);
  if (!view) notFound();

  return <CategoryPage axis={AXIS} view={view} />;
}
