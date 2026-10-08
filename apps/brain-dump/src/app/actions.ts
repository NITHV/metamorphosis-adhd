"use server";

import { and, eq } from "drizzle-orm";
import { headers } from "next/headers";
import { refresh } from "next/cache";
import { auth } from "@repo/auth/server";
import { db, schema } from "@repo/db";
import { MAX_CAPTURE_LENGTH } from "@/lib/limits";

const { captures } = schema;


// Server Actions are public endpoints, so each one re-checks the session itself.
async function requireUserId(): Promise<string> {
  const session = await auth.api.getSession({ headers: await headers() });
  if (!session) throw new Error("Not signed in");
  return session.user.id;
}

export async function createCapture(text: string): Promise<{ id: string }> {
  const userId = await requireUserId();
  const rawText = typeof text === "string" ? text.trim() : "";
  if (!rawText) throw new Error("Nothing to save");
  if (rawText.length > MAX_CAPTURE_LENGTH) throw new Error("That dump is too long");

  const [row] = await db
    .insert(captures)
    .values({ userId, rawText, source: "text" })
    .returning({ id: captures.id });
  refresh();
  return row;
}

async function setStatus(id: string, status: "inbox" | "archived") {
  const userId = await requireUserId();
  await db
    .update(captures)
    .set({ status })
    .where(and(eq(captures.id, id), eq(captures.userId, userId)));
  refresh();
}

export async function archiveCapture(id: string) {
  await setStatus(id, "archived");
}

export async function restoreCapture(id: string) {
  await setStatus(id, "inbox");
}
