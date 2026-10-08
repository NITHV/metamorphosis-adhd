import { isGoogleEnabled } from "@repo/auth/server";
import { SignInForm } from "./sign-in-form";

export default function SignInPage() {
  return (
    <main className="flex flex-1 items-center justify-center px-4 py-12">
      <div className="w-full max-w-sm">
        <h1 className="text-3xl font-semibold tracking-tight">Brain Dump</h1>
        <p className="mt-2 text-muted">Get it out of your head. Sort it later.</p>
        <div className="mt-8 rounded-2xl border border-border bg-card p-6 shadow-sm">
          <SignInForm googleEnabled={isGoogleEnabled} />
        </div>
      </div>
    </main>
  );
}
