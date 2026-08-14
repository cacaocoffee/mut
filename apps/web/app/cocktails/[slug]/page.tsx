import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { AXES, AXES_KO, COCKTAILS, relatedCocktails as prototypeRelated, getCocktail } from "@kca/domain";
import { DetailActions } from "@/components/detail-actions";
import { FlavorRadar } from "@/components/flavor-radar";
import { PhotoSlot } from "@/components/photo-slot";
import { cocktailDetail, publishedSlugs, relatedCocktails, usingApi, type RelatedItem } from "@/lib/api";
import { fromApi, fromPrototype, prototypeSlugs, type CocktailView } from "@/lib/cocktail-view";

/**
 * 칵테일 상세 — **SSG + ISR** (ISSUE-038 · `PRIN-T04` · SPEC-05 §4).
 *
 * ## 요청 시 렌더하지 않는다
 *
 * `PRIN-T04` 가 SEO 경로를 정적 우선으로 못박았다 — 검색 유입이 초기 성장의 절반이다
 * (PRD 2.2 유기 검색 50% 이상). `dynamic = "force-dynamic"` 을 쓰지 않는다.
 *
 * 그 선택의 절반은 `NFR-R-01` 이다: **API 가 죽어도 정적 페이지는 살아 있다.**
 * 빌드 시점에 HTML 이 만들어져 있어 런타임에 API 를 부르지 않는다.
 *
 * ## 재생성은 발행이 민다
 *
 * 주 경로는 어드민 발행 → API → `/api/revalidate` 다 (SPEC-07 §4).
 * 아래 `revalidate` 는 그 훅이 실패했을 때를 위한 폴백이다 — 한 시간이면
 * `NFR-O-02`(30초)를 못 지키지만, **아무 갱신도 없는 것보다는 낫다.**
 */
export const revalidate = 3600;

/**
 * **미리 만든 것만 존재한다.**
 *
 * 기본값(`true`)이면 모르는 슬러그도 요청 때 렌더한다. 그런데 이 앱에는 `loading.tsx` 가
 * 있어서 **셸이 먼저 나가고 상태 코드가 200 으로 굳는다** — 그 뒤에 `notFound()` 를 불러도
 * 늦다. 크롤러에게는 "없는 칵테일" 이 200 으로 보이고, 그것이 soft 404 다.
 *
 * `false` 면 목록에 없는 슬러그는 렌더를 시작하기 전에 404 다.
 * 발행 목록이 늘면 재생성 훅이 경로를 밀어 준다 (SPEC-07 §4).
 */
export const dynamicParams = false;

/** 발행분만 정적 생성한다. `draft` 는 공개 API 가 주지 않는다 (이슈 020 RED 3). */
export async function generateStaticParams() {
  const slugs = usingApi ? await publishedSlugs() : prototypeSlugs(COCKTAILS);
  return slugs.map((slug) => ({ slug }));
}

/**
 * 배리에이션. API 주소가 없으면 프로토타입의 같은 판정을 쓴다 (`relatedCocktails`).
 * `matchedOn` 은 계약만 주므로 폴백에서는 무엇이 같은지 직접 적는다.
 */
async function loadRelated(slug: string): Promise<RelatedItem[]> {
  if (usingApi) return relatedCocktails(slug);

  const c = getCocktail(slug);
  if (!c) return [];

  return prototypeRelated(c).map((r) => ({
    slug: r.id,
    nameKo: r.ko,
    nameEn: r.en,
    summary: r.summary,
    matchedOn: "같은 기주",
  }));
}

async function load(slug: string): Promise<CocktailView | null> {
  if (!usingApi) return fromPrototype(slug);

  const detail = await cocktailDetail(slug);
  return detail ? fromApi(detail) : null;
}

export async function generateMetadata({
  params,
}: PageProps<"/cocktails/[slug]">): Promise<Metadata> {
  const { slug } = await params;
  const c = await load(slug);
  if (!c) return {};

  // 색인 대상이다 (`NFR-S-01`). `noindex` 를 붙이지 않는다.
  return {
    title: `${c.nameKo} ${c.nameEn}`,
    description: c.tastingNote ?? c.summary,
    alternates: { canonical: `/cocktails/${c.slug}` },
    openGraph: {
      title: `${c.nameKo} · ${c.nameEn}`,
      description: c.tastingNote ?? c.summary,
      type: "article",
    },
  };
}

