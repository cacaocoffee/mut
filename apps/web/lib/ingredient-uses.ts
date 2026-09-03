import { cocktailsUsing, type SearchItem } from "@mut/domain";

/**
 * 재료를 쓰는 잔 — **코퍼스에 있는 것만** 센다 (#181).
 *
 * `cocktailsUsing` 은 코드 데이터(49종)를 보고, 화면이 실제로 그리는 목록은 코퍼스
 * (API 가 있으면 DB 41종)다. 목록은 앞의 수를, 상세는 뒤의 수를 보여 줘 "진 10잔" 과
 * "이 재료를 쓰는 칵테일 8" 이 한 흐름에서 어긋났다. 세 곳(목록 · 상세 · 상세 메타)이
 * 이 한 함수를 쓴다.
 */
export function cocktailsUsingInCorpus(slug: string, corpus: SearchItem[]): SearchItem[] {
  const using = new Set(cocktailsUsing(slug));
  return corpus.filter((c) => using.has(c.slug));
}
