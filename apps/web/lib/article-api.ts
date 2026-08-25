import type { components } from "@mut/domain/generated/api";
import type { Article, ArticleCategory } from "@mut/domain";
import { ARTICLES as CODE_ARTICLES, articleBySlug as codeArticleBySlug } from "@mut/domain";

/**
 * 아티클 조회 (ADR-0011 4단계).
 *
 * 칵테일과 같은 규약이다 — `MUT_API_URL` 이 있으면 DB(API), 없으면 `packages/domain`
 * 코드 배열로 폴백한다. 어드민 편집(5단계)이 붙으면 DB 가 정본이 되고 코드는 시드로만 남는다.
 *
 * 화면 코드를 덜 고치려고 반환 모양을 도메인 `Article` 과 최대한 맞춘다. API 의
 * `relatedCocktails`(slug+이름)는 화면이 이미 `getCocktail` 로 이름을 찾으므로
 * slug 목록으로 줄여 `Article.relatedCocktailSlugs` 와 같은 자리에 넣는다.
 */
type ArticleSummaryDto = components["schemas"]["ArticleSummary"];
type ArticleDetailDto = components["schemas"]["ArticleDetail"];

const BASE = process.env.MUT_API_URL?.replace(/\/$/, "") ?? "";
export const usingApi = BASE.length > 0;

function describe(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

/** 목록 카드가 쓰는 얇은 모양. 본문(blocks)은 상세에서만 받는다. */
export interface ArticleCard {
  slug: string;
  title: string;
  dek: string;
  category: ArticleCategory;
  hero: string;
  publishedAt: string;
  isSponsored?: boolean;
}

function summaryToCard(s: ArticleSummaryDto): ArticleCard {
  return {
    slug: s.slug,
    title: s.title,
    dek: s.dek,
    category: s.category as ArticleCategory,
    hero: s.hero,
    publishedAt: (s.publishedAt ?? "").slice(0, 10),
    isSponsored: s.isSponsored,
  };
}

/** 최신 발행순. 코드 폴백도 같은 정렬(index.ts 가 이미 최신순). */
export async function listArticles(): Promise<ArticleCard[]> {
  if (!usingApi) {
    return CODE_ARTICLES.map((a) => ({
      slug: a.slug,
      title: a.title,
      dek: a.dek,
      category: a.category,
      hero: a.hero,
      publishedAt: a.publishedAt,
      isSponsored: a.isSponsored,
    }));
  }
  try {
    const res = await fetch(`${BASE}/api/v1/articles`, { next: { revalidate: 600 } });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return ((await res.json()) as ArticleSummaryDto[]).map(summaryToCard);
  } catch (e) {
    console.warn(`[article-api] 목록 조회 실패 — 코드 데이터로 폴백: ${describe(e)}`);
    return CODE_ARTICLES.map((a) => ({
      slug: a.slug,
      title: a.title,
      dek: a.dek,
      category: a.category,
      hero: a.hero,
      publishedAt: a.publishedAt,
      isSponsored: a.isSponsored,
    }));
  }
}

/** 발행분 슬러그 전부 (generateStaticParams). 실패해도 빌드를 멈추지 않는다. */
export async function articleSlugs(): Promise<string[]> {
  if (!usingApi) return CODE_ARTICLES.map((a) => a.slug);
  try {
    const res = await fetch(`${BASE}/api/v1/articles`, { cache: "no-store" });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return ((await res.json()) as ArticleSummaryDto[]).map((s) => s.slug);
  } catch (e) {
    console.warn(`[article-api] 슬러그 목록 실패 — 코드로 폴백: ${describe(e)}`);
    return CODE_ARTICLES.map((a) => a.slug);
  }
}

/** 상세. 도메인 `Article` 모양으로 돌려줘 화면이 그대로 쓴다. 없으면 null. */
export async function getArticle(slug: string): Promise<Article | null> {
  if (!usingApi) return codeArticleBySlug(slug) ?? null;
  try {
    const res = await fetch(`${BASE}/api/v1/articles/${encodeURIComponent(slug)}`, {
      next: { revalidate: 600 },
    });
    if (res.status === 404) return null;
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const d = (await res.json()) as ArticleDetailDto;
    return {
      slug: d.slug,
      category: d.category as ArticleCategory,
      title: d.title,
      dek: d.dek,
      hero: d.hero,
      publishedAt: (d.publishedAt ?? "").slice(0, 10),
      sourceUrl: d.sourceUrl ?? "",
      isSponsored: d.isSponsored,
      relatedCocktailSlugs: d.relatedCocktails.map((c) => c.slug),
      // body 는 계약상 느슨한 JSON(블록 배열)이라 도메인 ArticleBlock 로 좁힌다.
      // 실제 모양은 서버가 시드로 넣은 것과 같다 (types.ts ArticleBlock).
      blocks: d.body as unknown as Article["blocks"],
    };
  } catch (e) {
    console.warn(`[article-api] 상세 조회 실패 — 코드로 폴백: ${describe(e)}`);
    return codeArticleBySlug(slug) ?? null;
  }
}

