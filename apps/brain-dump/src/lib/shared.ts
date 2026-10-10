"use client";

// "Share to Brain Dump" (design doc §2): Android hands shared photos/text to our service worker,
// which parks them in IndexedDB (no sign-in needed, works offline) and opens the app. The app then
// turns each parked share into normal dumps here, through the same outbox as everything else.

import { enqueuePhoto, enqueueText, openDb, SHARED_STORE } from "@/lib/outbox";
import { MAX_CAPTURE_LENGTH } from "@/lib/limits";
import { shrinkPhoto } from "@/lib/photo";

/** What public/sw.js stores for one share. Keep in sync with handleShare() there. */
type SharedEntry = { id: string; text: string; files: Blob[]; createdAt: string };

export type ShareResult = { dumped: number; failed: number };

async function readAll(): Promise<SharedEntry[]> {
  const db = await openDb();
  try {
    return await new Promise((resolve, reject) => {
      const req = db.transaction(SHARED_STORE, "readonly").objectStore(SHARED_STORE).getAll();
      req.onsuccess = () => resolve(req.result as SharedEntry[]);
      req.onerror = () => reject(req.error);
    });
  } finally {
    db.close();
  }
}

async function remove(id: string) {
  const db = await openDb();
  try {
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction(SHARED_STORE, "readwrite");
      tx.objectStore(SHARED_STORE).delete(id);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  } finally {
    db.close();
  }
}

/**
 * Moves everything shared into the app into the outbox as dumps. Each share is removed only after
 * its dumps are safely in the outbox, so a crash halfway can't lose it (at worst it's dumped twice).
 */
export function takeShared(userId: string): Promise<ShareResult> {
  // Overlapping calls (two Inbox views, React re-running effects) share one run, so the same
  // share can't be picked up twice.
  running ??= drain(userId).finally(() => {
    running = null;
  });
  return running;
}

let running: Promise<ShareResult> | null = null;

async function drain(userId: string): Promise<ShareResult> {
  let entries: SharedEntry[];
  try {
    entries = await readAll();
  } catch {
    return { dumped: 0, failed: 0 }; // storage unavailable: nothing was parked
  }

  const result: ShareResult = { dumped: 0, failed: 0 };
  for (const entry of entries) {
    const text = (entry.text ?? "").trim().slice(0, MAX_CAPTURE_LENGTH);
    const createdAt = new Date(entry.createdAt);
    const images = (entry.files ?? []).filter((f) => f.type.startsWith("image/"));

    if (images.length === 0) {
      if (text) {
        await enqueueText(userId, text);
        result.dumped++;
      }
    } else {
      let saved = 0;
      for (const image of images) {
        try {
          // Shared text (if any) becomes the caption of each photo.
          await enqueuePhoto(userId, text, await shrinkPhoto(image), createdAt);
          saved++;
        } catch {
          result.failed++;
        }
      }
      result.dumped += saved;
      // If none of this share's photos could be read, keep at least the words.
      if (saved === 0 && text) {
        await enqueueText(userId, text);
        result.dumped++;
      }
    }
    await remove(entry.id);
  }
  return result;
}
