"use client";

import Link from "next/link";
import { startTransition, useOptimistic, useState } from "react";
import {
  dismissCheckpoint,
  keepCheckpoint,
  resumeCheckpoint,
  undismissCheckpoint,
  unresumeCheckpoint,
} from "@/app/actions";
import { formatRelative } from "@/components/time";
import { ToastBar, useToast } from "@/components/toast";
import { useIsClient } from "@/components/use-is-client";
import { PlayButton } from "@/components/voice/play-button";
import type { Checkpoint } from "@/lib/checkpoints";
import { openPause } from "./pause-launcher";

const WEEK = 7 * 24 * 60 * 60 * 1000;

type Action = { type: "remove"; id: string } | { type: "touch"; id: string; at: string };

function useCheckpointActions(initial: Checkpoint[]) {
  const [items, apply] = useOptimistic(initial, (state, a: Action) =>
    a.type === "remove" ? state.filter((c) => c.id !== a.id) : state.map((c) => (c.id === a.id ? { ...c, updatedAt: a.at } : c)),
  );
  const { toast, showToast, hideToast } = useToast();

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

  return {
    items,
    toast,
    hideToast,
    resume: (c: Checkpoint) =>
      run({ type: "remove", id: c.id }, () => resumeCheckpoint(c.id), () =>
        showToast({ message: "Welcome back 👋", undo: () => startTransition(() => unresumeCheckpoint(c.id)) }),
      ),
    dismiss: (c: Checkpoint) =>
      run({ type: "remove", id: c.id }, () => dismissCheckpoint(c.id), () =>
        showToast({ message: "Let go 🍃", undo: () => startTransition(() => undismissCheckpoint(c.id)) }),
      ),
    keep: (c: Checkpoint) =>
      run({ type: "touch", id: c.id, at: new Date().toISOString() }, () => keepCheckpoint(c.id), () =>
        showToast({ message: "Kept. We'll ask again in a week." }),
      ),
  };
}

export function CheckpointList({ checkpoints }: { checkpoints: Checkpoint[] }) {
  const { items, toast, hideToast, resume, dismiss, keep } = useCheckpointActions(checkpoints);
  const [now] = useState(() => Date.now());

  return (
    <div>
      {items.length === 0 ? (
        <div className="rounded-2xl border-2 border-dashed border-hairline px-4 py-10 text-center text-muted">
          <p>Nothing paused. When you get pulled away from something, pause it here first.</p>
          <button
            type="button"
            onClick={openPause}
            className="mt-3 py-2 font-semibold"
            style={{ color: "var(--pill-blue)" }}
          >
            ⏸ Pause something now
          </button>
        </div>
      ) : (
        <ul className="flex flex-col gap-4">
          {items.map((c) => (
            <li key={c.id}>
              <CheckpointCard
                checkpoint={c}
                stale={now - new Date(c.updatedAt).getTime() > WEEK}
                onResume={() => resume(c)}
                onDismiss={() => dismiss(c)}
                onKeep={() => keep(c)}
              />
            </li>
          ))}
        </ul>
      )}
      <ToastBar toast={toast} onUndo={hideToast} />
    </div>
  );
}

function CheckpointCard({
  checkpoint: c,
  stale,
  compact,
  onResume,
  onDismiss,
  onKeep,
}: {
  checkpoint: Checkpoint;
  stale?: boolean;
  compact?: boolean;
  onResume: () => void;
  onDismiss?: () => void;
  onKeep?: () => void;
}) {
  const isClient = useIsClient();
  const body = c.transcript || c.label || (c.hasAudio ? "🎙️ Voice note" : "");

  return (
    <article className="chunky rounded-2xl bg-card p-4">
      <div className="flex flex-wrap items-center gap-2 text-xs">
        {c.projectTag && (
          <span className="rounded-full px-2.5 py-0.5 font-semibold" style={{ background: "var(--pill-purple-soft)", color: "var(--pill-purple)" }}>
            {c.projectTag}
          </span>
        )}
        <span className="text-muted" suppressHydrationWarning>
          Paused {isClient ? formatRelative(new Date(c.createdAt)) : ""}
        </span>
      </div>

      {body && <p className={`mt-2 whitespace-pre-wrap break-words leading-snug ${compact ? "line-clamp-2" : ""}`}>{body}</p>}

      {c.nextStep && (
        <p className="mt-3 rounded-xl px-3 py-2 font-semibold" style={{ background: "var(--brand-soft)" }}>
          <span className="mr-1.5 text-xs tracking-wide text-muted">NEXT →</span>
          {c.nextStep}
        </p>
      )}

      {(c.hasAudio || c.link) && (
        <div className="mt-3 flex flex-wrap items-center gap-2">
          {c.hasAudio && <PlayButton audioId={c.id} />}
          {c.link && (
            <a
              href={c.link}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex h-8 max-w-full items-center gap-1 truncate rounded-full px-3 text-xs font-semibold"
              style={{ background: "var(--pill-blue-soft)", color: "var(--pill-blue)" }}
            >
              ↗ {new URL(c.link).hostname.replace(/^www\./, "")}
            </a>
          )}
        </div>
      )}

      {stale && onKeep && onDismiss && (
        <div className="mt-3 rounded-xl border-2 border-dashed border-hairline p-3 text-sm">
          <p className="font-medium">Paused over a week ago. Still relevant?</p>
          <div className="mt-2 flex gap-2">
            <button type="button" onClick={onKeep} className="h-10 flex-1 rounded-xl border-2 border-hairline font-semibold sm:h-9">
              Keep it
            </button>
            <button type="button" onClick={onDismiss} className="h-10 flex-1 rounded-xl border-2 border-hairline font-semibold text-muted sm:h-9">
              Let it go 🍃
            </button>
          </div>
        </div>
      )}

      <div className="mt-4 flex items-center gap-3">
        <button
          type="button"
          onClick={onResume}
          className="chunky-sm press h-11 flex-1 rounded-xl bg-brand font-bold text-brand-foreground sm:flex-none sm:px-6"
        >
          I&apos;m back on it
        </button>
        {!stale && onDismiss && (
          <button type="button" onClick={onDismiss} className="h-11 px-2 text-sm font-medium text-muted hover:text-foreground">
            Let it go
          </button>
        )}
      </div>
    </article>
  );
}

/** Home page card: the most recent pause, so coming back starts with your bookmark. */
export function ResumeCard({ checkpoints }: { checkpoints: Checkpoint[] }) {
  const { items, toast, hideToast, resume } = useCheckpointActions(checkpoints);
  const latest = items[0];
  if (!latest) return <ToastBar toast={toast} onUndo={hideToast} />;
  return (
    <section aria-labelledby="resume-heading">
      <div className="mb-3 flex items-center justify-between">
        <h2 id="resume-heading" className="text-lg font-bold">
          Where you left off
        </h2>
        {items.length > 1 && (
          <Link href="/paused" className="py-2 text-sm font-medium" style={{ color: "var(--pill-blue)" }}>
            All paused ({items.length})
          </Link>
        )}
      </div>
      <CheckpointCard checkpoint={latest} compact onResume={() => resume(latest)} />
      <ToastBar toast={toast} onUndo={hideToast} />
    </section>
  );
}
