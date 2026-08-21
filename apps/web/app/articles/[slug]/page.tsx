import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import {
  ARTICLES,
  ARTICLE_CATEGORY_KO,
  articleBySlug,
  getCocktail,
  type ArticleBlock,
} from "@mut/domain";
import { ARTICLES_PATH } from "@/lib/routes";
import { openGraph, SITE_URL } from "@/lib/site";
import { articleJsonLd } from "@/lib/structured-data";

/**
 * 아티클 상세 (`FR-CONTENT-001` 앞당김 · ADR-0010).
 *
 * 렌더링 규약은 칵테일 상세와 같다 (SPEC-05 §4 · `PRIN-T04`) — SSG 에
 * `dynamicParams = false`. 이유는 그쪽 파일에 적혀 있다: `loading.tsx` 가 셸을
 * 먼저 내보내 200 이 굳으면 없는 슬러그가 soft 404 가 된다.
 */
export const revalidate = 3600;
export const dynamicParams = false;

export function generateStaticParams() {
  return ARTICLES.map((a) => ({ slug: a.slug }));
}

export async function generateMetadata({
  params,
}: PageProps<"/articles/[slug]">): Promise<Metadata> {
  const { slug } = await params;
  const a = articleBySlug(slug);
  if (!a) return {};

  return {
    title: a.title,
    description: a.dek,
    alternates: { canonical: `${ARTICLES_PATH}/${a.slug}` },
    openGraph: {
      ...openGraph({
        title: a.title,
        description: a.dek,
        url: `${ARTICLES_PATH}/${a.slug}`,
        type: "article",
      }),
      // 칵테일 상세와 달리 파일 규약(opengraph-image)이 없다 — 대표 사진이 실물로 있다
      images: [{ url: `${SITE_URL}${a.hero}` }],
    },
  };
}

function Block({ b, dropcap }: { b: ArticleBlock; dropcap?: boolean }) {
  switch (b.kind) {
    case "heading":
      return <h2>{b.text}</h2>;
    case "quote":
      return <blockquote>{b.text}</blockquote>;
    case "figure":
      return (
        <figure>
          <img src={b.src} alt={b.caption ?? "본문 사진"} loading="lazy" width={b.width} height={b.height} />
          {b.caption ? <figcaption>{b.caption}</figcaption> : null}
        </figure>
      );
    default:
      return <p className={dropcap ? "dropcap" : undefined}>{b.text}</p>;
  }
}

/** `2025-01-30` → `2025. 1. 30` — 잡지 날짜 표기 (목록 카드와 같은 규칙) */
function formatDate(iso: string): string {
  const [y, m, d] = iso.split("-");
  return `${y}. ${Number(m)}. ${Number(d)}`;
}

export default async function ArticleDetailPage({ params }: PageProps<"/articles/[slug]">) {
  const { slug } = await params;
  const a = articleBySlug(slug);
  if (!a) notFound();

  // 첫 블록이 대표 사진과 같은 파일이면 건너뛴다 — 히어로가 이미 그 사진을 그렸다
  const first = a.blocks[0];
  const blocks = first?.kind === "figure" && first.src === a.hero ? a.blocks.slice(1) : a.blocks;
  const heroDims = first?.kind === "figure" && first.src === a.hero ? first : null;

  // 드롭캡은 글자로 시작하는 첫 문단에만 — 따옴표·인용으로 여는 글에서 부호가 커지는 것을 막는다
  const firstParagraph = blocks.find((b) => b.kind === "paragraph");
  const dropcapOk = firstParagraph != null && /^[가-힣A-Za-z]/.test(firstParagraph.text);

  const related = a.relatedCocktailSlugs
    .map((s) => getCocktail(s))
    .filter((c) => c != null);

  // 이전 글(먼저 쓴 것) · 다음 글(나중에 쓴 것) — ARTICLES 는 최신순이다
  const idx = ARTICLES.findIndex((x) => x.slug === a.slug);
  const older = ARTICLES[idx + 1];
  const newer = idx > 0 ? ARTICLES[idx - 1] : undefined;

  return (
    <main className="shell">
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(articleJsonLd(a)) }}
      />

      <header>
        <Link
          href={ARTICLES_PATH}
          className="btn btn-ghost"
          style={{ fontSize: 11, paddingLeft: 0 }}
        >
          ← 아티클
        </Link>
        {/* 매거진 표제 — 마스트헤드와 같은 중앙 정렬 (ADR-0009) */}
        <div className="article-head">
          {/* 협찬 글 표기는 데이터 플래그로만 켜진다 — 끌 수 없다 (`NFR-L-02` · 배포 차단) */}
          <span className="article-card__kicker">
            {ARTICLE_CATEGORY_KO[a.category]}
            {a.isSponsored && " · 제휴 콘텐츠"}
          </span>
          <h1>{a.title}</h1>
          <p className="article-head__dek">{a.dek}</p>
          <p className="article-head__byline">글·사진 Shaking Like Bartender · {formatDate(a.publishedAt)}</p>
          <hr className="article-head__rule" />
        </div>
      </header>

      {/* 대표 사진은 본문보다 넓게 낸다. 첫 화면(LCP)이라 lazy 를 붙이지 않는다.
          클래스의 `hero` 가 image-guard 의 EAGER_ALLOWED 표식이다. 컬러 — ADR-0008 */}
      <figure className="article-hero">
        <img
          className="article-hero__img"
          src={a.hero}
          alt={`${a.title} 대표 사진`}
          width={heroDims?.width ?? 966}
          height={heroDims?.height ?? 725}
        />
      </figure>

      <article className="article-body">
        {blocks.map((b, i) => (
          <Block key={i} b={b} dropcap={dropcapOk && b === firstParagraph} />
        ))}

        <p className="article-source">
          {a.publishedAt.slice(0, 4)}년에 블로그에 쓴 글을 옮겼습니다 ·{" "}
          <a href={a.sourceUrl} rel="noopener noreferrer">
            원문 보기
          </a>
        </p>
      </article>

      {related.length > 0 && (
        <section>
          <h4 className="section-head">이 글의 칵테일</h4>
          {related.map((c) => (
            <Link key={c.id} href={`/cocktails/${c.id}`} className="btn related-link">
              <span className="name">
                {c.ko}
                <span>{c.en}</span>
              </span>
              <span className="meta">레시피 보기</span>
            </Link>
          ))}
        </section>
      )}

      {/* 글 끝이 막다른 길이 되지 않게 — 이전 글은 먼저 쓴 것, 다음 글은 나중에 쓴 것 */}
      {(older || newer) && (
        <nav className="article-pager" aria-label="이웃 글">
          <div>
            {older && (
              <Link href={`${ARTICLES_PATH}/${older.slug}`}>
                <span className="article-pager__dir">← 이전 글</span>
                <span className="article-pager__title">{older.title}</span>
              </Link>
            )}
          </div>
          <div className="article-pager__next">
            {newer && (
              <Link href={`${ARTICLES_PATH}/${newer.slug}`}>
                <span className="article-pager__dir">다음 글 →</span>
                <span className="article-pager__title">{newer.title}</span>
              </Link>
            )}
          </div>
        </nav>
      )}
    </main>
  );
}
