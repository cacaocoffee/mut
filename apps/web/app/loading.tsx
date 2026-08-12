import { PourLoader } from "@/components/pour-loader";

export default function Loading() {
  return (
    <main className="shell">
      <PourLoader label="따르는 중" sub="Pouring · 잠시만 기다려 주세요" />
    </main>
  );
}
