import type { Metadata } from "next";
import { AdminNav } from "@/components/admin/admin-nav";
import { adminAccess } from "@/lib/admin-session";

/**
 * 어드민 셸 (ISSUE-045 · `FR-ADMIN-001` · SPEC-05 §1·§4).
 *
 * ## 같은 앱 안의 라우트다
 *
 * SPEC-05 §1 — **별도 앱을 만들지 않는다.** 디자인 시스템과 인증을 공유하려는 것이고,
 * 나누는 순간 토큰과 세션이 두 벌이 된다.
 *
 * ## 못 들어오면 404 다
 *
 * SCREENS-00 §3.4 — "권한 없음" 을 보여 주지 않는다. 어드민이 **있다는 사실**도 정보다.
 *
 * ## 색인하지 않는다
 *
 * SPEC-05 §4 — `/admin/*` 은 CSR·비색인이다. 사이트맵에도 없다.
 */
export const metadata: Metadata = {
  title: "어드민",
  robots: { index: false, follow: false },
};

/** 세션을 봐야 하므로 요청마다 그린다. 미리 그려 두면 남의 화면을 보여 주게 된다. */
export const dynamic = "force-dynamic";

export default async function AdminLayout({ children }: { children: React.ReactNode }) {
  // **판정은 페이지가 한다** (`requireAdmin`). 레이아웃에서 `notFound()` 를 부르면 응답이
  // `200` 인 채 본문만 not-found 로 그려진다 — 크롤러와 스크립트는 본문이 아니라 상태를 본다.
  // 레이아웃은 껍데기만 그리고, 안쪽은 페이지가 막히면 애초에 안 온다.
  //
  // 메뉴는 역할에 따라 달라진다 (SPEC-08 §2, ISSUE-048). 페이지와 같은 판정을 쓴다 —
  // `adminAccess` 는 요청 안에서 한 번만 두드린다.
  const access = await adminAccess();
  const role = access.kind === "allowed" ? access.role : "editor";

  return (
    <main className="shell admin">
      <header className="admin__head">
        <h1>어드민 ADMIN</h1>
        <p className="lede">
          발행은 에디터가 한다. 개발자를 거치지 않는 것이 이 화면의 목적이다
          (<code>FR-ADMIN-001</code>).
        </p>
      </header>

      <div className="admin__body">
        <AdminNav role={role} />
        <section className="admin__content">{children}</section>
      </div>
    </main>
  );
}
