import type { Metadata } from "next";
import Link from "next/link";
import { ARTICLES, ARTICLE_CATEGORY_KO } from "@mut/domain";
import { ARTICLES_PATH } from "@/lib/routes";
import { openGraph } from "@/lib/site";

/**
 * 아티클 목록 (`FR-CONTENT-001` 앞당김 · ADR-0010).
 *
 * ## 색인한다
 *
 * 내용이 사람마다 달라지지 않고, 콘텐츠 유입이 이 화면의 존재 이유다 (SPEC-05 §4).
 *
 * ## 카테고리 필터가 없다
 *
 * 9편에 필터를 달면 누를 이유보다 누를 것이 많아진다. 카드의 카테고리 라벨이
 * 지금은 그 역할을 다 한다 — 글이 쌓여 한 화면을 넘기면 그때 단다.
 */
export const metadata: Metadata = {
  title: "아티클",
  description: "칵테일, 바, 위스키 — 마시는 것 너머의 이야기를 담습니다.",
  alternates: { canonical: ARTICLES_PATH },
  openGraph: openGraph({
    title: "아티클",
    description: "칵테일, 바, 위스키 — 마시는 것 너머의 이야기를 담습니다.",
    url: ARTICLES_PATH,
  }),
};

/** 아티클은 지금 정적 데이터라 배포로만 바뀐다. 상세와 같은 폴백 주기를 둔다. */
export const revalidate = 3600;

/** `2025-08-16` → `2025. 8. 16` — 잡지 날짜 표기 */
function formatDate(iso: string): string {
  const [y, m, d] = iso.split("-");
  return `${y}. ${Number(m)}. ${Number(d)}`;
}

export default function ArticlesPage() {
  return (
    <main className="shell">
      <header className="page-head">
        <div>
          <h1>
            아티클
            <span className="sub">{ARTICLES.length} articles</span>
          </h1>
        </div>
        <p className="lede">칵테일, 바, 위스키 — 마시는 것 너머의 이야기를 담습니다.</p>
      </header>

      <div className="card-grid">
        {ARTICLES.map((a) => (
          <Link key={a.slug} href={`${ARTICLES_PATH}/${a.slug}`} className="article-card">
            {/* 카드 사진은 4:3 고정 크롭이다 — 목록의 줄맞춤이 원본 비율보다 중요하다.
                본문(상세)에서는 원본 비율로 그린다. 컬러 — ADR-0008 */}
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
            <span className="article-card__kicker">{ARTICLE_CATEGORY_KO[a.category]}</span>
            <h3 className="article-card__title">{a.title}</h3>
            <p className="article-card__dek">{a.dek}</p>
            <span className="article-card__date">{formatDate(a.publishedAt)}</span>
          </Link>
        ))}
      </div>
    </main>
  );
}
