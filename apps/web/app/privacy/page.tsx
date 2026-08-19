import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "개인정보 처리방침",
  description: "MUT 가 수집하는 항목과 이용 목적.",
  // 문안이 확정되기 전까지 색인되지 않게 한다. 플레이스홀더가 검색에 걸리면
  // 그 자체가 잘못된 고지가 된다.
  robots: { index: false, follow: true },
};

/**
 * 개인정보 처리방침 (`NFR-L-04` — 배포 차단 조건).
 *
 * ## 문안이 미정이다
 *
 * SPEC-08 §9 가 "개인정보 처리방침 문안 — 법률 검토와 함께" 로 열어 뒀고,
 * `NFR-L-05`(법률 검토 1회)가 **정식 오픈 차단** 조건이다.
 *
 * 그래서 **페이지는 만들고 문안은 비워 둔다.** 페이지가 없으면 링크가 404 이고,
 * 404 는 "아직 안 썼다" 가 아니라 "그런 것이 없다" 로 보인다.
 * 무엇을 수집하는지는 이미 결정돼 있으므로(SPEC-08 §5.1) 그 표는 지금 적는다 —
 * 법률 검토가 바꿀 것은 표현이지 사실이 아니다.
 */
export default function PrivacyPage() {
  return (
    <main className="shell legal-page">
      <h1>개인정보 처리방침</h1>

      <p className="legal-page__draft">
        ⚠️ 이 문서는 <strong>법률 검토 전 초안</strong>입니다. 정식 오픈 전에 검토를 거쳐
        확정합니다 (<code>NFR-L-05</code>).
      </p>

      <h2>수집하는 항목</h2>
      <p>
        소셜 로그인(카카오 · 네이버 · 애플)으로 가입할 때 아래를 받습니다. 그 밖의 항목은
        받지 않습니다.
      </p>
      <ul>
        <li>
          <strong>제공자와 제공자 식별자</strong> — 같은 사람인지 판정하는 데만 씁니다.
        </li>
        <li>
          <strong>표시 이름</strong> — 화면에 보여 줍니다.
        </li>
        <li>
          <strong>이메일</strong> — 공지에 씁니다. <strong>없어도 가입할 수 있습니다.</strong>
        </li>
      </ul>

      <h2>받지 않는 것</h2>
      <p>
        생년월일 · 전화번호 · 주소 · 결제 정보를 받지 않습니다. 주류를 판매하지 않으므로
        성인 인증을 요구하지 않습니다.
      </p>
      <p>
        위치 정보는 <strong>저장하지 않습니다.</strong> &ldquo;내 주변 바&rdquo; 는 요청을
        처리하는 동안에만 좌표를 쓰고, 응답이 끝나면 남기지 않습니다.
      </p>

      <h2>탈퇴</h2>
      <p>
        탈퇴하면 계정과 저장한 북마크 · 컬렉션을 <strong>즉시 지웁니다.</strong>
        다만 콘텐츠 발행 이력에 남은 &ldquo;누가 발행했는지&rdquo; 는 유지합니다 —
        기록의 근거이자 신뢰의 근거라, 지우면 무엇이 언제 바뀌었는지 되짚을 수 없습니다.
      </p>
    </main>
  );
}
