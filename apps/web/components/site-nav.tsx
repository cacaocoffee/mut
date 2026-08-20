"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Wordmark } from "@/components/wordmark";
import { FINDER_PATH, MY_BAR_PATH, SEARCH_PATH } from "@/lib/routes";

export function SiteNav() {
  const pathname = usePathname();

  // 국문과 영문을 나눠 둔다 — 560px 아래에서 영문만 감춘다 (ISSUE-051 #69).
  // 레이블을 두 줄로 접는 대신 짧게 쓴다. `01`·`02` 가 화면 순서를 계속 드러낸다.
  //
  // **상세는 여기 없다.** 시안이 단일 페이지라 탭이 화면 전환기였고, 거기서는
  // "상세 = 지금 고른 것" 이 성립했다. 실제 주소로 옮긴 뒤에도 그 칸이 남아
  // `/cocktails/{마지막으로 본 것}` 을 가리켰는데, 처음 온 사람에게는 기준 없이
  // 네그로니가 열렸고 서버가 그린 HTML 에도 그 주소가 박혀 나갔다.
  // 상세는 카드에서 들어가는 잎사귀지 최상위 내비의 목적지가 아니다.
  //
  // **통합 검색도 여기 없다.** `01 탐색` 과 `03 검색` 이 둘 다 검색이라 이름만으로
  // 갈리지 않았다. 이제 이름 검색은 탐색 화면이 직접 한다 — 검색어와 필터를 한자리에서
  // 건다. `/search` 는 라우트로 남는다: 재료·바·아티클까지 타입별로 묶어 보여 주는 것은
  // (`R-F5-1`) 칵테일 그리드가 담을 수 없고, 그 화면에 걸린 요구사항이 따로 있다
  // (`FR-SEARCH-006`·`007`·`008`). 갈 곳이 생기면 다시 내건다.
  const tabs = [
    { href: SEARCH_PATH, ko: "01 탐색", en: "SEARCH", match: (p: string) => p === SEARCH_PATH },
    { href: FINDER_PATH, ko: "02 파인더", en: "FINDER", match: (p: string) => p === FINDER_PATH },
    { href: MY_BAR_PATH, ko: "03 내 술장", en: "MY BAR", match: (p: string) => p === MY_BAR_PATH },
  ];

  return (
    <nav className="nav site-nav">
      {/* 워드마크가 곧 이름이다 — 글자로 한 번 더 적지 않는다. 마크업에 직접 두는
          이유는 `components/wordmark.tsx` 에 적었다. */}
      <Link href={SEARCH_PATH} className="nav-brand" aria-label="MUT 홈으로">
        <Wordmark />
        <small>당신의 취향, 당신의 멋</small>
      </Link>
      {/* btn-secondary 를 뺐다 — 테두리 상자였는데 이제 밑줄 인디케이터다 (ISSUE-055) */}
      <div className="tabs">
        {tabs.map((t) => (
          <Link
            key={t.href}
            href={t.href}
            className="btn tab"
            aria-current={t.match(pathname) ? "page" : undefined}
          >
            {t.ko} <span className="en">{t.en}</span>
          </Link>
        ))}
      </div>
    </nav>
  );
}
