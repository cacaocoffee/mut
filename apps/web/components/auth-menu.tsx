"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { SAVED_PATH } from "@/lib/routes";
import { fetchProfile, startLogin, type MyProfile } from "@/lib/auth-client";

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

  // 로그아웃은 내 저장 화면 안에 있다 (#183) — 내비에 글자 둘이 붙으면 탭이 다섯처럼 읽힌다.
  return (
    <div className="auth-menu">
      <Link href={SAVED_PATH} className="btn auth-link">
        내 저장
      </Link>
    </div>
  );
}
