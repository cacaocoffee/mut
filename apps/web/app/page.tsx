import { redirect } from "next/navigation";
import { SEARCH_PATH } from "@/lib/routes";

/**
 * 홈 자리.
 *
 * 탐색 화면이 여기 있었는데 ISSUE-040 이 `/cocktails/search` 로 옮겼다 —
 * SPEC-05 §4 가 정한 자리이고, 필터 결과는 `noindex` 라서 색인 대상인 `/` 에 둘 수 없다.
 *
 * SPEC-05 §4 의 홈(ISR · 색인)은 **아직 이슈가 없다** ([GAPS](../../docs/prd/GAPS.md) G-31).
 * 그때까지 탐색으로 보낸다. 임시 이동(307)이라 주소가 굳지 않는다 — 홈이 생기면 이 파일이
 * 곧 홈이 된다.
 */
export default function HomePage() {
  redirect(SEARCH_PATH);
}
