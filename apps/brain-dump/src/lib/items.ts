import "server-only";
import { cache } from "react";
import { and, asc, count, desc, eq, gte, isNull, or, sql } from "drizzle-orm";
import { db, schema } from "@repo/db";
import type { Kind } from "./kinds";

const { items } = schema;

export type PileItem = {
  id: string;
  kind: Kind;
  title: string;
  dueAt: string | null;
  doneAt: string | null;
  createdAt: string;
};

const iso = (d: Date | null) => (d ? d.toISOString() : null);

/** Open items in one pile, plus tasks finished in the last day (so ticking one off feels good). */
export async function getPileItems(userId: string, kind: Kind): Promise<PileItem[]> {
  const dayAgo = new Date(Date.now() - 24 * 60 * 60 * 1000);
  const rows = await db
    .select()
    .from(items)
    .where(
      and(
        eq(items.userId, userId),
        eq(items.kind, kind),
        isNull(items.archivedAt),
        or(isNull(items.doneAt), gte(items.doneAt, dayAgo)),
      ),
    )
    .orderBy(kind === "reminder" ? sql`${items.dueAt} asc nulls last` : desc(items.createdAt), asc(items.id))
    .limit(500);
  return rows.map((r) => ({
    id: r.id,
    kind: r.kind,
    title: r.title,
    dueAt: iso(r.dueAt),
    doneAt: iso(r.doneAt),
    createdAt: r.createdAt.toISOString(),
  }));
}

/** Open (not done, not archived) items per pile, for the sidebar. */
export const getPileCounts = cache(async (userId: string): Promise<Record<Kind, number>> => {
  const rows = await db
    .select({ kind: items.kind, n: count() })
    .from(items)
    .where(and(eq(items.userId, userId), isNull(items.archivedAt), isNull(items.doneAt)))
    .groupBy(items.kind);
  const counts: Record<Kind, number> = { task: 0, idea: 0, reminder: 0, worry: 0 };
  for (const r of rows) counts[r.kind] = r.n;
  return counts;
});
