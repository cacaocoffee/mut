import type { Metadata } from "next";
import { FinderScreen } from "@/components/finder-screen";

export const metadata: Metadata = {
  title: "취향 파인더",
  description: "도수 · 당도 · 향 · 기주 4개 질문으로 오늘의 칵테일 3종을 좁힙니다.",
};

export default function FinderPage() {
  return <FinderScreen />;
}
