"use client";

import { useEffect } from "react";
import { useOffline } from "next/offline";
import { startOutboxSync } from "@/lib/outbox";
import { useOutbox, useOutboxStalled } from "./use-outbox";

/** Background services for signed-in pages: outbox delivery and the offline-capable service worker. */
export function AppServices({ userId }: { userId: string }) {
  useEffect(() => startOutboxSync(userId), [userId]);

  useEffect(() => {
    // Only in production: a service worker caching dev builds causes confusing stale pages.
    if (process.env.NODE_ENV !== "production" || !("serviceWorker" in navigator)) return;
    navigator.serviceWorker.register("/sw.js", { scope: "/" }).catch(() => {});
  }, []);

  return null;
}

/** Tells you when you're offline, and that nothing you dump is being lost. */
export function OfflineBanner({ userId }: { userId: string }) {
  const isOffline = useOffline();
  const waiting = useOutbox().filter((i) => i.userId === userId).length;
  const stalled = useOutboxStalled();
  if (!isOffline && waiting === 0) return null;

  const dumps = `${waiting} ${waiting === 1 ? "dump" : "dumps"}`;
  const message = isOffline
    ? waiting > 0
      ? `Offline · ${dumps} saved on this device. They'll upload when you're back online.`
      : "Offline · You can keep dumping. It'll upload when you're back online."
    : stalled
      ? `Can't reach Brain Dump right now · ${dumps} saved on this device. We'll keep trying.`
      : `Uploading ${dumps}…`;
  const warn = isOffline || stalled;

  return (
    <div
      role="status"
      className="mx-4 mb-3 flex items-center gap-2 rounded-xl px-3 py-2 text-sm font-medium md:mx-8"
      style={
        warn
          ? { background: "var(--pill-orange-soft)", color: "var(--foreground)" }
          : { background: "var(--pill-blue-soft)", color: "var(--pill-blue)" }
      }
    >
      <span aria-hidden>{isOffline ? "📴" : stalled ? "⚠️" : "⏳"}</span>
      {message}
    </div>
  );
}
