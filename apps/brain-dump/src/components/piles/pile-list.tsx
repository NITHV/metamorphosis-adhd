"use client";

import Link from "next/link";
import { startTransition, useOptimistic, useState } from "react";
import { archiveDone, moveItem, setItemArchived, setItemDone, setItemDue } from "@/app/actions";
import { formatDue } from "@/components/time";
import { ToastBar, useToast } from "@/components/toast";
import { useIsClient } from "@/components/use-is-client";
import type { PileItem } from "@/lib/items";
import { KIND_META, KINDS, type Kind } from "@/lib/kinds";

type Action = { type: "patch"; id: string; patch: Partial<PileItem> } | { type: "remove"; ids: string[] };

const EMPTY: Record<Kind, string> = {
  task: "No tasks. File some from your Inbox, or enjoy the quiet.",
  idea: "No ideas parked yet. They'll land here when you file them.",
  reminder: "Nothing to remember right now.",
  worry: "No worries parked. Nice.",
};

export function PileList({ kind, items: initial }: { kind: Kind; items: PileItem[] }) {
  const [items, apply] = useOptimistic(initial, (state, action: Action) =>
    action.type === "patch"
      ? state.map((i) => (i.id === action.id ? { ...i, ...action.patch } : i))
      : state.filter((i) => !action.ids.includes(i.id)),
  );
  const { toast, showToast, hideToast } = useToast();
  const [openId, setOpenId] = useState<string | null>(null);
  // Read the clock once per mount (for overdue styling), not on every render.
  const [now] = useState(() => Date.now());
  const checkable = kind === "task" || kind === "reminder";

  function run(action: Action, server: () => Promise<unknown>, done?: () => void) {
    startTransition(async () => {
      apply(action);
      try {
        await server();
        done?.();
      } catch {
        showToast({ message: "Something went wrong. Try again." });
      }
    });
  }

  const toggleDone = (item: PileItem) =>
    run({ type: "patch", id: item.id, patch: { doneAt: item.doneAt ? null : new Date().toISOString() } }, () =>
      setItemDone(item.id, !item.doneAt),
    );

  const archive = (item: PileItem) =>
    run(
      { type: "remove", ids: [item.id] },
      () => setItemArchived(item.id, true),
      () =>
        showToast({
          message: kind === "worry" ? "Let go 🍃" : "Archived",
          undo: () => startTransition(() => setItemArchived(item.id, false)),
        }),
    );

  const move = (item: PileItem, to: Kind) =>
    run(
      { type: "remove", ids: [item.id] },
      () => moveItem(item.id, to),
      () =>
        showToast({
          message: `Moved to ${KIND_META[to].pile}`,
          undo: () => startTransition(() => moveItem(item.id, kind)),
        }),
    );

  const setDue = (item: PileItem, value: string | null) =>
    run({ type: "patch", id: item.id, patch: { dueAt: value } }, () => setItemDue(item.id, value));

  const open = items.filter((i) => !i.doneAt);
  const done = items.filter((i) => i.doneAt);

  return (
    <div>
      {open.length === 0 && done.length === 0 ? (
        <div className="rounded-2xl border-2 border-dashed border-hairline px-4 py-10 text-center text-muted">
          <p>{EMPTY[kind]}</p>
          <Link href="/inbox" className="mt-3 inline-block py-2 font-semibold" style={{ color: "var(--pill-blue)" }}>
            Go to Inbox
          </Link>
        </div>
      ) : (
        open.length > 0 && (
          <ul className="chunky divide-y-2 divide-hairline overflow-hidden rounded-2xl bg-card">
            {open.map((item) => (
              <Row
                key={item.id}
                item={item}
                kind={kind}
                now={now}
                checkable={checkable}
                expanded={openId === item.id}
                onToggleMenu={() => setOpenId(openId === item.id ? null : item.id)}
                onToggleDone={() => toggleDone(item)}
                onArchive={() => archive(item)}
                onMove={(to) => move(item, to)}
                onSetDue={(v) => setDue(item, v)}
              />
            ))}
          </ul>
        )
      )}

      {done.length > 0 && (
        <section className="mt-8">
          <div className="mb-2 flex items-center justify-between">
            <h2 className="text-sm font-semibold tracking-wide text-muted">DONE TODAY · {done.length}</h2>
            <button
              type="button"
              onClick={() =>
                run({ type: "remove", ids: done.map((d) => d.id) }, () => archiveDone(done.map((d) => d.id)))
              }
              className="py-2 text-sm font-medium"
              style={{ color: "var(--pill-blue)" }}
            >
              Clear done
            </button>
          </div>
          <ul className="divide-y-2 divide-hairline overflow-hidden rounded-2xl border-2 border-hairline">
            {done.map((item) => (
              <Row
                key={item.id}
                item={item}
                kind={kind}
                now={now}
                checkable
                expanded={false}
                onToggleDone={() => toggleDone(item)}
              />
            ))}
          </ul>
        </section>
      )}

      <ToastBar toast={toast} onUndo={hideToast} />
    </div>
  );
}

