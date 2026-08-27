import type { Metadata } from "next";
import { SavedList } from "@/components/saved-list";

/**
 * 내 저장 (`FR-USER-003` · SPEC-07 §2.5).
 *
 * ## 색인하지 않는다
 *
 * 사람마다 내용이 다르고 로그인 세션이 있어야 뜬다. `/search` 와 같은 이유로 `noindex` 다.
 * 목록은 세션 쿠키가 필요해 서버 컴포넌트가 아니라 클라이언트가 API 를 부른다 —
 * 비로그인은 상류가 401 을 주고, 화면은 그걸로 로그인을 유도한다.
 */
export const metadata: Metadata = {
  title: "내 저장",
  description: "저장한 칵테일과 아티클을 모아 봅니다.",
  robots: { index: false, follow: true },
};

export default function SavedPage() {
  return (
    <main className="shell">
      <header className="page-head">
        <div>
          <h1>내 저장</h1>
        </div>
        <p className="lede">저장한 칵테일과 아티클을 모아 봅니다.</p>
      </header>
      <SavedList />
    </main>
  );
}
