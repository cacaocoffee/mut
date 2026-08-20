/**
 * 사진이 있는 칵테일 슬러그 목록 (ISSUE-060 #139).
 *
 * 파일은 `public/cocktails/{slug}.webp` 에 있다 (D-6 · DECISIONS §1.12 — Phase 1a 한시).
 * 카드 화면이 전부 클라이언트 컴포넌트라 빌드 시 디렉터리를 읽을 수 없어 목록을 코드로 둔다.
 * **목록과 디렉터리가 어긋나면 `scripts/image-guard.mjs` 가 `npm run check` 에서 막는다** —
 * 여기 한 줄을 더하거나 뺄 때 파일도 함께 움직인다.
 *
 * `Cocktail` 타입에 이미지 필드를 두지 않는 것은 #87 의 금지 그대로다.
 * 최종 형태는 레코드가 `hero_media_id` 로 들고 어드민에서 올리는 것이다 (G-46).
 */
const PHOTO_SLUGS = new Set([
  "adonis",
  "bamboo",
  "bananaboulevardier",
  "beeramericano",
  "betweenthesheets",
  "bnb",
  "bobbyburns",
  "bostoncooler",
  "caipiroska",
  "calvadostonic",
  "chinablue",
  "corpsereviver2",
  "kaikanfizz",
  "kingstonnegroni",
  "oldpal",
  "pompier",
  "robroy",
  "romewithaview",
  "rumsoda",
  "shoyojurin",
  "sloeginfizz",
  "spumoni",
  "stayuplate",
  "vesper",
  "whitenegroni",
]);

/** 사진이 있으면 그 경로, 없으면 null — 없는 종은 자리표시자로 그린다. */
export function cocktailPhotoSrc(slug: string): string | null {
  return PHOTO_SLUGS.has(slug) ? `/cocktails/${slug}.webp` : null;
}
