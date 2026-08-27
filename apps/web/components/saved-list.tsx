"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { adminWrite } from "@/lib/admin-csrf";
import { startLogin, bookmarkHref } from "@/lib/auth-client";

/**
 * 내 저장 목록 (`FR-USER-003` · SPEC-07 §2.5).
 *
 * API `GET /me/bookmarks` 를 세션 쿠키로 부른다. 비로그인은 401 이라 로그인 유도로 가른다.
 * 삭제는 `DELETE /me/bookmarks/{id}` — CSRF 가 필요해 `adminWrite`(범용 CSRF 쓰기)를 쓴다.
 */
interface BookmarkItem {
  id: number;
  targetType: string;
  targetSlug: string;
  nameKo: string;
  nameEn: string;
  collectionId: number | null;
}

type State =
  | { kind: "loading" }
  | { kind: "guest" }
  | { kind: "error" }
  | { kind: "ready"; items: BookmarkItem[] };

export function SavedList() {
  const [state, setState] = useState<State>({ kind: "loading" });

  useEffect(() => {
    let alive = true;
    (async () => {
      try {
        const res = await fetch("/api/v1/me/bookmarks", { cache: "no-store" });
        if (!alive) return;
        if (res.status === 401) return setState({ kind: "guest" });
        if (!res.ok) return setState({ kind: "error" });
        setState({ kind: "ready", items: (await res.json()) as BookmarkItem[] });
      } catch {
        if (alive) setState({ kind: "error" });
      }
    })();
    return () => {
      alive = false;
    };
  }, []);

  async function remove(id: number) {
    // 낙관적 제거 — 실패하면 되돌린다. 목록이 짧아 다시 부르는 대신 화면만 고친다.
    if (state.kind !== "ready") return;
    const prev = state.items;
    setState({ kind: "ready", items: prev.filter((b) => b.id !== id) });
    try {
      const res = await adminWrite(`/api/v1/me/bookmarks/${id}`, { method: "DELETE" });
      if (!res.ok && res.status !== 404) setState({ kind: "ready", items: prev });
    } catch {
      setState({ kind: "ready", items: prev });
    }
  }

  if (state.kind === "loading") {
    return <p className="saved-status" role="status">불러오는 중…</p>;
  }

  if (state.kind === "guest") {
    return (
      <div className="empty-state">
        <h3>로그인하면 저장한 것을 볼 수 있습니다</h3>
        <p>마음에 든 칵테일과 아티클을 저장해 두고 다시 찾아보세요.</p>
        <button type="button" className="btn btn-primary" onClick={startLogin}>
          카카오로 로그인
        </button>
      </div>
    );
  }

  if (state.kind === "error") {
    return (
      <div className="empty-state">
        <h3>지금은 목록을 불러올 수 없습니다</h3>
        <p>잠시 뒤 다시 시도해 주세요.</p>
      </div>
    );
  }

  if (state.items.length === 0) {
    return (
      <div className="empty-state">
        <h3>아직 저장한 것이 없습니다</h3>
        <p>칵테일이나 아티클에서 「저장」을 누르면 여기 모입니다.</p>
      </div>
    );
  }

  return (
    <ul className="saved-list">
      {state.items.map((b) => {
        const href = bookmarkHref(b.targetType, b.targetSlug);
        return (
          <li key={b.id} className="saved-item">
            <div className="saved-item__body">
              <span className="saved-item__kind">{kindLabel(b.targetType)}</span>
              {href ? (
                <Link href={href} className="saved-item__title">
                  {b.nameKo}
                </Link>
              ) : (
                <span className="saved-item__title">{b.nameKo}</span>
              )}
              {b.nameEn && <span className="saved-item__sub">{b.nameEn}</span>}
            </div>
            <button
              type="button"
              className="btn btn-secondary saved-item__remove"
              onClick={() => remove(b.id)}
              aria-label={`${b.nameKo} 저장 해제`}
            >
              해제
            </button>
          </li>
        );
      })}
    </ul>
  );
}

/** 다형 참조라 종류를 한 낱말로 붙인다. 모르는 종류는 그대로 코드를 보여 준다. */
function kindLabel(targetType: string): string {
  if (targetType === "cocktail") return "칵테일";
  if (targetType === "article") return "아티클";
  if (targetType === "bar") return "바";
  return targetType;
}
