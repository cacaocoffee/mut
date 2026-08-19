import Link from "next/link";
import { requireAdmin } from "@/lib/admin-session";

/**
 * 어드민 대시보드 (ISSUE-045 · `FR-ADMIN-006` · `PRIN-P02`).
 *
 * ## 여기에 노출 규칙 입력란이 없다
 *
 * `FR-ADMIN-006` 은 **입력란 자체를 만들지 말라**고 한다. `PRIN-P02` 가 이유를 적었다 —
 * "영업 편의로 조정할 수 있게 만들면 **반드시 조정된다.** 어드민에 수치 입력란을 두는
 * 순간 그 수치는 올라간다." 부스팅 한도·홈 슬롯 비율·제휴 라벨 토글이 그것이고,
 * `e2e/admin.spec.ts` 가 그 이름들이 화면에 없는지 본다.
 *
 * 라벨을 작게 하거나 흐리게 하는 것도 같은 위반이다 (`PRIN-P02`).
 */
export default async function AdminDashboard() {
  // 상태 코드는 페이지가 정한다 (`requireAdmin` 주석 참조). 새 어드민 화면도 이 한 줄로 시작한다.
  await requireAdmin();

  return (
    <>
      <h2 className="admin__section-head">할 일</h2>

      <ul className="admin__list">
        <li>
          <Link href="/admin/cocktails">
            <b>칵테일 편집 · 발행</b>
          </Link>
          <span>
            발행 조건 패널이 무엇이 모자란지 한 화면에 보여 준다 (<code>NFR-O-01</code>).
          </span>
        </li>
        <li>
          <Link href="/admin/ingredients">
            <b>재료 승인</b>
          </Link>
          <span>
            새 재료는 승인 대기로 들어간다. 승인은 admin 만 한다 — 만든 사람이 스스로
            통과시키면 승인 단계가 없는 것과 같다 (<code>SPEC-08 §2</code>).
          </span>
        </li>
        <li>
          <Link href="/admin/tasks">
            <b>검증 태스크</b>
          </Link>
          <span>
            배치가 찾아낸 불변식 위반과 게이트 우회가 여기로 온다 (<code>FR-ADMIN-004</code>).
            넘길 때는 사유가 필요하다.
          </span>
        </li>
      </ul>

      <h2 className="admin__section-head">여기서 못 하는 것</h2>

      {/* 없는 기능을 적어 두는 이유: 나중에 "왜 없지" 를 다시 묻지 않게 하려는 것이다.
          지우면 그 자리에 입력란이 생긴다 (`PRIN-P02`). */}
      <ul className="admin__list">
        <li>
          <b>노출 규칙 조정</b>
          <span>
            부스팅 한도 · 홈 슬롯 비율 · 제휴 라벨 표시 여부는 <b>어드민에서 바꿀 수 없다</b>.
            바꾸려면 코드를 고치고 배포한다 — 그게 의도다 (<code>SPEC-08 §2.1</code>).
            큐레이션 중립성은 하드 제약이라(<code>PRIN-P02</code>) 입력란을 만들지 않는다.
          </span>
        </li>
      </ul>

      <p className="admin__foot">
        <Link href="/cocktails/search">← 공개 화면으로</Link>
      </p>
    </>
  );
}
