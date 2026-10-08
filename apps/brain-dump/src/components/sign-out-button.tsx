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
        router.push("/sign-in");
        router.refresh();
      }}
      className="w-full rounded-lg px-3 py-2 text-left text-sm transition hover:bg-hairline/60"
    >
      Sign out
    </button>
  );
}
