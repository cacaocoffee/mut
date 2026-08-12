import { PourLoader } from "@/components/pour-loader";

/**
 * 상세는 골격을 알고 있으므로 스켈레톤이 스피너보다 낫다 —
 * 다만 지금은 SSG 라 이 화면이 거의 안 보인다. 이슈 040(#40)이 ISR 로 바꾸면
 * 그때 레이아웃 스켈레톤으로 키운다. 지금은 자리와 문구만 잡아 둔다.
 */
export default function Loading() {
  return (
    <main className="shell">
      <PourLoader label="레시피를 꺼내는 중" sub="Fetching recipe" />
    </main>
  );
}
