import { BASE_SPIRIT_LABELS, STYLE_KEY_LABELS, getCocktail } from "@mut/domain";
import { cocktailDetail, usingApi } from "@/lib/api";
import { OG_SIZE, renderOgCard } from "@/lib/og-card";

/** 칵테일 공유 카드 (ISSUE-044 · `FR-USER-005`). 카드 자체는 `lib/og-card.tsx` 다. */
export const size = OG_SIZE;
export const contentType = "image/png";
export const alt = "MUT";

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
