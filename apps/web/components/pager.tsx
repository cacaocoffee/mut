"use client";

/**
 * 한 쪽에 몇 장.
 *
 * 카드가 사진 자리(4:5)를 들고 있어 한 장이 화면에서 큰 편이다. 50장이면 넉넉히
 * 스크롤하되 DOM 이 감당하는 선이다. [SPEC-07 §1.5](../../../docs/spec/SPEC-07_API명세.md)
 * 의 목록 API 는 `size=24` 를 예로 들지만 저쪽은 **왕복이 있는** 페이지네이션이고
 * 여기는 이미 받아 둔 것을 자르기만 한다 — 크기를 맞출 이유가 없다.
 */
export const PAGE_SIZE = 50;

/** 지금 쪽 주변으로 몇 개를 펼칠지. 양옆 2개씩 + 처음·끝. */
const WINDOW = 2;

/**
 * 탐색 결과의 쪽 넘김 (`FR-SEARCH-005`).
 *
 * ## 왜 필요한가
 *
 * 필터는 클라이언트가 걸고 코퍼스는 통째로 받는다 (SPEC-05 §4). 그 선택은 그대로 두는데,
 * **거른 결과를 전부 그리는 것**은 별개 문제였다. Phase 1 목표가 칵테일 500 종이라
 * (SPEC-07 §1.5) 필터를 안 걸면 500장이 한꺼번에 DOM 에 올라간다.
 *
 * 세는 것과 그리는 것을 나눈다 — 결과 수와 칩 옆 숫자는 **거른 전부**를 말하고
 * (그래야 맞다), 그리는 것은 이 쪽뿐이다.
 *
 * ## 번호를 다 늘어놓지 않는다
 *
 * 500종이면 10쪽이지만 늘어날 것이다. 처음 · 끝 · 지금 주변만 두고 사이는 `…` 로 접는다.
 */
export function Pager({
  page,
  pageCount,
  total,
  onGo,
}: {
  page: number;
  pageCount: number;
  total: number;
  onGo: (next: number) => void;
}) {
  // 한 쪽에 다 들어가면 넘길 것이 없다.
  if (pageCount <= 1) return null;

  const from = (page - 1) * PAGE_SIZE + 1;
  const to = Math.min(page * PAGE_SIZE, total);

  return (
    <nav className="pager" aria-label="결과 쪽 넘김">
      <p className="pager__range" aria-live="polite">
        {total}개 중 {from}–{to}
      </p>

      <div className="pager__nums">
        <button
          type="button"
          className="btn btn-ghost pager__step"
          disabled={page === 1}
          onClick={() => onGo(page - 1)}
        >
          ← 이전
        </button>

        {pagesAround(page, pageCount).map((n, i) =>
          n === null ? (
            // 접힌 구간. 스크린리더가 "점점점" 을 읽을 이유가 없다
            <span key={`gap-${i}`} className="pager__gap" aria-hidden="true">
              …
            </span>
          ) : (
            <button
              key={n}
              type="button"
              className="btn pager__num"
              aria-label={`${n}쪽`}
              aria-current={n === page ? "page" : undefined}
              onClick={() => onGo(n)}
            >
              {n}
            </button>
          )
        )}

        <button
          type="button"
          className="btn btn-ghost pager__step"
          disabled={page === pageCount}
          onClick={() => onGo(page + 1)}
        >
          다음 →
        </button>
      </div>
    </nav>
  );
}

/** 처음 · 끝 · 지금 주변만 남기고 사이는 `null`(접힘) 한 칸으로 만든다. */
function pagesAround(page: number, pageCount: number): (number | null)[] {
  const keep = new Set<number>([1, pageCount]);
  for (let n = page - WINDOW; n <= page + WINDOW; n += 1) {
    if (n >= 1 && n <= pageCount) keep.add(n);
  }

  const out: (number | null)[] = [];
  let prev = 0;
  for (const n of [...keep].sort((a, b) => a - b)) {
    // 딱 하나만 건너뛰었으면 접지 않는다 — `…` 가 숫자 하나보다 넓다
    if (n - prev === 2) out.push(prev + 1);
    else if (n - prev > 2) out.push(null);
    out.push(n);
    prev = n;
  }
  return out;
}
