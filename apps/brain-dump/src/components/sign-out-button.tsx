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
      className="text-sm text-muted transition hover:text-foreground"
    >
      Sign out
    </button>
  );
}
