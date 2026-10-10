"use client";

import { useRef, useState } from "react";

export type Toast = { message: string; undo?: () => void; /** Override how long it stays. */ durationMs?: number } | null;

/** One toast at a time; ones with Undo stay a little longer. */
export function useToast() {
  const [toast, setToast] = useState<Toast>(null);
  const timer = useRef<ReturnType<typeof setTimeout>>(undefined);
  function showToast(next: Toast) {
    clearTimeout(timer.current);
    setToast(next);
    timer.current = setTimeout(() => setToast(null), next?.durationMs ?? (next?.undo ? 5000 : 1800));
  }
  return { toast, showToast, hideToast: () => setToast(null) };
}

export function ToastBar({ toast, onUndo }: { toast: Toast; onUndo: () => void }) {
  return (
    <div
      aria-live="polite"
      className="pointer-events-none fixed inset-x-0 bottom-[calc(5.5rem+env(safe-area-inset-bottom))] z-30 flex justify-center px-4 md:bottom-14"
    >
      {toast && (
        <div className="chunky pointer-events-auto flex items-center gap-4 rounded-xl bg-card px-4 py-2.5 text-sm font-medium">
          <span>{toast.message}</span>
          {toast.undo && (
            <button
              type="button"
              onClick={() => {
                toast.undo?.();
                onUndo();
              }}
              className="-my-2 py-2 font-bold underline underline-offset-4"
              style={{ color: "var(--pill-blue)" }}
            >
              Undo
            </button>
          )}
        </div>
      )}
    </div>
  );
}
