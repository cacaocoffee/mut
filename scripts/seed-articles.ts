/**
 * 아티클 143편(packages/domain)을 DB 시드 SQL 로 뽑는다 (ADR-0011 2단계).
 *
 * ⚠️ 결과 파일(R__seed_03_article.sql)을 손으로 고치지 않는다 — 여기를 고치고 다시 돌린다:
 *     npx tsx scripts/seed-articles.ts
 *
 * repeatable 시드다(R__). 매번 article 을 통째로 다시 채운다 — 이관 원본은 코드이고,
 * DB 는 그 사본이다. 어드민 편집이 붙기 전까지는 이 방향이 정본이다.
 * 편집이 붙은 뒤(5단계)에는 이 시드를 멈춘다 — 그때부터 DB 가 정본이 된다.
 */
import { writeFileSync } from "node:fs";
import { ARTICLES } from "../packages/domain/src/articles";
import { COCKTAILS } from "../packages/domain/src/data";

const OUT = "apps/api/src/main/resources/db/migration/R__seed_03_article.sql";

/** 코퍼스에 실재하는 칵테일 id 만 관련 링크로 남긴다 — 없는 것을 FK 로 걸 수 없다. */
const COCKTAIL_IDS = new Set(COCKTAILS.map((c) => c.id));

function sql(s: string): string {
  return "'" + s.replace(/'/g, "''") + "'";
}

/** JSONB 리터럴. 작은따옴표만 이스케이프하면 된다 (JSON 자체는 큰따옴표를 쓴다). */
function jsonb(value: unknown): string {
  return sql(JSON.stringify(value)) + "::jsonb";
}

const lines: string[] = [
  "-- ADR-0011 2단계 — 아티클 143편 이관 시드 (repeatable).",
  "--",
  "-- ⚠️ 손으로 고치지 않는다. scripts/seed-articles.ts 를 고치고 다시 뽑는다.",
  "--    원본은 packages/domain/src/articles/*.ts 다.",
  "",
  "-- 통째로 다시 채운다. 관련 조인부터 지우고(자식), 그다음 본체.",
  "DELETE FROM article_related_cocktail;",
  "-- article 은 물리 삭제가 막혀 있어(PRIN-D05) 시드에서는 migrate 롤로 지운다.",
  "-- migrate 롤은 DDL·초기적재용이라 REVOKE 대상이 아니다.",
  "DELETE FROM article;",
  "",
];

for (const a of ARTICLES) {
  // 코드에는 발행 상태 개념이 없다 — 이관분은 전부 이미 공개된 글이므로 published 로 넣는다.
  const publishedAt = `${a.publishedAt}T00:00:00Z`;
  lines.push(
    "INSERT INTO article (slug, category, title, dek, hero, source_url, is_sponsored, body, status, published_at)",
    "VALUES (",
    `  ${sql(a.slug)}, ${sql(a.category)}, ${sql(a.title)}, ${sql(a.dek)}, ${sql(a.hero)},`,
    `  ${a.sourceUrl ? sql(a.sourceUrl) : "NULL"}, ${a.isSponsored ? "true" : "false"},`,
    `  ${jsonb(a.blocks)}, 'published', ${sql(publishedAt)}`,
    ");",
  );

  const rels = a.relatedCocktailSlugs.filter((id) => COCKTAIL_IDS.has(id));
  rels.forEach((cocktailId, i) => {
    // 슬러그(코퍼스 id)로 cocktail.id 를 찾아 잇는다. 서브쿼리라 시드 순서를 안 탄다.
    lines.push(
      "INSERT INTO article_related_cocktail (article_id, cocktail_id, position)",
      `SELECT a.id, c.id, ${i}`,
      `FROM article a, cocktail c`,
      `WHERE a.slug = ${sql(a.slug)} AND c.slug = ${sql(cocktailId)};`,
    );
  });
  lines.push("");
}

writeFileSync(OUT, lines.join("\n") + "\n");
const relCount = ARTICLES.reduce(
  (n, a) => n + a.relatedCocktailSlugs.filter((id) => COCKTAIL_IDS.has(id)).length,
  0,
);
console.log(`${OUT}: 아티클 ${ARTICLES.length}편 · 관련 칵테일 링크 ${relCount}건`);
