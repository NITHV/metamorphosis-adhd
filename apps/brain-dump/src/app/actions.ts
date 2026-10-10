"use server";

import { and, eq, inArray } from "drizzle-orm";
import { headers } from "next/headers";
import { refresh } from "next/cache";
import { auth } from "@repo/auth/server";
import { db, schema } from "@repo/db";
import { isKind, type Kind } from "@/lib/kinds";
import { MAX_CAPTURE_LENGTH } from "@/lib/limits";
import { guessKind, splitDump } from "@/lib/smart-guess";

const { captures, items, checkpoints } = schema;

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

/** Accepts only files in this user's own folder (voice/ or photo/) of our Blob store. */
function checkBlobUrl(userId: string, blobUrl: string, folder: "voice" | "photo"): string {
  let url: URL;
  try {
    url = new URL(blobUrl);
  } catch {
    throw new Error(`Bad ${folder} link`);
  }
  if (!url.hostname.endsWith(".blob.vercel-storage.com") || !url.pathname.startsWith(`/${folder}/${userId}/`)) {
    throw new Error(`Bad ${folder} link`);
  }
  return url.toString();
}

const checkAudioUrl = (userId: string, audioUrl: string) => checkBlobUrl(userId, audioUrl, "voice");

const clip = (value: unknown, max: number) => (typeof value === "string" ? value.trim().slice(0, max) : "");

// ---------------------------------------------------------------------------
// Captures (inbox)
// ---------------------------------------------------------------------------

/**
 * Dumps saved offline arrive later with the id and time they were made on the phone.
 * Reusing the phone's id makes delivery idempotent: a retry can never create a duplicate.
 */
export type CaptureOrigin = { id?: string; createdAt?: string };

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const MAX_OFFLINE_AGE = 60 * 24 * 60 * 60 * 1000; // 60 days

function originValues(origin?: CaptureOrigin): { id?: string; createdAt?: Date } {
  const values: { id?: string; createdAt?: Date } = {};
  if (origin?.id && UUID.test(origin.id)) values.id = origin.id.toLowerCase();
  if (origin?.createdAt) {
    const t = new Date(origin.createdAt).getTime();
    // Trust the phone's clock only within sane bounds.
    if (Number.isFinite(t) && t <= Date.now() + 60_000 && t >= Date.now() - MAX_OFFLINE_AGE) values.createdAt = new Date(t);
  }
  return values;
}

async function insertCapture(values: typeof captures.$inferInsert): Promise<{ id: string }> {
  const [row] = await db.insert(captures).values(values).onConflictDoNothing({ target: captures.id }).returning({ id: captures.id });
  // A conflict means this exact dump was already delivered (an earlier attempt succeeded).
  return row ?? { id: values.id! };
}

export async function createCapture(text: string, origin?: CaptureOrigin): Promise<{ id: string }> {
  const userId = await requireUserId();
  const rawText = typeof text === "string" ? text.trim() : "";
  if (!rawText) throw new Error("Nothing to save");
  if (rawText.length > MAX_CAPTURE_LENGTH) throw new Error("That dump is too long");

  const suggestedKind = guessKind(rawText);
  const row = await insertCapture({
    ...originValues(origin),
    userId,
    rawText,
    source: "text",
    suggestedKind,
    suggestedBy: suggestedKind ? "rules" : null,
  });
  refresh();
  return row;
}

/** Saves a voice dump: the transcript (possibly edited) plus the private audio file uploaded from the browser. */
export async function createVoiceCapture(text: string, audioUrl: string, origin?: CaptureOrigin): Promise<{ id: string }> {
  const userId = await requireUserId();
  const url = checkAudioUrl(userId, audioUrl);
  const rawText = (typeof text === "string" ? text.trim() : "").slice(0, MAX_CAPTURE_LENGTH) || "🎙️ Voice note";
  const suggestedKind = guessKind(rawText);
  const row = await insertCapture({
    ...originValues(origin),
    userId,
    rawText,
    source: "voice",
    audioUrl: url,
    suggestedKind,
    suggestedBy: suggestedKind ? "rules" : null,
  });
  refresh();
  return row;
}

