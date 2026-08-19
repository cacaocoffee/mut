import { ImageResponse } from "next/og";

/**
 * 공유 카드 한 장 (ISSUE-044 · `FR-USER-005` · `NFR-S-06`).
 *
 * ## 사진이 없어 이름으로 그린다
 *
 * 히어로 사진을 담을 곳이 아직 없다 — 이미지 저장소가 미정이라(G-07) `media_asset` 이
 * 구현되지 않았다. 사진이 생길 때까지 **이름과 분류로 카드를 그린다.** 카카오톡 공유에서
 * 링크만 나오는 것보다 낫다 (GAPS G-36).
 *
 * ## 1200×630 이다
 *
 * 카카오톡·오픈그래프 공용 권장비(1.91:1)다. 카카오는 800×400 도 받지만 트위터·슬랙까지
 * 한 장으로 덮으려면 이 크기가 안전하다 (RED 21 결정).
 *
 * ## 색은 시안에서 온다
 *
 * Satori 는 CSS 변수를 모르므로 값을 적는다. **시안 원본의 hex 그대로**이고
 * (`scripts/color-parity.mjs` 의 BASELINE) 어긋나면 `e2e/opengraph.spec.ts` 가 잡는다.
 * `packages/ui` 는 건드리지 않는다 (CONVENTIONS §4).
 */
export const OG_SIZE = { width: 1200, height: 630 };

/** 시안 토큰의 hex. `--color-bg` · `--color-text` · `--color-accent` · `--color-neutral-700`. */
export const OG_PALETTE = {
  bg: "#f3f2f2",
  text: "#201e1d",
  accent: "#ec3013",
  muted: "#605d5d",
};

/**
 * 한글이 나오려면 한글 글꼴이 필요하다.
 *
 * `next/og` 가 들고 있는 글꼴은 라틴뿐이라 그대로 두면 이름이 네모로 나온다.
 * **쓸 글자만** 받아 온다 (`text=` 파라미터) — 폰트 한 벌이 아니라 몇 글자짜리 조각이라
 * 빌드가 느려지지 않는다 (RED 24). 이미 `next/font/google` 이 같은 곳을 부르고 있다.
 *
 * 실패하면 `null` 이다 — 글꼴 하나 때문에 공유 카드가 통째로 없어지지 않는다 (RED 23).
 */
async function koreanFont(text: string): Promise<ArrayBuffer | null> {
  try {
    const api = `https://fonts.googleapis.com/css2?family=Noto+Sans+KR:wght@700&text=${encodeURIComponent(text)}`;
    const css = await fetch(api, {
      // 이 UA 여야 구글이 woff2 대신 ttf 를 준다. Satori 는 woff2 를 못 읽는다.
      headers: { "User-Agent": "Mozilla/5.0 (compatible; Satori)" },
    }).then((r) => r.text());

    const url = css.match(/src:\s*url\((.+?)\)/)?.[1];
    if (!url) return null;

    return await fetch(url).then((r) => r.arrayBuffer());
  } catch {
    return null;
  }
}

export interface OgCard {
  /** 큰 글자. 칵테일 이름이거나 사이트 이름이다. */
  nameKo: string;
  /** 그 아래 라틴 표기. */
  nameEn: string;
  /** 맨 아래 한 줄 — 기주 · 스타일 · 도수처럼 카드에서 바로 읽히는 것. */
  meta: string;
}

export async function renderOgCard({ nameKo, nameEn, meta }: OgCard): Promise<ImageResponse> {
  const font = await koreanFont(`${nameKo}${meta}칵테일아카이브`);

  return new ImageResponse(
    (
      <div
        style={{
          width: "100%",
          height: "100%",
          display: "flex",
          flexDirection: "column",
          justifyContent: "space-between",
          background: OG_PALETTE.bg,
          color: OG_PALETTE.text,
          padding: 72,
          // 시안의 굵은 가로선 하나. 글자를 못 읽어도 어느 사이트인지 알아보게 한다.
          borderTop: `24px solid ${OG_PALETTE.accent}`,
        }}
      >
        <div style={{ display: "flex", fontSize: 28, letterSpacing: 4, color: OG_PALETTE.muted }}>
          MUT
        </div>

        <div style={{ display: "flex", flexDirection: "column" }}>
          <div style={{ fontSize: 96, fontWeight: 700, lineHeight: 1.1 }}>{nameKo}</div>
          <div style={{ fontSize: 40, letterSpacing: 6, color: OG_PALETTE.muted, marginTop: 12 }}>
            {nameEn.toUpperCase()}
          </div>
        </div>

        <div style={{ display: "flex", fontSize: 32, color: OG_PALETTE.accent }}>{meta}</div>
      </div>
    ),
    {
      ...OG_SIZE,
      fonts: font
        ? [{ name: "Noto Sans KR", data: font, weight: 700 as const, style: "normal" as const }]
        : undefined,
    },
  );
}
