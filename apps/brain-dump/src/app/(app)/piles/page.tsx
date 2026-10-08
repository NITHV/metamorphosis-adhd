import type { Metadata } from "next";
import Link from "next/link";
import { Suspense } from "react";
import { getCurrentUser } from "@/lib/auth";
import { getPileCounts } from "@/lib/items";
import { KIND_META, KINDS, type Kind } from "@/lib/kinds";

export const metadata: Metadata = { title: "Piles · Brain Dump" };

const HINT: Record<Kind, string> = {
  task: "Things to do",
  idea: "Sparks to keep",
  reminder: "Has a date",
  worry: "Parked, not overdue",
};

export default function PilesPage() {
  return (
    <div>
      <h1 className="text-3xl font-extrabold tracking-tight">Piles</h1>
      <p className="mt-1 mb-6 text-muted">Where your sorted dumps live.</p>
      <Suspense fallback={<Tiles counts={null} />}>
        <TilesWithCounts />
      </Suspense>
    </div>
  );
}

async function TilesWithCounts() {
  const user = await getCurrentUser();
  return <Tiles counts={await getPileCounts(user.id)} />;
}

function Tiles({ counts }: { counts: Record<Kind, number> | null }) {
  return (
    <div className="grid grid-cols-2 gap-3 sm:gap-4">
      {KINDS.map((k) => {
        const meta = KIND_META[k];
        return (
          <Link
            key={k}
            href={meta.href}
            className="chunky press flex min-h-32 flex-col justify-between rounded-2xl p-4"
            style={{ background: meta.soft }}
          >
            <span className="flex items-center gap-2 text-lg font-bold" style={{ color: meta.color }}>
              <span className="h-3 w-3 rounded-full" style={{ background: meta.color }} aria-hidden />
              {meta.pile}
            </span>
            <span>
              <span className="block text-3xl font-extrabold tabular-nums">{counts ? counts[k] : "–"}</span>
              <span className="text-sm text-muted">{HINT[k]}</span>
            </span>
          </Link>
        );
      })}
    </div>
  );
}
