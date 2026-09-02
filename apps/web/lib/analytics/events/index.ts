import { track } from "../core";

/**
 * SPEC-10 §4 의 3~4단계 이벤트 (ISSUE-049).
 *
 * ## 전송은 만들지 않는다
 *
 * 큐·세션·공통 필드는 이슈 035 가 만들었고 여기서는 **무엇을 보낼지**만 정한다 (RED 21).
 * 전송 경로가 두 벌이 되면 한쪽만 고치는 날이 오고, 그날 절반의 이벤트가 조용히 사라진다.
 *
 * ## 타입이 서버 계약과 쌍이다
 *
 * 서버(`EventType`)가 이벤트마다 **받을 payload 키를 정해 두고 나머지는 버린다.**
 * 화면에서는 버려진 것을 알 방법이 없으므로 여기서 타입으로 묶고, `e2e/analytics.spec.ts`
 * 가 계약 파일과 대조한다.
 */

/**
 * 필터 축 (SPEC-10 §4.2).
 *
 * **`method` 는 SPEC-10 의 목록에 없다** — 그 문서가 쓰인 뒤 이슈 040 이 축을 여섯으로
 * 늘렸다. 빼고 보내면 "안 쓰는 축은 UI 에서 내린다" 는 판단에서 메이킹만 영영 빠진다.
 * 보내고 [GAPS G-37](../../../../../docs/prd/GAPS.md) 에 적었다.
 */
export type FilterAxis = "base" | "style" | "method" | "sweet" | "abv" | "flavor" | "query";

export interface FilterApplyPayload {
  axis: FilterAxis;
  /** 고른 값. 해제는 빈 문자열이다 — 무엇을 껐는지보다 "그 축을 만졌다" 가 지표다. */
  value: string;
  /** 적용 **후** 결과 수. 0 이 잦은 축은 패싯 카운트가 제 역할을 못 한다는 신호다 */
  resultCount: number;
  /** 그때 동시에 걸려 있던 축 수. 사람들이 몇 축까지 겹쳐 쓰는지 */
  activeAxisCount: number;
}

/**
 * 필터 조작 (SPEC-10 §4.2).
 *
 * 같은 축을 연달아 고르는 동안에는 마지막 것만 보낸다 (RED 6) — 칩 다중 선택은 한 번의
 * 조작에 가깝고, 중간 상태까지 세면 "축 사용률" 이 손가락 빠른 사람 쪽으로 기운다.
 */
export function filterApply(payload: FilterApplyPayload) {
  debounced(`filter:${payload.axis}`, () => track("filter_apply", { ...payload }));
}

export interface FinderStepPayload {
  /** 1~4. **`step = 4` 도달이 완주다** — `finder_complete` 를 따로 두지 않는다 (SPEC-10 §4.4) */
  step: 1 | 2 | 3 | 4;
  /** 그 질문에 고른 값 */
  answered: string;
  /** 답한 뒤 남은 후보 수. **1~2로 급감하면 질문이 너무 좁게 거른다** */
  candidateCount: number;
}

export function finderStep(payload: FinderStepPayload) {
  track("finder_step", { ...payload });
}

/** 상세에서 만질 수 있는 것 셋. 이 밖은 없다 (SPEC-10 §4.5). */
export type RecipeAction = "servings_change" | "unit_toggle" | "substitute_open";

export interface RecipeInteractPayload {
  cocktailSlug: string;
  action: RecipeAction;
  /** 잔 수 · `ml`/`oz` · 재료명. 액션마다 뜻이 다르므로 문자열 하나로 둔다 */
  detail: string;
}

export function recipeInteract(payload: RecipeInteractPayload) {
  track("recipe_interact", { ...payload });
}

/**
 * 북마크가 가리키는 종류 (SPEC-10 §4.6). `article` 은 아티클이 DB 로 오며 북마크 대상이 된
 * 2026-08-28 에 추가됐다 (ADR-0011). `bar` 는 Phase 1b 라 아직 없다 — 생기면 여기 한 줄이다.
 */
export type BookmarkTargetType = "cocktail" | "article";

export interface BookmarkAddPayload {
  targetType: BookmarkTargetType;
  targetSlug: string;
}

export function bookmarkAdd(payload: BookmarkAddPayload) {
  track("bookmark_add", { ...payload });
}

/**
 * 공유 경로 (SPEC-10 §4.6).
 *
 * `system` 은 OS 공유 시트, `link` 는 주소 복사다. **`kakao` 는 아직 누를 데가 없다** —
 * 카카오 전용 버튼이 없고 공유 시트를 거쳐 카카오톡으로 간다. 값을 미리 두는 이유는
 * 버튼이 생기는 날 이 타입을 고치지 않기 위해서다.
 */
export type ShareChannel = "kakao" | "link" | "system";

export interface ShareClickPayload {
  targetType: BookmarkTargetType;
  targetSlug: string;
  channel: ShareChannel;
}

export function shareClick(payload: ShareClickPayload) {
  track("share_click", { ...payload });
}

/* ─────────────────────────  디바운스  ───────────────────────── */

/** 400ms. 칩을 연달아 누르는 간격보다 길고, 다음 조작을 기다리게 하지는 않는 정도다. */
const DEBOUNCE_MS = 400;

const timers = new Map<string, ReturnType<typeof setTimeout>>();

function debounced(key: string, send: () => void) {
  const running = timers.get(key);
  if (running) clearTimeout(running);

  timers.set(
    key,
    setTimeout(() => {
      timers.delete(key);
      send();
    }, DEBOUNCE_MS),
  );
}
