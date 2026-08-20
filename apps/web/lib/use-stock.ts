"use client";

import { useCallback, useSyncExternalStore } from "react";

const KEY = "mut:stock";
const EVENT = "mut:stock";

/**
 * 내 술장에 담은 재료 (`R-F2.2-4`).
 *
 * ## 로그인 없이 쓴다
 *
 * PRD 가 "비로그인 상태에서도 로컬 저장으로 체험 가능하게 하고, **저장 시점에 로그인을
 * 유도한다**" 고 정했다. 재료를 스무 개 체크하게 해 놓고 로그인을 요구하면 그 자리에서
 * 나간다. 계정은 나중 일이고, `/me/stock` 이 붙으면 이 값이 첫 동기화 대상이 된다.
 *
 * ## 첫 그림은 항상 빈 술장이다
 *
 * 미리 그려 두는 정적 페이지라 서버는 누가 무엇을 담았는지 모른다. 서버가 그린 것과
 * 다른 값으로 첫 렌더를 하면 하이드레이션이 어긋나므로 **서버 스냅샷을 빈 것으로 고정**하고
 * 붙은 뒤에 로컬 값으로 맞춘다.
 *
 * [ready] 가 그 시점을 밖에서 보게 한다 — 없으면 "아직 안 읽었다" 와 "정말 비었다" 가
 * 같은 모습이라 안내 문구가 잘못 뜬다.
 */
export function useStock() {
  const have = useSyncExternalStore(subscribe, snapshot, serverSnapshot);
  // 서버는 false, 붙은 뒤는 true. 하이드레이션 이후에만 참이면 되므로 상태가 필요 없다.
  const ready = useSyncExternalStore(subscribe, alwaysTrue, alwaysFalse);

  const toggle = useCallback((slug: string) => {
    const next = new Set(snapshot());
    if (!next.delete(slug)) next.add(slug);
    write(next);
  }, []);

  const clear = useCallback(() => write(new Set()), []);

  return { have, ready, toggle, clear };
}

function subscribe(onChange: () => void) {
  window.addEventListener(EVENT, onChange);
  // 다른 탭에서 담은 것도 따라온다
  window.addEventListener("storage", onChange);
  return () => {
    window.removeEventListener(EVENT, onChange);
    window.removeEventListener("storage", onChange);
  };
}

const alwaysTrue = () => true;
const alwaysFalse = () => false;

/**
 * 저장된 문자열이 그대로면 **같은 Set 을 돌려준다.**
 *
 * `useSyncExternalStore` 는 스냅샷을 참조로 비교한다. 부를 때마다 새 `Set` 을 만들면
 * 매번 달라진 것으로 보고 무한히 다시 그린다.
 */
let cachedRaw: string | null = null;
let cached: Set<string> = new Set();

const EMPTY: Set<string> = new Set();
const serverSnapshot = () => EMPTY;

function snapshot(): Set<string> {
  let raw: string | null = null;
  try {
    raw = window.localStorage.getItem(KEY);
  } catch {
    return cached;
  }

  if (raw !== cachedRaw) {
    cachedRaw = raw;
    cached = parse(raw);
  }
  return cached;
}

/** 남의 손을 탄 값일 수 있다. 모양이 아니면 빈 술장으로 시작한다 — 화면이 깨지는 것보다 낫다. */
function parse(raw: string | null): Set<string> {
  try {
    const parsed: unknown = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? new Set(parsed.filter((x) => typeof x === "string")) : new Set();
  } catch {
    return new Set();
  }
}

function write(next: Set<string>) {
  try {
    window.localStorage.setItem(KEY, JSON.stringify([...next]));
  } catch {
    // 저장 공간이 막혀 있어도 이번 세션은 그대로 쓴다. 담는 것을 막을 이유가 없다.
  }
  window.dispatchEvent(new Event(EVENT));
}
