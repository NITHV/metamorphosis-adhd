"use client";

import Link from "next/link";
import { startTransition, useEffect, useOptimistic, useRef, useState } from "react";
import { archiveCapture, createCapture, restoreCapture } from "@/app/actions";
import { FOCUS_DUMP_EVENT } from "@/components/toolbar-actions";
import { InboxIcon } from "@/components/icons";
import type { InboxCapture } from "@/lib/captures";
import { MAX_CAPTURE_LENGTH } from "@/lib/limits";

type OptimisticAction =
  | { type: "add"; capture: InboxCapture }
  | { type: "remove"; id: string };

type Toast = { message: string; undoId?: string } | null;

export function DumpInbox({
  captures,
  limit,
  autoFocus = true,
}: {
  captures: InboxCapture[];
  /** Show only the newest N, with a "See all" link (used on Home). */
  limit?: number;
  autoFocus?: boolean;
}) {
  const [items, applyOptimistic] = useOptimistic(captures, (state, action: OptimisticAction) =>
    action.type === "add" ? [action.capture, ...state] : state.filter((c) => c.id !== action.id),
  );
  const [text, setText] = useState("");
  const [toast, setToast] = useState<Toast>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const toastTimer = useRef<ReturnType<typeof setTimeout>>(undefined);

  function showToast(next: Toast) {
    clearTimeout(toastTimer.current);
    setToast(next);
    toastTimer.current = setTimeout(() => setToast(null), next?.undoId ? 5000 : 1800);
  }

  // Ctrl/⌘ + K (or the pencil in the toolbar) jumps to the capture box.
  useEffect(() => {
    const focus = () => inputRef.current?.focus();
    function onKey(e: KeyboardEvent) {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        focus();
      }
    }
    window.addEventListener("keydown", onKey);
    window.addEventListener(FOCUS_DUMP_EVENT, focus);
    return () => {
      window.removeEventListener("keydown", onKey);
      window.removeEventListener(FOCUS_DUMP_EVENT, focus);
    };
  }, []);

  function dump() {
    const value = text.trim();
    if (!value) return;
    setText("");
    inputRef.current?.focus();
    startTransition(async () => {
      applyOptimistic({
        type: "add",
        capture: { id: `temp-${Date.now()}`, rawText: value, createdAt: new Date().toISOString() },
      });
      try {
        await createCapture(value);
        showToast({ message: "Saved ✓" });
      } catch {
        setText(value); // give it back so nothing is lost
        showToast({ message: "Couldn't save. Your text is back in the box." });
      }
    });
  }

  function archive(id: string) {
    startTransition(async () => {
      applyOptimistic({ type: "remove", id });
      try {
        await archiveCapture(id);
        showToast({ message: "Cleared from inbox", undoId: id });
      } catch {
        showToast({ message: "Couldn't clear that one. Try again." });
      }
    });
  }

  function undo(id: string) {
    setToast(null);
    startTransition(async () => {
      await restoreCapture(id);
    });
  }

  const visible = limit ? items.slice(0, limit) : items;

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
            if (e.key === "Enter" && !e.shiftKey && !e.nativeEvent.isComposing) {
              e.preventDefault();
              dump();
            }
          }}
          rows={2}
          maxLength={MAX_CAPTURE_LENGTH}
          enterKeyHint="send"
          autoFocus={autoFocus}
          placeholder="What's on your mind? A task, an idea, a worry…"
          className="block w-full resize-none bg-transparent px-1.5 py-1 text-lg outline-none placeholder:text-muted"
        />
        <div className="mt-2 flex items-center justify-between gap-3">
          <ShortcutHint />
          <button
            type="submit"
            disabled={!text.trim()}
            className="chunky-sm press h-11 rounded-xl bg-brand px-7 text-base font-bold text-brand-foreground disabled:opacity-50"
          >
            Dump it
          </button>
        </div>
      </form>

      <section className="mt-8" aria-labelledby="inbox-heading">
        <div className="mb-3 flex items-center justify-between">
          <h2 id="inbox-heading" className="flex items-center gap-2 text-lg font-bold">
            <span
              className="chunky-sm flex h-7 w-7 items-center justify-center rounded-lg text-white"
              style={{ background: "var(--pill-blue)" }}
            >
              <InboxIcon className="h-4 w-4" />
            </span>
            Inbox
            {items.length > 0 && (
              <span className="rounded-full bg-hairline px-2 py-0.5 text-xs font-semibold tabular-nums">
                {items.length}
              </span>
            )}
          </h2>
          {limit && items.length > limit ? (
            <Link href="/inbox" className="text-sm font-medium" style={{ color: "var(--pill-blue)" }}>
              See all
            </Link>
          ) : (
            items.length > 0 && <span className="text-sm text-muted">Sort later. No rush.</span>
          )}
        </div>

        {items.length === 0 ? (
          <p className="rounded-2xl border-2 border-dashed border-hairline px-4 py-10 text-center text-muted">
            Inbox empty. Your head is clear 🌤️
          </p>
        ) : (
          <ul className="chunky divide-y-2 divide-hairline overflow-hidden rounded-2xl bg-card">
            {visible.map((c) => {
              const pending = c.id.startsWith("temp-");
              return (
                <li
                  key={c.id}
                  className={`flex items-start gap-3 px-4 py-3 transition ${pending ? "opacity-60" : "hover:bg-surface"}`}
                >
                  <button
                    type="button"
                    disabled={pending}
                    onClick={() => archive(c.id)}
                    aria-label={`Clear "${c.rawText.slice(0, 40)}" from inbox`}
                    title="Clear from inbox"
                    className="group mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full border-2 border-foreground transition hover:bg-brand-soft"
                  >
                    <svg viewBox="0 0 12 12" className="h-3 w-3 opacity-0 transition group-hover:opacity-100" aria-hidden>
                      <path d="m2.5 6.2 2.2 2.2 4.8-5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
                    </svg>
                  </button>
                  <p className="flex-1 whitespace-pre-wrap break-words leading-snug">{c.rawText}</p>
                  <RelativeTime iso={c.createdAt} />
                </li>
              );
            })}
          </ul>
        )}
      </section>

      <div aria-live="polite" className="pointer-events-none fixed inset-x-0 bottom-24 z-30 flex justify-center px-4 md:bottom-14">
        {toast && (
          <div className="chunky pointer-events-auto flex items-center gap-4 rounded-xl bg-card px-4 py-2.5 text-sm font-medium">
            <span>{toast.message}</span>
            {toast.undoId && (
              <button
                type="button"
                onClick={() => undo(toast.undoId!)}
                className="font-bold underline underline-offset-4"
                style={{ color: "var(--pill-blue)" }}
              >
                Undo
              </button>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

function ShortcutHint() {
  const [label, setLabel] = useState<string | null>(null);
  useEffect(() => {
    const isMac = /Mac|iPhone|iPad/.test(navigator.platform);
    const isTouch = window.matchMedia("(pointer: coarse)").matches;
    // eslint-disable-next-line react-hooks/set-state-in-effect -- platform is only known in the browser
    setLabel(isTouch ? null : isMac ? "⌘K to jump here · Enter to save" : "Ctrl K to jump here · Enter to save");
  }, []);
  return <span className="px-1.5 text-xs text-muted">{label}</span>;
}

function RelativeTime({ iso }: { iso: string }) {
  return (
    <time
      dateTime={iso}
      suppressHydrationWarning
      className="shrink-0 rounded-full px-2.5 py-0.5 text-xs font-semibold"
      style={{ background: "var(--pill-blue-soft)", color: "var(--pill-blue)" }}
    >
      {formatRelative(new Date(iso))}
    </time>
  );
}

function formatRelative(date: Date): string {
  const seconds = Math.round((Date.now() - date.getTime()) / 1000);
  if (seconds < 60) return "just now";
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.round(hours / 24);
  if (days === 1) return "yesterday";
  if (days < 7) return `${days}d ago`;
  return date.toLocaleDateString(undefined, { month: "short", day: "numeric" });
}
