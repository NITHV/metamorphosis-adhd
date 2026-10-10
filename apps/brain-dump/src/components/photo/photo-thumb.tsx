"use client";

import { useEffect, useState } from "react";

/**
 * A photo dump's thumbnail. Tap to see it full-screen. `src` is /api/photo/[id] for saved dumps,
 * or a local blob: URL for one still waiting in the device outbox.
 */
export function PhotoThumb({ src, alt }: { src: string; alt: string }) {
  const [open, setOpen] = useState(false);
  const [broken, setBroken] = useState(false);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open]);

  if (broken) {
    return (
      <span className="inline-flex h-24 w-32 items-center justify-center rounded-xl border-2 border-dashed border-hairline text-xs text-muted">
        📷 Can&apos;t load photo
      </span>
    );
  }

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        aria-label={`View photo: ${alt}`}
        className="block overflow-hidden rounded-xl border-2 border-ink"
      >
        {/* eslint-disable-next-line @next/next/no-img-element -- private, per-user images; next/image can't optimise them */}
        <img src={src} alt="" loading="lazy" decoding="async" onError={() => setBroken(true)} className="h-24 w-32 bg-surface object-cover sm:h-28 sm:w-40" />
      </button>
      {open && (
        <div
          role="dialog"
          aria-modal="true"
          aria-label="Photo"
          onClick={() => setOpen(false)}
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/85 p-4 pt-[max(1rem,env(safe-area-inset-top))] pb-[max(1rem,env(safe-area-inset-bottom))]"
        >
          {/* eslint-disable-next-line @next/next/no-img-element -- see above */}
          <img src={src} alt={alt} className="max-h-full max-w-full rounded-xl object-contain" />
          <button
            type="button"
            onClick={() => setOpen(false)}
            aria-label="Close photo"
            className="absolute top-[max(1rem,env(safe-area-inset-top))] right-4 flex h-11 w-11 items-center justify-center rounded-full bg-white/15 text-xl text-white"
          >
            ✕
          </button>
        </div>
      )}
    </>
  );
}