function Row({
  item,
  kind,
  now,
  checkable,
  expanded,
  onToggleMenu,
  onToggleDone,
  onArchive,
  onMove,
  onSetDue,
}: {
  item: PileItem;
  kind: Kind;
  now: number;
  checkable: boolean;
  expanded: boolean;
  onToggleMenu?: () => void;
  onToggleDone: () => void;
  onArchive?: () => void;
  onMove?: (to: Kind) => void;
  onSetDue?: (value: string | null) => void;
}) {
  const isClient = useIsClient();
  const done = Boolean(item.doneAt);
  const meta = KIND_META[kind];
  const due = item.dueAt ? new Date(item.dueAt) : null;
  const overdue = isClient && due && !done && due.getTime() < now;

  return (
    <li className={done ? "bg-surface" : ""}>
      <div className="flex items-start gap-2 px-3 py-2.5 sm:px-4">
        {checkable && (
          <button
            type="button"
            role="checkbox"
            aria-checked={done}
            aria-label={done ? `Mark "${item.title.slice(0, 40)}" not done` : `Mark "${item.title.slice(0, 40)}" done`}
            onClick={onToggleDone}
            className="-m-1 flex shrink-0 items-center justify-center p-2"
          >
            <span
              className="flex h-6 w-6 items-center justify-center rounded-full border-2 transition"
              style={{ borderColor: done ? meta.color : "var(--foreground)", background: done ? meta.color : undefined }}
            >
              {done && (
                <svg viewBox="0 0 12 12" className="h-3.5 w-3.5 text-white" aria-hidden>
                  <path d="m2.5 6.2 2.2 2.2 4.8-5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
                </svg>
              )}
            </span>
          </button>
        )}
        {!checkable && (
          <span className="mt-2 ml-1 h-2.5 w-2.5 shrink-0 rounded-full" style={{ background: meta.color }} aria-hidden />
        )}

        <div className="min-w-0 flex-1 py-1">
          <p className={`whitespace-pre-wrap break-words leading-snug ${done ? "text-muted line-through" : ""}`}>
            {item.title}
          </p>
          {due && isClient && (
            <p
              className="mt-1 inline-block rounded-full px-2.5 py-0.5 text-xs font-semibold"
              style={
                overdue
                  ? { background: "var(--pill-red-soft)", color: "var(--pill-red)" }
                  : { background: meta.soft, color: meta.color }
              }
            >
              📅 {formatDue(due)}
            </p>
          )}
        </div>

        {onToggleMenu && (
          <button
            type="button"
            onClick={onToggleMenu}
            aria-expanded={expanded}
            aria-label="More actions"
            className="-mr-1 flex h-10 w-10 shrink-0 items-center justify-center rounded-lg text-xl leading-none text-muted transition hover:bg-hairline/60"
          >
            ⋯
          </button>
        )}
      </div>

      {expanded && onMove && onArchive && onSetDue && (
        <div className="flex flex-col gap-3 border-t-2 border-dashed border-hairline bg-surface px-3 py-3 sm:px-4">
          <div>
            <p className="mb-1.5 text-xs font-semibold tracking-wide text-muted">MOVE TO</p>
            <div className="grid grid-cols-3 gap-1.5 sm:flex sm:gap-2">
              {KINDS.filter((k) => k !== kind).map((k) => (
                <button
                  key={k}
                  type="button"
                  onClick={() => onMove(k)}
                  className="press h-10 rounded-full border-2 px-3 text-[13px] font-semibold sm:h-8"
                  style={{ borderColor: KIND_META[k].color, color: KIND_META[k].color }}
                >
                  {KIND_META[k].label}
                </button>
              ))}
            </div>
          </div>
          {(kind === "task" || kind === "reminder") && (
            <label className="flex flex-col gap-1.5">
              <span className="text-xs font-semibold tracking-wide text-muted">DATE</span>
              <span className="flex gap-2">
                <input
                  type="datetime-local"
                  defaultValue={isClient && due ? toLocalInput(due) : ""}
                  onChange={(e) => onSetDue(e.target.value ? new Date(e.target.value).toISOString() : null)}
                  className="h-10 min-w-0 flex-1 rounded-xl border-2 border-hairline bg-card px-3 text-base outline-none focus:border-ink"
                />
                {due && (
                  <button
                    type="button"
                    onClick={() => onSetDue(null)}
                    className="h-10 rounded-xl border-2 border-hairline px-3 text-sm font-medium text-muted"
                  >
                    Clear
                  </button>
                )}
              </span>
            </label>
          )}
          <button
            type="button"
            onClick={onArchive}
            className="h-10 self-start rounded-xl border-2 border-hairline px-4 text-sm font-semibold text-muted hover:text-foreground"
          >
            {kind === "worry" ? "Let it go 🍃" : "Archive"}
          </button>
        </div>
      )}
    </li>
  );
}

/** Formats a Date for <input type="datetime-local"> in the viewer's timezone. */
function toLocalInput(d: Date) {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
