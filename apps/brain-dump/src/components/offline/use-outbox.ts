"use client";

import { useSyncExternalStore } from "react";
import { outbox } from "@/lib/outbox";

/** Dumps saved on this device that haven't reached the server yet. */
export function useOutbox() {
  return useSyncExternalStore(outbox.subscribe, outbox.getSnapshot, outbox.getServerSnapshot);
}

/** True when the last delivery attempt failed, e.g. Wi-Fi is on but the server can't be reached. */
export function useOutboxStalled() {
  return useSyncExternalStore(outbox.subscribe, outbox.isStalled, () => false);
}
