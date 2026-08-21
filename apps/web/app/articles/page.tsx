import type { Metadata } from "next";
import { ARTICLES } from "@mut/domain";
import { ArticleList } from "@/components/article-list";
import { ARTICLES_PATH } from "@/lib/routes";
import { openGraph } from "@/lib/site";

/**
 * 아티클 목록 (`FR-CONTENT-001` 앞당김 · ADR-0010).
 *
 * ## 색인한다
 *
 * 내용이 사람마다 달라지지 않고, 콘텐츠 유입이 이 화면의 존재 이유다 (SPEC-05 §4).
 *
 * ## 필터는 클라이언트, 목록은 서버
 *
 * 9편일 때는 필터를 안 달았고 "글이 쌓여 한 화면을 넘기면 그때 단다"고 적어 뒀다.
 * 142편이 되며 그때가 왔다 — 카드 데이터만 추려 `ArticleList` 로 넘긴다.
 * 본문(`blocks`)은 상세만 쓰므로 클라이언트에 싣지 않는다.
 */
export const metadata: Metadata = {
  title: "아티클",
  description: "칵테일, 바, 스피릿 — 마시는 것 너머의 이야기를 담습니다.",
  alternates: { canonical: ARTICLES_PATH },
  openGraph: openGraph({
    title: "아티클",
    description: "칵테일, 바, 스피릿 — 마시는 것 너머의 이야기를 담습니다.",
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
  const cards = ARTICLES.map((a) => ({
    slug: a.slug,
    title: a.title,
    dek: a.dek,
    category: a.category,
    dateLabel: formatDate(a.publishedAt),
    hero: a.hero,
    isSponsored: a.isSponsored,
  }));

  return (
    <main className="shell">
      {/* lede 를 두지 않는다 — 오른쪽 구석에 뜬 한 줄이 색인 띠와 자리를 다퉜다.
          이 화면이 무엇인지는 색인 띠와 카드가 이미 말한다. */}
      <header className="page-head">
        <div>
          <h1>
            아티클
            <span className="sub">{ARTICLES.length} articles</span>
          </h1>
        </div>
      </header>

      <ArticleList articles={cards} />
    </main>
  );
}
