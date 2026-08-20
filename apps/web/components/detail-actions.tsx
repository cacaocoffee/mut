"use client";

import { useState } from "react";
import { bookmarkAdd, shareClick } from "@/lib/analytics/events";

/**
 * 액션 블록 (`FR-COCKTAIL-027` — `FR-COCKTAIL-017` 의 여덟째 블록).
 *
 * > **저장 · 공유 · 내 술장 재료 대조** 액션을 제공한다 (**대조는 P2**)
 *
 * 내 술장 대조는 없다. 괄호가 P2 라고 적었고, 없는 것을 자리만 만들어 두면
 * 눌렀을 때 아무 일도 안 일어나는 버튼이 남는다.
 *
 * ## 저장 실패가 화면을 막지 않는다
 *
 * 북마크 API(이슈 031)가 죽어도 사용자는 레시피를 계속 읽을 수 있어야 한다.
 * 실패는 버튼 옆 한 줄로만 알린다 — 모달을 띄우면 읽던 것을 가린다.
 */
export function DetailActions({
  targetType,
  targetSlug,
  sharePath,
  nameKo,
}: {
  targetType: string;
  targetSlug: string;
  sharePath: string;
  nameKo: string;
}) {
  const [saved, setSaved] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  async function save() {
    setMessage(null);
    try {
      const res = await fetch("/api/v1/me/bookmarks", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ targetType, targetSlug }),
      });

      if (res.status === 401) {
        // `R-F2.2-4` 의 정신 — 막지 않고 유도한다. 저장하려던 곳으로 돌아온다.
        setMessage("로그인하면 저장할 수 있습니다");
        return;
      }
      if (!res.ok) throw new Error(`HTTP ${res.status}`);

      setSaved(true);
      // 저장이 **된 뒤에만** 센다 (SPEC-10 §4.6). 누른 것을 세면 로그인 유도로 끝난 것까지
      // 저장으로 잡히고, 그러면 저장률이 실제보다 높게 나온다.
      bookmarkAdd({ targetType: "cocktail", targetSlug });
    } catch {
      // 계측·저장 실패가 사용자 흐름을 막지 않는다 (`NFR-R-04` 의 정신).
      setMessage("지금은 저장할 수 없습니다");
    }
  }

  async function share() {
    const url = new URL(sharePath, window.location.origin).toString();

    // 공유 시트가 있으면 그것을 쓴다 — 카카오톡으로 바로 넘길 수 있다 (`FR-USER-005`).
    if (navigator.share) {
      try {
        await navigator.share({ title: nameKo, url });
        // 어느 앱으로 갔는지는 알 수 없다. 시트를 거친 것까지가 우리가 아는 것이다.
        shareClick({ targetType: "cocktail", targetSlug, channel: "system" });
        return;
      } catch {
        // 사용자가 취소한 경우도 여기로 온다. 링크 복사로 넘어간다.
      }
    }

    try {
      await navigator.clipboard.writeText(url);
      setMessage("링크를 복사했습니다");
      shareClick({ targetType: "cocktail", targetSlug, channel: "link" });
    } catch {
      setMessage("링크를 복사하지 못했습니다");
    }
  }

  return (
    <div className="detail-actions">
      <button type="button" className="btn btn-secondary" onClick={save} disabled={saved}>
        {saved ? "저장됨" : "저장"}
      </button>
      <button type="button" className="btn btn-secondary" onClick={share}>
        공유
      </button>
      {message && (
        <p className="detail-actions__note" role="status">
          {message}
        </p>
      )}
    </div>
  );
}
