import "server-only";
import { cache } from "react";
import { and, asc, count, desc, eq, gte, isNull, or, sql } from "drizzle-orm";
import { db, schema } from "@repo/db";
import type { Kind } from "./kinds";

const { items, captures } = schema;

export type PileItem = {
  id: string;
  kind: Kind;
  title: string;
  dueAt: string | null;
  doneAt: string | null;
  createdAt: string;
  /** Set when the item came from a voice dump; audio is served by /api/audio/[captureId]. */
  audioCaptureId: string | null;
  /** Set when the item came from a photo dump; the photo is served by /api/photo/[captureId]. */
  photoCaptureId: string | null;
};

const iso = (d: Date | null) => (d ? d.toISOString() : null);

/** Open items in one pile, plus tasks finished in the last day (so ticking one off feels good). */
export async function getPileItems(userId: string, kind: Kind): Promise<PileItem[]> {
  const dayAgo = new Date(Date.now() - 24 * 60 * 60 * 1000);
  const rows = await db
    .select({ item: items, audioUrl: captures.audioUrl, photoUrl: captures.photoUrl })
    .from(items)
    .leftJoin(captures, eq(captures.id, items.captureId))
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
  return rows.map(({ item: r, audioUrl, photoUrl }) => ({
    id: r.id,
    kind: r.kind,
    title: r.title,
    dueAt: iso(r.dueAt),
    doneAt: iso(r.doneAt),
    createdAt: r.createdAt.toISOString(),
    audioCaptureId: audioUrl ? r.captureId : null,
    photoCaptureId: photoUrl ? r.captureId : null,
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
