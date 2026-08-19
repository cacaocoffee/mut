import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "이용약관",
  description: "MUT 이용에 관한 약관.",
  robots: { index: false, follow: true },
};

/**
 * 이용약관 (`NFR-L-04` — 배포 차단 조건).
 *
 * [개인정보 처리방침](../privacy/page.tsx)과 같은 이유로 **초안**이다 (`NFR-L-05`).
 * 확정된 사실만 적고 나머지는 검토에 맡긴다.
 */
export default function TermsPage() {
  return (
    <main className="shell legal-page">
      <h1>이용약관</h1>

      <p className="legal-page__draft">
        ⚠️ 이 문서는 <strong>법률 검토 전 초안</strong>입니다. 정식 오픈 전에 검토를 거쳐
        확정합니다 (<code>NFR-L-05</code>).
      </p>

      <h2>이 서비스가 하는 일</h2>
      <p>
        칵테일 레시피와 향 · 맛 서술을 정리해 보여 줍니다. <strong>주류를 판매하지 않습니다.</strong>
      </p>

      <h2>음주에 관하여</h2>
      <p>
        지나친 음주는 뇌졸중, 기억력 손상이나 치매를 유발합니다. 임신 중 음주는 기형아 출생
        위험을 높입니다. <strong>만 19세 미만 청소년에게 판매하지 않습니다.</strong>
      </p>

      <h2>제휴 표시</h2>
      <p>
        대가를 받고 실린 내용에는 <strong>제휴 콘텐츠</strong> 라벨을 답니다. 이 표시는 끌 수
        없으며, 제휴 여부가 <strong>추천 순위에 영향을 주지 않습니다.</strong>
      </p>

      <h2>콘텐츠</h2>
      <p>
        레시피와 서술의 저작권은 이 서비스에 있습니다. 개인적으로 만들어 마시는 데에는 제약이
        없습니다.
      </p>
    </main>
  );
}
