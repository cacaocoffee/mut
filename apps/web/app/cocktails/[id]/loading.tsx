import { LoadingOverlay } from "@/components/loading-overlay";
import { DetailSkeleton } from "@/components/skeleton";

/**
 * 상세는 골격을 알고 있으므로 스켈레톤이 값이 크다 — 히어로 · 스펙 스트립 · 본문 3단이
 * 항상 같은 자리에 온다.
 *
 * 지금은 SSG 라 이 화면이 거의 안 보인다. 이슈 040(#40)이 ISR 로 바꾸면 그때 제 몫을 한다.
 */
export default function Loading() {
  return (
    <>
      <DetailSkeleton />
      <LoadingOverlay label="레시피를 꺼내는 중" sub="Fetching recipe" />
    </>
  );
}