/**
 * Saves a photo dump: the photo (already shrunk and stripped of hidden data in the browser, then uploaded
 * straight to the private Blob store) plus an optional caption. The caption alone drives the smart guess.
 */
export async function createPhotoCapture(caption: string, photoUrl: string, origin?: CaptureOrigin): Promise<{ id: string }> {
  const userId = await requireUserId();
  const url = checkBlobUrl(userId, photoUrl, "photo");
  const text = (typeof caption === "string" ? caption.trim() : "").slice(0, MAX_CAPTURE_LENGTH);
  const suggestedKind = text ? guessKind(text) : null;
  const row = await insertCapture({
    ...originValues(origin),
    userId,
    rawText: text || "📷 Photo",
    source: "photo",
    photoUrl: url,
    suggestedKind,
    suggestedBy: suggestedKind ? "rules" : null,
  });
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
    .select({ rawText: captures.rawText, createdAt: captures.createdAt, photoUrl: captures.photoUrl })
    .from(captures)
    .where(and(eq(captures.id, captureId), eq(captures.userId, userId), eq(captures.status, "inbox")));
  if (!capture) throw new Error("That dump isn't in your inbox anymore");
  // A photo can't be split, and splitting its caption would leave the photo behind.
  if (capture.photoUrl) throw new Error("Photo dumps can't be split");

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

// ---------------------------------------------------------------------------
// Checkpoints ("Where did I leave off?")
// ---------------------------------------------------------------------------

export type CheckpointInput = {
  transcript?: string;
  nextStep?: string;
  label?: string;
  projectTag?: string;
  link?: string;
  audioUrl?: string | null;
};

/** Saves a "here's where I was" bookmark before switching tasks. */
export async function createCheckpoint(input: CheckpointInput): Promise<{ id: string }> {
  const userId = await requireUserId();
  const transcript = clip(input.transcript, MAX_CAPTURE_LENGTH);
  const nextStep = clip(input.nextStep, 500) || null;
  if (!transcript && !nextStep && !input.audioUrl) throw new Error("Nothing to save");

  // Links are only kept if they're plain web addresses (no javascript: or similar).
  let link: string | null = null;
  const rawLink = clip(input.link, 2000);
  if (rawLink) {
    try {
      const u = new URL(/^[a-z][a-z0-9+.-]*:/i.test(rawLink) ? rawLink : `https://${rawLink}`);
      if (u.protocol === "https:" || u.protocol === "http:") link = u.toString();
    } catch {
      // not a usable link; drop it rather than fail the whole save
    }
  }

  const [row] = await db
    .insert(checkpoints)
    .values({
      userId,
      transcript,
      nextStep,
      label: clip(input.label, 200) || null,
      projectTag: clip(input.projectTag, 60) || null,
      link,
      audioUrl: input.audioUrl ? checkAudioUrl(userId, input.audioUrl) : null,
    })
    .returning({ id: checkpoints.id });
  refresh();
  return row;
}

async function updateCheckpoint(id: string, values: Partial<typeof checkpoints.$inferInsert>) {
  const userId = await requireUserId();
  await db
    .update(checkpoints)
    .set(values)
    .where(and(eq(checkpoints.id, id), eq(checkpoints.userId, userId)));
  refresh();
}

/** "I'm back on it": moves the bookmark to history. */
export async function resumeCheckpoint(id: string) {
  await updateCheckpoint(id, { resumedAt: new Date() });
}

export async function unresumeCheckpoint(id: string) {
  await updateCheckpoint(id, { resumedAt: null });
}

/** "Let it go": the task doesn't need finishing after all. */
export async function dismissCheckpoint(id: string) {
  await updateCheckpoint(id, { dismissedAt: new Date() });
}

export async function undismissCheckpoint(id: string) {
  await updateCheckpoint(id, { dismissedAt: null });
}

/** "Still relevant": resets the 7-day nudge (updated_at bumps automatically). */
export async function keepCheckpoint(id: string) {
  await updateCheckpoint(id, { updatedAt: new Date() });
}