/** Schema.org Recipe — 구글 리치 결과 근거 (`R-F1.1-6`). 확장은 이슈 044 다. */
function recipeJsonLd(c: CocktailView) {
  return {
    "@context": "https://schema.org",
    "@type": "Recipe",
    name: `${c.nameKo} ${c.nameEn}`,
    description: c.tastingNote ?? c.summary,
    recipeCategory: "Cocktail",
    recipeCuisine: c.base.labelKo,
    recipeYield: "1 serving",
    keywords: [c.base.labelKo, c.method.labelKo, c.glassType, ...c.aromaTags.map((t) => t.labelKo)].join(", "),
    recipeIngredient: c.ingredients.map((i) => `${i.amount} ${i.nameKo}`.trim()),
    recipeInstructions: c.steps.map((text, i) => ({
      "@type": "HowToStep",
      position: i + 1,
      text,
    })),
  };
}

export default async function CocktailDetailPage({ params }: PageProps<"/cocktails/[slug]">) {
  const { slug } = await params;
  const c = await load(slug);
  if (!c) notFound();

  const related = await loadRelated(slug);

  const specs = [
    { label: "ABV", value: c.abv != null ? `${c.abv}%` : "—", sub: "표준 배합 기준" },
    { label: "STYLE", value: c.stylePrimary.labelKo, sub: "대표 스타일" },
    { label: "GLASSWARE", value: c.glassType, sub: "권장 잔" },
    { label: "TECHNIQUE", value: c.method.slug, sub: c.method.labelKo },
    { label: "SWEETNESS", value: c.sweetness.en, sub: `${c.sweetness.ko} · 4단계` },
  ];

  return (
    <main className="shell">
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(recipeJsonLd(c)) }}
      />

      <div className="detail-hero">
        <div className="grayscale photo-slot photo-slot--4x5">
          <div className="photo-slot__label">HERO IMAGE 4:5 — PLACEHOLDER</div>
          <div className="photo-slot__caption">{c.nameEn}</div>
        </div>
        <div>
          <Link
            href="/"
            className="btn btn-ghost"
            style={{ fontSize: 11, paddingLeft: 0, marginBottom: 14 }}
          >
            ← 탐색으로 BACK TO SEARCH
          </Link>
          <h1>{c.nameKo}</h1>
          <div className="en">{c.nameEn}</div>
          <p className="summary">{c.summary}</p>
          <dl className="spec-strip">
            {specs.map((s) => (
              <div key={s.label}>
                <dt>{s.label}</dt>
                <dd>
                  {s.value}
                  <div className="sub">{s.sub}</div>
                </dd>
              </div>
            ))}
          </dl>

          {/* `FR-COCKTAIL-018` · `R-C-2` — 3축 각각이 카테고리 페이지로 간다.
              **축 조합 링크를 만들지 않는다** (`NFR-S-03`) — 조합은 색인 대상이 아니다. */}
          <nav className="detail-taxa" aria-label="분류">
            <Link href={`/cocktails/base/${c.base.slug}`} className="btn btn-ghost">
              {c.base.labelKo} BASE
            </Link>
            <Link href={`/cocktails/style/${c.stylePrimary.slug}`} className="btn btn-ghost">
              {c.stylePrimary.labelKo}
            </Link>
            <Link href={`/cocktails/method/${c.method.slug}`} className="btn btn-ghost">
              {c.method.labelKo}
            </Link>
          </nav>

          {/* `FR-COCKTAIL-017` 여덟째 블록 (`FR-COCKTAIL-027`). 대조는 P2 라 없다. */}
          <DetailActions
            targetType={c.actions.targetType}
            targetSlug={c.actions.targetSlug}
            sharePath={c.actions.sharePath}
            nameKo={c.nameKo}
          />
        </div>
      </div>

      <div className="detail-body">
        <section>
          <h4 className="section-head section-head--flush">재료 INGREDIENTS</h4>
          <table className="table">
            <tbody>
              {c.ingredients.map((i) => (
                <tr key={`${i.nameKo}-${i.amount}`}>
                  <th scope="row" style={{ width: "58%", fontWeight: 400 }}>
                    {i.nameKo}
                    <span className="en" style={{ display: "block", fontSize: 11 }}>
                      {i.nameEn}
                    </span>
                    {i.substitute && <p className="sub">대체 · {i.substitute}</p>}
                  </th>
                  <td style={{ fontWeight: 500 }}>{i.amount}</td>
                </tr>
              ))}
            </tbody>
          </table>

          <h4 className="section-head">제조 순서 METHOD</h4>
          <ol style={{ listStyle: "none", margin: 0, padding: 0 }}>
            {c.steps.map((text, i) => (
              <li className="step" key={text}>
                <div className="n">{String(i + 1).padStart(2, "0")}</div>
                <p>{text}</p>
              </li>
            ))}
          </ol>
        </section>

        <section>
          {/* `GATE-COCKTAIL-01` 이 발행 필수로 만든 블록이다 — `PRIN-P03` 의 핵심. */}
          {c.tastingNote && (
            <>
              <h4 className="section-head section-head--flush">향과 맛 TASTING</h4>
              <p className="summary">{c.tastingNote}</p>
              <div className="tag-row">
                {c.aromaTags.map((tag) => (
                  <span className="tag" key={tag.slug}>
                    {tag.labelKo}
                  </span>
                ))}
              </div>
            </>
          )}

          {c.profile && (
            <>
              <h4 className="section-head">맛 프로필 FLAVOR PROFILE</h4>
              <FlavorRadar profile={c.profile as [number, number, number, number, number]} title={c.nameKo} />
              <div style={{ borderTop: "2px solid var(--color-divider)" }}>
                {c.profile.map((v, i) => (
                  <div className="profile-row" key={AXES[i]}>
                    <span className="label">
                      {AXES_KO[i]} {AXES[i]}
                    </span>
                    <span className="meter">
                      <span style={{ width: `${(v / 5) * 100}%` }} />
                    </span>
                    <span className="val">{v}/5</span>
                  </div>
                ))}
              </div>
            </>
          )}

          {c.origin && (
            <>
              <h4 className="section-head">기록 ORIGIN</h4>
              <table className="table">
                <tbody>
                  {[
                    { label: "최초 기록", value: c.origin.year },
                    { label: "지역", value: c.origin.place },
                    { label: "고안자", value: c.origin.creator },
                    {
                      label: "분류",
                      value: `${c.base.labelKo} 베이스 · ${c.styles
                        .map((s) => s.labelKo)
                        .join(" · ")} · ${c.method.labelKo}`,
                    },
                  ]
                    .filter((row) => row.value)
                    .map((row) => (
                      <tr key={row.label}>
                        <th
                          scope="row"
                          style={{
                            width: "38%",
                            borderBottom: "1px solid var(--color-divider)",
                            fontWeight: 400,
                          }}
                        >
                          {row.label}
                        </th>
                        <td style={{ fontWeight: 500 }}>{row.value}</td>
                      </tr>
                    ))}
                </tbody>
              </table>
            </>
          )}
        </section>
      </div>

      {c.story && (
        <div className="editorial">
          <div>
            {c.story.title && <h2>{c.story.title}</h2>}
            {c.story.paragraphs.map((p) => (
              <p key={p}>{p}</p>
            ))}
          </div>
          <div>
            <figure>
              <PhotoSlot ratio="3x2" label="EDITORIAL IMAGE 3:2 — PLACEHOLDER" />
              <figcaption>
                에디토리얼 이미지 자리. 사진은 흑백(grayscale)으로 출력됩니다.
              </figcaption>
            </figure>

            {/* 배리에이션 (`FR-COCKTAIL-024` · 이슈 021). 상세 화면의 일부라 여기서 렌더한다 —
                별도 화면을 만들면 "비슷한 것" 을 보러 한 번 더 이동해야 한다. */}
            {related.length > 0 && (
              <div
                style={{ marginTop: 24, borderTop: "2px solid var(--color-divider)", paddingTop: 16 }}
              >
                <h6 style={{ margin: "0 0 10px" }}>같은 기주 RELATED</h6>
                <div style={{ display: "flex", flexDirection: "column" }}>
                  {related.map((r) => (
                    <Link key={r.slug} href={`/cocktails/${r.slug}`} className="btn related-link">
                      <span className="name">
                        {r.nameKo}
                        <span>{r.nameEn}</span>
                      </span>
                      <span className="meta">{r.matchedOn}</span>
                    </Link>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </main>
  );
}
