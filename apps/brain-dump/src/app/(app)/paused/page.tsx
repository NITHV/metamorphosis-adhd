import type { Metadata } from "next";
import { Suspense } from "react";
import { getCurrentUser } from "@/lib/auth";
import { getActiveCheckpoints, getRecentlyResumed } from "@/lib/checkpoints";
import { CheckpointList } from "@/components/pause/checkpoint-list";
import { PauseNowButton } from "@/components/pause/pause-now-button";
import { RelativeText } from "@/components/pause/history";

export const metadata: Metadata = { title: "Paused · Brain Dump" };

export default function PausedPage() {
  return (
    <div>
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-3xl font-extrabold tracking-tight">Paused</h1>
          <p className="mt-1 text-muted">Where did I leave off? Your bookmarks for getting back in.</p>
        </div>
        <PauseNowButton />
      </div>
      <div className="mt-6">
        <Suspense fallback={<div className="chunky h-40 animate-pulse rounded-2xl bg-card" />}>
          <Active />
        </Suspense>
      </div>
      <Suspense fallback={null}>
        <History />
      </Suspense>
    </div>
  );
}

async function Active() {
  const user = await getCurrentUser();
  return <CheckpointList checkpoints={await getActiveCheckpoints(user.id)} />;
}

async function History() {
  const user = await getCurrentUser();
  const resumed = await getRecentlyResumed(user.id);
  if (resumed.length === 0) return null;
  return (
    <section className="mt-10">
      <h2 className="mb-2 text-sm font-semibold tracking-wide text-muted">RECENTLY RESUMED</h2>
      <ul className="divide-y-2 divide-hairline overflow-hidden rounded-2xl border-2 border-hairline">
        {resumed.map((c) => (
          <li key={c.id} className="flex items-center justify-between gap-3 px-4 py-2.5 text-sm text-muted">
            <span className="truncate">{c.nextStep || c.transcript || "Voice note"}</span>
            {c.resumedAt && (
              <span className="shrink-0 text-xs">
                <RelativeText iso={c.resumedAt} />
              </span>
            )}
          </li>
        ))}
      </ul>
    </section>
  );
}
