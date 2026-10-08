import Link from "next/link";
import { Suspense } from "react";
import { getCurrentUser } from "@/lib/auth";
import { getPileItems } from "@/lib/items";
import { KIND_META, type Kind } from "@/lib/kinds";
import { PileList } from "./pile-list";

const BLURB: Record<Kind, string> = {
  task: "Things to do. Tick them off; they stay here until tomorrow so you can see your wins.",
  idea: "Sparks worth keeping. No pressure to act on any of them.",
  reminder: "Anything with a date. Soonest first.",
  worry: "Parked here so you don't have to carry them. They never go overdue.",
};

export function PilePage({ kind }: { kind: Kind }) {
  const meta = KIND_META[kind];
  return (
    <div>
      <Link href="/piles" className="-mt-2 mb-2 inline-block py-2 text-sm font-medium text-muted md:hidden">
        ← Piles
      </Link>
      <h1 className="flex items-center gap-2.5 text-3xl font-extrabold tracking-tight">
        <span className="h-3.5 w-3.5 rounded-full" style={{ background: meta.color }} aria-hidden />
        {meta.pile}
      </h1>
      <p className="mt-1 mb-6 text-muted">{BLURB[kind]}</p>
      <Suspense fallback={<div className="chunky h-32 animate-pulse rounded-2xl bg-card" />}>
        <Items kind={kind} />
      </Suspense>
    </div>
  );
}

async function Items({ kind }: { kind: Kind }) {
  const user = await getCurrentUser();
  const items = await getPileItems(user.id, kind);
  return <PileList kind={kind} items={items} />;
}
