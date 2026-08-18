/**
 * 화면 주소 (SPEC-05 §4).
 *
 * 경로를 문자열로 흩어 두면 옮길 때 한 곳이 남는다 — ISSUE-040 이 탐색 화면을 `/` 에서
 * 옮기며 실제로 겪은 일이다. 렌더링 방식과 색인 여부가 경로마다 정해져 있으므로
 * (`PRIN-T04`) 주소는 계약에 가깝다.
 */

/** 탐색 · 필터. 필터는 쿼리스트링에만 붙고 색인하지 않는다 (`NFR-S-02`). */
export const SEARCH_PATH = "/cocktails/search";

/** 취향 파인더 (이슈 041). */
export const FINDER_PATH = "/finder";

/**
 * 통합 검색 (이슈 042).
 *
 * 탐색(`SEARCH_PATH`)과 다른 화면이다 — 저쪽은 축을 교차해 좁히고, 이쪽은 이름·초성으로
 * 칵테일과 재료를 한 번에 찾는다. 둘 다 색인하지 않는다 (`PRIN-P06`).
 */
export const UNIFIED_SEARCH_PATH = "/search";
