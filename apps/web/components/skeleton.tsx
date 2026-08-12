/**
 * 스켈레톤 — 아직 안 온 UI 요소의 **자리와 크기**를 미리 그린다.
 *
 * 빈 화면을 보여 주면 사용자가 "무엇이 올지" 를 모른 채 기다린다.
 * 골격을 먼저 그리면 내용이 도착할 때 레이아웃이 튀지 않고, 기다리는 동안에도
 * 이 화면이 무엇인지 읽힌다.
 *
 * 실제 클래스(`page-head` · `search-layout` · `detail-hero` …)를 그대로 쓴다 —
 * 스켈레톤 전용 레이아웃을 따로 만들면 본 화면이 바뀔 때 같이 안 바뀌고,
 * 그때부터 **자리가 틀린 스켈레톤**이 된다.
 *
 * `aria-hidden` 인 이유: 골격은 장식이다. 상태는 오버레이의 `role="status"` 가 말한다.
 */
export function Skeleton({
  w,
  h,
  className = "",
}: {
  w?: string | number;
  h?: string | number;
  className?: string;
}) {
  return (
    <span
      className={`skeleton ${className}`.trim()}
      style={{ width: w, height: h }}
    />
  );
}

/** 탐색·검색 화면 (`SearchScreen`) 의 골격. */
export function SearchSkeleton() {
  return (
    <main className="shell" aria-hidden="true">
      <header className="page-head">
        <div>
          <Skeleton w="min(420px, 70%)" h={44} />
          <Skeleton w="min(560px, 90%)" h={14} className="skeleton--gap" />
          <Skeleton w="min(480px, 80%)" h={14} className="skeleton--gap-sm" />
        </div>
      </header>

      <div className="search-layout">
        <aside className="filter-panel">
          {[0, 1, 2].map((group) => (
            <div className="filter-group" key={group}>
              <Skeleton w={120} h={12} />
              <div className="skeleton-stack">
                {[0, 1, 2, 3].map((row) => (
                  <Skeleton key={row} h={34} />
                ))}
              </div>
            </div>
          ))}
        </aside>

        <div className="result-grid">
          {Array.from({ length: 6 }, (_, i) => (
            <div className="skeleton-card" key={i}>
              <Skeleton h={168} />
              <Skeleton w="70%" h={20} className="skeleton--gap" />
              <Skeleton w="45%" h={12} className="skeleton--gap-sm" />
            </div>
          ))}
        </div>
      </div>
    </main>
  );
}

/** 상세 화면의 골격. 히어로 · 스펙 스트립 · 본문 3단을 그대로 따른다. */
export function DetailSkeleton() {
  return (
    <main className="shell" aria-hidden="true">
      <div className="detail-hero">
        <Skeleton className="skeleton--hero" />
        <div>
          <Skeleton w="min(360px, 80%)" h={52} />
          <Skeleton w={160} h={16} className="skeleton--gap" />
          <Skeleton w="min(520px, 100%)" h={14} className="skeleton--gap" />
          <Skeleton w="min(460px, 92%)" h={14} className="skeleton--gap-sm" />

          <dl className="spec-strip">
            {Array.from({ length: 5 }, (_, i) => (
              <div key={i}>
                <Skeleton w={52} h={10} />
                <Skeleton w={72} h={18} className="skeleton--gap-sm" />
              </div>
            ))}
          </dl>
        </div>
      </div>

      <div className="detail-body">
        <div>
          <Skeleton w={200} h={14} />
          <div className="skeleton-stack">
            {[0, 1, 2, 3, 4].map((row) => (
              <Skeleton key={row} h={22} />
            ))}
          </div>
        </div>
        <div>
          <Skeleton w={220} h={14} />
          <div className="skeleton-stack">
            {[0, 1, 2, 3, 4].map((row) => (
              <Skeleton key={row} h={22} />
            ))}
          </div>
        </div>
      </div>
    </main>
  );
}
