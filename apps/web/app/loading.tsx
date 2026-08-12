import { LoadingOverlay } from "@/components/loading-overlay";
import { SearchSkeleton } from "@/components/skeleton";

/**
 * 골격을 깔고 그 위를 덮는다.
 *
 * 스켈레톤만 두면 사용자가 눌러 보고 반응이 없어 고장으로 읽고,
 * dim 만 두면 무엇을 기다리는지 모른 채 검은 화면을 본다. 둘이 각각 다른 것을 말한다.
 */
export default function Loading() {
  return (
    <>
      <SearchSkeleton />
      <LoadingOverlay label="따르는 중" sub="Pouring · 잠시만 기다려 주세요" />
    </>
  );
}
