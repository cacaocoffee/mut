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
 * 목록 리듬의 주기. 아홉 편이 [머리기사(2칸) + 측면 1] → [표준 3] → [목록 행 4] 로
 * 지면을 이룬다. 143편을 같은 카드로 이어 붙이면 상품 목록처럼 읽혀서
 * 잡지처럼 밀도를 계속 바꾼다. 모양은 `app.css` 의 `.article-card--lead`·`--row` 가 맡는다.
 */
const CYCLE = 9;

type Role = "lead" | "side" | "std" | "row";

function roleOf(index: number): Role {
  const r = index % CYCLE;
  if (r === 0) return "lead";
  if (r === 1) return "side";
  if (r <= 4) return "std";
  return "row";
}

/**
 * 아티클 카드 그리드 + 카테고리 색인 (`FR-CONTENT-001` 앞당김 · ADR-0010).
 *
 * 필터는 칩이 아니라 잡지 색인 띠다 — 고른 항목에 밑줄이 남는다.
 * 전체 카드는 초기 HTML 에 그대로 있으므로 크롤러는 필터와 무관하게
 * 모든 글로 가는 링크를 본다.
 */
export function ArticleList({ articles }: { articles: ArticleCardData[] }) {
  const [cat, setCat] = useState<ArticleCategory | "all">("all");
  const shown = cat === "all" ? articles : articles.filter((a) => a.category === cat);

  // 짝수 주기는 머리기사가 왼쪽, 홀수 주기는 오른쪽이다. 그리드 자동 배치가
  // 순서대로 채우므로 렌더 순서만 바꾸면 된다 — 역할(role)은 원래 자리를 따른다.
  const seq = shown.map((a, i) => ({ a, role: roleOf(i) }));
  for (let i = CYCLE; i + 1 < seq.length; i += CYCLE * 2) {
    [seq[i], seq[i + 1]] = [seq[i + 1], seq[i]];
  }

  return (
    <>
      <div className="article-index" role="group" aria-label="아티클 카테고리">
        <button
          type="button"
          className="article-index__btn"
          aria-pressed={cat === "all"}
          onClick={() => setCat("all")}
        >
          전체 <span className="count">{articles.length}</span>
        </button>
        {CATEGORIES.map((c) => (
          <button
            key={c}
            type="button"
            className="article-index__btn"
            aria-pressed={cat === c}
            onClick={() => setCat(c)}
          >
            {ARTICLE_CATEGORY_KO[c]}{" "}
            <span className="count">{articles.filter((a) => a.category === c).length}</span>
          </button>
        ))}
      </div>

      <div className="card-grid">
        {seq.map(({ a, role }) =>
          role === "row" ? (
            <Link key={a.slug} href={`${ARTICLES_PATH}/${a.slug}`} className="article-card article-card--row">
              <span className="article-card__kicker">
                {ARTICLE_CATEGORY_KO[a.category]}
                {a.isSponsored && " · 제휴 콘텐츠"}
              </span>
              <h3 className="article-card__title">{a.title}</h3>
              <span className="article-card__date">{a.dateLabel}</span>
            </Link>
          ) : (
            <Link
              key={a.slug}
              href={`${ARTICLES_PATH}/${a.slug}`}
              className={role === "lead" ? "article-card article-card--lead" : "article-card"}
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
          ),
        )}
      </div>
    </>
  );
}
