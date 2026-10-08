import "server-only";
import { and, desc, eq } from "drizzle-orm";
import { db, schema } from "@repo/db";
import type { Kind } from "./kinds";

const { captures } = schema;

export type InboxCapture = {
  id: string;
  rawText: string;
  suggestedKind: Kind | null;
  createdAt: string; // ISO, so it can cross into Client Components
};

/** Unsorted dumps for one user, newest first. Callers must pass the signed-in user's id. */
export async function getInboxCaptures(userId: string): Promise<InboxCapture[]> {
  const rows = await db
    .select({
      id: captures.id,
      rawText: captures.rawText,
      suggestedKind: captures.suggestedKind,
      createdAt: captures.createdAt,
    })
    .from(captures)
    .where(and(eq(captures.userId, userId), eq(captures.status, "inbox")))
    .orderBy(desc(captures.createdAt))
    .limit(200);
  return rows.map((r) => ({ ...r, createdAt: r.createdAt.toISOString() }));
}
