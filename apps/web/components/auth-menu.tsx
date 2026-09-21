"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ADMIN_PATH, SAVED_PATH } from "@/lib/routes";
import { fetchProfile, startLogin, logout, type MyProfile } from "@/lib/auth-client";

/**
 * 내비 오른쪽 로그인 영역 (SPEC-07 §2.5).
 *
 * 로그인 상태를 `GET /me/profile` 로 한 번 확인해 로그인/로그아웃을 가른다.
 * 확인 전에는 아무것도 그리지 않는다 — 로그인했는데 "로그인" 버튼이 잠깐 번쩍이면
 * 상태가 튄 것처럼 보인다.
 */
export function AuthMenu() {
  const [profile, setProfile] = useState<MyProfile | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    let alive = true;
    fetchProfile().then((p) => {
      if (alive) {
        setProfile(p);
        setReady(true);
      }
    });
    return () => {
      alive = false;
    };
  }, []);

  if (!ready) return <div className="auth-menu" aria-hidden />;

  if (!profile) {
    return (
      <div className="auth-menu">
        <button type="button" className="btn auth-link" onClick={startLogin}>
          로그인
        </button>
      </div>
    );
  }

  // 편집자·관리자에게만 어드민 입구 (#207). 판정은 서버가 한다 — 여기는 입구를 보여 줄지만 정한다 (SPEC-08 §2).
  const isStaff = profile.roles.some((r) => r === "admin" || r === "editor");

  return (
    <div className="auth-menu">
      {isStaff ? (
        <Link href={ADMIN_PATH} className="btn auth-link auth-link--admin">
          어드민
        </Link>
      ) : null}
      <Link href={SAVED_PATH} className="btn auth-link">
        내 저장
      </Link>
      <button type="button" className="btn auth-link auth-link--muted" onClick={logout}>
        로그아웃
      </button>
    </div>
  );
}
