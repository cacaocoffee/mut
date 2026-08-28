import { ARTICLES_PATH } from "@/lib/routes";

/**
 * 북마크가 가리키는 대상 종류의 정본 (SPEC-06 §3.5 · 서버 BookmarkTarget 과 코드가 같다).
 *
 * 라벨·주소를 화면 곳곳에 문자열로 흩어 두지 않는다 — 종류를 늘리거나 라벨을 바꿀 때
 * 한 곳만 고치면 되도록 여기 한 객체로 모은다. `href` 가 null 이면 아직 화면이 없다는 뜻이다
 * (bar 는 Phase 1b).
 */
export interface BookmarkTargetSpec {
  /** 목록에서 종류를 한 낱말로 보이는 라벨. */
  label: string;
  /** 그 대상 화면의 주소. null 이면 아직 화면이 없다(링크를 만들지 않는다). */
  href: ((slug: string) => string) | null;
}

export const BOOKMARK_TARGETS: Record<string, BookmarkTargetSpec> = {
  cocktail: { label: "칵테일", href: (slug) => `/cocktails/${slug}` },
  article: { label: "아티클", href: (slug) => `${ARTICLES_PATH}/${slug}` },
  bar: { label: "바", href: null }, // Phase 1b — 도메인이 없다
};

/** 대상 화면 주소. 모르는 종류·화면 없는 종류는 null — 링크를 만들지 않는다. */
export function bookmarkHref(targetType: string, targetSlug: string): string | null {
  return BOOKMARK_TARGETS[targetType]?.href?.(targetSlug) ?? null;
}

/** 종류 라벨. 모르는 종류는 코드를 그대로 보여 준다. */
export function bookmarkLabel(targetType: string): string {
  return BOOKMARK_TARGETS[targetType]?.label ?? targetType;
}
