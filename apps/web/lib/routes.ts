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

/**
 * 내 술장 — 가진 재료로 만들 수 있는 것을 되짚는다 (`R-F2.2-*`).
 *
 * 체크 상태에 따라 내용이 달라지는 화면이라 색인하지 않는다 (`PRIN-P06` · 탐색 필터와
 * 같은 성격). 로그인 없이 로컬 저장으로 쓴다 (`R-F2.2-4`) — `/me/*` 아래가 아닌 이유다.
 */
export const MY_BAR_PATH = "/my-bar";

/** 재료 사전 (`FR-INGREDIENT-002` · SCREENS-01 01-D). 이쪽은 색인한다. */
export const INGREDIENTS_PATH = "/ingredients";
