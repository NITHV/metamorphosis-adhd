"use client";

import { formatRelative } from "@/components/time";
import { useIsClient } from "@/components/use-is-client";

/** "3h ago", rendered only in the browser because it depends on the viewer's clock. */
export function RelativeText({ iso }: { iso: string }) {
  const isClient = useIsClient();
  return <span suppressHydrationWarning>{isClient ? formatRelative(new Date(iso)) : ""}</span>;
}
