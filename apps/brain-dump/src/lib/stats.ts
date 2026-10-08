import "server-only";
import { cache } from "react";
import { and, count, eq, gte, sql } from "drizzle-orm";
import { db, schema } from "@repo/db";

const { captures } = schema;

export type HomeStats = {
  dumpsThisWeek: number;
  sortedCount: number;
  totalNotes: number;
  /** Capture times from the last ~13 weeks, bucketed into days in the browser (its timezone). */
  activity: string[];
};

const DAY = 24 * 60 * 60 * 1000;

export const getHomeStats = cache(async (userId: string): Promise<HomeStats> => {
  const now = Date.now();
  const weekAgo = new Date(now - 7 * DAY);
  const since = new Date(now - 98 * DAY); // 14 weeks, so the oldest grid column is always full

  const [totals, recent] = await Promise.all([
    db
      .select({
        total: count(),
        thisWeek: sql<number>`count(*) filter (where ${captures.createdAt} >= ${weekAgo})`.mapWith(Number),
        sorted: sql<number>`count(*) filter (where ${captures.status} = 'sorted')`.mapWith(Number),
      })
      .from(captures)
      .where(eq(captures.userId, userId)),
    db
      .select({ createdAt: captures.createdAt })
      .from(captures)
      .where(and(eq(captures.userId, userId), gte(captures.createdAt, since)))
      .limit(5000),
  ]);

  const t = totals[0];
  return {
    dumpsThisWeek: t?.thisWeek ?? 0,
    sortedCount: t?.sorted ?? 0,
    totalNotes: t?.total ?? 0,
    activity: recent.map((r) => r.createdAt.toISOString()),
  };
});

export async function getInboxCount(userId: string): Promise<number> {
  const [row] = await db
    .select({ n: count() })
    .from(captures)
    .where(and(eq(captures.userId, userId), eq(captures.status, "inbox")));
  return row?.n ?? 0;
}
