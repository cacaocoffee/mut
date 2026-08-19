"use client";

import Link from "next/link";
import Image from "next/image";
import { usePathname } from "next/navigation";
import { FINDER_PATH, SEARCH_PATH, UNIFIED_SEARCH_PATH } from "@/lib/routes";

export function SiteNav() {
  const pathname = usePathname();

  // 국문과 영문을 나눠 둔다 — 560px 아래에서 영문만 감춘다 (ISSUE-051 #69).
  // 레이블을 두 줄로 접는 대신 짧게 쓴다. `01`~`03` 이 화면 순서를 계속 드러낸다.
  //
  // **상세는 여기 없다.** 시안이 단일 페이지라 탭이 화면 전환기였고, 거기서는
  // "상세 = 지금 고른 것" 이 성립했다. 실제 주소로 옮긴 뒤에도 그 칸이 남아
  // `/cocktails/{마지막으로 본 것}` 을 가리켰는데, 처음 온 사람에게는 기준 없이
  // 네그로니가 열렸고 서버가 그린 HTML 에도 그 주소가 박혀 나갔다.
  // 상세는 카드에서 들어가는 잎사귀지 최상위 내비의 목적지가 아니다.
  const tabs = [
    { href: SEARCH_PATH, ko: "01 탐색", en: "SEARCH", match: (p: string) => p === SEARCH_PATH },
    { href: FINDER_PATH, ko: "02 파인더", en: "FINDER", match: (p: string) => p === FINDER_PATH },
    {
      href: UNIFIED_SEARCH_PATH,
      ko: "03 검색",
      en: "FIND",
      match: (p: string) => p === UNIFIED_SEARCH_PATH,
    },
  ];

  return (
    <nav className="nav site-nav">
      {/* 워드마크가 곧 이름이다 — 글자로 한 번 더 적지 않는다. `next/image` 를 쓰는 이유는
          포맷 변환이다: png 를 그대로 걸면 `image-guard` 가 막는다 (`NFR-P-06`). */}
      <Link href={SEARCH_PATH} className="nav-brand" aria-label="MUT 홈으로">
        <Image
          src="/brand/mut-mark.png"
          alt="MUT"
          width={725}
          height={545}
          priority
          sizes="72px"
        />
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
