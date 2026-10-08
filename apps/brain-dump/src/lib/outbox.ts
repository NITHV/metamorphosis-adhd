"use client";

// A durable outbox for dumps. Every dump is written to IndexedDB on the phone *first*, then
// delivered to the server. If the network is down, or the app is closed before delivery, it
// stays here and is sent next time there's a connection. Each entry carries its own id, which
// the server reuses, so delivering twice can never create a duplicate.

import { upload } from "@vercel/blob/client";
import { createCapture, createVoiceCapture } from "@/app/actions";

export type OutboxItem = {
  id: string;
  userId: string;
  kind: "text" | "voice";
  text: string;
  audio?: Blob;
  createdAt: string;
  attempts: number;
};

const DB_NAME = "brain-dump";
const STORE = "outbox";

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, 1);
    req.onupgradeneeded = () => req.result.createObjectStore(STORE, { keyPath: "id" });
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

async function withStore<T>(mode: IDBTransactionMode, fn: (store: IDBObjectStore) => IDBRequest<T>): Promise<T> {
  const db = await openDb();
  try {
    return await new Promise<T>((resolve, reject) => {
      const tx = db.transaction(STORE, mode);
      const req = fn(tx.objectStore(STORE));
      tx.oncomplete = () => resolve(req.result);
      tx.onerror = () => reject(tx.error);
      tx.onabort = () => reject(tx.error);
    });
  } finally {
    db.close();
  }
}

// ---------------------------------------------------------------------------
// A tiny external store so components can subscribe with useSyncExternalStore.
// ---------------------------------------------------------------------------

let items: OutboxItem[] = [];
let syncing = false;
/** True after a delivery attempt failed (offline, or the server unreachable), until one succeeds. */
let stalled = false;
const listeners = new Set<() => void>();
const emit = () => listeners.forEach((l) => l());
const EMPTY: OutboxItem[] = [];

export const outbox = {
  subscribe(listener: () => void) {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
  getSnapshot: () => items,
  getServerSnapshot: () => EMPTY,
  isSyncing: () => syncing,
  isStalled: () => stalled,
};

async function reload() {
  try {
    const all = await withStore<OutboxItem[]>("readonly", (s) => s.getAll() as IDBRequest<OutboxItem[]>);
    items = all.sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  } catch {
    items = []; // private mode or storage blocked: the outbox is just unavailable
  }
  emit();
}

async function put(item: OutboxItem) {
  await withStore("readwrite", (s) => s.put(item));
  await reload();
}

async function remove(id: string) {
  await withStore("readwrite", (s) => s.delete(id));
  await reload();
}

/** Adds a text dump. Resolves once it's safely stored on the device (not when delivered). */
export async function enqueueText(userId: string, text: string): Promise<OutboxItem> {
  const item: OutboxItem = { id: crypto.randomUUID(), userId, kind: "text", text, createdAt: new Date().toISOString(), attempts: 0 };
  await saveOrDeliver(item);
  return item;
}

/** Adds a voice dump (audio + transcript). */
export async function enqueueVoice(userId: string, text: string, audio: Blob): Promise<OutboxItem> {
  const item: OutboxItem = { id: crypto.randomUUID(), userId, kind: "voice", text, audio, createdAt: new Date().toISOString(), attempts: 0 };
  await saveOrDeliver(item);
  return item;
}

async function saveOrDeliver(item: OutboxItem) {
  try {
    await put(item);
  } catch {
    // No IndexedDB (rare: private browsing on some browsers). Deliver directly instead.
    await deliver(item);
    return;
  }
  void sync();
}

async function deliver(item: OutboxItem) {
  const origin = { id: item.id, createdAt: item.createdAt };
  if (item.kind === "text") {
    await createCapture(item.text, origin);
    return;
  }
  const audio = item.audio!;
  const ext = audio.type.includes("mp4") ? "m4a" : audio.type.includes("ogg") ? "ogg" : "webm";
  // Same path on every attempt, so a retried upload replaces rather than duplicates.
  const blob = await upload(`voice/${item.userId}/${item.id}.${ext}`, audio, {
    access: "private",
    handleUploadUrl: "/api/audio/upload",
    contentType: audio.type || "audio/webm",
  });
  await createVoiceCapture(item.text, blob.url, origin);
}

let currentUserId: string | null = null;

/** Sends everything waiting, oldest first. Safe to call often; only one run happens at a time. */
export async function sync(): Promise<void> {
  if (syncing || !currentUserId) return;
  syncing = true;
  emit();
  try {
    await reload();
    // Only send this account's dumps (someone else may have used this phone before).
    const mine = items.filter((i) => i.userId === currentUserId).reverse();
    for (const item of mine) {
      if (typeof navigator !== "undefined" && !navigator.onLine) break;
      // With Next's offline mode, a request that can't reach the server waits (and retries)
      // instead of failing, so treat a long wait as "stuck" for the banner.
      const slow = setTimeout(() => {
        stalled = true;
        emit();
      }, 8000);
      try {
        await deliver(item);
        stalled = false;
        await remove(item.id);
      } catch {
        stalled = true;
        await put({ ...item, attempts: item.attempts + 1 }).catch(() => {});
        break; // probably offline; try again on the next trigger
      } finally {
        clearTimeout(slow);
      }
    }
  } finally {
    syncing = false;
    emit();
  }
}

/** Starts background delivery for the signed-in user. Returns a cleanup function. */
export function startOutboxSync(userId: string): () => void {
  currentUserId = userId;
  void reload().then(sync);
  const onOnline = () => void sync();
  const onVisible = () => document.visibilityState === "visible" && void sync();
  window.addEventListener("online", onOnline);
  document.addEventListener("visibilitychange", onVisible);
  const timer = setInterval(() => items.length > 0 && void sync(), 30_000);
  return () => {
    window.removeEventListener("online", onOnline);
    document.removeEventListener("visibilitychange", onVisible);
    clearInterval(timer);
    if (currentUserId === userId) currentUserId = null;
  };
}
