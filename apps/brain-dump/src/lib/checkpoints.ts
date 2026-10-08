import "server-only";
import { cache } from "react";
import { and, count, desc, eq, isNotNull, isNull } from "drizzle-orm";
import { db, schema } from "@repo/db";

const { checkpoints } = schema;

export type Checkpoint = {
  id: string;
  label: string | null;
  transcript: string;
  nextStep: string | null;
  projectTag: string | null;
  link: string | null;
  hasAudio: boolean;
  createdAt: string;
  /** Last time the user confirmed it's still relevant (or created it). */
  updatedAt: string;
  resumedAt: string | null;
};

type Row = typeof checkpoints.$inferSelect;

const toCheckpoint = (r: Row): Checkpoint => ({
  id: r.id,
  label: r.label,
  transcript: r.transcript,
  nextStep: r.nextStep,
  projectTag: r.projectTag,
  link: r.link,
  hasAudio: Boolean(r.audioUrl),
  createdAt: r.createdAt.toISOString(),
  updatedAt: r.updatedAt.toISOString(),
  resumedAt: r.resumedAt?.toISOString() ?? null,
});

const active = (userId: string) =>
  and(eq(checkpoints.userId, userId), isNull(checkpoints.resumedAt), isNull(checkpoints.dismissedAt));

/** Places you paused and haven't come back to yet, newest first. */
export const getActiveCheckpoints = cache(async (userId: string): Promise<Checkpoint[]> => {
  const rows = await db.select().from(checkpoints).where(active(userId)).orderBy(desc(checkpoints.createdAt)).limit(100);
  return rows.map(toCheckpoint);
});

/** The last few you resumed, for a small "history" list. */
export async function getRecentlyResumed(userId: string): Promise<Checkpoint[]> {
  const rows = await db
    .select()
    .from(checkpoints)
    .where(and(eq(checkpoints.userId, userId), isNotNull(checkpoints.resumedAt)))
    .orderBy(desc(checkpoints.resumedAt))
    .limit(5);
  return rows.map(toCheckpoint);
}

export const getPausedCount = cache(async (userId: string): Promise<number> => {
  const [row] = await db.select({ n: count() }).from(checkpoints).where(active(userId));
  return row?.n ?? 0;
});
