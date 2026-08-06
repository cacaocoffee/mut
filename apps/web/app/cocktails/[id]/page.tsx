import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import {
  AXES,
  AXES_KO,
  COCKTAILS,
  STYLE_LABELS,
  SWEETNESS,
  TECHNIQUES,
  getCocktail,
  relatedCocktails,
  type Cocktail,
} from "@kca/domain";
import { FlavorRadar } from "@/components/flavor-radar";
import { PhotoSlot } from "@/components/photo-slot";
import { RecipePanel } from "@/components/recipe-panel";

export function generateStaticParams() {
  return COCKTAILS.map((c) => ({ id: c.id }));
}

export async function generateMetadata({ params }: PageProps<"/cocktails/[id]">): Promise<Metadata> {
  const { id } = await params;
  const cocktail = getCocktail(id);
  if (!cocktail) return {};

  return {
    title: `${cocktail.ko} ${cocktail.en}`,
    description: cocktail.summary,
    openGraph: {
      title: `${cocktail.ko} · ${cocktail.en}`,
      description: cocktail.summary,
      type: "article",
    },
  };
}

/** Schema.org Recipe — 구글 리치 결과 노출 근거 (PRD R-F1.1-6). */
function recipeJsonLd(c: Cocktail) {
  return {
    "@context": "https://schema.org",
    "@type": "Recipe",
    name: `${c.ko} ${c.en}`,
    description: c.summary,
    recipeCategory: "Cocktail",
    recipeCuisine: c.base,
    recipeYield: "1 serving",
    keywords: [c.base, c.method, c.glass, ...c.flavors].join(", "),
    recipeIngredient: c.ingredients.map((i) =>
      i.amount ? `${i.amount} ${i.ko}` : `${i.ml}ml ${i.ko}`
    ),
    recipeInstructions: c.steps.map((text, i) => ({
      "@type": "HowToStep",
      position: i + 1,
      text,
    })),
  };
}

export default async function CocktailDetailPage({ params }: PageProps<"/cocktails/[id]">) {
  const { id } = await params;
  const cocktail = getCocktail(id);
  if (!cocktail) notFound();

  const [sweetKo, sweetEn] = SWEETNESS[cocktail.sweet];
  const related = relatedCocktails(cocktail);

  const specs = [
    { label: "ABV", value: `${cocktail.abv}%`, sub: "표준 배합 기준" },
    { label: "STYLE", value: STYLE_LABELS[cocktail.stylePrimary], sub: "대표 스타일" },
    { label: "GLASSWARE", value: cocktail.glass, sub: "권장 잔" },
    { label: "TECHNIQUE", value: cocktail.method, sub: TECHNIQUES[cocktail.method].ko },
    {
      label: "SWEETNESS",
      value: sweetEn,
      sub: `${sweetKo} · 4단계 중 ${cocktail.sweet + 1}`,
    },
  ];

  return (
    <main className="shell">
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(recipeJsonLd(cocktail)) }}
      />

      <div className="detail-hero">
        <div className="grayscale photo-slot photo-slot--4x5">
          <div className="photo-slot__label">HERO IMAGE 4:5 — PLACEHOLDER</div>
          <div className="photo-slot__caption">{cocktail.en}</div>
        </div>
        <div>
          <Link
            href="/"
            className="btn btn-ghost"
            style={{ fontSize: 11, paddingLeft: 0, marginBottom: 14 }}
          >
            ← 탐색으로 BACK TO SEARCH
          </Link>
          <h6 style={{ color: "var(--color-accent-700)", margin: "0 0 8px" }}>
            {cocktail.base} BASE · {sweetEn}
          </h6>
          <h1>{cocktail.ko}</h1>
          <div className="en">{cocktail.en}</div>
          <p className="summary">{cocktail.summary}</p>
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
        </div>
      </div>

      <div className="detail-body">
        <section>
          <RecipePanel cocktail={cocktail} />

          <h4 style={{ margin: "34px 0 0", paddingBottom: 12, borderBottom: "2px solid var(--color-divider)" }}>
            제조 순서 METHOD
          </h4>
          <ol style={{ listStyle: "none", margin: 0, padding: 0 }}>
            {cocktail.steps.map((text, i) => (
              <li className="step" key={text}>
                <div className="n">{String(i + 1).padStart(2, "0")}</div>
                <p>{text}</p>
              </li>
            ))}
          </ol>
        </section>

        <section>
          <h4 style={{ margin: 0, paddingBottom: 12, borderBottom: "2px solid var(--color-divider)" }}>
            맛 프로필 FLAVOR PROFILE
          </h4>
          <FlavorRadar profile={cocktail.profile} title={cocktail.ko} />
          <div style={{ borderTop: "2px solid var(--color-divider)" }}>
            {cocktail.profile.map((v, i) => (
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

          <h4 style={{ margin: "34px 0 0", paddingBottom: 12, borderBottom: "2px solid var(--color-divider)" }}>
            기록 ORIGIN
          </h4>
          <table className="table">
            <tbody>
              {[
                { label: "최초 기록", value: cocktail.origin.year },
                { label: "지역", value: cocktail.origin.place },
                { label: "고안자", value: cocktail.origin.creator },
                {
                  label: "분류",
                  value: `${cocktail.base} 베이스 · ${cocktail.styles
                    .map((s) => STYLE_LABELS[s])
                    .join(" · ")} · ${cocktail.method}`,
                },
              ].map((row) => (
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
        </section>
      </div>

      <div className="editorial">
        <div>
          <h6 style={{ color: "var(--color-accent-700)", margin: "0 0 14px" }}>Story &amp; Origin</h6>
          <h2>{cocktail.story.title}</h2>
          {cocktail.story.paragraphs.map((p) => (
            <p key={p}>{p}</p>
          ))}
        </div>
        <div>
          <figure>
            <PhotoSlot ratio="3x2" label="EDITORIAL IMAGE 3:2 — PLACEHOLDER" />
            <figcaption>에디토리얼 이미지 자리. 사진은 흑백(grayscale)으로 출력됩니다.</figcaption>
          </figure>
          <div style={{ marginTop: 24, borderTop: "2px solid var(--color-divider)", paddingTop: 16 }}>
            <h6 style={{ margin: "0 0 10px" }}>같은 기주 RELATED</h6>
            <div style={{ display: "flex", flexDirection: "column" }}>
              {related.map((r) => (
                <Link key={r.id} href={`/cocktails/${r.id}`} className="btn related-link">
                  <span className="name">
                    {r.ko}
                    <span>{r.en}</span>
                  </span>
                  <span className="meta">
                    {r.abv}% · {SWEETNESS[r.sweet][1]}
                  </span>
                </Link>
              ))}
            </div>
          </div>
        </div>
      </div>
    </main>
  );
}
