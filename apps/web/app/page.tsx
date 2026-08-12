import { Suspense } from "react";
import { SearchScreen } from "@/components/search-screen";
import { SearchSkeleton } from "@/components/skeleton";

/** 빈 shell 을 fallback 으로 두면 한 프레임 동안 화면이 통째로 비었다가 튄다 — 골격을 깔아 둔다. */
export default function SearchPage() {
  return (
    <Suspense fallback={<SearchSkeleton />}>
      <SearchScreen />
    </Suspense>
  );
}
