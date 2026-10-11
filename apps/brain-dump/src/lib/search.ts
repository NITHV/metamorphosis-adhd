import "server-only";
import { cache } from "react";
import { and, count, desc, eq, ilike, isNotNull, isNull, ne, or } from "drizzle-orm";
import { db, schema } from "@repo/db";
import type { Kind } from "./kinds";

const { captures, items, checkpoints } = schema;

export const MAX_QUERY_LENGTH = 100;
const LIMIT = 30;

export type SearchResults = {
  inbox: { id: string; text: string; createdAt: string }[];
  items: { id: string; kind: Kind; title: string; done: boolean; archived: boolean; createdAt: string }[];
  paused: { id: string; text: string; createdAt: string }[];
};

/** `ilike` treats % and _ as wildcards; escape them so "100%" finds "100%". */
function containsPattern(q: string) {
  return `%${q.replace(/[\\%_]/g, (c) => `\\${c}`)}%`;
}

/**
 * Plain "contains" search over everything a person wrote. At one person's scale (thousands of rows)
 * a scan is a few milliseconds, so there's no search index to build, pay for or keep in sync.
 * If it ever gets slow, Postgres trigram indexes (pg_trgm) make this same query fast.
 */
export async function searchEverything(userId: string, query: string): Promise<SearchResults> {
  const pattern = containsPattern(query.trim().slice(0, MAX_QUERY_LENGTH));
  const [inboxRows, itemRows, pausedRows] = await Promise.all([
    db
      .select({ id: captures.id, text: captures.rawText, createdAt: captures.createdAt })
      .from(captures)
      .where(and(eq(captures.userId, userId), eq(captures.status, "inbox"), ilike(captures.rawText, pattern)))
      .orderBy(desc(captures.createdAt))
      .limit(LIMIT),
    db
      .select()
      .from(items)
      .where(and(eq(items.userId, userId), ilike(items.title, pattern)))
      .orderBy(desc(items.createdAt))
      .limit(LIMIT),
    db
      .select({
        id: checkpoints.id,
        transcript: checkpoints.transcript,
        nextStep: checkpoints.nextStep,
        label: checkpoints.label,
        createdAt: checkpoints.createdAt,
      })
      .from(checkpoints)
      .where(
        and(
          eq(checkpoints.userId, userId),
          isNull(checkpoints.dismissedAt),
          or(
            ilike(checkpoints.transcript, pattern),
            ilike(checkpoints.nextStep, pattern),
            ilike(checkpoints.label, pattern),
            ilike(checkpoints.projectTag, pattern),
          ),
        ),
      )
      .orderBy(desc(checkpoints.createdAt))
      .limit(LIMIT),
  ]);

  return {
    inbox: inboxRows.map((r) => ({ id: r.id, text: r.text, createdAt: r.createdAt.toISOString() })),
    items: itemRows.map((r) => ({
      id: r.id,
      kind: r.kind,
      title: r.title,
      done: r.doneAt !== null,
      archived: r.archivedAt !== null,
      createdAt: r.createdAt.toISOString(),
    })),
    paused: pausedRows.map((r) => ({
      id: r.id,
      text: [r.label, r.nextStep && `Next: ${r.nextStep}`, r.transcript].filter(Boolean).join(" · "),
      createdAt: r.createdAt.toISOString(),
    })),
  };
}

export type DataSummary = { dumps: number; items: number; paused: number; photos: number; voiceNotes: number };

/** What "Delete everything" would remove, for the Settings page. */
export const getDataSummary = cache(async (userId: string): Promise<DataSummary> => {
  const [[c], [p], [v], [i], [k], [kv]] = await Promise.all([
    db.select({ n: count() }).from(captures).where(eq(captures.userId, userId)),
    db.select({ n: count() }).from(captures).where(and(eq(captures.userId, userId), isNotNull(captures.photoUrl))),
    db.select({ n: count() }).from(captures).where(and(eq(captures.userId, userId), isNotNull(captures.audioUrl))),
    db.select({ n: count() }).from(items).where(eq(items.userId, userId)),
    db.select({ n: count() }).from(checkpoints).where(eq(checkpoints.userId, userId)),
    db
      .select({ n: count() })
      .from(checkpoints)
      .where(and(eq(checkpoints.userId, userId), isNotNull(checkpoints.audioUrl), ne(checkpoints.audioUrl, ""))),
  ]);
  return { dumps: c.n, items: i.n, paused: k.n, photos: p.n, voiceNotes: v.n + kv.n };
});
