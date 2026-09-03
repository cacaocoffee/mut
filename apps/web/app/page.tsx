import type { Metadata } from "next";
import Link from "next/link";
import { ARTICLE_CATEGORY_KO } from "@mut/domain";
import { CocktailCard } from "@/components/cocktail-card";
import { Wordmark } from "@/components/wordmark";
import { searchCorpus } from "@/lib/api";
import { listArticles } from "@/lib/article-api";
import { cocktailPhotoSrc } from "@/lib/cocktail-photos";
import { ARTICLES_PATH, FINDER_PATH, SEARCH_PATH } from "@/lib/routes";
import { openGraph } from "@/lib/site";

/**
 * 홈 (ADR-0012).
 *
 * 첫 화면이다. 칵테일 + 매거진 두 기둥을 여기서 보이고, 탐색·파인더·아티클로 들어가는
 * 입구를 준다. 예전에는 `/` 가 탐색으로 리다이렉트만 했다(ISSUE-040) — 그 자리를 채운다.
 *
 * ## 색인한다
 *
 * 최상위 SEO 랜딩이다. 사이트맵은 이미 `/` 를 넣어 두었다. robots 기본값(색인)을 둔다.
 *
 * ## 읽기만 한다
 *
 * 새 데이터를 만들지 않는다 — 칵테일은 `searchCorpus()`, 아티클은 `listArticles()` 를
 * 그대로 읽어 몇 개만 추린다. 목록·상세와 같은 폴백 주기(10분)를 둔다.
 */
export const metadata: Metadata = {
  // title 은 두지 않는다 — 레이아웃 기본값("MUT — 당신의 취향, 당신의 멋")이 홈에 맞다.
  description: "칵테일 한 잔부터 그 뒤의 이야기까지. 취향으로 찾는 칵테일과 읽는 매거진.",
  alternates: { canonical: "/" },
  openGraph: openGraph({
    title: "MUT — 당신의 취향, 당신의 멋",
    description: "취향으로 찾는 칵테일과 읽는 매거진.",
    url: "/",
  }),
};

export const revalidate = 600;

/** `2026-08-16T00:00:00Z` → `2026. 8. 16` — 잡지 날짜 표기 (아티클 목록과 같은 규약) */
function formatDate(iso: string): string {
  const [y, m, d] = iso.split("T")[0].split("-");
  return `${y}. ${Number(m)}. ${Number(d)}`;
}

export default async function HomePage() {
  const [corpus, articles] = await Promise.all([searchCorpus(), listArticles()]);

  // 사진이 있는 것만 추천으로 낸다 — 첫 화면은 자리표시자보다 실제 사진이 값을 한다.
  const featured = corpus.filter((c) => cocktailPhotoSrc(c.slug)).slice(0, 8);
  const latest = articles.slice(0, 3);

  return (
    <main className="shell home">
      <header className="home-hero">
        <Wordmark className="home-hero__mark" />
        <p className="home-hero__tagline">당신의 취향, 당신의 멋</p>
        <p className="home-hero__intro">
          칵테일 한 잔부터 그 뒤의 이야기까지 — 마시는 것을 더 깊이 즐기는 법.
        </p>
        <nav className="home-hero__enter" aria-label="시작하기">
          <Link href={SEARCH_PATH} className="btn btn-primary">
            칵테일 탐색
          </Link>
          <Link href={FINDER_PATH} className="btn btn-secondary">
            취향 파인더
          </Link>
          <Link href={ARTICLES_PATH} className="btn btn-secondary">
            아티클
          </Link>
        </nav>
      </header>

      {latest.length > 0 && (
        <section className="home-section">
          <div className="home-section__head">
            <h2>최신 아티클</h2>
            <Link href={ARTICLES_PATH} className="home-section__more">
              전체 보기
            </Link>
          </div>
          {/* 3편이라 1 + 2 로 놓는다 (#182). 3열에 머리기사 2칸이면 셋째가 혼자 남아 오른쪽이 빈다. */}
          <div className="card-grid home-articles">
            {latest.map((a, i) => (
              <Link
                key={a.slug}
                href={`${ARTICLES_PATH}/${a.slug}`}
                className={i === 0 ? "article-card article-card--lead" : "article-card"}
              >
                {/* 카드 사진은 고정 크롭이다 — 목록의 줄맞춤이 원본 비율보다 중요하다 (ADR-0008) */}
                <div className="photo-slot photo-slot--4x3 photo-slot--photo">
                  <img
                    className="photo-slot__img"
                    src={a.hero}
                    alt={`${a.title} 대표 사진`}
                    loading="lazy"
                    width={800}
                    height={600}
                  />
                </div>
                <span className="article-card__kicker">
                  {ARTICLE_CATEGORY_KO[a.category]}
                  {a.isSponsored && " · 제휴 콘텐츠"}
                </span>
                <h3 className="article-card__title">{a.title}</h3>
                <p className="article-card__dek">{a.dek}</p>
                <span className="article-card__date">{formatDate(a.publishedAt)}</span>
              </Link>
            ))}
          </div>
        </section>
      )}

      {featured.length > 0 && (
        <section className="home-section">
          <div className="home-section__head">
            <h2>추천 칵테일</h2>
            <Link href={SEARCH_PATH} className="home-section__more">
              전체 보기
            </Link>
          </div>
          <div className="card-grid">
            {featured.map((c) => (
              <CocktailCard key={c.slug} cocktail={c} />
            ))}
          </div>
        </section>
      )}
    </main>
  );
}
