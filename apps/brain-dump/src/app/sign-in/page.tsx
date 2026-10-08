import Link from "next/link";
import { isGoogleEnabled } from "@repo/auth/server";
import { Mascot } from "@/components/mascot";
import { SignInForm } from "./sign-in-form";

export default function SignInPage() {
  return (
    <main className="flex flex-1 items-center justify-center px-4 py-12">
      <div className="w-full max-w-sm">
        <div className="chunky flex items-center gap-4 rounded-3xl bg-brand p-5 text-brand-foreground">
          <Mascot className="h-20 w-20 shrink-0" />
          <div>
            <h1 className="text-2xl font-extrabold tracking-tight">Brain Dump</h1>
            <p className="mt-0.5 text-sm font-medium opacity-80">Get it out of your head. Sort it later.</p>
          </div>
        </div>
        <div className="chunky mt-5 rounded-3xl bg-card p-6">
          <SignInForm googleEnabled={isGoogleEnabled} />
        </div>
        <p className="mt-6 text-center text-xs text-muted">
          <Link href="/privacy" className="underline-offset-4 hover:underline">
            Privacy policy
          </Link>
        </p>
      </div>
    </main>
  );
}
