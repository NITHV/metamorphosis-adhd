"use client";

import Link from "next/link";
import { startTransition, useEffect, useOptimistic, useRef, useState } from "react";
import { ToastBar, useToast } from "@/components/toast";
import {
  archiveCapture,
  restoreCapture,
  sortCapture,
  splitCapture,
  unsortCapture,
} from "@/app/actions";
import { FOCUS_DUMP_EVENT, OPEN_VOICE_EVENT } from "@/components/toolbar-actions";
import { InboxIcon, MicIcon } from "@/components/icons";
import { PlayButton } from "@/components/voice/play-button";
import { VoiceRecorder } from "@/components/voice/voice-recorder";
import { RelativeTime, formatDue } from "@/components/time";
import { useIsClient } from "@/components/use-is-client";
import type { InboxCapture } from "@/lib/captures";
import { KIND_META, KINDS, type Kind } from "@/lib/kinds";
import { MAX_CAPTURE_LENGTH } from "@/lib/limits";
import { enqueueText, enqueueVoice } from "@/lib/outbox";
import { useOutbox } from "@/components/offline/use-outbox";
import { findDate, guessKind, splitDump } from "@/lib/smart-guess";

/** A dump still on its way to the server (saved in the device outbox). */
type Row = InboxCapture & { waiting?: boolean };

type OptimisticAction =
  | { type: "add"; captures: InboxCapture[] }
  | { type: "remove"; id: string };

