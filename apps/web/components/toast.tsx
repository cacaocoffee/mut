"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";

/**
 * 토스트 알림 (디자인 시스템 컴포넌트).
 *
 * 저장·발행·삭제 같은 행동의 결과를 화면 구석에 잠깐 띄운다. 어드민 화면들이
 * 각자 `<p>` 로 메시지를 그리던 것을 이 하나로 모은다 — 양식이 한 곳에 있어야
 * 색·간격·사라지는 시간이 화면마다 어긋나지 않는다.
 *
 * 성공은 스스로 사라지고(4초), 오류는 남긴다 — 놓치면 안 되는 것이라 손으로 닫는다.
 * 접근성: 영역은 `aria-live="polite"`, 오류는 `role="alert"` 로 즉시 읽힌다.
 */
type ToastKind = "success" | "error";

interface Toast {
  id: number;
  kind: ToastKind;
  message: string;
}

interface ToastApi {
  success: (message: string) => void;
  error: (message: string) => void;
}

const ToastContext = createContext<ToastApi | null>(null);

/** 성공 토스트가 스스로 사라지기까지 (오류는 안 사라진다). */
const AUTO_DISMISS_MS = 4000;

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const seq = useRef(0);

  const remove = useCallback((id: number) => {
    setToasts((list) => list.filter((t) => t.id !== id));
  }, []);

  const push = useCallback((kind: ToastKind, message: string) => {
    const id = ++seq.current;
    setToasts((list) => [...list, { id, kind, message }]);
    if (kind === "success") {
      setTimeout(() => remove(id), AUTO_DISMISS_MS);
    }
  }, [remove]);

  // useMemo 로 한 번만 만든다. useRef 를 렌더에서 읽으면 React Compiler 가 막는다.
  const api = useMemo<ToastApi>(
    () => ({
      success: (m) => push("success", m),
      error: (m) => push("error", m),
    }),
    [push],
  );

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="toast-stack" role="region" aria-live="polite" aria-label="알림">
        {toasts.map((t) => (
          <ToastItem key={t.id} toast={t} onClose={() => remove(t.id)} />
        ))}
      </div>
    </ToastContext.Provider>
  );
}

function ToastItem({ toast, onClose }: { toast: Toast; onClose: () => void }) {
  // 들어올 때 한 프레임 뒤 `--in` 을 붙여 CSS 트랜지션이 걸리게 한다.
  const [shown, setShown] = useState(false);
  useEffect(() => {
    const raf = requestAnimationFrame(() => setShown(true));
    return () => cancelAnimationFrame(raf);
  }, []);

  return (
    <div
      className={`toast toast--${toast.kind}${shown ? " toast--in" : ""}`}
      role={toast.kind === "error" ? "alert" : "status"}
    >
      <span className="toast__mark" aria-hidden="true">
        {toast.kind === "success" ? "✓" : "!"}
      </span>
      <span className="toast__body">{toast.message}</span>
      <button type="button" className="toast__close" aria-label="닫기" onClick={onClose}>
        ✕
      </button>
    </div>
  );
}

/**
 * 어느 클라이언트 컴포넌트에서든 `const toast = useToast()` 로 부른다.
 * `ToastProvider` 밖에서 부르면 개발 중에 바로 알도록 던진다.
 */
export function useToast(): ToastApi {
  const api = useContext(ToastContext);
  if (!api) throw new Error("useToast 는 ToastProvider 안에서만 쓴다");
  return api;
}
