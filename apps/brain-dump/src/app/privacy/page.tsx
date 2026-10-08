import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = {
  title: "Privacy Policy · Brain Dump",
};

export default function PrivacyPage() {
  return (
    <main className="mx-auto w-full max-w-2xl flex-1 px-4 py-10 leading-relaxed">
      <Link href="/" className="text-sm text-[var(--pill-blue)] underline-offset-4 hover:underline">
        ← Brain Dump
      </Link>
      <h1 className="mt-4 text-3xl font-semibold tracking-tight">Privacy Policy</h1>
      <p className="mt-2 text-sm text-muted">Last updated: October 8, 2026</p>

      <div className="mt-8 flex flex-col gap-6 [&_h2]:text-lg [&_h2]:font-semibold [&_ul]:list-disc [&_ul]:pl-5">
        <p>
          Brain Dump is a personal, non-commercial project. This page explains what it stores and why.
        </p>

        <section>
          <h2>What we collect</h2>
          <ul>
            <li>
              <strong>Account details:</strong> your name, email address and profile picture (if you sign
              in with Google), or the name and email you enter when creating an account.
            </li>
            <li>
              <strong>What you put in the app:</strong> your notes, voice recordings, and anything else you
              save.
            </li>
            <li>
              <strong>Session data:</strong> a sign-in cookie, plus your IP address and browser type
              recorded with each session for security.
            </li>
          </ul>
        </section>

        <section>
          <h2>How it&apos;s used</h2>
          <p>
            Only to run the app for you: signing you in, saving your notes and showing them back to you.
            Your data is never sold, shared for advertising, or shown to other users.
          </p>
        </section>

        <section>
          <h2>Google sign-in</h2>
          <p>
            If you sign in with Google, the app only requests your basic profile (name, email, picture).
            It does not access your Gmail, Drive, Calendar or any other Google data.
          </p>
        </section>

        <section>
          <h2>Where it&apos;s stored</h2>
          <p>
            Data is stored with the app&apos;s hosting providers: Vercel (hosting and file storage) and
            Neon (database). Passwords are stored only as secure hashes.
          </p>
        </section>

        <section>
          <h2>Optional AI features</h2>
          <p>
            An optional &ldquo;AI assist&rdquo; feature is off by default. If you turn it on, the text of
            your notes (never notes marked private) is sent to a third-party AI service to suggest how to
            sort them. You can turn it off at any time.
          </p>
        </section>

        <section>
          <h2>Deleting your data</h2>
          <p>
            You can ask for your account and all of its data to be deleted at any time by opening an issue
            on the project&apos;s{" "}
            <a
              href="https://github.com/NITHV/metamorphosis-adhd/issues"
              className="text-[var(--pill-blue)] underline-offset-4 hover:underline"
            >
              GitHub page
            </a>
            . A self-serve &ldquo;delete everything&rdquo; button is planned.
          </p>
        </section>

        <section>
          <h2>Changes</h2>
          <p>If this policy changes, the date at the top of this page will be updated.</p>
        </section>
      </div>
    </main>
  );
}
