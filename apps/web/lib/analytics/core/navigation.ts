/**
 * 이 문서 안에서 어디를 거쳐 왔나 (ISSUE-035 · SPEC-10 §4.1).
 *
 * ## `document.referrer` 로는 안 된다
 *
 * 화면 안에서 링크를 눌러 옮기면 문서가 바뀌지 않는다 — 주소만 갈아 끼운다. 그래서
 * `document.referrer` 는 **처음 문서를 열 때의 값 그대로**다. 탐색에서 상세로 눌러 들어가도
 * 빈 값이라, 그것만 보면 사이트 안에서 다닌 것이 전부 "밖에서 왔다" 로 세어진다 —
 * `entryPoint = external` 비율이 곧 SEO 성과인데(§4.1) 그 숫자가 통째로 거짓이 된다.
 *
 * 그래서 옮겨 다닌 경로를 **여기 모듈 변수에** 적어 둔다. 문서를 새로 열면 초기화되므로
 * (자바스크립트가 다시 실행된다) 그때는 `document.referrer` 가 맞는 답이고,
 * 화면 안에서 옮긴 경우에만 이 값이 있다. 저장소를 쓰지 않는 이유가 그것이다 —
 * `sessionStorage` 는 문서를 새로 열어도 남아 있어 둘을 구분하지 못한다.
 */
/**
 * 기록은 **문서 하나에 하나**여야 한다.
 *
 * 모듈 변수로 두면 번들이 이 파일을 여러 조각에 복제할 때 기록하는 쪽과 읽는 쪽이 서로 다른
 * 변수를 보게 된다 — 실제로 그렇게 되어 화면 안 이동이 전부 `external` 로 나갔다.
 * `window` 는 문서에 하나뿐이고, 문서를 새로 열면 비워진다 (그때는 `document.referrer` 가
 * 맞는 답이다).
 */
interface Trail {
  previous: string | null;
  current: string | null;
}

declare global {
  interface Window {
    __kcaTrail?: Trail;
  }
}

function trail(): Trail {
  if (typeof window === "undefined") return { previous: null, current: null };
  return (window.__kcaTrail ??= { previous: null, current: null });
}

/** 화면이 바뀔 때마다 부른다. 직전 경로를 밀어 둔다. */
export function recordVisit(path: string) {
  const t = trail();
  if (t.current === path) return;

  t.previous = t.current;
  t.current = path;
}

/**
 * 이 문서 안에서 [path] 직전에 있던 경로. 문서를 새로 열었으면 `null` 이다.
 *
 * 기록하는 쪽과 읽는 쪽 중 **누가 먼저 도는지에 기대지 않는다** — 아직 기록되지 않았으면
 * `current` 가 곧 직전 경로다.
 */
export function previousPath(path: string): string | null {
  const t = trail();
  return t.current === path ? t.previous : t.current;
}