export function DumpInbox({
  userId,
  captures,
  limit,
  autoFocus = true,
}: {
  userId: string;
  captures: InboxCapture[];
  /** Show only the newest N, with a "See all" link (used on Home). */
  limit?: number;
  autoFocus?: boolean;
}) {
  const [items, applyOptimistic] = useOptimistic(captures, (state, action: OptimisticAction) =>
    action.type === "add" ? [...action.captures, ...state] : state.filter((c) => c.id !== action.id),
  );
  const [text, setText] = useState("");
  const { toast, showToast, hideToast } = useToast();
  const [voiceOpen, setVoiceOpen] = useState(false);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  // Ctrl/⌘ + K (or the pencil in the toolbar) jumps to the capture box.
  useEffect(() => {
    const focus = () => inputRef.current?.focus();
    function onKey(e: KeyboardEvent) {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        focus();
      }
    }
    const openVoice = () => setVoiceOpen(true);
    window.addEventListener("keydown", onKey);
    window.addEventListener(FOCUS_DUMP_EVENT, focus);
    window.addEventListener(OPEN_VOICE_EVENT, openVoice);
    // The toolbar mic on other pages links here with ?voice=1.
    if (new URLSearchParams(window.location.search).get("voice") === "1") {
      window.history.replaceState(null, "", window.location.pathname);
      openVoice();
    }
    return () => {
      window.removeEventListener("keydown", onKey);
      window.removeEventListener(FOCUS_DUMP_EVENT, focus);
      window.removeEventListener(OPEN_VOICE_EVENT, openVoice);
    };
  }, []);

  /** Stores the recording in the device outbox; it uploads now, or as soon as there's signal. */
  async function saveVoice(transcript: string, audio: Blob) {
    await enqueueVoice(userId, transcript, audio);
    showToast({ message: navigator.onLine ? "Voice dump saved ✓" : "Saved on this device ✓ Uploads when you're back online." });
  }

  /** Runs a server action with an optimistic update, and reports failures without losing anything. */
  function run(optimistic: OptimisticAction, action: () => Promise<unknown>, onDone: () => void, onError: string) {
    startTransition(async () => {
      applyOptimistic(optimistic);
      try {
        await action();
        onDone();
      } catch {
        showToast({ message: onError });
      }
    });
  }

  function dump() {
    const value = text.trim();
    if (!value) return;
    setText("");
    inputRef.current?.focus();
    enqueueText(userId, value).then(
      () => showToast({ message: navigator.onLine ? "Saved ✓" : "Saved on this device ✓ Uploads when you're back online." }),
      () => {
        setText(value); // give it back so nothing is lost
        showToast({ message: "Couldn't save. Your text is back in the box." });
      },
    );
  }

  function clear(c: InboxCapture) {
    run(
      { type: "remove", id: c.id },
      () => archiveCapture(c.id),
      () => showToast({ message: "Cleared from inbox", undo: () => startTransition(() => restoreCapture(c.id)) }),
      "Couldn't clear that one. Try again.",
    );
  }

  function sort(c: InboxCapture, kind: Kind) {
    const due = kind === "reminder" || kind === "task" ? findDate(c.rawText) : null;
    run(
      { type: "remove", id: c.id },
      () => sortCapture(c.id, kind, due?.date.toISOString() ?? null),
      () =>
        showToast({
          message: `Filed to ${KIND_META[kind].pile}`,
          undo: () => startTransition(() => unsortCapture(c.id)),
        }),
      "Couldn't file that one. Try again.",
    );
  }

  function split(c: InboxCapture) {
    const parts = splitDump(c.rawText);
    startTransition(async () => {
      applyOptimistic({ type: "remove", id: c.id });
      applyOptimistic({
        type: "add",
        captures: parts.map((rawText, i) => ({
          id: `temp-split-${c.id}-${i}`,
          rawText,
          suggestedKind: guessKind(rawText),
          hasAudio: false,
          createdAt: c.createdAt,
        })),
      });
      try {
        await splitCapture(c.id);
        showToast({ message: `Split into ${parts.length}` });
      } catch {
        showToast({ message: "Couldn't split that one. Try again." });
      }
    });
  }

  // Dumps still in the outbox show at the top until the server has them.
  const outbox = useOutbox();
  const waiting: Row[] = outbox
    .filter((o) => o.userId === userId && !items.some((c) => c.id === o.id))
    .map((o) => ({
      id: `temp-outbox-${o.id}`,
      rawText: o.text || "🎙️ Voice note",
      suggestedKind: null,
      hasAudio: false,
      createdAt: o.createdAt,
      waiting: true,
    }));
  const rows: Row[] = [...waiting, ...items];
  const visible = limit ? rows.slice(0, limit) : rows;

  return (
    <div className="w-full">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          dump();
        }}
        className="chunky rounded-2xl bg-card p-3"
      >
        <label htmlFor="dump" className="sr-only">
          What&apos;s on your mind?
        </label>
        <textarea
          id="dump"
          ref={inputRef}
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => {
            // On touch keyboards Enter adds a new line; the button saves.
            if (e.key === "Enter" && !e.shiftKey && !e.nativeEvent.isComposing && !isTouch()) {
              e.preventDefault();
              dump();
            }
          }}
          rows={2}
          maxLength={MAX_CAPTURE_LENGTH}
          autoFocus={autoFocus}
          autoCapitalize="sentences"
          placeholder="What's on your mind? A task, an idea, a worry…"
          className="block w-full resize-none bg-transparent px-1.5 py-1 text-lg outline-none placeholder:text-muted"
        />
        <div className="mt-2 flex items-center justify-between gap-3">
          <ShortcutHint />
          <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => setVoiceOpen(true)}
            aria-label="Record a voice dump"
            title="Voice dump"
            className="chunky-sm press flex h-12 w-12 items-center justify-center rounded-xl bg-card sm:h-11 sm:w-11"
            style={{ color: "var(--pill-red)" }}
          >
            <MicIcon className="h-5 w-5" />
          </button>
          <button
            type="submit"
            disabled={!text.trim()}
            className="chunky-sm press h-12 rounded-xl bg-brand px-7 text-base font-bold text-brand-foreground disabled:opacity-50 sm:h-11"
          >
            Dump it
          </button>
          </div>
        </div>
      </form>

      {voiceOpen && <VoiceRecorder onSave={saveVoice} onClose={() => setVoiceOpen(false)} />}

      <section className="mt-8" aria-labelledby="inbox-heading">
        <div className="mb-3 flex items-center justify-between gap-3">
          <h2 id="inbox-heading" className="flex items-center gap-2 text-lg font-bold">
            <span
              className="chunky-sm flex h-7 w-7 items-center justify-center rounded-lg text-white"
              style={{ background: "var(--pill-blue)" }}
            >
              <InboxIcon className="h-4 w-4" />
            </span>
            Inbox
            {rows.length > 0 && (
              <span className="rounded-full bg-hairline px-2 py-0.5 text-xs font-semibold tabular-nums">
                {rows.length}
              </span>
            )}
          </h2>
          {limit && rows.length > limit ? (
            <Link href="/inbox" className="py-2 text-sm font-medium" style={{ color: "var(--pill-blue)" }}>
              See all
            </Link>
          ) : (
            rows.length > 0 && <span className="text-sm text-muted">Tap a pile to file it.</span>
          )}
        </div>

        {rows.length === 0 ? (
          <p className="rounded-2xl border-2 border-dashed border-hairline px-4 py-10 text-center text-muted">
            Inbox empty. Your head is clear 🌤️
          </p>
        ) : (
          <ul className="chunky divide-y-2 divide-hairline overflow-hidden rounded-2xl bg-card">
            {visible.map((c) => (
              <InboxRow
                key={c.id}
                capture={c}
                waiting={c.waiting}
                onClear={() => clear(c)}
                onSort={(k) => sort(c, k)}
                onSplit={() => split(c)}
              />
            ))}
          </ul>
        )}
      </section>

      <ToastBar toast={toast} onUndo={hideToast} />
    </div>
  );
}

