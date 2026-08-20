import { BASE_SPIRIT_LABELS, COCKTAILS, STYLE_KEY_LABELS, getCocktail } from "@mut/domain";
import { cocktailDetail, publishedSlugs, usingApi } from "@/lib/api";
import { OG_SIZE, renderOgCard } from "@/lib/og-card";

/** 칵테일 공유 카드 (ISSUE-044 · `FR-USER-005`). 카드 자체는 `lib/og-card.tsx` 다. */
export const size = OG_SIZE;
export const contentType = "image/png";
export const alt = "MUT";

/**
 * **미리 그려 둔다.**
 *
 * 예전에는 이 경로가 요청마다 그려졌다(`ƒ`). 카드에 한글을 쓰려면 글꼴이 필요하고
 * `lib/og-card.tsx` 가 그것을 구글에서 받아 오는데, 그러면 **카카오·트위터 크롤러가
 * 올 때마다 바깥으로 두 번 왕복한다.** 상세 화면은 SSG 인데(`page.tsx`) 그 카드만
 * 요청 시 렌더였다.
 *
 * 발행분은 빌드 때 한 번 그린다. 글꼴 왕복도 그때 한 번이고, 크롤러는 만들어 둔 파일을
 * 받는다.
 *
 * ## `dynamicParams` 를 끄지 않는다
 *
 * 상세 화면(`page.tsx`)은 없는 슬러그를 404 로 막지만 **카드는 그럴 수 없다** — 지워진
 * 링크에도 미리보기 요청이 온다 (RED 23). 모르는 슬러그는 그 자리에서 사이트 카드로
 * 답한다.
 */
export async function generateStaticParams() {
  const slugs = usingApi ? await publishedSlugs() : COCKTAILS.map((c) => c.id);
  return slugs.map((slug) => ({ slug }));
}

/** 이름·분류만 있으면 된다. 상세 전체를 받을 이유가 없다. */
async function card(slug: string) {
  if (usingApi) {
    const detail = await cocktailDetail(slug);
    if (!detail) return null;

    return {
      nameKo: detail.hero.nameKo,
      nameEn: detail.hero.nameEn,
      meta: [
        detail.classification.base.labelKo,
        detail.classification.stylePrimary.labelKo,
        detail.spec.abv != null ? `${detail.spec.abv}%` : null,
      ]
        .filter(Boolean)
        .join(" · "),
    };
  }

  const c = getCocktail(slug);
  if (!c) return null;

  // 슬러그가 아니라 한국어다 — 카드에 `gin` 이 찍히면 그대로 공유된다.
  return {
    nameKo: c.ko,
    nameEn: c.en,
    meta: [BASE_SPIRIT_LABELS[c.base], STYLE_KEY_LABELS[c.stylePrimary], `${c.abv}%`].join(" · "),
  };
}

export default async function OpengraphImage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const c = await card(slug);

  // 없는 슬러그에도 미리보기 요청이 온다 (지워진 링크). 빈 이미지 대신 사이트 카드로 답한다.
  return renderOgCard(
    c ?? {
      nameKo: "당신의 취향, 당신의 멋",
      nameEn: "MUT",
      meta: "칵테일 · 재료 아카이브",
    },
  );
}
