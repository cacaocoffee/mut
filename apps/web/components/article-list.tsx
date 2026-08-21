"use client";

import { useState } from "react";
import Link from "next/link";
import type { ArticleCategory } from "@mut/domain";
import { ARTICLE_CATEGORY_KO } from "@mut/domain";
import { ARTICLES_PATH } from "@/lib/routes";

/**
 * 목록 카드가 쓰는 것만 받는다 — 본문(`blocks`)까지 클라이언트에 실을 이유가 없다.
 * 날짜 표기는 서버에서 만들어 문자열로 받는다.
 */
export interface ArticleCardData {
  slug: string;
  title: string;
  dek: string;
  category: ArticleCategory;
  dateLabel: string;
  hero: string;
  isSponsored?: boolean;
}

const CATEGORIES: ArticleCategory[] = ["cocktail", "bar", "spirits"];

/**
 * 아티클 카드 그리드 + 카테고리 필터 (`FR-CONTENT-001` 앞당김 · ADR-0010).
 *
 * 9편일 때는 필터가 없었다 — "글이 쌓여 한 화면을 넘기면 그때 단다"고 적어 뒀고,
 * 142편이 되며 그때가 왔다. 탐색 화면의 `.chip` 필터와 같은 패턴을 쓴다
 * (`aria-pressed` · `.count`). 전체 카드는 초기 HTML 에 그대로 있으므로
 * 크롤러는 필터와 무관하게 모든 글로 가는 링크를 본다.
 */
export function ArticleList({ articles }: { articles: ArticleCardData[] }) {
  const [cat, setCat] = useState<ArticleCategory | "all">("all");
  const shown = cat === "all" ? articles : articles.filter((a) => a.category === cat);

  return (
    <>
      <div className="chip-row" role="group" aria-label="아티클 카테고리">
        <button type="button" className="chip" aria-pressed={cat === "all"} onClick={() => setCat("all")}>
          전체 <span className="count">{articles.length}</span>
        </button>
        {CATEGORIES.map((c) => (
          <button key={c} type="button" className="chip" aria-pressed={cat === c} onClick={() => setCat(c)}>
            {ARTICLE_CATEGORY_KO[c]}{" "}
            <span className="count">{articles.filter((a) => a.category === c).length}</span>
          </button>
        ))}
      </div>

      <div className="card-grid">
        {shown.map((a) => (
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
            {/* 협찬 글 표기는 데이터 플래그로만 켜진다 — 끌 수 없다 (`NFR-L-02` · 배포 차단) */}
            <span className="article-card__kicker">
              {ARTICLE_CATEGORY_KO[a.category]}
              {a.isSponsored && " · 제휴 콘텐츠"}
            </span>
            <h3 className="article-card__title">{a.title}</h3>
            <p className="article-card__dek">{a.dek}</p>
            <span className="article-card__date">{a.dateLabel}</span>
          </Link>
        ))}
      </div>
    </>
  );
}
