import type { Metadata } from "next";
import Link from "next/link";
import { Suspense } from "react";
import { SignOutButton } from "@/components/sign-out-button";
import { DeleteEverything } from "@/components/settings/delete-everything";
import { getCurrentUser } from "@/lib/auth";
import { getDataSummary, type DataSummary } from "@/lib/search";

export const metadata: Metadata = { title: "Settings · Brain Dump" };

export default function SettingsPage() {
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-3xl font-extrabold tracking-tight">Settings</h1>
        <p className="mt-1 text-muted">Your account, your data, and how to get the most out of Brain Dump.</p>
      </div>

      <Suspense fallback={<Card title="Account"><p className="text-muted">…</p></Card>}>
        <AccountCard />
      </Suspense>

      <Suspense fallback={<Card title="Your data"><p className="text-muted">Counting…</p></Card>}>
        <DataCard />
      </Suspense>

      <Card title="Quicker dumping">
        <ul className="flex list-disc flex-col gap-2 pl-5 text-[15px]">
          <li>
            <b>Add to home screen</b> (browser menu → Install / Add to Home screen) so Brain Dump opens like an app,
            even offline.
          </li>
          <li>
            <b>Long-press the icon</b> for Type, Voice, Photo and Pause shortcuts.
          </li>
          <li>
            <b>Share → Brain Dump</b> from your gallery or any app to dump photos, links and text.
          </li>
          <li className="hidden md:list-item">
            <b>Ctrl/⌘ K</b> jumps to the dump box from anywhere.
          </li>
        </ul>
      </Card>

      <Card title="About">
        <p className="text-[15px]">
          Free, private by default, and no AI: your dumps are never sent to an AI service.{" "}
          <Link href="/privacy" className="font-semibold underline underline-offset-2">
            Privacy
          </Link>
        </p>
      </Card>

      <Suspense fallback={null}>
        <DangerCard />
      </Suspense>
    </div>
  );
}

function Card({ title, children, tone }: { title: string; children: React.ReactNode; tone?: "danger" }) {
  return (
    <section
      className="chunky rounded-2xl bg-card p-5"
      style={tone === "danger" ? { borderColor: "var(--pill-red)" } : undefined}
    >
      <h2 className="mb-3 text-lg font-bold" style={tone === "danger" ? { color: "var(--pill-red)" } : undefined}>
        {title}
      </h2>
      {children}
    </section>
  );
}

async function AccountCard() {
  const user = await getCurrentUser();
  return (
    <Card title="Account">
      <p className="font-semibold">{user.name}</p>
      <p className="text-muted">{user.email}</p>
      <div className="mt-3 w-fit rounded-xl border-2 border-hairline">
        <SignOutButton />
      </div>
    </Card>
  );
}

function summaryLine(s: DataSummary) {
  const part = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`;
  return [
    part(s.dumps, "dump", "dumps"),
    part(s.items, "filed item", "filed items"),
    part(s.paused, "paused note", "paused notes"),
    part(s.photos, "photo", "photos"),
    part(s.voiceNotes, "voice note", "voice notes"),
  ].join(" · ");
}

async function DataCard() {
  const user = await getCurrentUser();
  const summary = await getDataSummary(user.id);
  return (
    <Card title="Your data">
      <p className="text-[15px]">{summaryLine(summary)}</p>
      <a
        href="/api/export"
        download
        className="chunky-sm press mt-4 inline-flex h-11 items-center rounded-xl bg-card px-4 font-semibold"
      >
        ⬇ Download a copy (JSON)
      </a>
    </Card>
  );
}

async function DangerCard() {
  const user = await getCurrentUser();
  const summary = await getDataSummary(user.id);
  const empty = summary.dumps + summary.items + summary.paused === 0;
  return (
    <Card title="Delete everything" tone="danger">
      <p className="text-[15px]">
        Removes every dump, filed item, paused note, photo and voice note, here and on this device. Your account stays,
        so you can start fresh. <b>This can&apos;t be undone</b>, so download a copy first if you might want it.
      </p>
      <DeleteEverything empty={empty} />
    </Card>
  );
}
