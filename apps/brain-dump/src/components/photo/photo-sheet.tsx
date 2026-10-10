"use client";

import { useEffect, useRef, useState } from "react";
import { CameraIcon } from "@/components/icons";
import { MAX_CAPTURE_LENGTH } from "@/lib/limits";
import { PhotoError, shrinkPhoto } from "@/lib/photo";

type Phase =
  | { name: "pick" }
  | { name: "shrinking" }
  | { name: "review"; photo: Blob; preview: string }
  | { name: "saving"; photo: Blob; preview: string }
  | { name: "error"; message: string };

/**
 * Take or choose a photo, shrink it on the device, optionally add a caption, save.
 * Browsers only open the camera after a real tap, so this sheet starts with two big buttons.
 */
export function PhotoSheet({
  onSave,
  onClose,
}: {
  /** Stores the dump (in the device outbox). Throws on failure so the sheet can say so. */
  onSave: (caption: string, photo: Blob) => Promise<void>;
  onClose: () => void;
}) {
  const [phase, setPhase] = useState<Phase>({ name: "pick" });
  const [caption, setCaption] = useState("");
  const cameraRef = useRef<HTMLInputElement>(null);
  const galleryRef = useRef<HTMLInputElement>(null);

  const preview = phase.name === "review" || phase.name === "saving" ? phase.preview : null;
  useEffect(() => () => {
    if (preview) URL.revokeObjectURL(preview);
  }, [preview]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && phase.name !== "saving" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose, phase.name]);

  async function onPicked(file: File | undefined) {
    if (!file) return;
    setPhase({ name: "shrinking" });
    try {
      const photo = await shrinkPhoto(file);
      setPhase({ name: "review", photo, preview: URL.createObjectURL(photo) });
    } catch (e) {
      setPhase({ name: "error", message: e instanceof PhotoError ? e.message : "Couldn't read that photo." });
    }
  }

  async function save() {
    if (phase.name !== "review") return;
    setPhase({ ...phase, name: "saving" });
    try {
      await onSave(caption.trim(), phase.photo);
      onClose();
    } catch {
      setPhase({ ...phase, name: "review" });
    }
  }

  const pickers = (
    <>
      {/* capture="environment" opens the back camera directly on phones; desktops show a file picker. */}
      <input
        ref={cameraRef}
        type="file"
        accept="image/*"
        capture="environment"
        className="hidden"
        onChange={(e) => {
          void onPicked(e.target.files?.[0]);
          e.target.value = ""; // so picking the same photo again (after Retake) still fires
        }}
      />
      <input
        ref={galleryRef}
        type="file"
        accept="image/*"
        className="hidden"
        onChange={(e) => {
          void onPicked(e.target.files?.[0]);
          e.target.value = ""; // so picking the same photo again (after Retake) still fires
        }}
      />
    </>
  );

  return (
    <div
      className="fixed inset-0 z-40 flex items-end justify-center bg-black/40 sm:items-center"
      role="dialog"
      aria-modal="true"
      aria-label="Photo dump"
    >
      <div className="chunky w-full rounded-t-3xl bg-card p-5 pb-[calc(1.25rem+env(safe-area-inset-bottom))] sm:max-w-md sm:rounded-3xl sm:pb-5">
        {pickers}

        {phase.name === "pick" && (
          <div className="flex flex-col gap-3">
            <p className="text-center text-sm font-semibold text-muted">📷 Photo dump</p>
            <button
              type="button"
              onClick={() => cameraRef.current?.click()}
              className="chunky-sm press flex h-14 items-center justify-center gap-2 rounded-xl bg-brand text-base font-bold text-brand-foreground"
            >
              <CameraIcon className="h-5 w-5" /> Take photo
            </button>
            <button
              type="button"
              onClick={() => galleryRef.current?.click()}
              className="chunky-sm press h-12 rounded-xl bg-card font-semibold"
            >
              Choose from gallery
            </button>
            <button type="button" onClick={onClose} className="h-11 font-semibold text-muted">
              Cancel
            </button>
          </div>
        )}

        {phase.name === "shrinking" && (
          <p className="py-10 text-center font-semibold text-muted">Getting your photo ready…</p>
        )}

        {(phase.name === "review" || phase.name === "saving") && (
          <div className="flex flex-col gap-3">
            {/* eslint-disable-next-line @next/next/no-img-element -- a local blob: preview, not a site image */}
            <img
              src={phase.preview}
              alt="Your photo"
              className="max-h-[45dvh] w-full rounded-2xl border-2 border-ink bg-surface object-contain"
            />
            <label htmlFor="photo-caption" className="sr-only">
              Caption (optional)
            </label>
            <textarea
              id="photo-caption"
              value={caption}
              onChange={(e) => setCaption(e.target.value)}
              maxLength={MAX_CAPTURE_LENGTH}
              rows={2}
              autoCapitalize="sentences"
              placeholder="Add a caption (optional)"
              className="w-full resize-none rounded-2xl border-2 border-hairline bg-surface p-3 text-base outline-none focus:border-ink"
            />
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => setPhase({ name: "pick" })}
                disabled={phase.name === "saving"}
                className="h-12 flex-1 rounded-xl border-2 border-hairline font-semibold text-muted sm:h-11"
              >
                Retake
              </button>
              <button
                type="button"
                onClick={save}
                disabled={phase.name === "saving"}
                className="chunky-sm press h-12 flex-[2] rounded-xl bg-brand font-bold text-brand-foreground disabled:opacity-60 sm:h-11"
              >
                {phase.name === "saving" ? "Saving…" : "Dump it"}
              </button>
            </div>
          </div>
        )}

        {phase.name === "error" && (
          <div className="flex flex-col gap-4 text-center">
            <p className="text-lg font-semibold">📷 Hmm.</p>
            <p className="text-muted">{phase.message}</p>
            <div className="flex gap-2">
              <button type="button" onClick={onClose} className="h-12 flex-1 rounded-xl border-2 border-hairline font-semibold text-muted">
                Close
              </button>
              <button
                type="button"
                onClick={() => setPhase({ name: "pick" })}
                className="chunky-sm press h-12 flex-1 rounded-xl bg-card font-semibold"
              >
                Try another
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
