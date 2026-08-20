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

/** 바깥을 기다리는 한도. 넘으면 라틴 글꼴로 그린다 — 안 나오는 것보다 낫다. */
const FONT_TIMEOUT_MS = 3000;

/**
 * 같은 글자 묶음은 한 번만 받는다.
 *
 * 카드는 대부분 빌드 때 그려지지만(`opengraph-image.tsx` 의 `generateStaticParams`)
 * 49종을 잇달아 그리는 동안 같은 조각을 여러 번 받을 이유가 없다. 지워진 링크로 들어와
 * 요청 시 그리는 경우에도 같은 이름이면 두 번째부터는 바깥을 안 부른다.
 *
 * 프로세스가 살아 있는 동안만이다 — 디스크에 남기지 않는다.
 */
const fontCache = new Map<string, ArrayBuffer | null>();

/**
 * 한글이 나오려면 한글 글꼴이 필요하다.
 *
 * `next/og` 가 들고 있는 글꼴은 라틴뿐이라 그대로 두면 이름이 네모로 나온다.
 * **쓸 글자만** 받아 온다 (`text=` 파라미터) — 폰트 한 벌은 5.9MB 인데 몇 글자짜리
 * 조각은 몇 KB 다 (RED 24). 이미 `next/font/google` 이 같은 곳을 부르고 있다.
 *
 * ## 바깥을 부르는 자리다
 *
 * 그래서 **한도를 둔다.** 예전에는 타임아웃도 캐시도 없어서, 구글이 느리면 카드 요청이
 * 그만큼 매달려 있었다. 실패하면 `null` 이고 카드는 라틴 글꼴로 그려진다 — 글꼴 하나
 * 때문에 공유 카드가 통째로 없어지지 않는다 (RED 23).
 *
 * 한 벌을 리포에 넣지 않는 이유: 전체는 5.9MB 라 서버리스 번들에 얹기 어렵고,
 * 상용 한글 2350자만 잘라 달라고 하면 `text=` 가 21KB 라 구글이 400 을 준다.
 * 잘라 둔 파일을 직접 만들어 넣는 것은 [G-43](../../../docs/prd/GAPS.md) 에 남겼다.
 */
async function koreanFont(text: string): Promise<ArrayBuffer | null> {
  // 글자 종류만 같으면 같은 조각이다. 순서와 중복은 상관없다.
  const key = [...new Set(text)].sort().join("");
  const hit = fontCache.get(key);
  if (hit !== undefined) return hit;

  const font = await fetchFont(key);
  fontCache.set(key, font);
  return font;
}

async function fetchFont(text: string): Promise<ArrayBuffer | null> {
  try {
    const api = `https://fonts.googleapis.com/css2?family=Noto+Sans+KR:wght@700&text=${encodeURIComponent(text)}`;
    const css = await fetch(api, {
      // 이 UA 여야 구글이 woff2 대신 ttf 를 준다. Satori 는 woff2 를 못 읽는다.
      headers: { "User-Agent": "Mozilla/5.0 (compatible; Satori)" },
      signal: AbortSignal.timeout(FONT_TIMEOUT_MS),
    }).then((r) => r.text());

    const url = css.match(/src:\s*url\((.+?)\)/)?.[1];
    if (!url) return null;

    const buf = await fetch(url, { signal: AbortSignal.timeout(FONT_TIMEOUT_MS) }).then((r) =>
      r.arrayBuffer()
    );

    // 받은 것이 글꼴인지 본다. 오류 문서를 그대로 넘기면 Satori 가 던지고 카드가 없어진다
    return isFont(buf) ? buf : null;
  } catch {
    return null;
  }
}

/** ttf(`\0\1\0\0`) · `true` · otf(`OTTO`) 만 받는다. woff2 는 Satori 가 못 읽는다. */
function isFont(buf: ArrayBuffer): boolean {
  if (buf.byteLength < 4) return false;
  const tag = new Uint8Array(buf, 0, 4);
  const hex = [...tag].map((b) => b.toString(16).padStart(2, "0")).join("");
  return hex === "00010000" || hex === "74727565" || hex === "4f54544f";
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
