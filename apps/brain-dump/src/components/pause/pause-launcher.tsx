"use client";

import { upload } from "@vercel/blob/client";
import { useEffect, useState } from "react";
import { createCheckpoint } from "@/app/actions";
import { MicIcon, PauseIcon } from "@/components/icons";
import { ToastBar, useToast } from "@/components/toast";
import { VoiceRecorder } from "@/components/voice/voice-recorder";

export const OPEN_PAUSE_EVENT = "brain-dump:pause";

/** Opens the Pause sheet from anywhere: dispatch OPEN_PAUSE_EVENT, or link with ?pause=1. */
export function openPause() {
  window.dispatchEvent(new Event(OPEN_PAUSE_EVENT));
}

export function PauseLauncher({ userId }: { userId: string }) {
  const [open, setOpen] = useState(false);
  const { toast, showToast, hideToast } = useToast();

  useEffect(() => {
    const onOpen = () => setOpen(true);
    window.addEventListener(OPEN_PAUSE_EVENT, onOpen);
    if (new URLSearchParams(window.location.search).get("pause") === "1") {
      window.history.replaceState(null, "", window.location.pathname);
      onOpen();
    }
    return () => window.removeEventListener(OPEN_PAUSE_EVENT, onOpen);
  }, []);

  return (
    <>
      {open && (
        <PauseSheet
          userId={userId}
          onClose={() => setOpen(false)}
          onSaved={() => showToast({ message: "Paused ⏸ Your place is saved." })}
        />
      )}
      <ToastBar toast={toast} onUndo={hideToast} />
    </>
  );
}

function PauseSheet({ userId, onClose, onSaved }: { userId: string; onClose: () => void; onSaved: () => void }) {
  const [recording, setRecording] = useState(false);
  const [audio, setAudio] = useState<Blob | null>(null);
  const [whereText, setWhereText] = useState("");
  const [nextStep, setNextStep] = useState("");
  const [tag, setTag] = useState("");
  const [link, setLink] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canSave = Boolean(audio || whereText.trim() || nextStep.trim());

  async function save() {
    setSaving(true);
    setError(null);
    try {
      let audioUrl: string | null = null;
      if (audio) {
        const ext = audio.type.includes("mp4") ? "m4a" : audio.type.includes("ogg") ? "ogg" : "webm";
        const blob = await upload(`voice/${userId}/pause-${Date.now()}.${ext}`, audio, {
          access: "private",
          handleUploadUrl: "/api/audio/upload",
          contentType: audio.type || "audio/webm",
        });
        audioUrl = blob.url;
      }
      await createCheckpoint({ transcript: whereText, nextStep, projectTag: tag, link, audioUrl });
      onSaved();
      onClose();
    } catch {
      // Everything typed or recorded stays in the sheet, so retrying loses nothing.
      setError("Couldn't save. Check your connection and try again. Nothing you entered is lost.");
      setSaving(false);
    }
  }

  const input =
    "w-full rounded-xl border-2 border-hairline bg-surface px-3 py-2.5 text-base outline-none focus:border-ink";

  return (
    <>
      <div
        className="fixed inset-0 z-40 flex items-end justify-center bg-black/40 sm:items-center"
        role="dialog"
        aria-modal="true"
        aria-labelledby="pause-title"
      >
        <form
          noValidate // links like "docs.google.com/…" are fine; the server adds https://
          onSubmit={(e) => {
            e.preventDefault();
            if (canSave && !saving) void save();
          }}
          className="chunky flex max-h-[92dvh] w-full flex-col gap-4 overflow-y-auto rounded-t-3xl bg-card p-5 pb-[calc(1.25rem+env(safe-area-inset-bottom))] sm:max-w-md sm:rounded-3xl sm:pb-5"
        >
          <div>
            <h2 id="pause-title" className="flex items-center gap-2 text-xl font-extrabold tracking-tight">
              <PauseIcon className="h-5 w-5" /> Pause
            </h2>
            <p className="mt-0.5 text-sm text-muted">Leave a note for future you, so getting back in is easy.</p>
          </div>

          {audio ? (
            <div
              className="flex items-center justify-between rounded-xl px-3 py-2.5 text-sm font-semibold"
              style={{ background: "var(--pill-red-soft)", color: "var(--pill-red)" }}
            >
              <span>🎙️ Voice note added ✓</span>
              <button type="button" onClick={() => setRecording(true)} className="-my-2 py-2 underline underline-offset-4">
                Redo
              </button>
            </div>
          ) : (
            <button
              type="button"
              onClick={() => setRecording(true)}
              className="chunky-sm press flex h-14 items-center justify-center gap-2 rounded-2xl bg-card text-base font-bold"
              style={{ color: "var(--pill-red)" }}
            >
              <MicIcon className="h-5 w-5" /> Say where you&apos;re up to
            </button>
          )}

          <label className="flex flex-col gap-1.5">
            <span className="text-sm font-semibold">Where are you up to?</span>
            <textarea
              value={whereText}
              onChange={(e) => setWhereText(e.target.value)}
              rows={2}
              placeholder="Halfway through the budget sheet…"
              className={`${input} resize-none`}
            />
          </label>

          <label className="flex flex-col gap-1.5">
            <span className="text-sm font-semibold">Next, I need to…</span>
            <input
              value={nextStep}
              onChange={(e) => setNextStep(e.target.value)}
              placeholder="fill in March"
              enterKeyHint="done"
              className={input}
            />
          </label>

          <details className="group">
            <summary className="cursor-pointer list-none py-1 text-sm font-semibold text-muted [&::-webkit-details-marker]:hidden">
              <span className="group-open:hidden">+ Add a tag or link</span>
              <span className="hidden group-open:inline">Tag and link</span>
            </summary>
            <div className="mt-2 flex flex-col gap-3">
              <input
                value={tag}
                onChange={(e) => setTag(e.target.value)}
                placeholder="Project or tag (e.g. Taxes)"
                className={input}
              />
              <input
                value={link}
                onChange={(e) => setLink(e.target.value)}
                type="text"
                inputMode="url"
                autoCorrect="off"
                autoCapitalize="none"
                placeholder="Link to what you had open"
                className={input}
              />
            </div>
          </details>

          {error && (
            <p className="text-sm font-medium" role="alert" style={{ color: "var(--pill-red)" }}>
              {error}
            </p>
          )}

          <div className="flex gap-2">
            <button
              type="button"
              onClick={onClose}
              disabled={saving}
              className="h-12 flex-1 rounded-xl border-2 border-hairline font-semibold text-muted sm:h-11"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={!canSave || saving}
              className="chunky-sm press h-12 flex-[2] rounded-xl bg-brand font-bold text-brand-foreground disabled:opacity-50 sm:h-11"
            >
              {saving ? "Saving…" : "Save my place"}
            </button>
          </div>
        </form>
      </div>

      {recording && (
        <VoiceRecorder
          title="Pause note"
          saveLabel="Use this"
          onClose={() => setRecording(false)}
          onSave={async (text, blob) => {
            setAudio(blob);
            if (text.trim()) setWhereText((current) => current.trim() || text.trim());
          }}
        />
      )}
    </>
  );
}
