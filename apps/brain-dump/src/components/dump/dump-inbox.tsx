"use client";

import Link from "next/link";
import { startTransition, useEffect, useOptimistic, useRef, useState } from "react";
import { ToastBar, useToast } from "@/components/toast";
import {
  archiveCapture,
  createCapture,
  restoreCapture,
  sortCapture,
  splitCapture,
  unsortCapture,
} from "@/app/actions";
import { FOCUS_DUMP_EVENT } from "@/components/toolbar-actions";
import { InboxIcon } from "@/components/icons";
import { RelativeTime, formatDue } from "@/components/time";
import { useIsClient } from "@/components/use-is-client";
import type { InboxCapture } from "@/lib/captures";
import { KIND_META, KINDS, type Kind } from "@/lib/kinds";
import { MAX_CAPTURE_LENGTH } from "@/lib/limits";
import { findDate, guessKind, splitDump } from "@/lib/smart-guess";

type OptimisticAction =
  | { type: "add"; captures: InboxCapture[] }
  | { type: "remove"; id: string };

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
    action.type === "add" ? [...action.captures, ...state] : state.filter((c) => c.id !== action.id),
  );
  const [text, setText] = useState("");
  const { toast, showToast, hideToast } = useToast();
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
    window.addEventListener("keydown", onKey);
    window.addEventListener(FOCUS_DUMP_EVENT, focus);
    return () => {
      window.removeEventListener("keydown", onKey);
      window.removeEventListener(FOCUS_DUMP_EVENT, focus);
    };
  }, []);

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
    const temp: InboxCapture = {
      id: `temp-${Date.now()}`,
      rawText: value,
      suggestedKind: guessKind(value),
      createdAt: new Date().toISOString(),
    };
    startTransition(async () => {
      applyOptimistic({ type: "add", captures: [temp] });
      try {
        await createCapture(value);
        showToast({ message: "Saved ✓" });
      } catch {
        setText(value); // give it back so nothing is lost
        showToast({ message: "Couldn't save. Your text is back in the box." });
      }
    });
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
          <button
            type="submit"
            disabled={!text.trim()}
            className="chunky-sm press h-12 rounded-xl bg-brand px-7 text-base font-bold text-brand-foreground disabled:opacity-50 sm:h-11"
          >
            Dump it
          </button>
        </div>
      </form>

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
            {items.length > 0 && (
              <span className="rounded-full bg-hairline px-2 py-0.5 text-xs font-semibold tabular-nums">
                {items.length}
              </span>
            )}
          </h2>
          {limit && items.length > limit ? (
            <Link href="/inbox" className="py-2 text-sm font-medium" style={{ color: "var(--pill-blue)" }}>
              See all
            </Link>
          ) : (
            items.length > 0 && <span className="text-sm text-muted">Tap a pile to file it.</span>
          )}
        </div>

        {items.length === 0 ? (
          <p className="rounded-2xl border-2 border-dashed border-hairline px-4 py-10 text-center text-muted">
            Inbox empty. Your head is clear 🌤️
          </p>
        ) : (
          <ul className="chunky divide-y-2 divide-hairline overflow-hidden rounded-2xl bg-card">
            {visible.map((c) => (
              <InboxRow
                key={c.id}
                capture={c}
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
  onClear,
  onSort,
  onSplit,
}: {
  capture: InboxCapture;
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

      {!pending && (
        <div className="mt-2.5 pl-9 sm:pl-10">
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
