"use client";

import { useRouter } from "next/navigation";
import { authClient } from "@repo/auth/client";

export function SignOutButton() {
  const router = useRouter();
  return (
    <button
      type="button"
      onClick={async () => {
        await authClient.signOut();
        // Pages cached for offline use contain this account's data; drop them on sign-out.
        if ("caches" in window) {
          const keys = await caches.keys();
          await Promise.all(keys.filter((k) => k.startsWith("pages-")).map((k) => caches.delete(k)));
        }
        router.push("/sign-in");
        router.refresh();
      }}
      className="w-full rounded-lg px-3 py-2 text-left text-sm transition hover:bg-hairline/60"
    >
      Sign out
    </button>
  );
}
