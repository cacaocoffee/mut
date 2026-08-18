import { previousPath } from "./navigation";

/**
 * 유입 분류 (ISSUE-035 · SPEC-10 §3).
 *
 * ## 원본 주소를 보내지 않는다
 *
 * `document.referrer` 에는 개인정보가 섞일 수 있다 — 사내 위키 주소, 초대 링크의 토큰.
 * **다섯 갈래로 접어서** 보낸다. 유기 검색 비중(SPEC-10 §6)을 세는 데는 그것이면 충분하다.
 *
 * 서버도 5종 밖은 `unknown` 으로 접는다 (`ReferrerType`) — 여기서 새 값을 만들어 보내도
 * 저장되지 않는다.
 */
export type ReferrerType = "organic" | "internal" | "social" | "direct" | "unknown";

/** 검색엔진. 유기 검색 비중이 Phase 1a 의 세 지표 중 하나다 (`PRIN-T04`). */
const SEARCH_HOSTS = [
  "google.",
  "naver.com",
  "daum.net",
  "bing.com",
  "duckduckgo.com",
  "yahoo.",
  "search.",
];

const SOCIAL_HOSTS = [
  "instagram.com",
  "facebook.com",
  "threads.net",
  "twitter.com",
  "x.com",
  "youtube.com",
  "kakao.com",
  "kakaocdn.net",
  "band.us",
  "t.co",
  "linkedin.com",
  "reddit.com",
];

export function classifyReferrer(referrer: string, currentHost: string): ReferrerType {
  // 주소창에 직접 치거나 북마크로 들어오면 referrer 가 없다.
  if (!referrer) return "direct";

  let host: string;
  try {
    host = new URL(referrer).hostname.toLowerCase();
  } catch {
    return "unknown"; // 분류 실패도 정보다 — 조용히 direct 로 접지 않는다
  }

  if (host === currentHost.toLowerCase()) return "internal";
  if (SEARCH_HOSTS.some((h) => host.includes(h))) return "organic";
  if (SOCIAL_HOSTS.some((h) => host.includes(h))) return "social";
  return "unknown";
}

/**
 * 지금 화면의 유입 분류. 브라우저 밖에서는 `direct` 다 (서버 렌더).
 *
 * **화면 안에서 옮겨 왔으면 `internal` 이다.** 링크를 눌러 옮기면 문서가 바뀌지 않아
 * `document.referrer` 가 처음 값에서 멈춰 있고, 그것만 보면 사이트 안의 이동이 전부
 * `direct` 로 세어진다 (`navigation.ts` 가 같은 이유로 있다).
 */
export function currentReferrerType(): ReferrerType {
  if (typeof document === "undefined") return "direct";
  if (previousPath(window.location.pathname)) return "internal";

  return classifyReferrer(document.referrer, window.location.hostname);
}
