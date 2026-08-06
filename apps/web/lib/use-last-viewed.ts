"use client";

import { useSyncExternalStore } from "react";

const KEY = "kca:last-viewed";
const EVENT = "kca:last-viewed";

/** 시안의 초기 `sel` 값과 같다. 아직 아무것도 안 본 상태의 상세 탭 목적지. */
export const DEFAULT_COCKTAIL_ID = "negroni";

function subscribe(onChange: () => void) {
  window.addEventListener(EVENT, onChange);
  window.addEventListener("storage", onChange);
  return () => {
    window.removeEventListener(EVENT, onChange);
    window.removeEventListener("storage", onChange);
  };
}

function getSnapshot() {
  return window.localStorage.getItem(KEY) ?? DEFAULT_COCKTAIL_ID;
}

/**
 * 상세 탭이 "마지막으로 본 칵테일"로 돌아가게 한다.
 * 서버 렌더와 하이드레이션 첫 프레임은 기본값을 쓰고, 그 뒤 localStorage 값으로 맞춰진다.
 */
export function useLastViewed(): string {
  return useSyncExternalStore(subscribe, getSnapshot, () => DEFAULT_COCKTAIL_ID);
}

export function rememberLastViewed(id: string) {
  if (window.localStorage.getItem(KEY) === id) return;
  window.localStorage.setItem(KEY, id);
  window.dispatchEvent(new Event(EVENT));
}
