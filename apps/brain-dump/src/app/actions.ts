"use server";

import { and, eq, inArray } from "drizzle-orm";
import { headers } from "next/headers";
import { refresh } from "next/cache";
import { auth } from "@repo/auth/server";
import { db, schema } from "@repo/db";
import { isKind, type Kind } from "@/lib/kinds";
import { MAX_CAPTURE_LENGTH } from "@/lib/limits";
import { guessKind, splitDump } from "@/lib/smart-guess";

const { captures, items } = schema;

// Server Actions are public endpoints, so each one re-checks the session itself.
async function requireUserId(): Promise<string> {
  const session = await auth.api.getSession({ headers: await headers() });
  if (!session) throw new Error("Not signed in");
  return session.user.id;
}

function parseDue(value: string | null | undefined): Date | null {
  if (!value) return null;
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? null : d;
}

// ---------------------------------------------------------------------------
// Captures (inbox)
// ---------------------------------------------------------------------------

export async function createCapture(text: string): Promise<{ id: string }> {
  const userId = await requireUserId();
  const rawText = typeof text === "string" ? text.trim() : "";
  if (!rawText) throw new Error("Nothing to save");
  if (rawText.length > MAX_CAPTURE_LENGTH) throw new Error("That dump is too long");

  const suggestedKind = guessKind(rawText);
  const [row] = await db
    .insert(captures)
    .values({ userId, rawText, source: "text", suggestedKind, suggestedBy: suggestedKind ? "rules" : null })
    .returning({ id: captures.id });
  refresh();
  return row;
}

async function setCaptureStatus(id: string, status: "inbox" | "archived") {
  const userId = await requireUserId();
  await db
    .update(captures)
    .set({ status })
    .where(and(eq(captures.id, id), eq(captures.userId, userId)));
  refresh();
}

export async function archiveCapture(id: string) {
  await setCaptureStatus(id, "archived");
}

export async function restoreCapture(id: string) {
  await setCaptureStatus(id, "inbox");
}

/** Files an inbox capture into a pile. `dueAt` comes from the browser so dates use the user's timezone. */
export async function sortCapture(captureId: string, kind: Kind, dueAt?: string | null) {
  const userId = await requireUserId();
  if (!isKind(kind)) throw new Error("Unknown pile");
  const [capture] = await db
    .select({ rawText: captures.rawText })
    .from(captures)
    .where(and(eq(captures.id, captureId), eq(captures.userId, userId), eq(captures.status, "inbox")));
  if (!capture) throw new Error("That dump isn't in your inbox anymore");

  await db.batch([
    db.insert(items).values({
      userId,
      captureId,
      kind,
      title: capture.rawText,
      dueAt: kind === "reminder" || kind === "task" ? parseDue(dueAt) : null,
    }),
    db.update(captures).set({ status: "sorted" }).where(eq(captures.id, captureId)),
  ]);
  refresh();
}

/** Undo for sortCapture: removes the filed item and puts the dump back in the inbox. */
export async function unsortCapture(captureId: string) {
  const userId = await requireUserId();
  await db.batch([
    db.delete(items).where(and(eq(items.captureId, captureId), eq(items.userId, userId))),
    db
      .update(captures)
      .set({ status: "inbox" })
      .where(and(eq(captures.id, captureId), eq(captures.userId, userId))),
  ]);
  refresh();
}

/** Turns one long dump into separate inbox dumps (one per line or sentence). */
export async function splitCapture(captureId: string) {
  const userId = await requireUserId();
  const [capture] = await db
    .select({ rawText: captures.rawText, createdAt: captures.createdAt })
    .from(captures)
    .where(and(eq(captures.id, captureId), eq(captures.userId, userId), eq(captures.status, "inbox")));
  if (!capture) throw new Error("That dump isn't in your inbox anymore");

  const parts = splitDump(capture.rawText);
  if (parts.length < 2) throw new Error("Nothing to split");

  // Inbox is newest-first, so give earlier parts slightly later timestamps to keep reading order.
  const base = capture.createdAt.getTime();
  await db.batch([
    db.insert(captures).values(
      parts.map((rawText, i) => {
        const suggestedKind = guessKind(rawText);
        return {
          userId,
          rawText,
          source: "text" as const,
          suggestedKind,
          suggestedBy: suggestedKind ? ("rules" as const) : null,
          createdAt: new Date(base + (parts.length - i)),
        };
      }),
    ),
    db.update(captures).set({ status: "archived" }).where(eq(captures.id, captureId)),
  ]);
  refresh();
}

// ---------------------------------------------------------------------------
// Items (piles)
// ---------------------------------------------------------------------------

async function updateItem(id: string, values: Partial<typeof items.$inferInsert>) {
  const userId = await requireUserId();
  await db
    .update(items)
    .set(values)
    .where(and(eq(items.id, id), eq(items.userId, userId)));
  refresh();
}

export async function setItemDone(id: string, done: boolean) {
  await updateItem(id, { doneAt: done ? new Date() : null });
}

export async function setItemArchived(id: string, archived: boolean) {
  await updateItem(id, { archivedAt: archived ? new Date() : null });
}

export async function moveItem(id: string, kind: Kind) {
  if (!isKind(kind)) throw new Error("Unknown pile");
  // Worries and ideas never carry a due date.
  await updateItem(id, kind === "worry" || kind === "idea" ? { kind, dueAt: null } : { kind });
}

export async function setItemDue(id: string, dueAt: string | null) {
  await updateItem(id, { dueAt: parseDue(dueAt) });
}

/** Clears finished tasks off the list in one go. */
export async function archiveDone(ids: string[]) {
  const userId = await requireUserId();
  if (!Array.isArray(ids) || ids.length === 0) return;
  await db
    .update(items)
    .set({ archivedAt: new Date() })
    .where(and(eq(items.userId, userId), inArray(items.id, ids.slice(0, 500))));
  refresh();
}
