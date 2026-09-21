import Link from "next/link";

/**
 * 목록 페이지 넘김 — 서버 렌더 링크판 (#207).
 *
 * 탐색 화면의 `Pager` 는 클라이언트 상태를 넘기는 버튼이고, 여기는 주소를 바꾸는 링크다.
 * 모양은 같다 — 같은 `.pager*` 클래스를 쓴다. 「← 이전 · 6 / 810 · 다음 →」 은 810쪽을
 * 한 칸씩만 움직이게 했다. 처음·끝·현재 주변을 숫자로 두고 사이는 `…` 로 접는다.
 *
 * `page` 는 0부터다(계약 `PageMeta.number`). 화면엔 1부터 보인다.
 */
export function PageLinks({
  page,
  totalPages,
  totalElements,
  size,
  href,
}: {
  page: number;
  totalPages: number;
  totalElements: number;
  size: number;
  href: (page: number) => string;
}) {
  if (totalPages <= 1) return null;

  const from = page * size + 1;
  const to = Math.min(totalElements, (page + 1) * size);

  return (
    <nav className="pager" aria-label="쪽 넘김">
      <p className="pager__range">
        {totalElements.toLocaleString()}개 중 {from.toLocaleString()}–{to.toLocaleString()}
      </p>
      <div className="pager__nums">
        {page > 0 ? (
          <Link href={href(page - 1)} className="btn btn-ghost pager__step">
            ← 이전
          </Link>
        ) : (
          <span className="btn btn-ghost pager__step pager__step--off" aria-hidden>
            ← 이전
          </span>
        )}
        {pagesAround(page, totalPages).map((n, i) =>
          n === null ? (
            <span key={`gap-${i}`} className="pager__gap" aria-hidden="true">
              …
            </span>
          ) : n === page ? (
            <span key={n} className="btn pager__num" aria-current="page">
              {n + 1}
            </span>
          ) : (
            <Link key={n} href={href(n)} className="btn pager__num" aria-label={`${n + 1}쪽`}>
              {n + 1}
            </Link>
          ),
        )}
        {page + 1 < totalPages ? (
          <Link href={href(page + 1)} className="btn btn-ghost pager__step">
            다음 →
          </Link>
        ) : (
          <span className="btn btn-ghost pager__step pager__step--off" aria-hidden>
            다음 →
          </span>
        )}
      </div>
    </nav>
  );
}

/** 처음 · 끝 · 지금 ±2 만 남기고 사이는 `null`(접힘). 하나만 건너뛰면 접지 않는다 — `…` 가 숫자보다 넓다. */
function pagesAround(page: number, totalPages: number): (number | null)[] {
  const keep = new Set<number>([0, totalPages - 1]);
  for (let n = page - 2; n <= page + 2; n += 1) if (n >= 0 && n < totalPages) keep.add(n);
  const out: (number | null)[] = [];
  let prev = -1;
  for (const n of [...keep].sort((a, b) => a - b)) {
    if (n - prev === 2) out.push(prev + 1);
    else if (n - prev > 2) out.push(null);
    out.push(n);
    prev = n;
  }
  return out;
}
