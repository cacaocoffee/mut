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

/** 한 페이지 분량 — 머리기사(2칸)+측면 1 이 첫 줄, 나머지 12장이 3장씩 네 줄. */
const PAGE_SIZE = 14;

/**
 * 아티클 카드 그리드 + 카테고리 색인 + 페이지네이션
 * (`FR-CONTENT-001` 앞당김 · ADR-0010).
 *
 * 143편을 한 페이지에 다 이어 붙이면 끝이 없어서 페이지로 나눈다. 페이지마다
 * 첫 글이 머리기사(2칸)다. 상세 URL 143개가 발행일과 함께 사이트맵에 다
 * 포함되어 있으므로, 목록이 첫 페이지만 그려도 크롤러 색인에는 문제가 없다.
 *
 * 필터는 칩이 아니라 잡지 색인 띠다 — 고른 항목에 밑줄이 남는다.
 */
export function ArticleList({ articles }: { articles: ArticleCardData[] }) {
  const [cat, setCat] = useState<ArticleCategory | "all">("all");
  const [page, setPage] = useState(1);

  const filtered = cat === "all" ? articles : articles.filter((a) => a.category === cat);
  const pageCount = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const current = Math.min(page, pageCount);
  const shown = filtered.slice((current - 1) * PAGE_SIZE, current * PAGE_SIZE);

  function selectCategory(next: ArticleCategory | "all") {
    setCat(next);
    setPage(1);
  }

  function goTo(next: number) {
    setPage(next);
    window.scrollTo({ top: 0 });
  }

  return (
    <>
      <div className="article-index" role="group" aria-label="아티클 카테고리">
        <button
          type="button"
          className="article-index__btn"
          aria-pressed={cat === "all"}
          onClick={() => selectCategory("all")}
        >
          전체 <span className="count">{articles.length}</span>
        </button>
        {CATEGORIES.map((c) => (
          <button
            key={c}
            type="button"
            className="article-index__btn"
            aria-pressed={cat === c}
            onClick={() => selectCategory(c)}
          >
            {ARTICLE_CATEGORY_KO[c]}{" "}
            <span className="count">{articles.filter((a) => a.category === c).length}</span>
          </button>
        ))}
      </div>

      <div className="card-grid">
        {shown.map((a, i) => (
          <Link
            key={a.slug}
            href={`${ARTICLES_PATH}/${a.slug}`}
            className={i === 0 ? "article-card article-card--lead" : "article-card"}
          >
            {/* 카드 사진은 고정 크롭이다 — 목록의 줄맞춤이 원본 비율보다 중요하다.
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

      {pageCount > 1 && (
        <nav className="article-pagenav" aria-label="아티클 페이지">
          <button
            type="button"
            className="article-pagenav__btn"
            disabled={current === 1}
            onClick={() => goTo(current - 1)}
          >
            ← 이전
          </button>
          {Array.from({ length: pageCount }, (_, i) => i + 1).map((n) => (
            <button
              key={n}
              type="button"
              className="article-pagenav__btn article-pagenav__btn--num"
              aria-current={n === current ? "page" : undefined}
              onClick={() => goTo(n)}
            >
              {n}
            </button>
          ))}
          <button
            type="button"
            className="article-pagenav__btn"
            disabled={current === pageCount}
            onClick={() => goTo(current + 1)}
          >
            다음 →
          </button>
        </nav>
      )}
    </>
  );
}
