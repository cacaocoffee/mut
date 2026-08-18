import type { MetadataRoute } from "next";
import { COCKTAILS } from "@kca/domain";
import { CATEGORY_AXES, publishedSlugs, usingApi } from "@/lib/api";
import { categorySlugs } from "@/lib/category-page";
import { SITE_URL } from "@/lib/site";

/**
 * 사이트맵 (ISSUE-039 · `NFR-S-03`·`S-04`).
 *
 * ## 여기 들어가는 것이 색인 대상이다
 *
 * `NFR-S-02` — 필터 결과는 `noindex` 이고 카테고리 경로는 색인한다.
 * 그 경계가 이 파일에 그대로 있다: **필터 경로를 넣지 않는다.**
 *
 * ## `NFR-S-03` 을 사이트맵으로 측정한다
 *
 * "축 조합 경로가 0개" 를 어떻게 재는가가 문제였다. 디렉터리 구조로 막았지만
 * (`base/[slug]` 아래에 다른 축이 없다) 그것만으로는 **측정**이 안 된다.
 *
 * 사이트맵의 모든 경로가 [ALLOWED] 패턴 중 하나여야 한다는 규칙이 그 측정이다.
 * 조합 경로가 생기면 여기 나타나고, 테스트가 잡는다.
 */
export const revalidate = 3600;

/**
 * 색인하는 경로 모양. **여기 없는 모양은 사이트맵에 못 들어간다.**
 *
 * 조합 경로(`/cocktails/base/gin/style/sour`)는 어느 패턴에도 안 맞는다 —
 * 축 하나만 허용하기 때문이다 (`R-C-2`).
 */
export const ALLOWED_PATTERNS = [
  /^\/$/,
  /^\/finder$/,
  /^\/privacy$/,
  /^\/terms$/,
  /^\/cocktails\/[a-z0-9-]+$/,
  /^\/cocktails\/(base|style|method)\/[a-z0-9-]+$/,
] as const;

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  // 절대 주소는 한 곳에서 온다 (이슈 044) — 슬래시 하나 차이로 갈리지 않게.
  const base = SITE_URL;
  const now = new Date();

  const paths: string[] = ["/", "/finder", "/privacy", "/terms"];

  // 발행분만. `draft` 는 공개 API 가 주지 않는다 (RED 26).
  const cocktails = usingApi ? await publishedSlugs() : COCKTAILS.map((c) => c.id);
  paths.push(...cocktails.map((slug) => `/cocktails/${slug}`));

  for (const axis of CATEGORY_AXES) {
    const slugs = await categorySlugs(axis);
    paths.push(...slugs.map((slug) => `/cocktails/${axis}/${slug}`));
  }

  return paths.map((path) => ({
    url: `${base}${path}`,
    lastModified: now,
    // 상세가 콘텐츠의 중심이라 조금 높인다. 카테고리는 그것으로 가는 길이다.
    priority: path.startsWith("/cocktails/") && path.split("/").length === 3 ? 0.8 : 0.6,
  }));
}