function InboxRow({
  capture: c,
  waiting,
  onClear,
  onSort,
  onSplit,
}: {
  capture: InboxCapture;
  waiting?: boolean;
  onClear: () => void;
  onSort: (kind: Kind) => void;
  onSplit: () => void;
}) {
  const isClient = useIsClient();
  const pending = c.id.startsWith("temp-");
  const suggested = c.suggestedKind ?? guessKind(c.rawText);
  // Dates depend on the viewer's timezone, so they're only worked out in the browser.
  const due = isClient ? findDate(c.rawText) : null;
  const canSplit = splitDump(c.rawText).length > 1;

  return (
    <li className={`px-3 py-3 sm:px-4 ${pending ? "opacity-60" : ""}`}>
      <div className="flex items-start gap-2 sm:gap-3">
        <button
          type="button"
          disabled={pending}
          onClick={onClear}
          aria-label={`Clear "${c.rawText.slice(0, 40)}" from inbox`}
          title="Clear from inbox"
          className="group -m-1.5 flex shrink-0 items-center justify-center p-1.5"
        >
          <span className="flex h-6 w-6 items-center justify-center rounded-full border-2 border-foreground transition group-hover:bg-brand-soft group-active:bg-brand-soft">
            <svg viewBox="0 0 12 12" className="h-3 w-3 opacity-0 transition group-hover:opacity-100" aria-hidden>
              <path d="m2.5 6.2 2.2 2.2 4.8-5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
            </svg>
          </span>
        </button>
        <p className="min-w-0 flex-1 whitespace-pre-wrap break-words pt-0.5 leading-snug">{c.rawText}</p>
        <RelativeTime iso={c.createdAt} />
      </div>
      {waiting && (
        <p className="mt-1.5 pl-9 text-xs font-semibold sm:pl-10" style={{ color: "var(--pill-orange)" }}>
          ⏳ Saved on this device, waiting to upload
        </p>
      )}

      {!pending && (
        <div className="mt-2.5 pl-9 sm:pl-10">
          {c.hasAudio && (
            <div className="mb-2">
              <PlayButton audioId={c.id} />
            </div>
          )}
          {due && (
            <p className="mb-2 text-xs font-semibold" style={{ color: KIND_META.reminder.color }}>
              📅 {formatDue(due.date)}
            </p>
          )}
          <div className="flex flex-wrap items-center gap-2">
            <div className="grid flex-1 grid-cols-4 gap-1.5 sm:flex sm:flex-none sm:gap-2" role="group" aria-label="File into a pile">
              {KINDS.map((k) => {
                const meta = KIND_META[k];
                const isSuggested = suggested === k;
                return (
                  <button
                    key={k}
                    type="button"
                    onClick={() => onSort(k)}
                    aria-label={`File as ${meta.label}${isSuggested ? " (suggested)" : ""}`}
                    className={`press h-10 rounded-full px-1 text-[13px] font-semibold sm:h-8 sm:px-3.5 ${
                      isSuggested ? "chunky-sm" : "border-2 border-hairline text-muted hover:text-foreground"
                    }`}
                    style={isSuggested ? { background: meta.soft, color: meta.color, borderColor: meta.color } : undefined}
                  >
                    {meta.label}
                  </button>
                );
              })}
            </div>
            {canSplit && (
              <button
                type="button"
                onClick={onSplit}
                className="h-10 rounded-full border-2 border-dashed border-hairline px-3.5 text-[13px] font-semibold text-muted hover:text-foreground sm:h-8"
              >
                ✂ Split
              </button>
            )}
          </div>
        </div>
      )}
    </li>
  );
}

function isTouch() {
  return typeof window !== "undefined" && window.matchMedia("(pointer: coarse)").matches;
}

function ShortcutHint() {
  const [label, setLabel] = useState<string | null>(null);
  useEffect(() => {
    const isMac = /Mac|iPhone|iPad/.test(navigator.platform);
    // eslint-disable-next-line react-hooks/set-state-in-effect -- platform is only known in the browser
    setLabel(isTouch() ? null : isMac ? "⌘K to jump here · Enter to save" : "Ctrl K to jump here · Enter to save");
  }, []);
  return <span className="px-1.5 text-xs text-muted">{label}</span>;
}
