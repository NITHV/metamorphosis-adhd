import { Suspense } from "react";
import { getCurrentUser } from "@/lib/auth";
import { SignOutButton } from "@/components/sign-out-button";

export default function HomePage() {
  return (
    <main className="mx-auto flex w-full max-w-2xl flex-1 flex-col px-4 py-6">
      <header className="flex items-center justify-between">
        <h1 className="text-xl font-semibold tracking-tight">Brain Dump</h1>
        <Suspense fallback={null}>
          <UserMenu />
        </Suspense>
      </header>

      <Suspense fallback={<p className="mt-16 text-center text-muted">Loading…</p>}>
        <Home />
      </Suspense>
    </main>
  );
}

async function UserMenu() {
  const user = await getCurrentUser();
  return (
    <div className="flex items-center gap-3">
      <span className="hidden text-sm text-muted sm:inline">{user.email}</span>
      <SignOutButton />
    </div>
  );
}

async function Home() {
  const user = await getCurrentUser();
  const firstName = user.name.split(" ")[0] || "there";
  return (
    <section className="mt-16 flex flex-col items-center text-center">
      <p className="text-muted">Hi {firstName} 👋</p>
      <h2 className="mt-2 text-3xl font-semibold tracking-tight">What&apos;s on your mind?</h2>
      <div className="mt-8 w-full rounded-2xl border border-dashed border-border bg-card p-8 text-muted">
        The Dump button arrives in the next step.
      </div>
    </section>
  );
}
